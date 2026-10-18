package com.agentguard.apikeys;

import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/agents/{agentId}/api-keys")
public class ApiKeyController {
  private final AgentApiKeyService service;

  public ApiKeyController(AgentApiKeyService service) {
    this.service = service;
  }

  /** Issues a key — the raw value is returned ONCE. Store it; it can't be retrieved later. */
  @PostMapping
  public ResponseEntity<?> issue(@PathVariable UUID agentId,
      @RequestBody(required = false) Map<String, String> body) {
    var issued = service.issue(agentId, body == null ? null : body.get("label"));
    return ResponseEntity.ok(Map.of(
        "keyId", issued.keyId().toString(),
        "apiKey", issued.rawKey(),
        "warning", "store this now — it will never be shown again"));
  }

  @PostMapping("/{keyId}/revoke")
  public ResponseEntity<?> revoke(@PathVariable UUID agentId, @PathVariable UUID keyId) {
    service.revoke(keyId);
    return ResponseEntity.ok(Map.of("revoked", true));
  }
}
