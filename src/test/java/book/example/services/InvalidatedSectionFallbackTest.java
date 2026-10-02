package book.example.services;

import book.example.dto.DocumentationSection;
import book.example.dto.ChapterSectionOutput;
import book.example.dto.DocumentationPlan;
import book.example.dto.ProjectFacts;
import book.example.dto.RagSearchResult;
import book.example.Repository.JobRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InvalidatedSectionFallbackTest {
    private ExecutorService executor;

    @Test
    void marksFinalDraftAndPreservesItsTextWithoutEvidenceTokens() {
        String marked = SectionQualityValidator.markInvalidatedSection(
                "The model's final draft. [EVIDENCE: E-abcdef]");

        assertEquals("[Not validated] The model's final draft.", marked);
        assertTrue(SectionQualityValidator.isInvalidatedSection(marked));
        assertFalse(SectionQualityValidator.validate(marked, section(), null).valid());
    }

    @Test
    void createsVisibleMarkerWhenNoUsableDraftWasReturned() {
        String marked = SectionQualityValidator.markInvalidatedSection("  ");

        assertEquals(SectionQualityValidator.INVALIDATED_SECTION_MARKER, marked);
        assertFalse(SectionQualityValidator.validate(marked, section(), null).valid());
    }

    @Test
    void doesNotTreatOrdinaryContentAsInvalidated() {
        assertFalse(SectionQualityValidator.isInvalidatedSection("Ordinary report content."));
        assertFalse(SectionQualityValidator.validate("Ordinary report content.", section(), null).valid());
    }

    @Test
    void retainsLastDraftAfterInitialGenerationAndTwoRepairs() throws Exception {
        String response = "The model's final draft is still shown, although it is not valid JSON.";
        LlmWorkerPool workerPool = mock(LlmWorkerPool.class);
        when(workerPool.generateWithRetry(anyString(), anyMap()))
                .thenReturn(response, response, response);
        executor = Executors.newSingleThreadExecutor();
        ChapterGenerationService service = new ChapterGenerationService(
                mock(ChromaSearchService.class),
                workerPool,
                mock(DiagramDecisionService.class),
                mock(DiagramRenderingService.class),
                mock(UserAssetService.class),
                mock(JobRepository.class),
                new ObjectMapper(),
                executor);
        ReflectionTestUtils.setField(service, "allowInvalidatedSections", true);

        DocumentationSection section = section();
        section.setEvidenceMode("PROJECT");
        Map<String, RagSearchResult> evidence = Map.of(
                "E-abcdef", new RagSearchResult("Verified project source", "Source.java", "JAVA", 1, 2, 1));
        Method generateAndRepair = ChapterGenerationService.class.getDeclaredMethod(
                "generateAndRepairSections",
                String.class,
                ProjectFacts.class,
                DocumentationPlan.class,
                DocumentationSection.class,
                List.class,
                Map.class,
                String.class,
                Map.class,
                String.class);
        generateAndRepair.setAccessible(true);

        Object rawResult = generateAndRepair.invoke(
                service,
                "test-job",
                new ProjectFacts(),
                new DocumentationPlan(),
                chapter(),
                List.of(section),
                Map.of(),
                "",
                evidence,
                "initial");

        assertInstanceOf(Map.class, rawResult);
        Map<?, ?> result = (Map<?, ?>) rawResult;
        ChapterSectionOutput output = assertInstanceOf(ChapterSectionOutput.class, result.get("test"));
        assertEquals(
                "[Not validated] The model's final draft is still shown, although it is not valid JSON.",
                output.getContent());
        verify(workerPool, times(3)).generateWithRetry(anyString(), anyMap());
    }

    @AfterEach
    void stopExecutor() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    private DocumentationSection section() {
        DocumentationSection section = new DocumentationSection();
        section.setId("test");
        section.setTitle("Test");
        section.setContentPurpose("Test content.");
        return section;
    }

    private DocumentationSection chapter() {
        DocumentationSection chapter = new DocumentationSection();
        chapter.setId("chapter-1");
        chapter.setTitle("Chapter 1");
        return chapter;
    }
}
