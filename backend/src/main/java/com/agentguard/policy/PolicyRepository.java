package com.agentguard.policy;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PolicyRepository extends JpaRepository<Policy, UUID> {
  Optional<Policy> findByAgentIdAndToolId(UUID agentId, UUID toolId);
  List<Policy> findByAgentId(UUID agentId);
}
