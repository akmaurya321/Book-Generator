package book.example.services;

import book.example.dto.DocumentationSection;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChapterGenerationBatchingTest {

    @Test
    void splitsSelectedSectionsIntoSmallOrderedBatches() {
        List<DocumentationSection> sections = IntStream.range(0, 7)
                .mapToObj(index -> {
                    DocumentationSection section = new DocumentationSection();
                    section.setId("section-" + index);
                    return section;
                })
                .toList();

        List<List<DocumentationSection>> batches =
                ChapterGenerationService.sectionBatches(sections, 3);

        assertEquals(List.of(3, 3, 1), batches.stream().map(List::size).toList());
        assertEquals(
                sections.stream().map(DocumentationSection::getId).toList(),
                batches.stream()
                        .flatMap(List::stream)
                        .map(DocumentationSection::getId)
                        .toList());
    }

    @Test
    void rejectsInvalidBatchSize() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ChapterGenerationService.sectionBatches(List.of(new DocumentationSection()), 0));
    }
}
