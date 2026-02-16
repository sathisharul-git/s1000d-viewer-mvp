package com.s1000Dorg.viewer.csdb.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "pmc_dm")
public class PmcDmEntity {

    @EmbeddedId
    private PmcDmId id;

    @Column(name = "sort_order")
    private Integer sortOrder;

    public PmcDmEntity() {
    }

    public PmcDmEntity(PmcDmId id, Integer sortOrder) {
        this.id = id;
        this.sortOrder = sortOrder;
    }

    public PmcDmId getId() {
        return id;
    }

    public void setId(PmcDmId id) {
        this.id = id;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }
}
