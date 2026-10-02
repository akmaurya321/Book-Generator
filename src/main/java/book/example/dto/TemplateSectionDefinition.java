package book.example.dto;

public class TemplateSectionDefinition {

    private String id;
    private String title;
    private String description;
    private String category;
    private boolean defaultEnabled;
    private boolean optional;
    private String recommendedWhen;
    private String parentId;
    private int order;
    private String level = "2";
    private String contentSource = "LLM_GENERATED";
    private String elementType = "CONTENT";
    private String diagramType;
    private boolean chapter;
    private boolean requiresAdditionalInformation;
    private String evidenceMode = "PROJECT";
    private java.util.List<String> dependsOn = new java.util.ArrayList<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public boolean isDefaultEnabled() {
        return defaultEnabled;
    }

    public void setDefaultEnabled(boolean defaultEnabled) {
        this.defaultEnabled = defaultEnabled;
    }

    public boolean isOptional() {
        return optional;
    }

    public void setOptional(boolean optional) {
        this.optional = optional;
    }

    public String getRecommendedWhen() {
        return recommendedWhen;
    }

    public void setRecommendedWhen(String recommendedWhen) {
        this.recommendedWhen = recommendedWhen;
    }

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getContentSource() {
        return contentSource;
    }

    public void setContentSource(String contentSource) {
        this.contentSource = contentSource;
    }

    public String getElementType() {
        return elementType;
    }

    public void setElementType(String elementType) {
        this.elementType = elementType;
    }

    public String getDiagramType() {
        return diagramType;
    }

    public void setDiagramType(String diagramType) {
        this.diagramType = diagramType;
    }

    public boolean isChapter() {
        return chapter;
    }

    public void setChapter(boolean chapter) {
        this.chapter = chapter;
    }

    public java.util.List<String> getDependsOn() { return dependsOn; }
    public void setDependsOn(java.util.List<String> dependsOn) { this.dependsOn = dependsOn == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(dependsOn); }

    public String getEvidenceMode() { return evidenceMode; }
    public void setEvidenceMode(String evidenceMode) { this.evidenceMode = evidenceMode == null || evidenceMode.isBlank() ? "PROJECT" : evidenceMode; }

    public boolean isRequiresAdditionalInformation() {
        return requiresAdditionalInformation;
    }

    public void setRequiresAdditionalInformation(boolean requiresAdditionalInformation) {
        this.requiresAdditionalInformation = requiresAdditionalInformation;
    }
}