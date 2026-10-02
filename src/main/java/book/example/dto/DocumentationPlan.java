package book.example.dto;

import java.util.ArrayList;
import java.util.List;

public class DocumentationPlan {

    private String templateId = "global_student_project_v1";
    private String projectName;

    private String documentTitle;

    private List<DocumentationSection> sections = new ArrayList<>();
    private StudentProjectDetails studentDetails = new StudentProjectDetails();
    private StudentContext studentContext = new StudentContext();
    private java.util.Map<String, String> additionalInformation = new java.util.LinkedHashMap<>();
    private List<String> selectedDiagrams = new ArrayList<>();
    private List<UploadedAsset> uploadedAssets = new ArrayList<>();
    private List<SectionConfiguration> sectionConfigurations = new ArrayList<>();
    private java.util.Map<String, List<String>> chapterDependencies = new java.util.LinkedHashMap<>();
    private DocumentFormatDefinition format = new DocumentFormatDefinition();
    private boolean projectEvidencePartial;
    private int failedEvidenceChunks;

    public DocumentationPlan() {
    }

    public String getTemplateId() {
        return templateId;
    }

    public void setTemplateId(String templateId) {
        this.templateId = templateId;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getDocumentTitle() {
        return documentTitle;
    }

    public void setDocumentTitle(String documentTitle) {
        this.documentTitle = documentTitle;
    }

    public List<DocumentationSection> getSections() {
        return sections;
    }

    public void setSections(List<DocumentationSection> sections) {
        this.sections = sections;
    }

    public StudentContext getStudentContext() { return studentContext; }
    public void setStudentContext(StudentContext studentContext) { this.studentContext = studentContext == null ? new StudentContext() : studentContext; }

    public StudentProjectDetails getStudentDetails() {
        return studentDetails;
    }

    public void setStudentDetails(StudentProjectDetails studentDetails) {
        this.studentDetails = studentDetails;
    }

    public java.util.Map<String, String> getAdditionalInformation() {
        return additionalInformation;
    }

    public void setAdditionalInformation(java.util.Map<String, String> additionalInformation) {
        this.additionalInformation = additionalInformation;
    }

    public List<SectionConfiguration> getSectionConfigurations() { return sectionConfigurations; }
    public void setSectionConfigurations(List<SectionConfiguration> value) { this.sectionConfigurations = value == null ? new ArrayList<>() : new ArrayList<>(value); }
    public java.util.Map<String, List<String>> getChapterDependencies() { return chapterDependencies; }
    public void setChapterDependencies(java.util.Map<String, List<String>> value) {
        this.chapterDependencies = value == null ? new java.util.LinkedHashMap<>() : new java.util.LinkedHashMap<>(value);
    }

    public DocumentFormatDefinition getFormat() { return format; }
    public void setFormat(DocumentFormatDefinition format) { this.format = format == null ? new DocumentFormatDefinition() : format; }

    public boolean isProjectEvidencePartial() { return projectEvidencePartial; }
    public void setProjectEvidencePartial(boolean value) { this.projectEvidencePartial = value; }
    public int getFailedEvidenceChunks() { return failedEvidenceChunks; }
    public void setFailedEvidenceChunks(int value) { this.failedEvidenceChunks = value; }

    public List<UploadedAsset> getUploadedAssets() { return uploadedAssets; }
    public void setUploadedAssets(List<UploadedAsset> value) { this.uploadedAssets = value == null ? new ArrayList<>() : new ArrayList<>(value); }

    public List<String> getSelectedDiagrams() {
        return selectedDiagrams;
    }

    public void setSelectedDiagrams(List<String> selectedDiagrams) {
        this.selectedDiagrams = selectedDiagrams;
    }
}