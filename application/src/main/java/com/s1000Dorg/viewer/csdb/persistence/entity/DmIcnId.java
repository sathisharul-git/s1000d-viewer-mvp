package com.s1000Dorg.viewer.csdb.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class DmIcnId implements Serializable {

    @Column(name = "dm_id", length = 255, nullable = false)
    private String dmId;

    @Column(name = "icn_id", length = 255, nullable = false)
    private String icnId;

    public DmIcnId() {
    }

    public DmIcnId(String dmId, String icnId) {
        this.dmId = dmId;
        this.icnId = icnId;
    }

    public String getDmId() {
        return dmId;
    }

    public void setDmId(String dmId) {
        this.dmId = dmId;
    }

    public String getIcnId() {
        return icnId;
    }

    public void setIcnId(String icnId) {
        this.icnId = icnId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DmIcnId that)) {
            return false;
        }
        return Objects.equals(dmId, that.dmId) && Objects.equals(icnId, that.icnId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dmId, icnId);
    }
}
