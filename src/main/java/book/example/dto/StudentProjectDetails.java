package book.example.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Canonical student and institution information.
 *
 * The same object is carried through the complete generation request so
 * front matter, prompts, and the document assembler never need duplicate
 * versions of these fields.
 */
public class StudentProjectDetails {

    private String name;
    private String rollNumber;
    private String enrollmentNumber;
    private String course;
    private String department;
    private String academicYear;
    private String collegeName;
    private String universityName;
    private String guideName;
    private String guideDesignation;
    private String hodName;
    private String hodDesignation;
    private String submissionDate;
    private String collegeLogoAssetId;
    private String guideSignatureAssetId;
    private String hodSignatureAssetId;
    private List<String> teamMembers = new ArrayList<>();
    private String projectTitleOverride;
    private CoverLayout coverLayout = new CoverLayout();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRollNumber() {
        return rollNumber;
    }

    public void setRollNumber(String rollNumber) {
        this.rollNumber = rollNumber;
    }

    public String getEnrollmentNumber() {
        return enrollmentNumber;
    }

    public void setEnrollmentNumber(String enrollmentNumber) {
        this.enrollmentNumber = enrollmentNumber;
    }

    public String getCourse() {
        return course;
    }

    public void setCourse(String course) {
        this.course = course;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getAcademicYear() {
        return academicYear;
    }

    public void setAcademicYear(String academicYear) {
        this.academicYear = academicYear;
    }

    public String getCollegeName() {
        return collegeName;
    }

    public void setCollegeName(String collegeName) {
        this.collegeName = collegeName;
    }

    public String getUniversityName() {
        return universityName;
    }

    public void setUniversityName(String universityName) {
        this.universityName = universityName;
    }

    public String getGuideName() {
        return guideName;
    }

    public void setGuideName(String guideName) {
        this.guideName = guideName;
    }

    public String getGuideDesignation() {
        return guideDesignation;
    }

    public void setGuideDesignation(String guideDesignation) {
        this.guideDesignation = guideDesignation;
    }

    public String getHodName() { return hodName; }
    public void setHodName(String hodName) { this.hodName = hodName; }
    public String getHodDesignation() { return hodDesignation; }
    public void setHodDesignation(String hodDesignation) { this.hodDesignation = hodDesignation; }
    public String getSubmissionDate() { return submissionDate; }
    public void setSubmissionDate(String submissionDate) { this.submissionDate = submissionDate; }
    public String getCollegeLogoAssetId() { return collegeLogoAssetId; }
    public void setCollegeLogoAssetId(String collegeLogoAssetId) { this.collegeLogoAssetId = collegeLogoAssetId; }
    public String getGuideSignatureAssetId() { return guideSignatureAssetId; }
    public void setGuideSignatureAssetId(String guideSignatureAssetId) { this.guideSignatureAssetId = guideSignatureAssetId; }
    public String getHodSignatureAssetId() { return hodSignatureAssetId; }
    public void setHodSignatureAssetId(String hodSignatureAssetId) { this.hodSignatureAssetId = hodSignatureAssetId; }

    public List<String> getTeamMembers() {
        return teamMembers;
    }

    public void setTeamMembers(List<String> teamMembers) {
        this.teamMembers = teamMembers == null ? new ArrayList<>() : teamMembers;
    }

    public String getProjectTitleOverride() {
        return projectTitleOverride;
    }

    public void setProjectTitleOverride(String projectTitleOverride) {
        this.projectTitleOverride = projectTitleOverride;
    }

    public CoverLayout getCoverLayout() {
        return coverLayout;
    }

    public void setCoverLayout(CoverLayout coverLayout) {
        this.coverLayout = coverLayout == null ? new CoverLayout() : coverLayout;
    }
}