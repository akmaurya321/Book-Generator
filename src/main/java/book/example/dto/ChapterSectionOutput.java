package book.example.dto;

import java.util.ArrayList;
import java.util.List;

public class ChapterSectionOutput {
    private String sectionId;
    private String content;
    private List<String> evidenceIds = new ArrayList<>();
    private List<ChapterAssetOutput> assets = new ArrayList<>();

    public String getSectionId() { return sectionId; }
    public void setSectionId(String sectionId) { this.sectionId = sectionId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public List<String> getEvidenceIds() { return evidenceIds; }
    public void setEvidenceIds(List<String> evidenceIds) { this.evidenceIds = evidenceIds == null ? new ArrayList<>() : evidenceIds; }
    public List<ChapterAssetOutput> getAssets() { return assets; }
    public void setAssets(List<ChapterAssetOutput> assets) { this.assets = assets == null ? new ArrayList<>() : assets; }
}
