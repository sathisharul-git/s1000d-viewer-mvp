package com.s1000Dorg.viewer.csdb.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "pmc")
public class PmcEntity {

    @Id
    @Column(name = "pmc_id", length = 255, nullable = false)
    private String pmcId;

    @Column(name = "title", length = 500)
    private String title;

    @Column(name = "vault_path", length = 1000, nullable = false)
    private String vaultPath;

    @Column(name = "file_hash", length = 128)
    private String fileHash;

    @Column(name = "last_indexed")
    private OffsetDateTime lastIndexed;

    public String getPmcId() {
        return pmcId;
    }

    public void setPmcId(String pmcId) {
        this.pmcId = pmcId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getVaultPath() {
        return vaultPath;
    }

    public void setVaultPath(String vaultPath) {
        this.vaultPath = vaultPath;
    }

    public String getFileHash() {
        return fileHash;
    }

    public void setFileHash(String fileHash) {
        this.fileHash = fileHash;
    }

    public OffsetDateTime getLastIndexed() {
        return lastIndexed;
    }

    public void setLastIndexed(OffsetDateTime lastIndexed) {
        this.lastIndexed = lastIndexed;
    }
}
