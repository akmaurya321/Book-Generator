package book.example.services;

import book.example.dto.DocumentationPlan;
import book.example.dto.DocumentationSection;
import book.example.dto.GeneratedSectionCheckpoint;
import book.example.dto.GenerationCheckpoint;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts a completion-order checkpoint into deterministic document order.
 * LLM workers are allowed to finish chapters in any order; the document plan
 * remains the single source of truth for what the user sees in preview.
 */
@Service
public class GenerationPreviewOrderer {

    public Map<String, List<GeneratedSectionCheckpoint>> order(
            DocumentationPlan plan,
            GenerationCheckpoint checkpoint) {
        Map<String, List<GeneratedSectionCheckpoint>> ordered = new LinkedHashMap<>();
        if (checkpoint == null || checkpoint.getChapters() == null) return ordered;

        if (plan != null && plan.getSections() != null) {
            plan.getSections().stream()
                    .filter(section -> section != null && section.getChapterId() != null)
                    .sorted(Comparator.comparingInt(DocumentationSection::getOrder))
                    .map(DocumentationSection::getChapterId)
                    .distinct()
                    .forEach(chapterId -> addOrderedChapter(ordered, checkpoint, chapterId));
        }

        // Backward-compatible fallback for old jobs whose persisted plan is unavailable.
        if (ordered.isEmpty()) {
            checkpoint.getChapters().entrySet().stream()
                    .sorted(Comparator.comparingInt(entry -> entry.getValue().stream()
                            .mapToInt(GeneratedSectionCheckpoint::getOrder)
                            .min().orElse(Integer.MAX_VALUE)))
                    .forEach(entry -> ordered.put(entry.getKey(), sortSections(entry.getValue())));
        }
        return ordered;
    }

    private void addOrderedChapter(
            Map<String, List<GeneratedSectionCheckpoint>> ordered,
            GenerationCheckpoint checkpoint,
            String chapterId) {
        List<GeneratedSectionCheckpoint> generated = checkpoint.getChapters().get(chapterId);
        if (generated != null && !generated.isEmpty()) {
            ordered.put(chapterId, sortSections(generated));
        }
    }

    private List<GeneratedSectionCheckpoint> sortSections(List<GeneratedSectionCheckpoint> sections) {
        return sections == null ? List.of() : sections.stream()
                .sorted(Comparator.comparingInt(GeneratedSectionCheckpoint::getOrder))
                .toList();
    }
}
