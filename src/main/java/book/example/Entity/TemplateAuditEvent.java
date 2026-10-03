package book.example.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "template_audit_events")
public class TemplateAuditEvent {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_version_id", nullable = false)
    private TemplateVersion templateVersion;

    @Column(name = "actor_id", columnDefinition = "uuid")
    private UUID actorId;

    @Column(nullable = false, length = 40)
    private String action;

    @Column(name = "old_status", length = 24)
    private String oldStatus;

    @Column(name = "new_status", length = 24)
    private String newStatus;

    @Column(length = 2000)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected TemplateAuditEvent() {
    }

    public TemplateAuditEvent(TemplateVersion templateVersion, UUID actorId, String action,
                              String oldStatus, String newStatus, String reason) {
        this.id = UUID.randomUUID();
        this.templateVersion = templateVersion;
        this.actorId = actorId;
        this.action = action;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
        this.reason = reason;
        this.createdAt = LocalDateTime.now();
    }

    public UUID getId() { return id; }
    public TemplateVersion getTemplateVersion() { return templateVersion; }
    public UUID getActorId() { return actorId; }
    public String getAction() { return action; }
    public String getOldStatus() { return oldStatus; }
    public String getNewStatus() { return newStatus; }
    public String getReason() { return reason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
