package com.agentguard.decisions;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DecisionRepository extends JpaRepository<Decision, UUID> {
  List<Decision> findByEventId(UUID eventId);
}
