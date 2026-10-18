package com.agentguard.events;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "tool_definitions")
public class ToolDefinition {
  @Id
  private UUID id;

  @Column(unique = true, nullable = false)
  private String name;

  @Column(nullable = false, columnDefinition = "text")
  private String description;

  @Column(name = "data_sensitivity", nullable = false)
  private String dataSensitivity;

  protected ToolDefinition() {}

  public UUID getId() { return id; }
  public String getName() { return name; }
  public String getDescription() { return description; }
  public String getDataSensitivity() { return dataSensitivity; }
}
