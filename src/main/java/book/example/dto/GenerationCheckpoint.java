package book.example.dto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Durable generation checkpoint used to resume document generation after a
 * process restart without regenerating chapters that already completed.
 * Diagram binary data is intentionally excluded; rendered diagrams are
 * recreated from their persisted specifications during recovery.
 */
public class GenerationCheckpoint {
    private List<GeneratedSectionCheckpoint> frontMatter = new ArrayList<>();
    private Map<String, List<GeneratedSectionCheckpoint>> chapters = new LinkedHashMap<>();

    public List<GeneratedSectionCheckpoint> getFrontMatter() { return frontMatter; }
    public void setFrontMatter(List<GeneratedSectionCheckpoint> frontMatter) {
        this.frontMatter = frontMatter == null ? new ArrayList<>() : frontMatter;
    }
    public Map<String, List<GeneratedSectionCheckpoint>> getChapters() { return chapters; }
    public void setChapters(Map<String, List<GeneratedSectionCheckpoint>> chapters) {
        this.chapters = chapters == null ? new LinkedHashMap<>() : chapters;
    }
}
