package com.s1000Dorg.viewer.csdb.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class PmcDmId implements Serializable {

    @Column(name = "pmc_id", length = 255, nullable = false)
    private String pmcId;

    @Column(name = "dm_id", length = 255, nullable = false)
    private String dmId;

    public PmcDmId() {
    }

    public PmcDmId(String pmcId, String dmId) {
        this.pmcId = pmcId;
        this.dmId = dmId;
    }

    public String getPmcId() {
        return pmcId;
    }

    public void setPmcId(String pmcId) {
        this.pmcId = pmcId;
    }

    public String getDmId() {
        return dmId;
    }

    public void setDmId(String dmId) {
        this.dmId = dmId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PmcDmId that)) {
            return false;
        }
        return Objects.equals(pmcId, that.pmcId) && Objects.equals(dmId, that.dmId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pmcId, dmId);
    }
}
