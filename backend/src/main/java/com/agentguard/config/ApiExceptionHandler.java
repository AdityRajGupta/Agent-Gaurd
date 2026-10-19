package com.agentguard.config;

import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Turns expected database conflicts into useful API responses instead of empty HTML errors. */
@RestControllerAdvice
public class ApiExceptionHandler {
  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<Map<String, String>> handleDataConflict(
      DataIntegrityViolationException exception) {
    String detail = exception.getMostSpecificCause() == null
        ? "A record conflicts with an existing database value"
        : exception.getMostSpecificCause().getMessage();
    String message = detail != null && detail.contains("agent_identities_name_key")
        ? "An agent with this name already exists"
        : detail != null && detail.contains("policies_agent_id_tool_id_key")
            ? "A policy for this agent and tool already exists"
        : "The request conflicts with existing data";
    return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
        "error", "conflict",
        "message", message));
  }
}
