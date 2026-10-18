package com.agentguard.events;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ToolEventRepository extends JpaRepository<ToolEvent, UUID> {
  Optional<ToolEvent> findByEventId(String eventId);
  List<ToolEvent> findByAgentIdOrderByCreatedAtDesc(UUID agentId);
  List<ToolEvent> findByAgentIdAndCreatedAtAfterOrderByCreatedAtAsc(
      UUID agentId, Instant after);
  long countByAgentIdAndToolId(UUID agentId, UUID toolId);
}
