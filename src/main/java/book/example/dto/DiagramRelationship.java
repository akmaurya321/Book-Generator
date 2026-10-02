package book.example.dto;

public class DiagramRelationship {

    private String from;
    private String to;
    private String label;

    public DiagramRelationship() {
    }

    public DiagramRelationship(
            String from,
            String to,
            String label) {

        this.from = from;
        this.to = to;
        this.label = label;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }
}