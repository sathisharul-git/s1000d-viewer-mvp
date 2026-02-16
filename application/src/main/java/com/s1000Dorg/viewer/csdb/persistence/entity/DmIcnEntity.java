package com.s1000Dorg.viewer.csdb.persistence.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "dm_icn")
public class DmIcnEntity {

    @EmbeddedId
    private DmIcnId id;

    public DmIcnEntity() {
    }

    public DmIcnEntity(DmIcnId id) {
        this.id = id;
    }

    public DmIcnId getId() {
        return id;
    }

    public void setId(DmIcnId id) {
        this.id = id;
    }
}
