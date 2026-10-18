package com.agentguard.rag;

import com.agentguard.agent.AgentRepository;
import com.agentguard.audit.AuditService;
import com.agentguard.cases.AnomalyCase;
import com.agentguard.cases.AnomalyCaseRepository;
import com.agentguard.events.ToolDefinitionRepository;
import com.agentguard.events.ToolEvent;
import com.agentguard.events.ToolEventRepository;
import com.agentguard.policy.PolicyRepository;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RAG investigation (Phase 4). When a case is investigated:
 *   1. Gather evidence: agent's declared task, policy rows, the tool-call
 *      timeline, and similar past cases (pgvector similarity).
 *   2. An LLM drafts a summary + evidence timeline + containment proposal.
 *   3. A DETERMINISTIC citation validator checks every [evt:<id>] claim
 *      against real events; unverifiable claims are marked, not trusted.
 *   4. The summary is stored; a human still approves any containment.
 * The LLM proposes — it never executes, and its claims never bypass validation.
 */
@Service
public class InvestigationService {
  private static final Pattern CITE = Pattern.compile("\\[evt:([0-9a-fA-F-]{36})\\]");

  private final AnomalyCaseRepository cases;
  private final AgentRepository agents;
  private final ToolEventRepository events;
  private final ToolDefinitionRepository tools;
  private final PolicyRepository policies;
  private final EmbeddingRepository embeddings;
  private final LlmClient llm;
  private final EmbeddingClient embedder;
  private final AuditService audit;

  public InvestigationService(AnomalyCaseRepository cases, AgentRepository agents,
      ToolEventRepository events, ToolDefinitionRepository tools,
      PolicyRepository policies, EmbeddingRepository embeddings,
      LlmClient llm, EmbeddingClient embedder, AuditService audit) {
    this.cases = cases;
    this.agents = agents;
    this.events = events;
    this.tools = tools;
    this.policies = policies;
    this.embeddings = embeddings;
    this.llm = llm;
    this.embedder = embedder;
    this.audit = audit;
  }

  public record Investigation(String summary, String provider) {}

