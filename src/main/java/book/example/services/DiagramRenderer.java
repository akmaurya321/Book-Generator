package book.example.services;

import book.example.dto.DiagramImage;
import book.example.dto.DiagramSpecification;

public interface DiagramRenderer {

    DiagramImage render(
            DiagramSpecification specification,
            String mermaidSource);
}