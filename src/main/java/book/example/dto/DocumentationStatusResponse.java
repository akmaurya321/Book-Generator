package book.example.dto;

public class DocumentationStatusResponse {

    private String jobId;
    private String status;
    private int progress;
    private String message;

    public DocumentationStatusResponse() {
    }

    public DocumentationStatusResponse(
            String jobId,
            String status,
            int progress,
            String message) {

        this.jobId = jobId;
        this.status = status;
        this.progress = progress;
        this.message = message;
    }

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getProgress() {
        return progress;
    }

    public void setProgress(int progress) {
        this.progress = progress;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}