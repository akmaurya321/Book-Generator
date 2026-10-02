package book.example.services;

import book.example.dto.ProjectFacts;
import book.example.dto.StudentContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StudentContextSuggestionServiceTest {
    @Test
    void createsDraftSuggestionsForEveryAdditionalInformationField() {
        LlmWorkerPool workerPool = mock(LlmWorkerPool.class);
        String response = """
                [MOTIVATION] motivation draft
                [PROBLEM] problem draft
                [OBJECTIVES]
                first objective
                second objective
                [TARGET_USERS] target users draft
                [BENEFITS] benefits draft
                [LIMITATIONS] limitations draft
                [FUTURE_WORK] future draft
                [NOTES] notes draft
                [END]
                """;
        when(workerPool.generateWithRetry(contains("[TARGET_USERS]"))).thenReturn(response);

        StudentContext suggestions = new StudentContextSuggestionService(workerPool).suggest(new ProjectFacts());

        assertEquals("AI_SUGGESTION", suggestions.getSource());
        assertEquals("motivation draft", suggestions.getMotivation());
        assertEquals("problem draft", suggestions.getProblemStatement());
        assertEquals(2, suggestions.getObjectives().size());
        assertEquals("target users draft", suggestions.getTargetUsers());
        assertEquals("benefits draft", suggestions.getExpectedBenefits());
        assertEquals("limitations draft", suggestions.getLimitations());
        assertEquals("future draft", suggestions.getFutureIdeas());
        assertEquals("notes draft", suggestions.getAdditionalNotes());
        assertFalse(suggestions.getAdditionalNotes().isBlank());
        verify(workerPool).generateWithRetry(contains("[NOTES]"));
    }
}
