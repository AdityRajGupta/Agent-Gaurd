package com.agentguard.events;

import com.agentguard.agent.AgentRepository;
import com.agentguard.audit.AuditService;
import com.agentguard.cases.AnomalyCase;
import com.agentguard.cases.AnomalyCaseRepository;
import com.agentguard.ml.AnomalyScorer;
import com.agentguard.ml.FeatureService;
import com.agentguard.policy.PolicyService;
import com.agentguard.security.Hashing;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Policy enforcement proxy (PRD §4.2/4.3) + anomaly detection (Phase 3).
 * Every tool call: authenticate (API key) → evaluate policy → score behavior
 * → record event (idempotent on event_id) → if ALLOW, forward to MCP.
 *
 * Fail-closed: if the scorer is unreachable, high-sensitivity tools flip to
 * APPROVE instead of executing blind.
 */
@Service
public class ToolEventService {
  private final ToolEventRepository events;
  private final ToolDefinitionRepository tools;
  private final PolicyService policies;
  private final AuditService audit;
  private final McpClient mcp;
  private final FeatureService features;
  private final AnomalyScorer scorer;
  private final AnomalyCaseRepository cases;
  private final AgentRepository agents;
  private final double thresholdHigh;
  private final double thresholdStandard;
  private final double thresholdLow;

  public ToolEventService(ToolEventRepository events, ToolDefinitionRepository tools,
      PolicyService policies, AuditService audit, McpClient mcp,
      FeatureService features, AnomalyScorer scorer, AnomalyCaseRepository cases,
      AgentRepository agents,
      @Value("${agentguard.anomaly.threshold-high:0.5}") double thresholdHigh,
      @Value("${agentguard.anomaly.threshold-standard:0.7}") double thresholdStandard,
      @Value("${agentguard.anomaly.threshold-low:0.85}") double thresholdLow) {
    this.events = events;
    this.tools = tools;
    this.policies = policies;
    this.audit = audit;
    this.mcp = mcp;
    this.features = features;
    this.scorer = scorer;
    this.cases = cases;
    this.agents = agents;
    this.thresholdHigh = thresholdHigh;
    this.thresholdStandard = thresholdStandard;
    this.thresholdLow = thresholdLow;
  }

  public record InvokeRequest(String eventId, String toolName,
                             UUID runId, Map<String, Object> args) {}
  public record InvokeResult(String decision, String reason, UUID eventDbId,
                            String toolResult, Double anomalyScore, UUID caseId) {}

  @Transactional
  public InvokeResult invoke(UUID agentId, InvokeRequest req) {
    var existing = events.findByEventId(req.eventId());
    if (existing.isPresent()) {
      var e = existing.get();
      return new InvokeResult(e.getPolicyDecision(), "duplicate event_id — replayed",
          e.getId(), null, e.getAnomalyScore(), null);
    }

    var tool = tools.findByName(req.toolName()).orElse(null);
    if (tool == null) {
      return new InvokeResult("DENY", "unknown tool: " + req.toolName(),
          null, null, null, null);
    }
    boolean highSensitivity = "high".equals(tool.getDataSensitivity());

    long callsSoFar = events.countByAgentIdAndToolId(agentId, tool.getId());
    var decision = policies.evaluate(agentId, tool.getId(), callsSoFar);

    String finalDecision = decision.effect();
    String finalReason = decision.reason();
    Double anomalyScore = null;
    UUID caseId = null;

    if ("ALLOW".equals(decision.effect())) {
      // Phase 3: score the behavior window
      var score = scorer.score(agentId, features.features(agentId));
      anomalyScore = score.orElse(null);

      if (score.isPresent()) {
        double threshold = agents.findById(agentId)
            .map(a -> switch (a.getRiskTier()) {
              case "high" -> thresholdHigh;
              case "low" -> thresholdLow;
              default -> thresholdStandard;
            })
            .orElse(thresholdStandard);
        if (score.get() >= threshold) {
          AnomalyCase c = new AnomalyCase();
          c.setAgentId(agentId);
          c.setRunId(req.runId());
          c.setScore(score.get());
          c.setStatus("OPEN");
          cases.save(c);
          caseId = c.getId();
          audit.log("system", "anomaly.case.opened", c.getId().toString(),
              Map.of("agent", agentId.toString(),
                  "score", String.format("%.3f", score.get())));
          if (highSensitivity) {
            finalDecision = "APPROVE";
            finalReason = String.format(
                "Anomaly score %.3f >= threshold %.2f on high-sensitivity tool '%s'"
                    + " — human approval required (case %s)",
                score.get(), threshold, tool.getName(), c.getId());
          }
        }
      } else if (highSensitivity) {
        finalDecision = "APPROVE";
        finalReason = "Anomaly scorer unreachable — fail-closed: human approval "
            + "required for high-sensitivity tool '" + tool.getName() + "'";
      }
    }

    ToolEvent e = new ToolEvent();
    e.setEventId(req.eventId());
    e.setAgentId(agentId);
    e.setToolId(tool.getId());
    e.setRunId(req.runId());
    e.setArgsHash(Hashing.sha256Hex(canonical(req.args())));
    e.setPolicyDecision(finalDecision);
    e.setAnomalyScore(anomalyScore);
    events.save(e);

    audit.log("agent:" + agentId, "tool.invoke", tool.getName(),
        Map.of("decision", finalDecision,
            "score", anomalyScore == null ? "n/a" : String.format("%.3f", anomalyScore)));

    String toolResult = null;
    if ("ALLOW".equals(finalDecision)) {
      toolResult = mcp.invoke(tool.getName(), req.args());
    }
    return new InvokeResult(finalDecision, finalReason, e.getId(),
        toolResult, anomalyScore, caseId);
  }

  private static String canonical(Map<String, Object> args) {
    if (args == null) return "{}";
    return args.entrySet().stream().sorted(Map.Entry.comparingByKey())
        .map(en -> en.getKey() + "=" + en.getValue()).toList().toString();
  }
}
