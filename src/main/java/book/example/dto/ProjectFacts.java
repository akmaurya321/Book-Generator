package book.example.dto;

import java.util.ArrayList;
import java.util.List;

public class ProjectFacts {

    private String projectName;

    private String projectType;

    private String description;

    private List<String> technologies = new ArrayList<>();

    private List<String> frameworks = new ArrayList<>();

    private List<String> dependencies = new ArrayList<>();

    private List<String> modules = new ArrayList<>();

    private List<String> apiFiles = new ArrayList<>();

    private List<String> databaseFiles = new ArrayList<>();

    private List<String> securityFiles = new ArrayList<>();

    private List<String> configurationFiles = new ArrayList<>();

    private List<String> testFiles = new ArrayList<>();

    private List<String> modelFiles = new ArrayList<>();

    private List<String> documentationFiles = new ArrayList<>();

    private List<String> imageFiles = new ArrayList<>();

    private List<String> allFiles = new ArrayList<>();
    private int analyzedFileCount;
    private int textFileCount;
    private int binaryFileCount;
    private int structurallyAnalyzedFileCount;

    public ProjectFacts() {
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getProjectType() {
        return projectType;
    }

    public void setProjectType(String projectType) {
        this.projectType = projectType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getTechnologies() {
        return technologies;
    }

    public void setTechnologies(List<String> technologies) {
        this.technologies = technologies;
    }

    public List<String> getFrameworks() {
        return frameworks;
    }

    public void setFrameworks(List<String> frameworks) {
        this.frameworks = frameworks;
    }

    public List<String> getDependencies() {
        return dependencies;
    }

    public void setDependencies(List<String> dependencies) {
        this.dependencies = dependencies;
    }

    public List<String> getModules() {
        return modules;
    }

    public void setModules(List<String> modules) {
        this.modules = modules;
    }

    public List<String> getApiFiles() {
        return apiFiles;
    }

    public void setApiFiles(List<String> apiFiles) {
        this.apiFiles = apiFiles;
    }

    public List<String> getDatabaseFiles() {
        return databaseFiles;
    }

    public void setDatabaseFiles(List<String> databaseFiles) {
        this.databaseFiles = databaseFiles;
    }

    public List<String> getSecurityFiles() { return securityFiles; }
    public void setSecurityFiles(List<String> securityFiles) { this.securityFiles = securityFiles == null ? new ArrayList<>() : securityFiles; }

    public List<String> getConfigurationFiles() {
        return configurationFiles;
    }

    public void setConfigurationFiles(List<String> configurationFiles) {
        this.configurationFiles = configurationFiles;
    }

    public List<String> getTestFiles() {
        return testFiles;
    }

    public void setTestFiles(List<String> testFiles) {
        this.testFiles = testFiles;
    }

    public List<String> getModelFiles() {
        return modelFiles;
    }

    public void setModelFiles(List<String> modelFiles) {
        this.modelFiles = modelFiles;
    }

    public List<String> getDocumentationFiles() {
        return documentationFiles;
    }

    public void setDocumentationFiles(List<String> documentationFiles) {
        this.documentationFiles = documentationFiles;
    }

    public List<String> getImageFiles() {
        return imageFiles;
    }

    public void setImageFiles(List<String> imageFiles) {
        this.imageFiles = imageFiles;
    }

    public int getAnalyzedFileCount() { return analyzedFileCount; }
    public void setAnalyzedFileCount(int analyzedFileCount) { this.analyzedFileCount = analyzedFileCount; }
    public int getTextFileCount() { return textFileCount; }
    public void setTextFileCount(int textFileCount) { this.textFileCount = textFileCount; }
    public int getBinaryFileCount() { return binaryFileCount; }
    public void setBinaryFileCount(int binaryFileCount) { this.binaryFileCount = binaryFileCount; }
    public int getStructurallyAnalyzedFileCount() { return structurallyAnalyzedFileCount; }
    public void setStructurallyAnalyzedFileCount(int structurallyAnalyzedFileCount) { this.structurallyAnalyzedFileCount = structurallyAnalyzedFileCount; }

    public List<String> getAllFiles() {
        return allFiles;
    }

    public void setAllFiles(List<String> allFiles) {
        this.allFiles = allFiles;
    }
}