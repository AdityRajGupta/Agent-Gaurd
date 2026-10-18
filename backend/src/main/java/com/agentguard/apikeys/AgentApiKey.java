package com.agentguard.apikeys;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "agent_api_keys")
public class AgentApiKey {
  @Id
  private UUID id = UUID.randomUUID();

  @Column(name = "agent_id", nullable = false)
  private UUID agentId;

  @Column(name = "key_hash", nullable = false)
  private String keyHash; // sha256 hex — the raw key is never stored

  @Column(nullable = false)
  private String label = "default";

  @Column(nullable = false)
  private boolean revoked = false;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  protected AgentApiKey() {}

  public UUID getId() { return id; }
  public UUID getAgentId() { return agentId; }
  public void setAgentId(UUID agentId) { this.agentId = agentId; }
  public String getKeyHash() { return keyHash; }
  public void setKeyHash(String keyHash) { this.keyHash = keyHash; }
  public String getLabel() { return label; }
  public void setLabel(String label) { this.label = label; }
  public boolean isRevoked() { return revoked; }
  public void setRevoked(boolean revoked) { this.revoked = revoked; }
}
