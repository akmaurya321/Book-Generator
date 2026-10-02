package book.example;

import book.example.Entity.AppUser;
import book.example.dto.DiagramSpecification;
import book.example.dto.DocumentationSection;
import book.example.dto.GeneratedDocumentation;
import book.example.dto.GeneratedSection;
import book.example.dto.ProjectFacts;
import book.example.services.LlmWorkerPool;
import book.example.services.DiagramDecisionService;
import book.example.services.DocumentAssembler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
class BookGeneratorProjectApplicationTests {

    @Test
    void contextLoads() {
    }

    @Test
    void appUser_idUsesUuidToMatchPostgresSchema() throws NoSuchFieldException {
        assertEquals(UUID.class, AppUser.class.getDeclaredField("id").getType());
    }

    @Test
    void decide_skipsDiagramForGenericOverviewSection() {
        LlmWorkerPool workerPool = mock(LlmWorkerPool.class);
        DiagramDecisionService service = new DiagramDecisionService(workerPool, new tools.jackson.databind.ObjectMapper());

        ProjectFacts projectFacts = new ProjectFacts();
        projectFacts.setProjectName("Demo Project");
        projectFacts.setProjectType("Spring Boot");
        projectFacts.setTechnologies(List.of("Java", "Spring Boot"));
        projectFacts.setFrameworks(List.of("Spring Web"));

        GeneratedSection section = new GeneratedSection();
        section.setOrder(1);
        section.setTitle("Overview");
        section.setLevel("H2");
        section.setContent("This application provides a general introduction to the system and is designed to help users understand the product.");

        DiagramSpecification result = service.decide("job-1", projectFacts, section);

        assertFalse(result.isRequired());
        verify(workerPool, never()).generateWithRetry(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void assemble_usesGlobalHeadingStyles() throws Exception {
        DocumentAssembler assembler = new DocumentAssembler();

        GeneratedDocumentation documentation = new GeneratedDocumentation();
        documentation.setProjectName("Demo Project");
        documentation.setTitle("Demo Documentation");

        GeneratedSection overview = new GeneratedSection(1, "Overview", "H2", "General overview text.");
        GeneratedSection architecture = new GeneratedSection(2, "Architecture", "H2", "Architecture text.");
        documentation.setSections(List.of(overview, architecture));

        java.nio.file.Path path = assembler.assemble(documentation, "job-numbered");

        try (org.apache.poi.xwpf.usermodel.XWPFDocument document =
                     new org.apache.poi.xwpf.usermodel.XWPFDocument(java.nio.file.Files.newInputStream(path))) {
                assertTrue(document.getParagraphs().stream().anyMatch(p -> p.getText().equals("1.1 Overview")
                        && "Heading2".equals(p.getStyle())));
                assertTrue(document.getParagraphs().stream().anyMatch(p -> p.getText().equals("1.2 Architecture")
                    && "Heading2".equals(p.getStyle())));
        }
    }
}
