package com.agentguard.decisions;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "decisions")
public class Decision {
  @Id
  private UUID id = UUID.randomUUID();

  @Column(name = "case_id")
  private UUID caseId;

  @Column(name = "event_id")
  private UUID eventId;

  @Column(name = "decided_by", nullable = false)
  private UUID decidedBy;

  @Column(nullable = false)
  private String decision; // APPROVE | DENY

  @Column(columnDefinition = "text")
  private String reason;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  protected Decision() {}

  public UUID getId() { return id; }
  public UUID getCaseId() { return caseId; }
  public void setCaseId(UUID caseId) { this.caseId = caseId; }
  public UUID getEventId() { return eventId; }
  public void setEventId(UUID eventId) { this.eventId = eventId; }
  public UUID getDecidedBy() { return decidedBy; }
  public void setDecidedBy(UUID decidedBy) { this.decidedBy = decidedBy; }
  public String getDecision() { return decision; }
  public void setDecision(String decision) { this.decision = decision; }
  public String getReason() { return reason; }
  public void setReason(String reason) { this.reason = reason; }
}
