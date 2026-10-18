package com.agentguard.runs;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "agent_runs")
public class AgentRun {
  @Id
  private UUID id = UUID.randomUUID();

  @Column(name = "agent_id", nullable = false)
  private UUID agentId;

  @Column(nullable = false)
  private String status = "RUNNING";

  @Column(name = "started_at", nullable = false)
  private Instant startedAt = Instant.now();

  @Column(name = "ended_at")
  private Instant endedAt;

  protected AgentRun() {}

  public UUID getId() { return id; }
  public UUID getAgentId() { return agentId; }
  public void setAgentId(UUID agentId) { this.agentId = agentId; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public Instant getStartedAt() { return startedAt; }
  public Instant getEndedAt() { return endedAt; }
  public void setEndedAt(Instant endedAt) { this.endedAt = endedAt; }
}
