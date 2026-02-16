package com.s1000Dorg.viewer.csdb.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "dm")
public class DmEntity {

    @Id
    @Column(name = "dm_id", length = 255, nullable = false)
    private String dmId;

    @Column(name = "display_name", length = 500)
    private String displayName;

    @Column(name = "model_ident", length = 100)
    private String modelIdent;

    @Column(name = "system_code", length = 50)
    private String systemCode;

    @Column(name = "info_code", length = 50)
    private String infoCode;

    @Column(name = "language_code", length = 10)
    private String languageCode;

    @Column(name = "issue_number", length = 10)
    private String issueNumber;

    @Column(name = "in_work", length = 10)
    private String inWork;

    @Column(name = "vault_path", length = 1000, nullable = false)
    private String vaultPath;

    @Column(name = "file_hash", length = 128)
    private String fileHash;

    @Lob
    @Column(name = "aircraft_tags")
    private String aircraftTags;

    @Lob
    @Column(name = "engine_tags")
    private String engineTags;

    @Lob
    @Column(name = "variant_tags")
    private String variantTags;

    @Column(name = "last_indexed")
    private LocalDateTime lastIndexed;

    public String getDmId() {
        return dmId;
    }

    public void setDmId(String dmId) {
        this.dmId = dmId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getModelIdent() {
        return modelIdent;
    }

    public void setModelIdent(String modelIdent) {
        this.modelIdent = modelIdent;
    }

    public String getSystemCode() {
        return systemCode;
    }

    public void setSystemCode(String systemCode) {
        this.systemCode = systemCode;
    }

    public String getInfoCode() {
        return infoCode;
    }

    public void setInfoCode(String infoCode) {
        this.infoCode = infoCode;
    }

    public String getLanguageCode() {
        return languageCode;
    }

    public void setLanguageCode(String languageCode) {
        this.languageCode = languageCode;
    }

    public String getIssueNumber() {
        return issueNumber;
    }

    public void setIssueNumber(String issueNumber) {
        this.issueNumber = issueNumber;
    }

    public String getInWork() {
        return inWork;
    }

    public void setInWork(String inWork) {
        this.inWork = inWork;
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

    public String getAircraftTags() {
        return aircraftTags;
    }

    public void setAircraftTags(String aircraftTags) {
        this.aircraftTags = aircraftTags;
    }

    public String getEngineTags() {
        return engineTags;
    }

    public void setEngineTags(String engineTags) {
        this.engineTags = engineTags;
    }

    public String getVariantTags() {
        return variantTags;
    }

    public void setVariantTags(String variantTags) {
        this.variantTags = variantTags;
    }

    public LocalDateTime getLastIndexed() {
        return lastIndexed;
    }

    public void setLastIndexed(LocalDateTime lastIndexed) {
        this.lastIndexed = lastIndexed;
    }
}
