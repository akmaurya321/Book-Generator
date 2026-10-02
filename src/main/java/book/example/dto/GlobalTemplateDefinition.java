package book.example.dto;

import java.util.ArrayList;
import java.util.List;

public class GlobalTemplateDefinition {

    private String id;
    private String name;
    private String type;
    private String description;
    private List<TemplateSectionDefinition> sections = new ArrayList<>();
    private DocumentFormatDefinition format = new DocumentFormatDefinition();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public DocumentFormatDefinition getFormat() { return format; }

    public void setFormat(DocumentFormatDefinition format) { this.format = format == null ? new DocumentFormatDefinition() : format; }

    public List<TemplateSectionDefinition> getSections() {
        return sections;
    }

    public void setSections(List<TemplateSectionDefinition> sections) {
        this.sections = sections;
    }
}