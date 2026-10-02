package book.example.dto;

import java.util.ArrayList;
import java.util.List;

public class DiagramSpecification {

    private boolean required;
    private String type;
    private String title;
    private String description;

    private List<DiagramNode> nodes =
            new ArrayList<>();

    private List<DiagramRelationship> relationships =
            new ArrayList<>();

    public DiagramSpecification() {
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<DiagramNode> getNodes() {
        return nodes;
    }

    public void setNodes(List<DiagramNode> nodes) {
        this.nodes = nodes;
    }

    public List<DiagramRelationship> getRelationships() {
        return relationships;
    }

    public void setRelationships(
            List<DiagramRelationship> relationships) {

        this.relationships = relationships;
    }
}