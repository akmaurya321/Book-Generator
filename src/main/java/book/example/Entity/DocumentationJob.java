package book.example.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "documentation_jobs",
        indexes = {
                @Index(
                        name = "idx_documentation_jobs_owner_id",
                        columnList = "owner_id"
                )
        }
)
public class DocumentationJob {

    @Id
    private String jobId;

    private String githubUrl;

    private String projectName;

    @Column(name = "owner_id", columnDefinition = "uuid")
    private UUID ownerId;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    private String status;

    private String type;

    private String documentPath;

    private String pdfPath;

    /*
     * JSON is stored as TEXT.
     *
     * Do NOT use @Lob here.
     * With PostgreSQL + Hibernate, @Lob String can be mapped to OID,
     * which causes Hibernate to try converting TEXT columns to OID
     * during schema update.
     */

    @Column(name = "project_facts_json", columnDefinition = "TEXT")
    private String projectFactsJson;

    @Column(name = "plan_json", columnDefinition = "TEXT")
    private String planJson;

    @Column(name = "repository_snapshot_json", columnDefinition = "TEXT")
    private String repositorySnapshotJson;

    @Column(name = "generation_checkpoint_json", columnDefinition = "TEXT")
    private String generationCheckpointJson;

    /**
     * JSON array of chunk IDs whose indexing failed
     * and should be retried.
     */
    @Column(name = "indexing_failures_json", columnDefinition = "TEXT")
    private String indexingFailuresJson;

    @Column(nullable = false)
    private long version;

    @Column(name = "editor_history_json", columnDefinition = "TEXT")
    private String editorHistoryJson;

    @Column(name = "editor_history_position", nullable = false)
    private int editorHistoryPosition = -1;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime expiresAt;

    public DocumentationJob() {
    }

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getGithubUrl() {
        return githubUrl;
    }

    public void setGithubUrl(String githubUrl) {
        this.githubUrl = githubUrl;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(UUID ownerId) {
        this.ownerId = ownerId;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(int attemptCount) {
        this.attemptCount = attemptCount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDocumentPath() {
        return documentPath;
    }

    public void setDocumentPath(String documentPath) {
        this.documentPath = documentPath;
    }

    public String getPdfPath() {
        return pdfPath;
    }

    public void setPdfPath(String pdfPath) {
        this.pdfPath = pdfPath;
    }

    public String getProjectFactsJson() {
        return projectFactsJson;
    }

    public void setProjectFactsJson(String projectFactsJson) {
        this.projectFactsJson = projectFactsJson;
    }

    public String getPlanJson() {
        return planJson;
    }

    public void setPlanJson(String planJson) {
        this.planJson = planJson;
    }

    public String getRepositorySnapshotJson() {
        return repositorySnapshotJson;
    }

    public void setRepositorySnapshotJson(String repositorySnapshotJson) {
        this.repositorySnapshotJson = repositorySnapshotJson;
    }

    public String getGenerationCheckpointJson() {
        return generationCheckpointJson;
    }

    public void setGenerationCheckpointJson(String generationCheckpointJson) {
        this.generationCheckpointJson = generationCheckpointJson;
    }

    public String getIndexingFailuresJson() {
        return indexingFailuresJson;
    }

    public void setIndexingFailuresJson(String indexingFailuresJson) {
        this.indexingFailuresJson = indexingFailuresJson;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public String getEditorHistoryJson() {
        return editorHistoryJson;
    }

    public void setEditorHistoryJson(String editorHistoryJson) {
        this.editorHistoryJson = editorHistoryJson;
    }

    public int getEditorHistoryPosition() {
        return editorHistoryPosition;
    }

    public void setEditorHistoryPosition(int editorHistoryPosition) {
        this.editorHistoryPosition = editorHistoryPosition;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }
}