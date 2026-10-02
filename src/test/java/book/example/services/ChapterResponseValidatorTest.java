package book.example.services;

import book.example.dto.ChapterSectionOutput;
import book.example.dto.DocumentationSection;
import book.example.dto.RagSearchResult;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ChapterResponseValidatorTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void acceptsExactChapterContract() {
        DocumentationSection a = section("a");
        DocumentationSection b = section("b");
        Map<String, RagSearchResult> evidence = evidence();
        String json = """
                {"chapterId":"chapter-1","sections":[
                  {"sectionId":"a","content":"Java backend uses Spring Boot. [EVIDENCE: E-aaaaaa]","evidenceIds":["E-aaaaaa"],"assets":[]},
                  {"sectionId":"b","content":"PostgreSQL stores data. [EVIDENCE: E-bbbbbb]","evidenceIds":["E-bbbbbb"],"assets":[]}
                ]}
                """;
        var result = ChapterResponseValidator.validate(json, "chapter-1", List.of(a, b), evidence, mapper);
        assertTrue(result.failures().isEmpty());
        assertEquals(Set.of("a", "b"), result.validSections().keySet());
    }

    @Test
    void rejectsMissingDuplicateUnknownAndUnknownEvidence() {
        DocumentationSection a = section("a");
        DocumentationSection b = section("b");
        String json = """
                {"chapterId":"chapter-1","sections":[
                  {"sectionId":"a","content":"ok","evidenceIds":["E-aaaaaa"],"assets":[]},
                  {"sectionId":"a","content":"duplicate","evidenceIds":["E-aaaaaa"],"assets":[]},
                  {"sectionId":"x","content":"unknown","evidenceIds":[],"assets":[]}
                ]}
                """;
        var result = ChapterResponseValidator.validate(json, "chapter-1", List.of(a, b), evidence(), mapper);
        assertTrue(result.failures().containsKey("a"));
        assertTrue(result.failures().containsKey("b"));
        assertTrue(result.failures().containsKey("x"));
    }

    private DocumentationSection section(String id) {
        DocumentationSection s = new DocumentationSection();
        s.setId(id);
        s.setTitle(id);
        s.setContentPurpose("test");
        return s;
    }

    private Map<String, RagSearchResult> evidence() {
        Map<String, RagSearchResult> m = new LinkedHashMap<>();
        m.put("E-aaaaaa", new RagSearchResult("Java Spring Boot", "A.java", "JAVA", 1, 4, 1.0));
        m.put("E-bbbbbb", new RagSearchResult("PostgreSQL", "B.java", "JAVA", 1, 4, 1.0));
        return m;
    }
    @Test
    void rejectsUnknownJsonFieldsAtBackendContractBoundary() {
        DocumentationSection section = section("a");
        String json = "{\"chapterId\":\"c1\",\"unexpected\":true,\"sections\":[]}";
        var result = ChapterResponseValidator.validate(
                json, "c1", List.of(section), Map.of(), mapper);
        assertTrue(result.failures().containsKey("__STRUCTURE__"));
        assertTrue(result.validSections().isEmpty());
    }

    @Test
    void duplicateSectionForcesRepairInsteadOfPreservingAmbiguousCopy() {
        DocumentationSection section = new DocumentationSection();
        section.setId("a");
        section.setTitle("A");
        section.setContentPurpose("test");
        RagSearchResult evidence = new RagSearchResult("Java", "A.java", "JAVA", 1, 2, 1);
        String json = "{\"chapterId\":\"c1\",\"sections\":["
                + "{\"sectionId\":\"a\",\"content\":\"first [EVIDENCE: E-1]\",\"evidenceIds\":[\"E-1\"],\"assets\":[]},"
                + "{\"sectionId\":\"a\",\"content\":\"duplicate [EVIDENCE: E-1]\",\"evidenceIds\":[\"E-1\"],\"assets\":[]}] }";
        var result = ChapterResponseValidator.validate(
                json, "c1", List.of(section), Map.of("E-1", evidence), new ObjectMapper());
        assertFalse(result.validSections().containsKey("a"));
        assertTrue(result.failures().containsKey("a"));
    }

    @Test
    void extractsGeneratedTextEvenWhenEvidenceValidationRejectsTheSection() throws Exception {
        DocumentationSection section = section("a");
        String json = """
                {"chapterId":"c1","sections":[
                  {"sectionId":"a","content":"Keep this generated draft visible.","evidenceIds":["E-missing"],"assets":[]}
                ]}
                """;

        var extracted = ChapterResponseValidator.extractDraftSections(
                json,
                List.of(section),
                mapper);

        assertEquals(
                "Keep this generated draft visible.",
                extracted.get("a").getContent());
        var validation = ChapterResponseValidator.validate(
                json,
                "c1",
                List.of(section),
                evidence(),
                mapper);
        assertTrue(validation.validSections().isEmpty());
        assertTrue(validation.failures().containsKey("a"));
    }

}
