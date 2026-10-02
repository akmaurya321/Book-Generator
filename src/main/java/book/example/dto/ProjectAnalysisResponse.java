package book.example.dto;

import java.util.ArrayList;
import java.util.List;

public class ProjectAnalysisResponse {

    private String jobId;
    private String templateId;
    private String status;
    private ProjectFacts projectFacts;
    private GlobalTemplateDefinition template;
    private List<SectionRecommendation> recommendations = new ArrayList<>();
    private boolean indexingPartial;
    private int failedIndexingChunks;

    public ProjectAnalysisResponse() {
    }

    public ProjectAnalysisResponse(
            String jobId,
            String templateId,
            String status,
            ProjectFacts projectFacts,
            GlobalTemplateDefinition template,
            List<SectionRecommendation> recommendations) {
        this.jobId = jobId;
        this.templateId = templateId;
        this.status = status;
        this.projectFacts = projectFacts;
        this.template = template;
        this.recommendations = recommendations;
    }

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getTemplateId() {
        return templateId;
    }

    public void setTemplateId(String templateId) {
        this.templateId = templateId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public ProjectFacts getProjectFacts() {
        return projectFacts;
    }

    public void setProjectFacts(ProjectFacts projectFacts) {
        this.projectFacts = projectFacts;
    }

    public GlobalTemplateDefinition getTemplate() {
        return template;
    }

    public void setTemplate(GlobalTemplateDefinition template) {
        this.template = template;
    }

    public boolean isIndexingPartial() { return indexingPartial; }
    public void setIndexingPartial(boolean indexingPartial) { this.indexingPartial = indexingPartial; }
    public int getFailedIndexingChunks() { return failedIndexingChunks; }
    public void setFailedIndexingChunks(int failedIndexingChunks) { this.failedIndexingChunks = failedIndexingChunks; }

    public List<SectionRecommendation> getRecommendations() {
        return recommendations;
    }

    public void setRecommendations(List<SectionRecommendation> recommendations) {
        this.recommendations = recommendations;
    }
}