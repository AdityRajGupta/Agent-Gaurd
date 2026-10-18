package com.agentguard.events;

import com.agentguard.apikeys.AgentApiKeyService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class ToolEventController {
  private final ToolEventService service;
  private final ToolEventRepository repo;
  private final ToolDefinitionRepository tools;
  private final AgentApiKeyService apiKeys;

  public ToolEventController(ToolEventService service, ToolEventRepository repo,
      ToolDefinitionRepository tools, AgentApiKeyService apiKeys) {
    this.service = service;
    this.repo = repo;
    this.tools = tools;
    this.apiKeys = apiKeys;
  }

  /**
   * The policy enforcement proxy — agents call tools ONLY through here,
   * authenticated with X-API-Key (the agent's key, issued once).
   */
  @PostMapping("/tools/invoke")
  public ResponseEntity<?> invoke(@RequestHeader(value = "X-API-Key", required = false) String apiKey,
      @RequestBody ToolEventService.InvokeRequest req) {
    var agentId = apiKeys.resolveAgent(apiKey);
    if (agentId.isEmpty()) {
      return ResponseEntity.status(401).body(java.util.Map.of("error", "invalid API key"));
    }
    return ResponseEntity.ok(service.invoke(agentId.get(), req));
  }

  @GetMapping("/tools")
  public List<ToolDefinition> catalog() {
    return tools.findAll();
  }

  @GetMapping("/agents/{agentId}/events")
  public List<ToolEvent> byAgent(@PathVariable UUID agentId) {
    return repo.findByAgentIdOrderByCreatedAtDesc(agentId);
  }
}
