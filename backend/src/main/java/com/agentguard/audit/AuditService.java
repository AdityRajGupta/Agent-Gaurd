package com.agentguard.audit;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Append-only audit writer. No update/delete methods exist by design. */
@Service
public class AuditService {
  private final AuditRepository repo;

  public AuditService(AuditRepository repo) {
    this.repo = repo;
  }

  public void log(String actor, String action, String target, Map<String, String> detail) {
    String json = detail.entrySet().stream()
        .map(e -> "\"" + e.getKey() + "\":\"" + e.getValue() + "\"")
        .collect(Collectors.joining(",", "{", "}"));
    repo.save(new AuditLog(actor, action, target, json));
  }

  public List<AuditLog> recent() {
    return repo.findAllByOrderByCreatedAtDesc();
  }
}
