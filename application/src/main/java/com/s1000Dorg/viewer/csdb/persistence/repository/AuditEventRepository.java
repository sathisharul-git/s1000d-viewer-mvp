package com.s1000Dorg.viewer.csdb.persistence.repository;

import com.s1000Dorg.viewer.csdb.persistence.entity.AuditEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEventEntity, Long> {
}
