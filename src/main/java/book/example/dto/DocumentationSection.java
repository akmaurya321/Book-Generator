package book.example.dto;

import java.util.ArrayList;
import java.util.List;

public class DocumentationSection {

    private String id;
    private int order;

    private String title;

    private String level;
    private String parentId;

    private String sourceHeading;

    private List<String> subsections = new ArrayList<>();

    private boolean requiresDiagram;
    private boolean contentEnabled = true;
    private boolean imageEnabled;
    private boolean diagramEnabled;
    private String chapterId;
    private List<String> imageIds = new ArrayList<>();

    private String status;

    private String elementType = "CONTENT";

    private String generationMode = "CONTENT";

    private String contentPurpose;

    private boolean required;

    private boolean optional;

    private boolean diagramEligible;

    private boolean tableEligible;

    private String targetContentSize;
    private boolean requiresAdditionalInformation;
    private String evidenceMode = "PROJECT";

    private List<String> templateFields = new ArrayList<>();

    private List<String> signatureFields = new ArrayList<>();

    public DocumentationSection() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public String getSourceHeading() {
        return sourceHeading;
    }

    public void setSourceHeading(String sourceHeading) {
        this.sourceHeading = sourceHeading;
    }

    public List<String> getSubsections() {
        return subsections;
    }

    public void setSubsections(List<String> subsections) {
        this.subsections = subsections;
    }

    public boolean isContentEnabled() { return contentEnabled; }
    public void setContentEnabled(boolean value) { this.contentEnabled = value; }
    public boolean isImageEnabled() { return imageEnabled; }
    public void setImageEnabled(boolean value) { this.imageEnabled = value; }
    public boolean isDiagramEnabled() { return diagramEnabled; }
    public void setDiagramEnabled(boolean value) { this.diagramEnabled = value; }
    public String getChapterId() { return chapterId; }
    public void setChapterId(String chapterId) { this.chapterId = chapterId; }
    public List<String> getImageIds() { return imageIds; }
    public void setImageIds(List<String> imageIds) { this.imageIds = imageIds == null ? new ArrayList<>() : new ArrayList<>(imageIds); }

    public boolean isRequiresDiagram() {
        return requiresDiagram;
    }

    public void setRequiresDiagram(boolean requiresDiagram) {
        this.requiresDiagram = requiresDiagram;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getElementType() {
        return elementType;
    }

    public void setElementType(String elementType) {
        this.elementType = elementType;
    }

    public String getGenerationMode() {
        return generationMode;
    }

    public void setGenerationMode(String generationMode) {
        this.generationMode = generationMode;
    }

    public String getContentPurpose() {
        return contentPurpose;
    }

    public void setContentPurpose(String contentPurpose) {
        this.contentPurpose = contentPurpose;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public boolean isOptional() {
        return optional;
    }

    public void setOptional(boolean optional) {
        this.optional = optional;
    }

    public boolean isDiagramEligible() {
        return diagramEligible;
    }

    public void setDiagramEligible(boolean diagramEligible) {
        this.diagramEligible = diagramEligible;
    }

    public boolean isTableEligible() {
        return tableEligible;
    }

    public void setTableEligible(boolean tableEligible) {
        this.tableEligible = tableEligible;
    }

    public String getTargetContentSize() {
        return targetContentSize;
    }

    public void setTargetContentSize(String targetContentSize) {
        this.targetContentSize = targetContentSize;
    }

    public String getEvidenceMode() { return evidenceMode; }
    public void setEvidenceMode(String evidenceMode) { this.evidenceMode = evidenceMode == null ? "PROJECT" : evidenceMode; }

    public boolean isRequiresAdditionalInformation() {
        return requiresAdditionalInformation;
    }

    public void setRequiresAdditionalInformation(boolean requiresAdditionalInformation) {
        this.requiresAdditionalInformation = requiresAdditionalInformation;
    }

    public List<String> getTemplateFields() {
        return templateFields;
    }

    public void setTemplateFields(List<String> templateFields) {
        this.templateFields = templateFields;
    }

    public List<String> getSignatureFields() {
        return signatureFields;
    }

    public void setSignatureFields(List<String> signatureFields) {
        this.signatureFields = signatureFields;
    }
}