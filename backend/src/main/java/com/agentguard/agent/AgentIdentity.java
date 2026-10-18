package com.agentguard.agent;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "agent_identities")
public class AgentIdentity {
  @Id
  private UUID id = UUID.randomUUID();

  @Column(unique = true, nullable = false)
  private String name;

  @Column(name = "owner_id", nullable = false)
  private UUID ownerId;

  @Column(name = "declared_task", nullable = false, columnDefinition = "text")
  private String declaredTask;

  @Column(name = "risk_tier", nullable = false)
  private String riskTier = "standard";

  @Column(nullable = false)
  private String status = "ACTIVE";

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  protected AgentIdentity() {}

  public UUID getId() { return id; }
  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  public UUID getOwnerId() { return ownerId; }
  public void setOwnerId(UUID ownerId) { this.ownerId = ownerId; }
  public String getDeclaredTask() { return declaredTask; }
  public void setDeclaredTask(String declaredTask) { this.declaredTask = declaredTask; }
  public String getRiskTier() { return riskTier; }
  public void setRiskTier(String riskTier) { this.riskTier = riskTier; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
}
