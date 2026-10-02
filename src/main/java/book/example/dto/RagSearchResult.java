package book.example.dto;

public class RagSearchResult {

    private String content;
    private String filePath;
    private String fileType;
    private int startLine;
    private int endLine;
    private double distance;
    private java.util.List<String> symbols = new java.util.ArrayList<>();
    private java.util.List<String> imports = new java.util.ArrayList<>();
    private java.util.List<String> annotations = new java.util.ArrayList<>();
    private java.util.List<String> constructs = new java.util.ArrayList<>();

    public RagSearchResult() {
    }

    public RagSearchResult(
            String content,
            String filePath,
            String fileType,
            int startLine,
            int endLine,
            double distance) {

        this.content = content;
        this.filePath = filePath;
        this.fileType = fileType;
        this.startLine = startLine;
        this.endLine = endLine;
        this.distance = distance;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public int getStartLine() {
        return startLine;
    }

    public void setStartLine(int startLine) {
        this.startLine = startLine;
    }

    public int getEndLine() {
        return endLine;
    }

    public void setEndLine(int endLine) {
        this.endLine = endLine;
    }

    public double getDistance() {
        return distance;
    }

    public void setDistance(double distance) {
        this.distance = distance;
    }

    public java.util.List<String> getSymbols() { return symbols; }
    public void setSymbols(java.util.List<String> values) { this.symbols = values == null ? new java.util.ArrayList<>() : values; }
    public java.util.List<String> getImports() { return imports; }
    public void setImports(java.util.List<String> values) { this.imports = values == null ? new java.util.ArrayList<>() : values; }
    public java.util.List<String> getAnnotations() { return annotations; }
    public void setAnnotations(java.util.List<String> values) { this.annotations = values == null ? new java.util.ArrayList<>() : values; }
    public java.util.List<String> getConstructs() { return constructs; }
    public void setConstructs(java.util.List<String> values) { this.constructs = values == null ? new java.util.ArrayList<>() : values; }
}