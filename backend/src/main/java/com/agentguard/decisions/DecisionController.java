package com.agentguard.decisions;

import com.agentguard.auth.UserRepository;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Human-in-the-loop approvals. A tool call or case with an APPROVE decision
 * waits here until a human approves or denies it. The LLM can only propose.
 */
@RestController
@RequestMapping("/api/v1/decisions")
public class DecisionController {
  private final DecisionRepository repo;
  private final UserRepository users;

  public DecisionController(DecisionRepository repo, UserRepository users) {
    this.repo = repo;
    this.users = users;
  }

  @PostMapping
  public ResponseEntity<?> decide(@RequestBody Map<String, String> body,
      Authentication auth) {
    String decision = body.get("decision");
    if (!Set.of("APPROVE", "DENY").contains(decision)) {
      return ResponseEntity.badRequest().body(Map.of("error", "decision must be APPROVE or DENY"));
    }
    String caseId = body.get("caseId");
    String eventId = body.get("eventId");
    if ((caseId == null || caseId.isBlank()) && (eventId == null || eventId.isBlank())) {
      return ResponseEntity.badRequest()
          .body(Map.of("error", "caseId or eventId is required"));
    }
    UUID decidedBy = users.findByUsername(auth.getName())
        .map(u -> u.getId())
        .orElse(null);
    if (decidedBy == null) {
      return ResponseEntity.status(401).body(Map.of("error", "unknown user"));
    }
    Decision d = new Decision();
    if (caseId != null && !caseId.isBlank()) d.setCaseId(UUID.fromString(caseId));
    if (eventId != null && !eventId.isBlank()) d.setEventId(UUID.fromString(eventId));
    d.setDecidedBy(decidedBy);
    d.setDecision(decision);
    d.setReason(body.get("reason"));
    return ResponseEntity.ok(repo.save(d));
  }
}
