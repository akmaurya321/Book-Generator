package book.example.services;

import book.example.dto.DiagramNode;
import book.example.dto.DiagramRelationship;
import book.example.dto.DiagramSpecification;
import org.springframework.stereotype.Service;

@Service
public class MermaidGenerator {

    public String generate(
            DiagramSpecification specification) {

        if (specification == null ||
                !specification.isRequired()) {

            return null;
        }

                validateGraph(specification);

        String type =
                specification.getType()
                        .toUpperCase();

        return switch (type) {

            case "FLOWCHART",
                 "ARCHITECTURE" ->
                    generateFlowchart(specification);

            case "SEQUENCE" ->
                    generateSequence(specification);

            case "ER" ->
                    generateErDiagram(specification);

            default ->
                    throw new IllegalArgumentException(
                            "Unsupported diagram type: "
                                    + type
                    );
        };
    }

        private void validateGraph(DiagramSpecification specification) {
                if (specification.getNodes() == null || specification.getNodes().isEmpty()) {
                        throw new IllegalArgumentException("Diagram requires at least one node");
                }

                java.util.Set<String> nodeIds = new java.util.HashSet<>();
                for (DiagramNode node : specification.getNodes()) {
                        if (node == null || node.getId() == null || node.getId().isBlank()
                                        || node.getLabel() == null || node.getLabel().isBlank()) {
                                throw new IllegalArgumentException("Diagram node id and label are required");
                        }
                        if (!nodeIds.add(sanitizeId(node.getId()))) {
                                throw new IllegalArgumentException("Diagram node ids must be unique after sanitization");
                        }
                }

                if (specification.getRelationships() == null || specification.getRelationships().isEmpty()) {
                        throw new IllegalArgumentException("Diagram requires at least one relationship");
                }

                for (DiagramRelationship relationship : specification.getRelationships()) {
                        if (relationship == null || relationship.getFrom() == null || relationship.getTo() == null
                                        || !nodeIds.contains(sanitizeId(relationship.getFrom()))
                                        || !nodeIds.contains(sanitizeId(relationship.getTo()))) {
                                throw new IllegalArgumentException("Diagram relationship references an unknown node");
                        }
                }
        }

    private String generateFlowchart(
            DiagramSpecification specification) {

        StringBuilder mermaid =
                new StringBuilder();

        mermaid.append("flowchart TD\n");

        for (DiagramNode node :
                specification.getNodes()) {

            String id =
                    sanitizeId(node.getId());

            String label =
                    escapeLabel(node.getLabel());

            mermaid.append("    ")
                    .append(id)
                    .append("[\"")
                    .append(label)
                    .append("\"]\n");
        }

        for (DiagramRelationship relationship :
                specification.getRelationships()) {

            String from =
                    sanitizeId(
                            relationship.getFrom()
                    );

            String to =
                    sanitizeId(
                            relationship.getTo()
                    );

            String label =
                    escapeLabel(
                            relationship.getLabel()
                    );

            if (label.isBlank()) {

                mermaid.append("    ")
                        .append(from)
                        .append(" --> ")
                        .append(to)
                        .append("\n");

            } else {

                mermaid.append("    ")
                        .append(from)
                        .append(" -->|")
                        .append(label)
                        .append("| ")
                        .append(to)
                        .append("\n");
            }
        }

        return mermaid.toString();
    }

    private String generateSequence(
            DiagramSpecification specification) {

        StringBuilder mermaid =
                new StringBuilder();

        mermaid.append("sequenceDiagram\n");

        for (DiagramNode node :
                specification.getNodes()) {

            String id =
                    sanitizeId(node.getId());

            String label =
                    escapeLabel(node.getLabel());

            mermaid.append("    participant ")
                    .append(id)
                    .append(" as ")
                    .append(label)
                    .append("\n");
        }

        for (DiagramRelationship relationship :
                specification.getRelationships()) {

            String from =
                    sanitizeId(
                            relationship.getFrom()
                    );

            String to =
                    sanitizeId(
                            relationship.getTo()
                    );

            String label =
                    escapeLabel(
                            relationship.getLabel()
                    );

            mermaid.append("    ")
                    .append(from)
                    .append("->>")
                    .append(to)
                    .append(": ")
                    .append(label)
                    .append("\n");
        }

        return mermaid.toString();
    }

    private String generateErDiagram(
            DiagramSpecification specification) {

        StringBuilder mermaid =
                new StringBuilder();

        mermaid.append("erDiagram\n");

        for (DiagramRelationship relationship :
                specification.getRelationships()) {

            String from =
                    sanitizeId(
                            relationship.getFrom()
                    );

            String to =
                    sanitizeId(
                            relationship.getTo()
                    );

            mermaid.append("    ")
                    .append(from)
                    .append(" ||--o{ ")
                    .append(to)
                    .append(" : ")
                    .append(
                            escapeLabel(
                                    relationship.getLabel()
                            )
                    )
                    .append("\n");
        }

        return mermaid.toString();
    }

    private String sanitizeId(
            String value) {

        if (value == null ||
                value.isBlank()) {

            return "Node";
        }

        return value
                .replaceAll(
                        "[^a-zA-Z0-9_]",
                        "_"
                );
    }

    private String escapeLabel(
            String value) {

        if (value == null) {
            return "";
        }

        return value
                .replace("\"", "'")
                .replace("\n", " ")
                .replace("\r", " ")
                .trim();
    }
}