  @Transactional
  public Investigation investigate(UUID caseId) {
    AnomalyCase c = cases.findById(caseId)
        .orElseThrow(() -> new IllegalArgumentException("case not found: " + caseId));
    var agent = agents.findById(c.getAgentId())
        .orElseThrow(() -> new IllegalArgumentException("agent not found"));

    List<ToolEvent> timeline = events
        .findByAgentIdOrderByCreatedAtDesc(agent.getId()).stream()
        .filter(e -> c.getRunId() == null || c.getRunId().equals(e.getRunId()))
        .sorted(Comparator.comparing(ToolEvent::getCreatedAt))
        .limit(50).toList();
    Map<UUID, String> toolNames = tools.findAll().stream()
        .collect(Collectors.toMap(t -> t.getId(), t -> t.getName(), (a, b) -> a));

    String timelineText = timeline.stream()
        .map(e -> String.format("- %s | %s -> %s%s [evt:%s]",
            e.getCreatedAt(), toolNames.getOrDefault(e.getToolId(), "?"),
            e.getPolicyDecision(),
            e.getAnomalyScore() == null ? ""
                : String.format(" (score %.2f)", e.getAnomalyScore()),
            e.getId()))
        .collect(Collectors.joining("\n"));

    String policyText = policies.findByAgentId(agent.getId()).stream()
        .map(p -> String.format("- tool %s: %s%s",
            toolNames.getOrDefault(p.getToolId(), "?"), p.getEffect(),
            p.getMaxCalls() == null ? "" : " (max " + p.getMaxCalls() + " calls)"))
        .collect(Collectors.joining("\n"));

    String similarCases = findSimilarCases(agent, timeline);

    String system = """
        You are a security analyst investigating an AI agent anomaly.
        Rules: use ONLY the evidence provided. Every factual claim about what
        the agent did MUST cite its event as [evt:<id>] using the exact IDs from
        the timeline. Never invent event IDs, timestamps, or tool calls. If the
        evidence is thin, say so explicitly. You recommend actions; you do not
        take them.""";
    String user = String.format("""
        Agent: %s (risk tier: %s)
        Declared task: %s
        Case anomaly score: %.3f

        POLICY (what this agent is allowed to do):
        %s

        TOOL-CALL TIMELINE (cite IDs exactly as shown):
        %s

        SIMILAR PAST CASES:
        %s

        Write:
        1. Summary (2-3 sentences)
        2. Evidence timeline — each bullet MUST cite [evt:<id>]
        3. Recommended containment: one of PAUSE_AGENT, REVOKE_TOOL:<tool>,
           REQUIRE_APPROVAL, or NO_ACTION, with a one-line reason.""",
        agent.getName(), agent.getRiskTier(), agent.getDeclaredTask(), c.getScore(),
        policyText.isEmpty() ? "(none)" : policyText,
        timelineText.isEmpty() ? "(no events)" : timelineText,
        similarCases.isEmpty() ? "(none on file)" : similarCases);

    var result = llm.chat(system, user);
    Set<String> validIds = timeline.stream()
        .map(e -> e.getId().toString()).collect(Collectors.toSet());
    String validated = validateCitations(result.text(), validIds);

    String summary = validated
        + "\n\n---\n_investigated by " + result.provider() + "; "
        + "citations validated deterministically against recorded events._";
    c.setSummary(summary);
    c.setStatus("INVESTIGATING");
    cases.save(c);

    // Index this case for future similarity retrieval (best effort).
    embedder.embed("Agent " + agent.getName() + " task: " + agent.getDeclaredTask()
        + "\nTools: " + timeline.stream()
            .map(e -> toolNames.getOrDefault(e.getToolId(), "?"))
            .distinct().collect(Collectors.joining(","))
        + "\n" + summary)
        .ifPresent(vec -> {
          Embedding emb = new Embedding();
          emb.setSource("case");
          emb.setSourceId(c.getId());
          emb.setChunk(summary.substring(0, Math.min(2000, summary.length())));
          emb.setEmbedding(EmbeddingClient.toVectorLiteral(vec));
          embeddings.save(emb);
        });

    audit.log("system", "case.investigated", c.getId().toString(),
        Map.of("provider", result.provider()));
    return new Investigation(summary, result.provider());
  }

  /** Retrieve resolved past cases similar to this one (pgvector). Empty if unavailable. */
  private String findSimilarCases(com.agentguard.agent.AgentIdentity agent,
      List<ToolEvent> timeline) {
    try {
      var vec = embedder.embed("Agent task: " + agent.getDeclaredTask()
          + " tools: " + timeline.stream().map(e -> e.getToolId().toString())
              .distinct().collect(Collectors.joining(",")));
      if (vec.isEmpty()) return "";
      return embeddings.findSimilar("case", EmbeddingClient.toVectorLiteral(vec.get()), 3)
          .stream()
          .map(e -> cases.findById(e.getSourceId()))
          .filter(Optional::isPresent).map(Optional::get)
          .filter(past -> "RESOLVED".equals(past.getStatus())
              || "DISMISSED".equals(past.getStatus()))
          .map(past -> "- past case " + past.getId()
              + " (score " + String.format("%.2f", past.getScore()) + ", "
              + past.getStatus() + "): "
              + (past.getSummary() == null ? "(no summary)"
                  : past.getSummary().substring(0,
                      Math.min(300, past.getSummary().length()))))
          .collect(Collectors.joining("\n"));
    } catch (Exception e) {
      return ""; // retrieval is best-effort; the investigation stands on direct evidence
    }
  }

  /**
   * Deterministic citation check: every [evt:<id>] must reference a real
   * event from this case's timeline. Unverifiable citations are replaced
   * with [unverified] rather than trusted.
   */
  static String validateCitations(String text, Set<String> validIds) {
    Matcher m = CITE.matcher(text);
    StringBuffer out = new StringBuffer();
    while (m.find()) {
      String id = m.group(1);
      m.appendReplacement(out, validIds.contains(id) ? m.group(0) : "[unverified]");
    }
    m.appendTail(out);
    return out.toString();
  }
}
