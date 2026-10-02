package book.example.dto;

public class SectionRecommendation {
    private String sectionId;
    private boolean recommended;
    private boolean contentRecommended;
    private boolean imageRecommended;
    private boolean diagramRecommended;
    private String reason;

    public SectionRecommendation() {}

    public SectionRecommendation(String sectionId, boolean recommended, String reason) {
        this(sectionId, recommended, !"INDEX".equalsIgnoreCase(reason), false, false, reason);
    }

    public SectionRecommendation(String sectionId, boolean recommended, boolean contentRecommended,
                                 boolean imageRecommended, boolean diagramRecommended, String reason) {
        this.sectionId = sectionId;
        this.recommended = recommended;
        this.contentRecommended = contentRecommended;
        this.imageRecommended = imageRecommended;
        this.diagramRecommended = diagramRecommended;
        this.reason = reason;
    }

    public String getSectionId() { return sectionId; }
    public void setSectionId(String sectionId) { this.sectionId = sectionId; }
    public boolean isRecommended() { return recommended; }
    public void setRecommended(boolean recommended) { this.recommended = recommended; }
    public boolean isContentRecommended() { return contentRecommended; }
    public void setContentRecommended(boolean value) { this.contentRecommended = value; }
    public boolean isImageRecommended() { return imageRecommended; }
    public void setImageRecommended(boolean value) { this.imageRecommended = value; }
    public boolean isDiagramRecommended() { return diagramRecommended; }
    public void setDiagramRecommended(boolean value) { this.diagramRecommended = value; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
