package com.agentguard.audit;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.ColumnTransformer;

@Entity
@Table(name = "audit_log")
public class AuditLog {
  @Id
  private UUID id = UUID.randomUUID();

  @Column(nullable = false)
  private String actor;

  @Column(nullable = false)
  private String action;

  @Column(nullable = false)
  private String target;

  @ColumnTransformer(write = "?::jsonb")
  @Column(columnDefinition = "jsonb")
  private String detail = "{}";

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  protected AuditLog() {}

  public AuditLog(String actor, String action, String target, String detailJson) {
    this.actor = actor;
    this.action = action;
    this.target = target;
    this.detail = detailJson;
  }

  public UUID getId() { return id; }
  public String getActor() { return actor; }
  public String getAction() { return action; }
  public String getTarget() { return target; }
  public String getDetail() { return detail; }
  public Instant getCreatedAt() { return createdAt; }
}
