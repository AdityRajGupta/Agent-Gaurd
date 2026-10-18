package com.agentguard.policy;

import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Deterministic policy engine. Policies are data, not code.
 * Evaluation order for an (agent, tool) pair:
 *   1. No policy row        → DENY (default-deny)
 *   2. effect = DENY        → DENY, citing the rule id
 *   3. max_calls exceeded   → APPROVE (human must allow the extra call)
 *   4. effect = APPROVE     → APPROVE
 *   5. effect = ALLOW       → ALLOW
 *
 * The LLM never decides here — this is pure deterministic code (PRD §6).
 */
@Service
public class PolicyService {
  private final PolicyRepository policies;

  public PolicyService(PolicyRepository policies) {
    this.policies = policies;
  }

  public record Decision(String effect, String reason, UUID policyId) {}

  /**
   * @param callsSoFar number of successful calls this agent made to this tool
   *                   in the current run (Phase 2 wires the counter).
   */
  public Decision evaluate(UUID agentId, UUID toolId, long callsSoFar) {
    return policies.findByAgentIdAndToolId(agentId, toolId)
        .map(p -> {
          if ("DENY".equals(p.getEffect())) {
            return new Decision("DENY",
                "Denied by policy " + p.getId() + ": tool not permitted for this agent",
                p.getId());
          }
          if (p.getMaxCalls() != null && callsSoFar >= p.getMaxCalls()) {
            return new Decision("APPROVE",
                "Policy " + p.getId() + " allows " + p.getMaxCalls()
                    + " calls; " + callsSoFar + " used — human approval required",
                p.getId());
          }
          if ("APPROVE".equals(p.getEffect())) {
            return new Decision("APPROVE",
                "Policy " + p.getId() + " requires human approval for this tool",
                p.getId());
          }
          return new Decision("ALLOW", "Allowed by policy " + p.getId(), p.getId());
        })
        .orElseGet(() -> new Decision("DENY",
            "Default-deny: no policy row for this agent+tool pair", null));
  }

  public Policy save(Policy policy) {
    if (!java.util.Set.of("ALLOW", "DENY", "APPROVE").contains(policy.getEffect())) {
      throw new IllegalArgumentException("effect must be ALLOW, DENY or APPROVE");
    }
    return policies.save(policy);
  }

  public java.util.List<Policy> forAgent(UUID agentId) {
    return policies.findByAgentId(agentId);
  }
}
