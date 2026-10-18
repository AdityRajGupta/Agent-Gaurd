package com.agentguard.events;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tool_events")
public class ToolEvent {
  @Id
  private UUID id = UUID.randomUUID();

  @Column(name = "event_id", unique = true, nullable = false)
  private String eventId; // client idempotency key

  @Column(name = "run_id")
  private UUID runId;

  @Column(name = "agent_id", nullable = false)
  private UUID agentId;

  @Column(name = "tool_id", nullable = false)
  private UUID toolId;

  @Column(name = "args_hash", nullable = false)
  private String argsHash; // sha256 of canonical args — never raw args

  @Column(name = "policy_decision", nullable = false)
  private String policyDecision;

  @Column(name = "anomaly_score")
  private Double anomalyScore;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  protected ToolEvent() {}

  public UUID getId() { return id; }
  public String getEventId() { return eventId; }
  public void setEventId(String eventId) { this.eventId = eventId; }
  public UUID getRunId() { return runId; }
  public void setRunId(UUID runId) { this.runId = runId; }
  public UUID getAgentId() { return agentId; }
  public void setAgentId(UUID agentId) { this.agentId = agentId; }
  public UUID getToolId() { return toolId; }
  public void setToolId(UUID toolId) { this.toolId = toolId; }
  public String getArgsHash() { return argsHash; }
  public void setArgsHash(String argsHash) { this.argsHash = argsHash; }
  public String getPolicyDecision() { return policyDecision; }
  public void setPolicyDecision(String policyDecision) { this.policyDecision = policyDecision; }
  public Double getAnomalyScore() { return anomalyScore; }
  public void setAnomalyScore(Double anomalyScore) { this.anomalyScore = anomalyScore; }
  public Instant getCreatedAt() { return createdAt; }
}
