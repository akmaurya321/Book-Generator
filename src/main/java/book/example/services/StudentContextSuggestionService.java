package book.example.services;

import book.example.dto.ProjectFacts;
import book.example.dto.StudentContext;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Produces editable context suggestions. Suggestions are deliberately framed as
 * drafts; they never become project facts automatically.
 */
@Service
public class StudentContextSuggestionService {
    private final LlmWorkerPool workerPool;

    public StudentContextSuggestionService(LlmWorkerPool workerPool) {
        this.workerPool = workerPool;
    }

    public StudentContext suggest(ProjectFacts facts) {
        if (facts == null) throw new IllegalArgumentException("Project facts are required.");
        String prompt = buildPrompt(facts);
        String response = workerPool.generateWithRetry(prompt);
        return parse(response);
    }

    private String buildPrompt(ProjectFacts facts) {
        return """
                You are preparing editable suggestions for a student's technical project report.
                These are DRAFT SUGGESTIONS, NOT verified implementation facts.
                Use the supplied project evidence for project-specific statements.
                Do not invent metrics, users, deployments, results, certifications, APIs, or technologies.
                Provide a useful, non-empty draft for every marker, even when the evidence is limited.
                Clearly label uncertain audiences and benefits as possibilities, not confirmed facts.
                If no additional note can be supported, say that no further evidence-based notes were identified.
                Future ideas may be reasonable proposals inferred from visible limitations, but must be phrased as possible future enhancements.
                Return exactly these markers, one value per marker:
                [MOTIVATION]
                [PROBLEM]
                [OBJECTIVES]
                [TARGET_USERS]
                [BENEFITS]
                [LIMITATIONS]
                [FUTURE_WORK]
                [NOTES]
                [END]
                OBJECTIVES must be 3-6 concise bullet-like lines separated by newline.

                PROJECT EVIDENCE:
                Name: %s
                Type: %s
                Description: %s
                Technologies: %s
                Frameworks: %s
                Modules: %s
                APIs: %s
                Database evidence: %s
                Test evidence: %s
                Security evidence: %s
                Files analyzed: %d
                """.formatted(
                safe(facts.getProjectName()), safe(facts.getProjectType()), safe(facts.getDescription()),
                safe(facts.getTechnologies()), safe(facts.getFrameworks()), safe(facts.getModules()),
                safe(facts.getApiFiles()), safe(facts.getDatabaseFiles()), safe(facts.getTestFiles()),
                safe(facts.getSecurityFiles()), facts.getAnalyzedFileCount());
    }

    private StudentContext parse(String raw) {
        StudentContext result = new StudentContext();
        result.setSource("AI_SUGGESTION");
        result.setMotivation(value(raw, "MOTIVATION"));
        result.setProblemStatement(value(raw, "PROBLEM"));
        result.setObjectives(lines(value(raw, "OBJECTIVES")));
        result.setTargetUsers(value(raw, "TARGET_USERS"));
        result.setExpectedBenefits(value(raw, "BENEFITS"));
        result.setLimitations(value(raw, "LIMITATIONS"));
        result.setFutureIdeas(value(raw, "FUTURE_WORK"));
        result.setAdditionalNotes(value(raw, "NOTES"));
        return result;
    }

    private String value(String raw, String marker) {
        if (raw == null) return "";
        String start = "[" + marker + "]";
        int a = raw.indexOf(start);
        if (a < 0) return "";
        a += start.length();
        int b = raw.indexOf("[", a);
        return raw.substring(a, b < 0 ? raw.length() : b).trim();
    }

    private List<String> lines(String value) {
        List<String> result = new ArrayList<>();
        for (String line : value.split("\\R")) {
            String cleaned = line.trim().replaceFirst("^[-*•]\\s*", "");
            if (!cleaned.isBlank()) result.add(cleaned);
        }
        return result;
    }

    private String safe(Object value) { return value == null ? "" : String.valueOf(value); }
}
