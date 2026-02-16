package com.s1000Dorg.viewer.csdb.persistence.repository;

import com.s1000Dorg.viewer.csdb.persistence.entity.PmcEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PmcRepository extends JpaRepository<PmcEntity, String> {

    @Query("select p from PmcEntity p where upper(p.pmcId) = upper(:pmcId)")
    Optional<PmcEntity> findByPmcIdIgnoreCase(@Param("pmcId") String pmcId);
}
