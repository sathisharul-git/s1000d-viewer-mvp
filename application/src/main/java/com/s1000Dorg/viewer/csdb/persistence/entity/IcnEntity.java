package com.s1000Dorg.viewer.csdb.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "icn")
public class IcnEntity {

    @Id
    @Column(name = "icn_id", length = 255, nullable = false)
    private String icnId;

    @Column(name = "vault_path", length = 1000, nullable = false)
    private String vaultPath;

    @Column(name = "type", length = 20)
    private String type;

    @Column(name = "file_hash", length = 128)
    private String fileHash;

    @Column(name = "last_indexed")
    private LocalDateTime lastIndexed;

    public String getIcnId() {
        return icnId;
    }

    public void setIcnId(String icnId) {
        this.icnId = icnId;
    }

    public String getVaultPath() {
        return vaultPath;
    }

    public void setVaultPath(String vaultPath) {
        this.vaultPath = vaultPath;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getFileHash() {
        return fileHash;
    }

    public void setFileHash(String fileHash) {
        this.fileHash = fileHash;
    }

    public LocalDateTime getLastIndexed() {
        return lastIndexed;
    }

    public void setLastIndexed(LocalDateTime lastIndexed) {
        this.lastIndexed = lastIndexed;
    }
}
