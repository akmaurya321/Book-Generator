package book.example.services;

import book.example.dto.DocumentationPlan;
import book.example.dto.DocumentationSection;
import book.example.dto.GeneratedSectionCheckpoint;
import book.example.dto.GenerationCheckpoint;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GenerationPreviewOrdererTest {

    private final GenerationPreviewOrderer orderer = new GenerationPreviewOrderer();

    @Test
    void allSixChapterCompletionPermutationsRenderInDocumentOrder() {
        List<String> chapterIds = List.of("chapter-1", "chapter-2", "chapter-3");
        for (List<String> completionOrder : permutations(chapterIds)) {
            GenerationCheckpoint checkpoint = new GenerationCheckpoint();
            Map<String, List<GeneratedSectionCheckpoint>> chapters = new LinkedHashMap<>();
            for (String chapterId : completionOrder) {
                chapters.put(chapterId, List.of(section(chapterId, order(chapterId))));
            }
            checkpoint.setChapters(chapters);

            DocumentationPlan plan = plan(chapterIds);
            assertEquals(chapterIds, new ArrayList<>(orderer.order(plan, checkpoint).keySet()),
                    "Completion order must never leak into document preview order: " + completionOrder);
        }
    }

    @Test
    void sectionCompletionOrderInsideChapterIsAlsoNormalized() {
        DocumentationPlan plan = plan(List.of("chapter-1"));
        GenerationCheckpoint checkpoint = new GenerationCheckpoint();
        GeneratedSectionCheckpoint second = section("second", 2);
        GeneratedSectionCheckpoint first = section("first", 1);
        checkpoint.setChapters(Map.of("chapter-1", List.of(second, first)));

        List<GeneratedSectionCheckpoint> ordered = orderer.order(plan, checkpoint).get("chapter-1");
        assertEquals(List.of("first", "second"), ordered.stream().map(GeneratedSectionCheckpoint::getTitle).toList());
    }

    @Test
    void missingCompletedChapterIsOmittedWithoutReorderingAvailableChapters() {
        DocumentationPlan plan = plan(List.of("chapter-1", "chapter-2", "chapter-3"));
        GenerationCheckpoint checkpoint = new GenerationCheckpoint();
        checkpoint.setChapters(Map.of(
                "chapter-3", List.of(section("chapter-3", 30)),
                "chapter-1", List.of(section("chapter-1", 10))));

        assertEquals(List.of("chapter-1", "chapter-3"), new ArrayList<>(orderer.order(plan, checkpoint).keySet()));
    }

    private DocumentationPlan plan(List<String> chapterIds) {
        DocumentationPlan plan = new DocumentationPlan();
        List<DocumentationSection> sections = new ArrayList<>();
        int order = 10;
        for (String chapterId : chapterIds) {
            DocumentationSection chapter = new DocumentationSection();
            chapter.setId(chapterId);
            chapter.setChapterId(chapterId);
            chapter.setOrder(order);
            chapter.setTitle(chapterId);
            sections.add(chapter);
            order += 10;
        }
        plan.setSections(sections);
        return plan;
    }

    private GeneratedSectionCheckpoint section(String title, int order) {
        GeneratedSectionCheckpoint section = new GeneratedSectionCheckpoint();
        section.setTitle(title);
        section.setOrder(order);
        section.setContent(title);
        return section;
    }

    private int order(String chapterId) {
        return Integer.parseInt(chapterId.substring(chapterId.length() - 1)) * 10;
    }

    private <T> List<List<T>> permutations(List<T> input) {
        if (input.size() <= 1) return List.of(new ArrayList<>(input));
        List<List<T>> result = new ArrayList<>();
        for (int i = 0; i < input.size(); i++) {
            T head = input.get(i);
            List<T> rest = new ArrayList<>(input);
            rest.remove(i);
            for (List<T> tail : permutations(rest)) {
                List<T> permutation = new ArrayList<>();
                permutation.add(head);
                permutation.addAll(tail);
                result.add(permutation);
            }
        }
        return result;
    }
}
