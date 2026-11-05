package com.slokam.av.repository;

import com.slokam.av.entity.RoleAssignmentAudit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleAssignmentAuditRepository extends JpaRepository<RoleAssignmentAudit, String> {}
