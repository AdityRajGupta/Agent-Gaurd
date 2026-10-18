package com.agentguard.cases;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cases")
public class CaseController {
  private static final Set<String> STATUSES =
      Set.of("OPEN", "INVESTIGATING", "RESOLVED", "DISMISSED");

  private final AnomalyCaseRepository repo;

  public CaseController(AnomalyCaseRepository repo) {
    this.repo = repo;
  }

  @GetMapping
  public List<AnomalyCase> list(@RequestParam(required = false) String status) {
    if (status != null) return repo.findByStatusOrderByCreatedAtDesc(status);
    return repo.findAll();
  }

  @GetMapping("/{id}")
  public ResponseEntity<AnomalyCase> get(@PathVariable UUID id) {
    return repo.findById(id).map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @PatchMapping("/{id}")
  public ResponseEntity<?> setStatus(@PathVariable UUID id,
      @RequestBody Map<String, String> body) {
    String status = body.get("status");
    if (!STATUSES.contains(status)) {
      return ResponseEntity.badRequest().body(Map.of("error", "invalid status"));
    }
    return repo.findById(id).map(c -> {
      c.setStatus(status);
      return ResponseEntity.ok(repo.save(c));
    }).orElseGet(() -> ResponseEntity.notFound().build());
  }
}
