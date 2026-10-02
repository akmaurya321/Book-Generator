package book.example.dto;

public class GeneratedSection {

    private int order;
    private String title;
    private String level;
    private String content;

    private DiagramSpecification diagramSpecification;
    private DiagramImage diagramImage;
    private String diagramStatus = "NOT_REQUESTED";
    private java.util.List<GeneratedTable> tables = new java.util.ArrayList<>();
    private java.util.List<String> imagePaths = new java.util.ArrayList<>();

    public GeneratedSection() {
    }

    public GeneratedSection(
            int order,
            String title,
            String level,
            String content) {

        this.order = order;
        this.title = title;
        this.level = level;
        this.content = content;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public DiagramSpecification getDiagramSpecification() {
        return diagramSpecification;
    }

    public void setDiagramSpecification(
            DiagramSpecification diagramSpecification) {

        this.diagramSpecification =
                diagramSpecification;
    }

    public DiagramImage getDiagramImage() {
        return diagramImage;
    }

    public void setDiagramImage(
            DiagramImage diagramImage) {

        this.diagramImage = diagramImage;
    }

    public String getDiagramStatus() {
        return diagramStatus;
    }

    public void setDiagramStatus(String diagramStatus) {
        this.diagramStatus = diagramStatus;
    }

    public java.util.List<String> getImagePaths() { return imagePaths; }
    public void setImagePaths(java.util.List<String> imagePaths) { this.imagePaths = imagePaths == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(imagePaths); }

    public java.util.List<GeneratedTable> getTables() {
        return tables;
    }

    public void setTables(java.util.List<GeneratedTable> tables) {
        this.tables = tables;
    }
}