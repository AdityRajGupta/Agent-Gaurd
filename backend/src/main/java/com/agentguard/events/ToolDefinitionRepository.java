package com.agentguard.events;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ToolDefinitionRepository extends JpaRepository<ToolDefinition, UUID> {
  Optional<ToolDefinition> findByName(String name);
}
