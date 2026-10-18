package com.agentguard.policy;

import com.agentguard.auth.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/policies")
public class PolicyController {
  private final PolicyService service;
  private final UserRepository users;

  public PolicyController(PolicyService service, UserRepository users) {
    this.service = service;
    this.users = users;
  }

  @PostMapping
  public ResponseEntity<?> create(@RequestBody Map<String, String> body,
      Authentication auth) {
    var createdBy = users.findByUsername(auth.getName()).map(u -> u.getId());
    if (createdBy.isEmpty()) {
      return ResponseEntity.status(401).body(Map.of("error", "unknown user"));
    }
    try {
      Policy p = new Policy();
      p.setAgentId(UUID.fromString(body.get("agentId")));
      p.setToolId(UUID.fromString(body.get("toolId")));
      p.setEffect(body.get("effect"));
      if (body.get("maxCalls") != null) p.setMaxCalls(Integer.valueOf(body.get("maxCalls")));
      p.setCreatedBy(createdBy.get());
      return ResponseEntity.ok(service.save(p));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
  }

  @GetMapping("/agent/{agentId}")
  public List<Policy> forAgent(@PathVariable UUID agentId) {
    return service.forAgent(agentId);
  }
}
