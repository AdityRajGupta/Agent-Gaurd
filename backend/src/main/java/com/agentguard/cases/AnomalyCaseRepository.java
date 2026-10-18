package com.agentguard.cases;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnomalyCaseRepository extends JpaRepository<AnomalyCase, UUID> {
  List<AnomalyCase> findByStatusOrderByCreatedAtDesc(String status);
}
