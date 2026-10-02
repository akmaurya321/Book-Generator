package book.example.services;

import book.example.dto.DiagramImage;
import book.example.dto.DiagramSpecification;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class MermaidCliRendererTest {

    @TempDir
    Path tempDirectory;

    @Test
    void rendersDiagramWithWindowsNpmCmdWrapper() {
        String appData = System.getenv("APPDATA");
        assumeTrue(appData != null && Files.exists(Path.of(appData, "npm", "mmdc.cmd")),
                "Windows npm Mermaid CLI wrapper is not installed");

        MermaidRenderProperties properties = new MermaidRenderProperties();
        DiagramSpecification specification = new DiagramSpecification();
        specification.setTitle("Renderer smoke test");

        DiagramImage image = new MermaidCliRenderer(properties).render(
                specification,
                "flowchart TD\n    A[Start] --> B[Finish]\n"
        );

        assertTrue(image.getData().length > 0);
        assertTrue(image.getFormat().equalsIgnoreCase("png"));
    }
}
