package book.example.dto;

import java.util.ArrayList;
import java.util.List;

public class GeneratedSectionCheckpoint {
    private int order;
    private String title;
    private String level;
    private String content;
    private DiagramSpecification diagramSpecification;
    private String diagramStatus = "NOT_REQUESTED";
    private List<GeneratedTable> tables = new ArrayList<>();
    private List<String> imagePaths = new ArrayList<>();

    public int getOrder() { return order; }
    public void setOrder(int order) { this.order = order; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public DiagramSpecification getDiagramSpecification() { return diagramSpecification; }
    public void setDiagramSpecification(DiagramSpecification diagramSpecification) { this.diagramSpecification = diagramSpecification; }
    public String getDiagramStatus() { return diagramStatus; }
    public void setDiagramStatus(String diagramStatus) { this.diagramStatus = diagramStatus; }
    public List<GeneratedTable> getTables() { return tables; }
    public void setTables(List<GeneratedTable> tables) { this.tables = tables == null ? new ArrayList<>() : tables; }
    public List<String> getImagePaths() { return imagePaths; }
    public void setImagePaths(List<String> imagePaths) { this.imagePaths = imagePaths == null ? new ArrayList<>() : imagePaths; }
}
