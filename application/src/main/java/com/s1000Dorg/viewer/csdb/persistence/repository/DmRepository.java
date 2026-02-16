package com.s1000Dorg.viewer.csdb.persistence.repository;

import com.s1000Dorg.viewer.csdb.persistence.entity.DmEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DmRepository extends JpaRepository<DmEntity, String> {

    @Query("select d from DmEntity d where upper(d.dmId) = upper(:dmId)")
    Optional<DmEntity> findByDmIdIgnoreCase(@Param("dmId") String dmId);
}
