package com.agentguard.policy;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "policies")
public class Policy {
  @Id
  private UUID id = UUID.randomUUID();

  @Column(name = "agent_id", nullable = false)
  private UUID agentId;

  @Column(name = "tool_id", nullable = false)
  private UUID toolId;

  @Column(nullable = false)
  private String effect; // ALLOW | DENY | APPROVE

  @Column(name = "max_calls")
  private Integer maxCalls; // null = unlimited

  @Column(name = "created_by", nullable = false)
  private UUID createdBy;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  protected Policy() {}

  public UUID getId() { return id; }
  public UUID getAgentId() { return agentId; }
  public void setAgentId(UUID agentId) { this.agentId = agentId; }
  public UUID getToolId() { return toolId; }
  public void setToolId(UUID toolId) { this.toolId = toolId; }
  public String getEffect() { return effect; }
  public void setEffect(String effect) { this.effect = effect; }
  public Integer getMaxCalls() { return maxCalls; }
  public void setMaxCalls(Integer maxCalls) { this.maxCalls = maxCalls; }
  public UUID getCreatedBy() { return createdBy; }
  public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
}
