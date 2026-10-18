package com.agentguard.runs;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentRunRepository extends JpaRepository<AgentRun, UUID> {
  List<AgentRun> findByAgentIdOrderByStartedAtDesc(UUID agentId);
}
