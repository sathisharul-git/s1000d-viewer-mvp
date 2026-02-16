package com.s1000Dorg.viewer.csdb.persistence.repository;

import com.s1000Dorg.viewer.csdb.persistence.entity.IcnEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IcnRepository extends JpaRepository<IcnEntity, String> {

    @Query("select i from IcnEntity i where upper(i.icnId) = upper(:icnId)")
    Optional<IcnEntity> findByIcnIdIgnoreCase(@Param("icnId") String icnId);
}
