package com.agentguard.rag;

import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Phase 4 — RAG investigation endpoint.
 * Produces an evidence-cited summary + containment proposal for a case.
 * The LLM proposes; deterministic code validates citations; humans approve.
 */
@RestController
@RequestMapping("/api/v1/cases")
public class RagController {
  private final InvestigationService investigations;

  public RagController(InvestigationService investigations) {
    this.investigations = investigations;
  }

  @PostMapping("/{caseId}/investigate")
  public ResponseEntity<?> investigate(@PathVariable UUID caseId) {
    try {
      var r = investigations.investigate(caseId);
      return ResponseEntity.ok(Map.of(
          "summary", r.summary(), "provider", r.provider()));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
    } catch (Exception e) {
      return ResponseEntity.status(502).body(Map.of(
          "error", "investigation failed: " + e.getMessage(),
          "hint", "Phase 4 needs an LLM: run Ollama locally "
              + "(`ollama serve`, `ollama pull llama3.1:8b`) or set GROQ_API_KEY / GEMINI_API_KEY"));
    }
  }
}
