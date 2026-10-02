package book.example.dto;

public class UploadedAsset {
    private String assetId;
    private String jobId;
    private String sectionId;
    private String originalFilename;
    private String contentType;
    private String path;
    private String caption;

    public UploadedAsset() {}
    public UploadedAsset(String assetId, String jobId, String sectionId, String originalFilename,
                         String contentType, String path, String caption) {
        this.assetId = assetId; this.jobId = jobId; this.sectionId = sectionId;
        this.originalFilename = originalFilename; this.contentType = contentType; this.path = path; this.caption = caption;
    }
    public String getAssetId() { return assetId; }
    public void setAssetId(String assetId) { this.assetId = assetId; }
    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }
    public String getSectionId() { return sectionId; }
    public void setSectionId(String sectionId) { this.sectionId = sectionId; }
    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getCaption() { return caption; }
    public void setCaption(String caption) { this.caption = caption; }
}
