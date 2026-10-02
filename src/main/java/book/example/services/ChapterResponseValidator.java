package book.example.services;

import book.example.dto.ChapterLlmResponse;
import book.example.dto.ChapterSectionOutput;
import book.example.dto.DocumentationSection;
import book.example.dto.RagSearchResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ChapterResponseValidator {

    private ChapterResponseValidator() {
    }

    public record ValidationResult(
            Map<String, ChapterSectionOutput> validSections,
            Map<String, String> failures
    ) {
    }

    public static Map<String, ChapterSectionOutput> extractDraftSections(
            String json,
            List<DocumentationSection> sections,
            ObjectMapper objectMapper) throws tools.jackson.core.JacksonException {

        Map<String, ChapterSectionOutput> drafts = new LinkedHashMap<>();
        if (json == null || json.isBlank() || sections == null || sections.isEmpty()) {
            return drafts;
        }
        if (objectMapper == null) {
            throw new IllegalArgumentException("ObjectMapper is required to extract generated drafts.");
        }

        Set<String> expected = new HashSet<>();
        for (DocumentationSection section : sections) {
            if (section != null && section.getId() != null && !section.getId().isBlank()) {
                expected.add(section.getId());
            }
        }

        JsonNode root = objectMapper.readTree(json);
        JsonNode sectionNodes = root == null ? null : root.get("sections");
        if (sectionNodes == null || !sectionNodes.isArray()) {
            return drafts;
        }

        for (JsonNode sectionNode : sectionNodes) {
            if (sectionNode == null || !sectionNode.isObject()) {
                continue;
            }

            JsonNode idNode = sectionNode.get("sectionId");
            JsonNode contentNode = sectionNode.get("content");
            if (idNode == null || !idNode.isString()
                    || contentNode == null || !contentNode.isString()
                    || contentNode.asText().isBlank()) {
                continue;
            }

            String id = idNode.asText();
            if (!expected.contains(id)) {
                continue;
            }

            ChapterSectionOutput draft = new ChapterSectionOutput();
            draft.setSectionId(id);
            draft.setContent(contentNode.asText());

            JsonNode evidenceIdsNode = sectionNode.get("evidenceIds");
            if (evidenceIdsNode != null && evidenceIdsNode.isArray()) {
                List<String> evidenceIds = new java.util.ArrayList<>();
                for (JsonNode evidenceId : evidenceIdsNode) {
                    if (evidenceId != null && evidenceId.isString()) {
                        evidenceIds.add(evidenceId.asText());
                    }
                }
                draft.setEvidenceIds(evidenceIds);
            }

            drafts.put(id, draft);
        }

        return drafts;
    }

    public static ValidationResult validate(
            String json,
            String expectedChapterId,
            List<DocumentationSection> sections,
            Map<String, RagSearchResult> evidence,
            ObjectMapper objectMapper) {

        Map<String, ChapterSectionOutput> valid =
                new LinkedHashMap<>();

        Map<String, String> failures =
                new LinkedHashMap<>();

        // =========================================================
        // BASIC INPUT VALIDATION
        // =========================================================

        if (json == null || json.isBlank()) {
            return new ValidationResult(
                    valid,
                    Map.of(
                            "__STRUCTURE__",
                            "LLM response is empty."
                    )
            );
        }

        if (objectMapper == null) {
            return new ValidationResult(
                    valid,
                    Map.of(
                            "__STRUCTURE__",
                            "ObjectMapper is not available."
                    )
            );
        }

        if (sections == null || sections.isEmpty()) {
            return new ValidationResult(
                    valid,
                    Map.of(
                            "__STRUCTURE__",
                            "Expected chapter contains no sections."
                    )
            );
        }

        /*
         * IMPORTANT:
         * Keep this variable final because it is used inside
         * the evidenceIds stream lambda below.
         */
        final Map<String, RagSearchResult> evidenceMap =
                evidence == null ? Map.of() : evidence;

        // =========================================================
        // PARSE JSON
        // =========================================================

        ChapterLlmResponse response;

        try {

            JsonNode root =
                    objectMapper.readTree(json);

            if (root == null || !root.isObject()) {
                return new ValidationResult(
                        valid,
                        Map.of(
                                "__STRUCTURE__",
                                "Response root must be a JSON object."
                        )
                );
            }

            // =====================================================
            // ROOT-LEVEL FIELD VALIDATION
            // Jackson 3 propertyNames() returns Collection<String>
            // =====================================================

            Set<String> allowedRoot =
                    Set.of(
                            "chapterId",
                            "sections"
                    );

            for (String field : root.propertyNames()) {

                if (!allowedRoot.contains(field)) {

                    return new ValidationResult(
                            valid,
                            Map.of(
                                    "__STRUCTURE__",
                                    "Unknown top-level field: "
                                            + field
                            )
                    );
                }
            }

            // =====================================================
            // CHAPTER ID JSON VALIDATION
            // =====================================================

            JsonNode chapterIdNode =
                    root.get("chapterId");

            if (chapterIdNode == null
                    || chapterIdNode.isNull()
                    || !chapterIdNode.isString()
                    || chapterIdNode.asText().isBlank()) {

                return new ValidationResult(
                        valid,
                        Map.of(
                                "__STRUCTURE__",
                                "chapterId is missing or invalid."
                        )
                );
            }

            // =====================================================
            // SECTIONS ARRAY VALIDATION
            // =====================================================

            JsonNode sectionsNode =
                    root.get("sections");

            if (sectionsNode == null
                    || !sectionsNode.isArray()) {

                return new ValidationResult(
                        valid,
                        Map.of(
                                "__STRUCTURE__",
                                "sections must be a JSON array."
                        )
                );
            }

            // =====================================================
            // SECTION JSON FIELD VALIDATION
            // =====================================================

            Set<String> allowedSectionFields =
                    Set.of(
                            "sectionId",
                            "content",
                            "evidenceIds",
                            "assets"
                    );

            for (JsonNode sectionNode :
                    sectionsNode) {

                if (sectionNode == null
                        || !sectionNode.isObject()) {

                    return new ValidationResult(
                            valid,
                            Map.of(
                                    "__STRUCTURE__",
                                    "Every section must be a JSON object."
                            )
                    );
                }

                /*
                 * Validate the fields of THIS section.
                 *
                 * Do NOT use root.propertyNames() here.
                 */
                for (String field :
                        sectionNode.propertyNames()) {

                    if (!allowedSectionFields.contains(field)) {

                        return new ValidationResult(
                                valid,
                                Map.of(
                                        "__STRUCTURE__",
                                        "Unknown section field: "
                                                + field
                                )
                        );
                    }
                }

                // =================================================
                // SECTION ID JSON VALIDATION
                // =================================================

                JsonNode sectionIdNode =
                        sectionNode.get("sectionId");

                if (sectionIdNode == null
                        || sectionIdNode.isNull()
                        || !sectionIdNode.isString()
                        || sectionIdNode.asText().isBlank()) {

                    return new ValidationResult(
                            valid,
                            Map.of(
                                    "__STRUCTURE__",
                                    "A section has no valid sectionId."
                            )
                    );
                }

                // =================================================
                // CONTENT JSON VALIDATION
                // =================================================

                JsonNode contentNode =
                        sectionNode.get("content");

                if (contentNode == null
                        || contentNode.isNull()
                        || !contentNode.isString()
                        || contentNode.asText().isBlank()) {

                    return new ValidationResult(
                            valid,
                            Map.of(
                                    "__STRUCTURE__",
                                    "Section content must be a non-empty string."
                            )
                    );
                }

                // =================================================
                // EVIDENCE IDS JSON VALIDATION
                // =================================================

                JsonNode evidenceIdsNode =
                        sectionNode.get("evidenceIds");

                if (evidenceIdsNode != null
                        && !evidenceIdsNode.isNull()
                        && !evidenceIdsNode.isArray()) {

                    return new ValidationResult(
                            valid,
                            Map.of(
                                    "__STRUCTURE__",
                                    "evidenceIds must be an array."
                            )
                    );
                }

                // =================================================
                // ASSETS JSON VALIDATION
                // =================================================

                JsonNode assetsNode =
                        sectionNode.get("assets");

                if (assetsNode != null
                        && !assetsNode.isNull()
                        && !assetsNode.isArray()) {

                    return new ValidationResult(
                            valid,
                            Map.of(
                                    "__STRUCTURE__",
                                    "assets must be an array."
                            )
                    );
                }
            }

            // =====================================================
            // DESERIALIZE INTO DTO
            // =====================================================

            response =
                    objectMapper.readValue(
                            json,
                            ChapterLlmResponse.class
                    );

        } catch (Exception e) {

            String message =
                    e.getMessage() == null
                            ? "Unknown JSON parsing error."
                            : e.getMessage();

            return new ValidationResult(
                    valid,
                    Map.of(
                            "__STRUCTURE__",
                            "Response is not valid chapter JSON: "
                                    + message
                    )
            );
        }

        // =========================================================
        // NULL RESPONSE
        // =========================================================

        if (response == null) {

            return new ValidationResult(
                    valid,
                    Map.of(
                            "__STRUCTURE__",
                            "Response is null."
                    )
            );
        }

        // =========================================================
        // CHAPTER ID VALIDATION
        // =========================================================

        if (expectedChapterId != null
                && !expectedChapterId.isBlank()) {

            String actualChapterId =
                    response.getChapterId();

            if (!expectedChapterId.equals(actualChapterId)) {

                failures.put(
                        "__STRUCTURE__",
                        "Wrong or missing chapterId: "
                                + actualChapterId
                );
            }
        }

        // =========================================================
        // BUILD EXPECTED SECTION ID SET
        // =========================================================

        Set<String> expected =
                new HashSet<>();

        for (DocumentationSection section :
                sections) {

            if (section == null) {
                continue;
            }

            String id =
                    section.getId();

            if (id != null
                    && !id.isBlank()) {

                expected.add(id);
            }
        }

        // =========================================================
        // RESPONSE SECTIONS
        // =========================================================

        Set<String> seen =
                new HashSet<>();

        if (response.getSections() == null) {

            failures.put(
                    "__STRUCTURE__",
                    "sections array is missing."
            );

            return new ValidationResult(
                    valid,
                    failures
            );
        }

        // =========================================================
        // VALIDATE EACH GENERATED SECTION
        // =========================================================

        for (ChapterSectionOutput output :
                response.getSections()) {

            // -----------------------------------------------------
            // NULL SECTION
            // -----------------------------------------------------

            if (output == null) {

                failures.put(
                        "__STRUCTURE__",
                        "A section output is null."
                );

                continue;
            }

            String id =
                    output.getSectionId();

            // -----------------------------------------------------
            // MISSING SECTION ID
            // -----------------------------------------------------

            if (id == null || id.isBlank()) {

                failures.put(
                        "__STRUCTURE__",
                        "A section has no sectionId."
                );

                continue;
            }

            // -----------------------------------------------------
            // UNKNOWN SECTION ID
            // -----------------------------------------------------

            if (!expected.contains(id)) {

                failures.put(
                        id,
                        "Unknown sectionId: "
                                + id
                );

                continue;
            }

            // -----------------------------------------------------
            // DUPLICATE SECTION ID
            // -----------------------------------------------------

            if (!seen.add(id)) {

                /*
                 * Duplicate section is considered invalid.
                 *
                 * Do not keep the first copy.
                 * The section must go through targeted repair.
                 */
                valid.remove(id);

                failures.put(
                        id,
                        "Duplicate sectionId: "
                                + id
                );

                continue;
            }

            // -----------------------------------------------------
            // EMPTY CONTENT
            // -----------------------------------------------------

            if (output.getContent() == null
                    || output.getContent().isBlank()) {

                failures.put(
                        id,
                        "Section content is empty."
                );

                continue;
            }

            // -----------------------------------------------------
            // EVIDENCE VALIDATION
            // -----------------------------------------------------

            List<String> evidenceIds =
                    output.getEvidenceIds() == null
                            ? List.of()
                            : output.getEvidenceIds();

            boolean badEvidence =
                    evidenceIds.stream()
                            .anyMatch(
                                    evidenceId ->
                                            evidenceId == null
                                                    || !evidenceMap.containsKey(
                                                    evidenceId
                                            )
                            );

            if (badEvidence) {

                failures.put(
                        id,
                        "Section contains an unknown evidenceId."
                );

                continue;
            }

            // -----------------------------------------------------
            // SECTION ACCEPTED
            // -----------------------------------------------------

            valid.put(
                    id,
                    output
            );
        }

        // =========================================================
        // CHECK REQUIRED / MISSING SECTIONS
        // =========================================================

        for (String id :
                expected) {

            if (!seen.contains(id)) {

                failures.putIfAbsent(
                        id,
                        "Required section is missing."
                );
            }
        }

        // =========================================================
        // FINAL RESULT
        // =========================================================

        return new ValidationResult(
                valid,
                failures
        );
    }
}