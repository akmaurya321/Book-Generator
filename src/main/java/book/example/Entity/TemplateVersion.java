package book.example.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "template_versions", uniqueConstraints =
        @UniqueConstraint(name = "uq_template_catalog_version", columnNames = {"template_catalog_id", "version"}))
public class TemplateVersion {
    public enum Status { DRAFT, PROCESSING, UNDER_REVIEW, APPROVED, PUBLISHED, REJECTED, SUSPENDED, ARCHIVED }
    public enum VerificationStatus { UNVERIFIED, PENDING, VERIFIED, REJECTED }
    public enum SecurityScanStatus { PENDING, CLEAN, REJECTED, NOT_REQUIRED }
    public enum ValidationStatus { PENDING, VALID, INVALID, NOT_REQUIRED }
    public enum SourceType { ADMIN, COMMUNITY, SYSTEM, PRIVATE }
    public enum ProjectType {
        MINOR_PROJECT,
        MAJOR_FINAL_YEAR_PROJECT,
        CAPSTONE,
        BACHELOR_PROJECT,
        BACHELOR_THESIS
    }

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_catalog_id", nullable = false)
    private TemplateCatalogEntry catalog;

    @Column(nullable = false)
    private int version;

    @Column(length = 120)
    private String country;
    @Column(length = 120)
    private String state;
    @Column(length = 120)
    private String region;
    @Column(length = 120)
    private String city;
    @Column(length = 255)
    private String college;
    @Column(length = 255)
    private String university;
    @Column(name = "aliases_json", nullable = false, columnDefinition = "TEXT")
    private String aliasesJson = "[]";
    @Column(length = 180)
    private String department;
    @Column(length = 120)
    private String degree;
    @Column(name = "project_type", length = 40)
    private String projectType;
    @Column(name = "template_storage_key", length = 1024)
    private String templateStorageKey;
    @Column(name = "preview_storage_key", length = 1024)
    private String previewStorageKey;
    @Column(name = "front_page_config_json", nullable = false, columnDefinition = "TEXT")
    private String frontPageConfigJson = "{}";
    @Column(name = "format_schema_json", nullable = false, columnDefinition = "TEXT")
    private String formatSchemaJson = "{}";
    @Column(name = "analysis_metadata_json", nullable = false, columnDefinition = "TEXT")
    private String analysisMetadataJson = "{}";
    @Column(name = "preview_text", nullable = false, columnDefinition = "TEXT")
    private String previewText = "";
    @Column(name = "source_type", nullable = false, length = 24)
    private String sourceType;
    @Column(name = "source_url", length = 2048)
    private String sourceUrl;
    @Column(length = 500)
    private String license;
    @Column(name = "ownership_declaration", columnDefinition = "TEXT")
    private String ownershipDeclaration;
    @Column(name = "ownership_confirmed_at")
    private LocalDateTime ownershipConfirmedAt;
    @Column(nullable = false, length = 24)
    private String status;
    @Column(name = "verification_status", nullable = false, length = 24)
    private String verificationStatus;
    @Column(nullable = false)
    private boolean available;
    @Column(name = "created_by", columnDefinition = "uuid")
    private UUID createdBy;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    @Column(name = "verified_by", columnDefinition = "uuid")
    private UUID verifiedBy;
    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;
    @Column(name = "security_scan_status", nullable = false, length = 24)
    private String securityScanStatus;
    @Column(name = "validation_status", nullable = false, length = 24)
    private String validationStatus;
    @Column(name = "published_at")
    private LocalDateTime publishedAt;
    @Column(name = "suspended_at")
    private LocalDateTime suspendedAt;

    public TemplateVersion() {
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public TemplateCatalogEntry getCatalog() { return catalog; }
    public void setCatalog(TemplateCatalogEntry catalog) { this.catalog = catalog; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getCollege() { return college; }
    public void setCollege(String college) { this.college = college; }
    public String getUniversity() { return university; }
    public void setUniversity(String university) { this.university = university; }
    public String getAliasesJson() { return aliasesJson; }
    public void setAliasesJson(String aliasesJson) { this.aliasesJson = aliasesJson; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public String getDegree() { return degree; }
    public void setDegree(String degree) { this.degree = degree; }
    public String getProjectType() { return projectType; }
    public void setProjectType(String projectType) { this.projectType = projectType; }
    public String getTemplateStorageKey() { return templateStorageKey; }
    public void setTemplateStorageKey(String templateStorageKey) { this.templateStorageKey = templateStorageKey; }
    public String getPreviewStorageKey() { return previewStorageKey; }
    public void setPreviewStorageKey(String previewStorageKey) { this.previewStorageKey = previewStorageKey; }
    public String getFrontPageConfigJson() { return frontPageConfigJson; }
    public void setFrontPageConfigJson(String frontPageConfigJson) { this.frontPageConfigJson = frontPageConfigJson; }
    public String getFormatSchemaJson() { return formatSchemaJson; }
    public void setFormatSchemaJson(String formatSchemaJson) { this.formatSchemaJson = formatSchemaJson; }
    public String getAnalysisMetadataJson() { return analysisMetadataJson; }
    public void setAnalysisMetadataJson(String analysisMetadataJson) { this.analysisMetadataJson = analysisMetadataJson; }
    public String getPreviewText() { return previewText; }
    public void setPreviewText(String previewText) { this.previewText = previewText; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
    public String getLicense() { return license; }
    public void setLicense(String license) { this.license = license; }
    public String getOwnershipDeclaration() { return ownershipDeclaration; }
    public void setOwnershipDeclaration(String ownershipDeclaration) { this.ownershipDeclaration = ownershipDeclaration; }
    public LocalDateTime getOwnershipConfirmedAt() { return ownershipConfirmedAt; }
    public void setOwnershipConfirmedAt(LocalDateTime ownershipConfirmedAt) { this.ownershipConfirmedAt = ownershipConfirmedAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getVerificationStatus() { return verificationStatus; }
    public void setVerificationStatus(String verificationStatus) { this.verificationStatus = verificationStatus; }
    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }
    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public UUID getVerifiedBy() { return verifiedBy; }
    public void setVerifiedBy(UUID verifiedBy) { this.verifiedBy = verifiedBy; }
    public LocalDateTime getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(LocalDateTime verifiedAt) { this.verifiedAt = verifiedAt; }
    public String getSecurityScanStatus() { return securityScanStatus; }
    public void setSecurityScanStatus(String securityScanStatus) { this.securityScanStatus = securityScanStatus; }
    public String getValidationStatus() { return validationStatus; }
    public void setValidationStatus(String validationStatus) { this.validationStatus = validationStatus; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }
    public LocalDateTime getSuspendedAt() { return suspendedAt; }
    public void setSuspendedAt(LocalDateTime suspendedAt) { this.suspendedAt = suspendedAt; }
}
