package book.example.dto;

public class DiagramImage {

    private String title;
    private String format;
    private byte[] data;

    public DiagramImage() {
    }

    public DiagramImage(
            String title,
            String format,
            byte[] data) {

        this.title = title;
        this.format = format;
        this.data = data;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }
}