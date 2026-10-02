package book.example.services;

import book.example.dto.DocumentationSection;
import book.example.dto.ProjectFacts;
import book.example.dto.RagSearchResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmartRagSectionPromptTest {

    @Test
    void sectionValidationRejectsMarkdownJsonAndPlaceholderLeakage() {
        DocumentationSection section = new DocumentationSection();
        section.setTitle("System Architecture");

        ProjectFacts facts = new ProjectFacts();
        facts.setProjectName("Demo Project");
        facts.setTechnologies(List.of("Java", "Spring Boot"));

        String invalid = "```json\n{\"bad\": true}\n```\nThis is a placeholder and repeated repeated repeated.";
        var result = SectionQualityValidator.validate(invalid, section, facts);

        assertFalse(result.valid());
        assertTrue(result.repairable());
    }

    @Test
    void smartRagFilteringPrefersRelevantEvidence() {
        RagSearchResult irrelevant = new RagSearchResult("README text", "README.md", "MARKDOWN", 1, 10, 0.9);
        RagSearchResult relevant = new RagSearchResult("Spring Boot backend service configuration and PostgreSQL entity model", "src/main/java/com/demo/service/UserService.java", "JAVA", 40, 80, 0.2);

        List<RagSearchResult> ranked = ChromaSearchService.rankForSection(List.of(irrelevant, relevant), "database design");

        assertTrue(ranked.getFirst().getFilePath().contains("UserService"));
    }

    @Test
    void sectionValidationRejectsRunawayAndRepeatedParagraphs() {
        DocumentationSection section = new DocumentationSection();
        section.setTitle("Implementation");

        String paragraph = "The implementation evidence describes a concrete repository module and its verified responsibility.";
        String repeated = paragraph + "\n\n" + paragraph;
        assertFalse(SectionQualityValidator.validate(repeated, section, new ProjectFacts()).valid());

        String oversized = "x".repeat(12_001);
        assertFalse(SectionQualityValidator.validate(oversized, section, new ProjectFacts()).valid());
    }
}
