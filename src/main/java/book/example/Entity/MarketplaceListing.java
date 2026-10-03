package book.example.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "marketplace_listings", indexes = {
        @Index(name = "idx_marketplace_listing_public", columnList = "status, category, published_at"),
        @Index(name = "idx_marketplace_listing_owner", columnList = "owner_id, updated_at")
})
public class MarketplaceListing {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private AppUser owner;

    @Column(nullable = false, unique = true, length = 180)
    private String slug;

    @Column(name = "listing_type", nullable = false, length = 40)
    private String listingType;

    @Column(name = "origin_type", nullable = false, length = 20)
    private String originType;

    @Column(nullable = false, length = 180)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 80)
    private String category;

    @Column(columnDefinition = "TEXT")
    private String technologies;

    @Column(name = "project_name")
    private String projectName;

    @Column(name = "source_job_id")
    private String sourceJobId;

    @Column(name = "project_file_path", length = 2048)
    private String projectFilePath;

    @Column(name = "document_file_path", length = 2048)
    private String documentFilePath;

    @Column(name = "preview_text", columnDefinition = "TEXT")
    private String previewText;

    @Column(nullable = false, length = 24)
    private String status;

    @Column(name = "ownership_confirmed", nullable = false)
    private boolean ownershipConfirmed;

    @Column(name = "ownership_confirmed_at")
    private LocalDateTime ownershipConfirmedAt;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    public MarketplaceListing() {
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public AppUser getOwner() { return owner; }
    public void setOwner(AppUser owner) { this.owner = owner; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getListingType() { return listingType; }
    public void setListingType(String listingType) { this.listingType = listingType; }
    public String getOriginType() { return originType; }
    public void setOriginType(String originType) { this.originType = originType; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getTechnologies() { return technologies; }
    public void setTechnologies(String technologies) { this.technologies = technologies; }
    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }
    public String getSourceJobId() { return sourceJobId; }
    public void setSourceJobId(String sourceJobId) { this.sourceJobId = sourceJobId; }
    public String getProjectFilePath() { return projectFilePath; }
    public void setProjectFilePath(String projectFilePath) { this.projectFilePath = projectFilePath; }
    public String getDocumentFilePath() { return documentFilePath; }
    public void setDocumentFilePath(String documentFilePath) { this.documentFilePath = documentFilePath; }
    public String getPreviewText() { return previewText; }
    public void setPreviewText(String previewText) { this.previewText = previewText; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public boolean isOwnershipConfirmed() { return ownershipConfirmed; }
    public void setOwnershipConfirmed(boolean ownershipConfirmed) { this.ownershipConfirmed = ownershipConfirmed; }
    public LocalDateTime getOwnershipConfirmedAt() { return ownershipConfirmedAt; }
    public void setOwnershipConfirmedAt(LocalDateTime ownershipConfirmedAt) { this.ownershipConfirmedAt = ownershipConfirmedAt; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }
}
