package com.s1000Dorg.viewer.csdb.persistence.repository;

import com.s1000Dorg.viewer.csdb.persistence.entity.PmcDmEntity;
import com.s1000Dorg.viewer.csdb.persistence.entity.PmcDmId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PmcDmRepository extends JpaRepository<PmcDmEntity, PmcDmId> {

    @Modifying
    @Query("delete from PmcDmEntity rel where upper(rel.id.pmcId) = upper(:pmcId)")
    void deleteByPmcIdIgnoreCase(@Param("pmcId") String pmcId);

    @Query("select rel from PmcDmEntity rel where upper(rel.id.pmcId) = upper(:pmcId) order by rel.sortOrder asc")
    List<PmcDmEntity> findByPmcIdIgnoreCaseOrderBySortOrder(@Param("pmcId") String pmcId);
}
