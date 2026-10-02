package book.example.services;

import book.example.dto.DocumentationSection;
import book.example.dto.ProjectFacts;

import java.util.Locale;
import java.util.regex.Pattern;

public final class SectionQualityValidator {

    public static final String INVALIDATED_SECTION_MARKER = "[Not validated]";

    private static final int MAX_CONTENT_CHARACTERS = 12_000;
    private static final Pattern REPEATED_WORD_PATTERN =
            Pattern.compile("(\\b[a-zA-Z]+\\b)(\\s+\\1){2,}", Pattern.CASE_INSENSITIVE);
        private static final Pattern FAKE_CAPTION_PATTERN =
            Pattern.compile("(?im)^\\s*(figure|table)\\s*:");

    private SectionQualityValidator() {
    }

    public static boolean isInvalidatedSection(String content) {
        return content != null && content.stripLeading().startsWith(INVALIDATED_SECTION_MARKER);
    }

    public static String markInvalidatedSection(String content) {
        String safeContent = content == null
                ? ""
                : content.replaceAll("\\[EVIDENCE:[^\\]]*\\]", "").trim();
        return safeContent.isEmpty()
                ? INVALIDATED_SECTION_MARKER
                : INVALIDATED_SECTION_MARKER + " " + safeContent;
    }

    public static ValidationResult validate(
            String content,
            DocumentationSection section,
            ProjectFacts projectFacts) {

        if (content == null || content.isBlank()) {
            return new ValidationResult(false, false, "Section content is empty");
        }

        String normalized = content.trim();
        String lower = normalized.toLowerCase(Locale.ROOT);

        if (normalized.length() > MAX_CONTENT_CHARACTERS) {
            return new ValidationResult(false, true, "Section exceeds the maximum content length");
        }

        if (normalized.length() < 80) {
            return new ValidationResult(false, true, "Section is too short to be useful");
        }

        if (lower.contains("```") || lower.startsWith("{") || lower.startsWith("[")) {
            return new ValidationResult(false, true, "Section still contains fenced or structured output markers");
        }

        if (FAKE_CAPTION_PATTERN.matcher(normalized).find()) {
            return new ValidationResult(false, true, "Section contains a caption without a structured figure or table");
        }

        if (lower.contains("here is") || lower.contains("i can help") ||
                lower.contains("analysis:") || lower.contains("prompt") && lower.contains("instructions")) {
            return new ValidationResult(false, true, "Section contains meta commentary instead of final prose");
        }

        if (lower.contains("placeholder") || lower.contains("lorem ipsum") || lower.contains("not identified") && lower.contains("not identified") && normalized.length() < 150) {
            return new ValidationResult(false, true, "Section reads like a placeholder instead of a real summary");
        }

        if (REPEATED_WORD_PATTERN.matcher(normalized).find()) {
            return new ValidationResult(false, true, "Section repeats words and looks malformed");
        }

        if (containsRepeatedParagraph(normalized)) {
            return new ValidationResult(false, true, "Section repeats a paragraph");
        }

        if (lower.contains("this section") && lower.contains("in this document")) {
            return new ValidationResult(false, true, "Section is meta-descriptive rather than evidence-based content");
        }

        if (projectFacts != null && projectFacts.getProjectName() != null && !projectFacts.getProjectName().isBlank()) {
            String projectName = projectFacts.getProjectName().toLowerCase(Locale.ROOT);
            if (normalized.length() > 120 && !lower.contains(projectName) && (lower.contains("application") || lower.contains("system") || lower.contains("service"))) {
                // allow section prose that does not explicitly repeat the project name; only reject if it is clearly generic filler.
            }
        }

        return new ValidationResult(true, false, "Valid section content");
    }

    private static boolean containsRepeatedParagraph(String content) {
        String[] paragraphs = content.split("\\R\\s*\\R");
        java.util.Set<String> seen = new java.util.HashSet<>();

        for (String paragraph : paragraphs) {
            String normalized = paragraph.toLowerCase(Locale.ROOT)
                    .replaceAll("\\s+", " ")
                    .trim();
            if (normalized.length() >= 80 && !seen.add(normalized)) {
                return true;
            }
        }

        return false;
    }

    public record ValidationResult(boolean valid, boolean repairable, String reason) {
    }
}
