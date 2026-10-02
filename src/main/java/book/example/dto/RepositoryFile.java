package book.example.dto;

import java.util.ArrayList;
import java.util.List;

public class RepositoryFile {

    private String path;
    private String content;
    private String fileType;
    private long size;
    private boolean binary;
    private String sha256;
    private int lineCount;
    private List<String> imports = new ArrayList<>();
    private List<String> symbols = new ArrayList<>();
    private List<String> annotations = new ArrayList<>();
    private List<String> constructs = new ArrayList<>();

    public RepositoryFile() {
    }

    public RepositoryFile(
            String path,
            String content,
            String fileType,
            long size,
            boolean binary) {

        this.path = path;
        this.content = content;
        this.fileType = fileType;
        this.size = size;
        this.binary = binary;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }
    public int getLineCount() { return lineCount; }
    public void setLineCount(int lineCount) { this.lineCount = lineCount; }
    public List<String> getImports() { return imports; }
    public void setImports(List<String> imports) { this.imports = imports == null ? new ArrayList<>() : imports; }
    public List<String> getSymbols() { return symbols; }
    public void setSymbols(List<String> symbols) { this.symbols = symbols == null ? new ArrayList<>() : symbols; }
    public List<String> getAnnotations() { return annotations; }
    public void setAnnotations(List<String> annotations) { this.annotations = annotations == null ? new ArrayList<>() : annotations; }
    public List<String> getConstructs() { return constructs; }
    public void setConstructs(List<String> constructs) { this.constructs = constructs == null ? new ArrayList<>() : constructs; }

    public boolean isBinary() {
        return binary;
    }

    public void setBinary(boolean binary) {
        this.binary = binary;
    }
}