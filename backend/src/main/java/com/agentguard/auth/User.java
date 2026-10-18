package com.agentguard.auth;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {
  @Id
  private UUID id = UUID.randomUUID();

  @Column(unique = true, nullable = false)
  private String username;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  @Column(nullable = false)
  private String role; // ADMIN | OPERATOR | AUDITOR

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  protected User() {}

  public User(String username, String passwordHash, String role) {
    this.username = username;
    this.passwordHash = passwordHash;
    this.role = role;
  }

  public UUID getId() { return id; }
  public String getUsername() { return username; }
  public String getPasswordHash() { return passwordHash; }
  public String getRole() { return role; }
}
