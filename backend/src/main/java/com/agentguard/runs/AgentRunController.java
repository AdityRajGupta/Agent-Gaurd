package com.agentguard.runs;

import com.agentguard.events.ToolEvent;
import com.agentguard.events.ToolEventRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/runs")
public class AgentRunController {
  private static final Set<String> STATUSES =
      Set.of("RUNNING", "AWAITING_APPROVAL", "COMPLETED", "BLOCKED", "FAILED");

  private final AgentRunRepository runs;
  private final ToolEventRepository events;

  public AgentRunController(AgentRunRepository runs, ToolEventRepository events) {
    this.runs = runs;
    this.events = events;
  }

  @PostMapping
  public AgentRun start(@RequestBody Map<String, String> body) {
    AgentRun r = new AgentRun();
    r.setAgentId(UUID.fromString(body.get("agentId")));
    return runs.save(r);
  }

  @GetMapping("/{id}")
  public ResponseEntity<AgentRun> get(@PathVariable UUID id) {
    return runs.findById(id).map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @PatchMapping("/{id}")
  public ResponseEntity<?> status(@PathVariable UUID id,
      @RequestBody Map<String, String> body) {
    String status = body.get("status");
    if (!STATUSES.contains(status)) {
      return ResponseEntity.badRequest().body(Map.of("error", "invalid status"));
    }
    return runs.findById(id).map(r -> {
      r.setStatus(status);
      if (!"RUNNING".equals(status) && !"AWAITING_APPROVAL".equals(status)) {
        r.setEndedAt(Instant.now());
      }
      return ResponseEntity.ok(runs.save(r));
    }).orElseGet(() -> ResponseEntity.notFound().build());
  }

  /**
   * Live run stream (Phase 2: snapshot stream; true push on new events
   * arrives with the Phase 5 dashboard work).
   */
  @GetMapping(value = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter stream(@PathVariable UUID id) {
    SseEmitter emitter = new SseEmitter(60_000L);
    try {
      var run = runs.findById(id).orElse(null);
      if (run == null) {
        emitter.send(SseEmitter.event().name("error").data("run not found"));
      } else {
        List<ToolEvent> evts = events.findByAgentIdOrderByCreatedAtDesc(run.getAgentId());
        emitter.send(SseEmitter.event().name("run").data(Map.of(
            "runId", run.getId().toString(),
            "status", run.getStatus(),
            "events", evts.stream().limit(50).map(e -> Map.of(
                "tool", e.getToolId().toString(),
                "decision", e.getPolicyDecision(),
                "at", e.getCreatedAt().toString())).toList())));
      }
      emitter.complete();
    } catch (Exception e) {
      emitter.completeWithError(e);
    }
    return emitter;
  }
}
