package com.agentguard.cases;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "anomaly_cases")
public class AnomalyCase {
  @Id
  private UUID id = UUID.randomUUID();

  @Column(name = "agent_id", nullable = false)
  private UUID agentId;

  @Column(name = "run_id")
  private UUID runId;

  @Column(nullable = false)
  private Double score;

  @Column(nullable = false)
  private String status = "OPEN";

  @Column(columnDefinition = "text")
  private String summary; // filled by the Phase 4 RAG investigation

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  public AnomalyCase() {} // public: created cross-package by ToolEventService

  public UUID getId() { return id; }
  public UUID getAgentId() { return agentId; }
  public void setAgentId(UUID agentId) { this.agentId = agentId; }
  public UUID getRunId() { return runId; }
  public void setRunId(UUID runId) { this.runId = runId; }
  public Double getScore() { return score; }
  public void setScore(Double score) { this.score = score; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public String getSummary() { return summary; }
  public void setSummary(String summary) { this.summary = summary; }
  public Instant getCreatedAt() { return createdAt; }
}
