package book.example.dto;

import java.util.List;

public class RepositorySnapshot {

    private String repositoryUrl;
    private List<RepositoryFile> files;

    public RepositorySnapshot() {
    }

    public RepositorySnapshot(
            String repositoryUrl,
            List<RepositoryFile> files) {

        this.repositoryUrl = repositoryUrl;
        this.files = files;
    }

    public String getRepositoryUrl() {
        return repositoryUrl;
    }

    public void setRepositoryUrl(String repositoryUrl) {
        this.repositoryUrl = repositoryUrl;
    }

    public List<RepositoryFile> getFiles() {
        return files;
    }

    public void setFiles(List<RepositoryFile> files) {
        this.files = files;
    }
}