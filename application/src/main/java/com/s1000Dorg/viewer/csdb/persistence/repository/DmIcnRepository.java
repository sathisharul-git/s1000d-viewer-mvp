package com.s1000Dorg.viewer.csdb.persistence.repository;

import com.s1000Dorg.viewer.csdb.persistence.entity.DmIcnEntity;
import com.s1000Dorg.viewer.csdb.persistence.entity.DmIcnId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DmIcnRepository extends JpaRepository<DmIcnEntity, DmIcnId> {

    @Modifying
    @Query("delete from DmIcnEntity rel where upper(rel.id.dmId) = upper(:dmId)")
    void deleteByDmIdIgnoreCase(@Param("dmId") String dmId);

    @Query("select rel from DmIcnEntity rel where upper(rel.id.dmId) = upper(:dmId)")
    List<DmIcnEntity> findByDmIdIgnoreCase(@Param("dmId") String dmId);
}
