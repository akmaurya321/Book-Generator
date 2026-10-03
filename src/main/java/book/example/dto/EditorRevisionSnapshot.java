package book.example.dto;

public class EditorRevisionSnapshot {
    private String documentPath;
    private String pdfPath;

    public EditorRevisionSnapshot() {
    }

    public EditorRevisionSnapshot(String documentPath, String pdfPath) {
        this.documentPath = documentPath;
        this.pdfPath = pdfPath;
    }

    public String getDocumentPath() {
        return documentPath;
    }

    public void setDocumentPath(String documentPath) {
        this.documentPath = documentPath;
    }

    public String getPdfPath() {
        return pdfPath;
    }

    public void setPdfPath(String pdfPath) {
        this.pdfPath = pdfPath;
    }
}
