package book.example.services;

import book.example.dto.DiagramSpecification;
import book.example.dto.GeneratedSection;
import book.example.dto.ProjectFacts;
import book.example.dto.RagSearchResult;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DiagramDecisionServiceTest {

    @Test
    void acceptsDiagramNodesPresentInRetrievedRepositoryEvidence() {
        LlmWorkerPool workerPool = mock(LlmWorkerPool.class);
        when(workerPool.generateWithRetry(anyString())).thenReturn("""
                {"required":true,"type":"ARCHITECTURE","title":"Email Flow",
                 "description":"Observed call flow","nodes":[
                   {"id":"controller","label":"EmailController","type":"component"},
                   {"id":"service","label":"EmailService","type":"component"}],
                 "relationships":[{"from":"controller","to":"service","label":"calls"}]}
                """);

        ProjectFacts facts = new ProjectFacts();
        facts.setProjectName("Mail App");
        facts.setAllFiles(List.of("src/main/java/EmailController.java", "src/main/java/EmailService.java"));

        GeneratedSection section = architectureSection("The application processes incoming mail.");
        List<RagSearchResult> evidence = List.of(new RagSearchResult(
                "EmailController delegates message handling to EmailService.",
                "src/main/java/EmailController.java", "JAVA", 1, 30, 0.1));

        DiagramSpecification result = new DiagramDecisionService(workerPool, new ObjectMapper())
                .decide("job-1", facts, section, evidence);

        assertTrue(result.isRequired());
        assertTrue(result.getRelationships().getFirst().getTo().equals("service"));
    }

    @Test
    void rejectsDiagramNodeFoundOnlyInGeneratedProse() {
        LlmWorkerPool workerPool = mock(LlmWorkerPool.class);
        when(workerPool.generateWithRetry(anyString())).thenReturn("""
                {"required":true,"type":"ARCHITECTURE","title":"Fake Flow",
                 "description":"Unsupported component","nodes":[
                   {"id":"ghost","label":"GhostService","type":"component"},
                   {"id":"app","label":"Demo.java","type":"component"}],
                 "relationships":[{"from":"ghost","to":"app","label":"calls"}]}
                """);

        ProjectFacts facts = new ProjectFacts();
        facts.setProjectName("Demo");
        facts.setAllFiles(List.of("src/main/java/Demo.java"));

        GeneratedSection section = architectureSection(
                "GhostService performs all processing before Demo.java starts.");

        DiagramSpecification result = new DiagramDecisionService(workerPool, new ObjectMapper())
                .decide("job-2", facts, section, List.of());

        assertFalse(result.isRequired());
    }

    private GeneratedSection architectureSection(String content) {
        GeneratedSection section = new GeneratedSection();
        section.setTitle("System Architecture");
        section.setLevel("H1");
        section.setContent(content);
        return section;
    }
}
