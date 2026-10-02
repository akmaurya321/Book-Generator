package book.example.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "usage_events",
        uniqueConstraints = @UniqueConstraint(name = "uk_usage_events_job_id", columnNames = "job_id"),
        indexes = @Index(name = "idx_usage_events_owner_created", columnList = "owner_id, created_at"))
public class UsageEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "owner_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(name = "job_id", nullable = false, updatable = false, length = 36)
    private String jobId;

    @Column(name = "operation", nullable = false, length = 64)
    private String operation = "DOCUMENTATION_GENERATION";

    @Column(name = "units", nullable = false)
    private int units = 1;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "QUEUED";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    protected UsageEvent() {
    }

    public UsageEvent(UUID ownerId, String jobId) {
        this.ownerId = ownerId;
        this.jobId = jobId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getJobId() {
        return jobId;
    }

    public String getOperation() {
        return operation;
    }

    public int getUnits() {
        return units;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
