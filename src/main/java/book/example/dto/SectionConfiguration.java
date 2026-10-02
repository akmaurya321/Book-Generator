package book.example.dto;

import java.util.ArrayList;
import java.util.List;

public class SectionConfiguration {
    private String sectionId;
    private boolean enabled = true;
    private boolean contentEnabled = true;
    private boolean imageEnabled;
    private boolean diagramEnabled;
    private List<String> imageIds = new ArrayList<>();

    public String getSectionId() { return sectionId; }
    public void setSectionId(String sectionId) { this.sectionId = sectionId; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isContentEnabled() { return contentEnabled; }
    public void setContentEnabled(boolean contentEnabled) { this.contentEnabled = contentEnabled; }
    public boolean isImageEnabled() { return imageEnabled; }
    public void setImageEnabled(boolean imageEnabled) { this.imageEnabled = imageEnabled; }
    public boolean isDiagramEnabled() { return diagramEnabled; }
    public void setDiagramEnabled(boolean diagramEnabled) { this.diagramEnabled = diagramEnabled; }
    public List<String> getImageIds() { return imageIds; }
    public void setImageIds(List<String> imageIds) { this.imageIds = imageIds == null ? new ArrayList<>() : new ArrayList<>(imageIds); }
}
