package book.example.dto;

import java.util.ArrayList;
import java.util.List;

/** Human-provided context that repository analysis cannot reliably infer. */
public class StudentContext {
    private String motivation = "";
    private String problemStatement = "";
    private List<String> objectives = new ArrayList<>();
    private String targetUsers = "";
    private String expectedBenefits = "";
    private String limitations = "";
    private String futureIdeas = "";
    private String additionalNotes = "";
    private String source = "USER";

    public String getMotivation() { return motivation; }
    public void setMotivation(String value) { this.motivation = value == null ? "" : value; }
    public String getProblemStatement() { return problemStatement; }
    public void setProblemStatement(String value) { this.problemStatement = value == null ? "" : value; }
    public List<String> getObjectives() { return objectives; }
    public void setObjectives(List<String> value) { this.objectives = value == null ? new ArrayList<>() : new ArrayList<>(value); }
    public String getTargetUsers() { return targetUsers; }
    public void setTargetUsers(String value) { this.targetUsers = value == null ? "" : value; }
    public String getExpectedBenefits() { return expectedBenefits; }
    public void setExpectedBenefits(String value) { this.expectedBenefits = value == null ? "" : value; }
    public String getLimitations() { return limitations; }
    public void setLimitations(String value) { this.limitations = value == null ? "" : value; }
    public String getFutureIdeas() { return futureIdeas; }
    public void setFutureIdeas(String value) { this.futureIdeas = value == null ? "" : value; }
    public String getAdditionalNotes() { return additionalNotes; }
    public void setAdditionalNotes(String value) { this.additionalNotes = value == null ? "" : value; }
    public String getSource() { return source; }
    public void setSource(String value) { this.source = value == null ? "USER" : value; }
}
