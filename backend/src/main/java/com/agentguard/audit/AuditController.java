package com.agentguard.audit;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {
  private final AuditService audit;

  public AuditController(AuditService audit) {
    this.audit = audit;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ADMIN','AUDITOR','OPERATOR')")
  public List<AuditLog> recent() {
    return audit.recent();
  }
}
