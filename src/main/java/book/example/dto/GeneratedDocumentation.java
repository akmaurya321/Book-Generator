package book.example.dto;

import java.util.ArrayList;
import java.util.List;

public class GeneratedDocumentation {

    private String projectName;
    private String title;
    private StudentProjectDetails studentDetails = new StudentProjectDetails();

    private List<GeneratedSection> sections =
            new ArrayList<>();
    private List<String> enabledSectionIds = new ArrayList<>();
    private DocumentFormatDefinition format = new DocumentFormatDefinition();

    public GeneratedDocumentation() {
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public StudentProjectDetails getStudentDetails() {
        return studentDetails;
    }

    public void setStudentDetails(StudentProjectDetails studentDetails) {
        this.studentDetails = studentDetails;
    }

    public List<GeneratedSection> getSections() {
        return sections;
    }

    public void setSections(
            List<GeneratedSection> sections) {

        this.sections = sections;
    }

    public DocumentFormatDefinition getFormat() { return format; }
    public void setFormat(DocumentFormatDefinition format) { this.format = format == null ? new DocumentFormatDefinition() : format; }

    public List<String> getEnabledSectionIds() {
        return enabledSectionIds;
    }

    public void setEnabledSectionIds(List<String> enabledSectionIds) {
        this.enabledSectionIds = enabledSectionIds;
    }
}