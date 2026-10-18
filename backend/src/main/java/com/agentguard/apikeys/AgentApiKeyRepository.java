package com.agentguard.apikeys;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentApiKeyRepository extends JpaRepository<AgentApiKey, UUID> {
  List<AgentApiKey> findByRevokedFalse();
  List<AgentApiKey> findByAgentId(UUID agentId);
}
