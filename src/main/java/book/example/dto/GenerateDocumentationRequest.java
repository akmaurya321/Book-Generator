package book.example.dto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GenerateDocumentationRequest {

    private String templateId = "global_student_project_v1";
    private List<String> selectedSections = new ArrayList<>();
    private List<String> selectedDiagrams = new ArrayList<>();
    private List<SectionConfiguration> sectionConfigurations = new ArrayList<>();
    private StudentProjectDetails studentDetails = new StudentProjectDetails();
    private StudentContext studentContext = new StudentContext();
    private Map<String, String> additionalInformation = new LinkedHashMap<>();
    private String outputFormat = "docx";

    public String getTemplateId() {
        return templateId;
    }

    public void setTemplateId(String templateId) {
        this.templateId = templateId;
    }

    public List<String> getSelectedSections() {
        return selectedSections;
    }

    public void setSelectedSections(List<String> selectedSections) {
        this.selectedSections = selectedSections;
    }

    public List<String> getSelectedDiagrams() {
        return selectedDiagrams;
    }

    public void setSelectedDiagrams(List<String> selectedDiagrams) {
        this.selectedDiagrams = selectedDiagrams;
    }

    public List<SectionConfiguration> getSectionConfigurations() { return sectionConfigurations; }
    public void setSectionConfigurations(List<SectionConfiguration> sectionConfigurations) { this.sectionConfigurations = sectionConfigurations == null ? new ArrayList<>() : new ArrayList<>(sectionConfigurations); }

    public StudentContext getStudentContext() { return studentContext; }
    public void setStudentContext(StudentContext studentContext) { this.studentContext = studentContext == null ? new StudentContext() : studentContext; }

    public StudentProjectDetails getStudentDetails() {
        return studentDetails;
    }

    public void setStudentDetails(StudentProjectDetails studentDetails) {
        this.studentDetails = studentDetails;
    }

    public Map<String, String> getAdditionalInformation() {
        return additionalInformation;
    }

    public void setAdditionalInformation(Map<String, String> additionalInformation) {
        this.additionalInformation = additionalInformation;
    }

    public String getOutputFormat() {
        return outputFormat;
    }

    public void setOutputFormat(String outputFormat) {
        this.outputFormat = outputFormat;
    }
}