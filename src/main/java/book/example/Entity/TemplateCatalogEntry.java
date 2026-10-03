package book.example.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "template_catalog")
public class TemplateCatalogEntry {
    @Id
    private UUID id;

    @Column(name = "template_id", nullable = false, unique = true, length = 180)
    private String templateId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected TemplateCatalogEntry() {
    }

    public TemplateCatalogEntry(UUID id, String templateId, LocalDateTime now) {
        this.id = id;
        this.templateId = templateId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public String getTemplateId() { return templateId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
