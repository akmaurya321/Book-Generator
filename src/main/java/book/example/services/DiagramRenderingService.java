package book.example.services;

import book.example.dto.DiagramImage;
import book.example.dto.DiagramSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DiagramRenderingService {

        private static final Logger logger = LoggerFactory.getLogger(DiagramRenderingService.class);

    private final MermaidGenerator mermaidGenerator;
    private final DiagramRenderer diagramRenderer;

    public DiagramRenderingService(
            MermaidGenerator mermaidGenerator,
            DiagramRenderer diagramRenderer) {

        this.mermaidGenerator =
                mermaidGenerator;

        this.diagramRenderer =
                diagramRenderer;
    }

    public DiagramImage render(
            DiagramSpecification specification) {

        if (specification == null ||
                !specification.isRequired()) {

            return null;
        }

        String mermaidSource =
                mermaidGenerator.generate(
                        specification
                );

                try {
                        return diagramRenderer.render(specification, mermaidSource);
                } catch (RuntimeException e) {
                        logger.error("Mermaid rendering failed: title={}, source={}",
                                        specification.getTitle(), mermaidSource, e);
                        throw e;
                }
    }
}