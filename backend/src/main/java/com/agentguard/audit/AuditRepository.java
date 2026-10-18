package com.agentguard.audit;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditRepository extends JpaRepository<AuditLog, UUID> {
  List<AuditLog> findAllByOrderByCreatedAtDesc();
}
