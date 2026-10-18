package com.agentguard.agent;

import com.agentguard.auth.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/agents")
public class AgentController {
  private final AgentRepository repo;
  private final UserRepository users;

  public AgentController(AgentRepository repo, UserRepository users) {
    this.repo = repo;
    this.users = users;
  }

  @GetMapping
  public List<AgentIdentity> list() {
    return repo.findAll();
  }

  @GetMapping("/{id}")
  public ResponseEntity<AgentIdentity> get(@PathVariable UUID id) {
    return repo.findById(id).map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @PostMapping
  public ResponseEntity<?> create(@RequestBody Map<String, String> body,
      Authentication auth) {
    var ownerId = users.findByUsername(auth.getName()).map(u -> u.getId());
    if (ownerId.isEmpty()) {
      return ResponseEntity.status(401).body(Map.of("error", "unknown user"));
    }
    AgentIdentity agent = new AgentIdentity();
    agent.setName(body.get("name"));
    agent.setDeclaredTask(body.getOrDefault("declaredTask", ""));
    agent.setRiskTier(body.getOrDefault("riskTier", "standard"));
    agent.setOwnerId(ownerId.get());
    agent.setStatus("ACTIVE");
    return ResponseEntity.ok(repo.save(agent));
  }
}
