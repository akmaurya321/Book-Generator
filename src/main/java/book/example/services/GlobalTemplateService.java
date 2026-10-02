package book.example.services;

import book.example.dto.DocumentationPlan;
import book.example.dto.DocumentationSection;
import book.example.dto.GlobalTemplateDefinition;
import book.example.dto.ProjectFacts;
import book.example.dto.StudentProjectDetails;
import book.example.dto.TemplateSectionDefinition;
import tools.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import book.example.dto.DocumentFormatDefinition;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class GlobalTemplateService {

    public static final String TEMPLATE_ID = "global_student_project_v1";

    private final GlobalTemplateDefinition template;

    public GlobalTemplateService(ObjectMapper objectMapper) {
        try {
            this.template = objectMapper.readValue(
                    new ClassPathResource("templates/global-student-project-v1.json").getInputStream(),
                    GlobalTemplateDefinition.class
            );
            validateTemplate();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load the global student project template.", exception);
        }
    }


    private void validateTemplate() {
        if (template.getSections() == null || template.getSections().isEmpty()) {
            throw new IllegalStateException("Global documentation template contains no sections.");
        }
        Set<String> ids = new HashSet<>();
        for (TemplateSectionDefinition definition : template.getSections()) {
            if (definition == null || definition.getId() == null || definition.getId().isBlank()) {
                throw new IllegalStateException("Global documentation template contains a section without an id.");
            }
            if (!ids.add(definition.getId())) {
                throw new IllegalStateException("Duplicate global documentation section id: " + definition.getId());
            }
            String evidenceMode = definition.getEvidenceMode();
            if (evidenceMode == null || !("PROJECT".equalsIgnoreCase(evidenceMode) || "HYBRID".equalsIgnoreCase(evidenceMode) || "RESEARCH".equalsIgnoreCase(evidenceMode))) {
                throw new IllegalStateException("Invalid evidence mode for section: " + definition.getId());
            }
        }
        for (TemplateSectionDefinition definition : template.getSections()) {
            if (definition.getParentId() != null && !ids.contains(definition.getParentId())) {
                throw new IllegalStateException("Unknown parent section " + definition.getParentId() + " for " + definition.getId());
            }
            if (definition.getDependsOn() != null) {
                for (String dependency : definition.getDependsOn()) {
                    if (!ids.contains(dependency)) {
                        throw new IllegalStateException("Unknown chapter dependency " + dependency + " for " + definition.getId());
                    }
                    TemplateSectionDefinition dependencyDefinition = template.getSections().stream()
                            .filter(candidate -> dependency.equals(candidate.getId()))
                            .findFirst().orElse(null);
                    if (dependencyDefinition != null && !dependencyDefinition.isChapter()) {
                        throw new IllegalStateException("Chapter dependency " + dependency + " is not a chapter: " + definition.getId());
                    }
                }
            }
        }
        validateParentGraph(ids);
        if (template.getFormat() == null) {
            throw new IllegalStateException("Global documentation template format is missing.");
        }
        Map<String, TemplateSectionDefinition> chapters = new LinkedHashMap<>();
        for (TemplateSectionDefinition definition : template.getSections()) {
            if (definition.isChapter()) chapters.put(definition.getId(), definition);
        }
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        for (String chapterId : chapters.keySet()) {
            validateDependencyGraph(chapterId, chapters, visiting, visited);
        }
    }

    private void validateParentGraph(Set<String> ids) {
        for (String id : ids) {
            Set<String> seen = new HashSet<>();
            String current = id;
            while (current != null) {
                if (!seen.add(current)) {
                    throw new IllegalStateException("Cycle detected in section parent hierarchy at: " + current);
                }
                final String currentId = current;
                TemplateSectionDefinition definition = template.getSections().stream()
                        .filter(candidate -> currentId.equals(candidate.getId()))
                        .findFirst().orElse(null);
                current = definition == null ? null : definition.getParentId();
            }
        }
    }

    private void validateDependencyGraph(String chapterId,
                                         Map<String, TemplateSectionDefinition> chapters,
                                         Set<String> visiting,
                                         Set<String> visited) {
        if (visited.contains(chapterId)) return;
        if (!visiting.add(chapterId)) {
            throw new IllegalStateException("Cycle detected in global chapter dependencies at: " + chapterId);
        }
        TemplateSectionDefinition definition = chapters.get(chapterId);
        if (definition != null && definition.getDependsOn() != null) {
            for (String dependency : definition.getDependsOn()) {
                if (chapters.containsKey(dependency)) {
                    validateDependencyGraph(dependency, chapters, visiting, visited);
                }
            }
        }
        visiting.remove(chapterId);
        visited.add(chapterId);
    }

    public GlobalTemplateDefinition getTemplate() {
        return template;
    }


    public boolean isSupportedTemplateId(String templateId) {
        return switch (templateId == null ? "" : templateId) {
            case TEMPLATE_ID, "academic_college_v2", "university_formal_v2", "technical_report_v2" -> true;
            default -> false;
        };
    }

    /** Applies the selected visual/document format variant while keeping the proven V1 section contract stable. */
    public void applyTemplateVariant(DocumentationPlan plan, String templateId) {
        if (plan == null) return;
        DocumentFormatDefinition format = plan.getFormat() == null ? new DocumentFormatDefinition() : plan.getFormat();
        switch (templateId == null ? TEMPLATE_ID : templateId) {
            case "academic_college_v2" -> {
                format.setDefaultFont("Times New Roman"); format.setDefaultFontSize(12); format.setLineSpacing(1.5);
                format.setTitleFontSize(20); format.setHeading1FontSize(16); format.setHeading2FontSize(14);
            }
            case "university_formal_v2" -> {
                format.setDefaultFont("Georgia"); format.setDefaultFontSize(12); format.setLineSpacing(1.5);
                format.setTitleFontSize(21); format.setHeading1FontSize(16); format.setHeading2FontSize(14);
                format.setMarginTopTwips(1512); format.setMarginBottomTwips(1512);
            }
            case "technical_report_v2" -> {
                format.setDefaultFont("Arial"); format.setDefaultFontSize(11); format.setLineSpacing(1.15);
                format.setTitleFontSize(19); format.setHeading1FontSize(15); format.setHeading2FontSize(13);
                format.setMarginLeftTwips(1296); format.setMarginRightTwips(1296);
            }
            default -> {
                format.setDefaultFont("Times New Roman"); format.setDefaultFontSize(12); format.setLineSpacing(1.5);
            }
        }
        plan.setFormat(format);
    }

    public List<TemplateSectionDefinition> definitions() {
        return template.getSections() == null ? List.of() : template.getSections();
    }

    public DocumentationPlan buildPlan(
            ProjectFacts facts,
            List<String> selectedIds,
            List<String> selectedDiagramIds,
            List<book.example.dto.SectionConfiguration> configurations,
            StudentProjectDetails studentDetails,
            Map<String, String> additionalInformation) {

        if (facts == null) {
            throw new IllegalArgumentException("Project facts are required.");
        }
        if (selectedIds == null || selectedIds.isEmpty()) {
            throw new IllegalArgumentException("Select at least one report section.");
        }
        if (selectedIds.size() > 100) {
            throw new IllegalArgumentException("At most 100 report sections may be selected.");
        }
        if (selectedDiagramIds != null && selectedDiagramIds.size() > 100) {
            throw new IllegalArgumentException("At most 100 diagram selections may be supplied.");
        }

        Map<String, TemplateSectionDefinition> byId = new LinkedHashMap<>();
        for (TemplateSectionDefinition definition : definitions()) {
            byId.put(definition.getId(), definition);
        }

        Set<String> selected = new HashSet<>();
        for (String selectedId : selectedIds) {
            if (selectedId == null || selectedId.isBlank()) {
                throw new IllegalArgumentException("Selected report section ids cannot be blank.");
            }
            if (!byId.containsKey(selectedId)) {
                throw new IllegalArgumentException("Unknown report section: " + selectedId);
            }
            selected.add(selectedId);
        }

        if (selected.isEmpty()) {
            throw new IllegalArgumentException("The selected report sections are not valid.");
        }

        Set<String> disabledByConfiguration = new HashSet<>();
        if (configurations != null) {
            configurations.stream()
                    .filter(java.util.Objects::nonNull)
                    .filter(configuration -> !configuration.isEnabled())
                    .map(book.example.dto.SectionConfiguration::getSectionId)
                    .filter(java.util.Objects::nonNull)
                    .forEach(disabledByConfiguration::add);
        }
        selected.removeAll(disabledByConfiguration);
        if (selected.isEmpty()) {
            throw new IllegalArgumentException("All selected report sections are disabled by the final configuration.");
        }

        // A selected child requires every selected ancestor. Never silently remove
        // user choices: an invalid hierarchy must be reported to the caller.
        for (String id : selected) {
            String parentId = byId.get(id).getParentId();
            while (parentId != null) {
                if (!selected.contains(parentId)) {
                    throw new IllegalArgumentException("Section " + id + " requires its parent section to be enabled: " + parentId);
                }
                parentId = byId.get(parentId).getParentId();
            }
        }

        DocumentationPlan plan = new DocumentationPlan();
        plan.setTemplateId(TEMPLATE_ID);
        plan.setProjectName(effectiveProjectName(facts, studentDetails));
        plan.setDocumentTitle(plan.getProjectName());
        plan.setStudentDetails(studentDetails == null ? new StudentProjectDetails() : studentDetails);
        plan.setAdditionalInformation(
                additionalInformation == null
                        ? new LinkedHashMap<>()
                        : new LinkedHashMap<>(additionalInformation)
        );
        plan.setSelectedDiagrams(normalizeSelectedDiagrams(selectedDiagramIds, byId));
        plan.setSectionConfigurations(configurations == null ? List.of() : configurations);
        plan.setFormat(template.getFormat());

        Map<String, List<String>> chapterDependencies = new LinkedHashMap<>();
        for (TemplateSectionDefinition definition : definitions()) {
            if (!definition.isChapter() || !selected.contains(definition.getId())) {
                continue;
            }
            List<String> dependencies = definition.getDependsOn() == null
                    ? List.of()
                    : definition.getDependsOn().stream()
                    .filter(selected::contains)
                    .distinct()
                    .toList();
            chapterDependencies.put(definition.getId(), dependencies);
        }
        plan.setChapterDependencies(chapterDependencies);
        Map<String, book.example.dto.SectionConfiguration> configurationById = new LinkedHashMap<>();
        if (configurations != null) {
            if (configurations.size() > 100) {
                throw new IllegalArgumentException("At most 100 section configurations may be supplied.");
            }
            for (book.example.dto.SectionConfiguration configuration : configurations) {
                if (configuration == null || configuration.getSectionId() == null) continue;
                TemplateSectionDefinition target = byId.get(configuration.getSectionId());
                if (target == null) throw new IllegalArgumentException("Unknown section configuration: " + configuration.getSectionId());
                if (!selected.contains(configuration.getSectionId())) {
                    throw new IllegalArgumentException("Section configuration was supplied for an unselected section: " + configuration.getSectionId());
                }
                if (configuration.isDiagramEnabled() && target.getDiagramType() == null) {
                    throw new IllegalArgumentException("Diagrams are not supported for section: " + target.getId());
                }
                if (!configuration.isImageEnabled() && configuration.getImageIds() != null && !configuration.getImageIds().isEmpty()) {
                    throw new IllegalArgumentException("Image IDs were supplied while images are disabled for section: " + target.getId());
                }
                if (configurationById.put(configuration.getSectionId(), configuration) != null) {
                    throw new IllegalArgumentException("Duplicate section configuration: " + configuration.getSectionId());
                }
            }
        }

        int order = 1;
        for (TemplateSectionDefinition definition : definitions().stream()
                .filter(item -> selected.contains(item.getId()))
                .sorted(Comparator.comparingInt(TemplateSectionDefinition::getOrder))
                .toList()) {

            DocumentationSection section = new DocumentationSection();
            section.setId(definition.getId());
            section.setOrder(order++);
            section.setTitle(definition.getTitle());
            section.setLevel(definition.getLevel());
            section.setParentId(definition.getParentId());
            section.setChapterId(resolveChapterId(definition, byId));
            section.setSourceHeading(definition.getTitle());
            section.setElementType(definition.getElementType());
            section.setGenerationMode(resolveGenerationMode(definition));
            section.setEvidenceMode(resolveEvidenceMode(definition));
            section.setContentPurpose(definition.getDescription());
            section.setRequired(!definition.isOptional() && definition.isDefaultEnabled());
            section.setOptional(definition.isOptional());
            book.example.dto.SectionConfiguration configuration = configurationById.get(definition.getId());
            boolean contentEnabled = configuration == null || configuration.isContentEnabled();
            boolean imageEnabled = configuration != null && configuration.isImageEnabled();
            boolean diagramEnabled = configuration != null
                    ? configuration.isDiagramEnabled()
                    : plan.getSelectedDiagrams().contains(definition.getId());
            section.setContentEnabled(contentEnabled);
            section.setImageEnabled(imageEnabled);
            section.setDiagramEnabled(diagramEnabled);
            section.setImageIds(configuration == null ? List.of() : configuration.getImageIds());
            section.setRequiresDiagram(diagramEnabled);
            section.setDiagramEligible(
                    definition.getDiagramType() != null && diagramEnabled
            );
            section.setTableEligible(
                    "TECHNOLOGY".equals(definition.getElementType())
                            || "IMPLEMENTATION".equals(definition.getElementType())
                            || "REQUIREMENTS".equals(definition.getElementType())
                            || "TESTING".equals(definition.getElementType())
            );
            section.setTargetContentSize(
                    definition.isChapter() ? "MODERATE" : "PROPORTIONAL_TO_EVIDENCE"
            );
            section.setRequiresAdditionalInformation(definition.isRequiresAdditionalInformation());
            plan.getSections().add(section);
        }

        return plan;
    }

    private String resolveEvidenceMode(TemplateSectionDefinition definition) {
        String configured = definition.getEvidenceMode();
        if (configured != null && !configured.isBlank() && !"PROJECT".equalsIgnoreCase(configured)) {
            return configured.toUpperCase(java.util.Locale.ROOT);
        }
        String id = definition.getId() == null ? "" : definition.getId().toLowerCase(java.util.Locale.ROOT);
        String type = definition.getElementType() == null ? "" : definition.getElementType().toUpperCase(java.util.Locale.ROOT);
        if (id.contains("literature") || id.contains("related_work") || id.contains("existing_system") || id.contains("comparison") || "BACKGROUND".equals(type)) return "RESEARCH";
        if (definition.isChapter() && (id.contains("introduction") || id.contains("conclusion") || id.contains("future") || id.contains("discussion"))) return "HYBRID";
        return "PROJECT";
    }

    private String resolveGenerationMode(TemplateSectionDefinition definition) {
        if ("CERTIFICATE".equals(definition.getElementType())
                || "DECLARATION".equals(definition.getElementType())
                || "ACKNOWLEDGEMENT".equals(definition.getElementType())
                || "APPROVAL".equals(definition.getElementType())) {
            return "TEMPLATE";
        }
        if ("INDEX".equals(definition.getElementType())) {
            return "INDEX";
        }
        return "CONTENT";
    }

    private List<String> normalizeSelectedDiagrams(
            List<String> selectedDiagramIds,
            Map<String, TemplateSectionDefinition> byId) {
        if (selectedDiagramIds == null) {
            return new ArrayList<>();
        }
        List<String> normalized = new ArrayList<>();
        for (String id : selectedDiagramIds) {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("Selected diagram ids cannot be blank.");
            }
            TemplateSectionDefinition definition = byId.get(id);
            if (definition == null) {
                throw new IllegalArgumentException("Unknown diagram section: " + id);
            }
            if (definition.getDiagramType() == null) {
                throw new IllegalArgumentException("Diagrams are not supported for section: " + id);
            }
            if (!normalized.contains(id)) normalized.add(id);
        }
        return List.copyOf(normalized);
    }

    public List<String> defaultSelection(ProjectFacts facts, List<book.example.dto.SectionRecommendation> recommendations) {
        Set<String> selected = new HashSet<>();
        for (TemplateSectionDefinition definition : definitions()) {
            if (definition.isDefaultEnabled()) {
                selected.add(definition.getId());
            }
        }
        if (recommendations != null) {
            recommendations.stream()
                    .filter(book.example.dto.SectionRecommendation::isRecommended)
                    .map(book.example.dto.SectionRecommendation::getSectionId)
                    .forEach(selected::add);
        }
        return definitions().stream()
                .filter(definition -> selected.contains(definition.getId()))
                .sorted(Comparator.comparingInt(TemplateSectionDefinition::getOrder))
                .map(TemplateSectionDefinition::getId)
                .toList();
    }

    public List<String> requiredAdditionalInformation(List<String> selectedIds) {
        Set<String> selected = selectedIds == null ? new HashSet<>() : new HashSet<>(selectedIds);
        Map<String, TemplateSectionDefinition> byId = new LinkedHashMap<>();
        definitions().forEach(definition -> byId.put(definition.getId(), definition));
        selected.removeIf(id -> !byId.containsKey(id));
        boolean changed;
        do {
            changed = false;
            for (String id : new HashSet<>(selected)) {
                String parentId = byId.get(id).getParentId();
                if (parentId != null && !selected.contains(parentId)) {
                    selected.remove(id);
                    changed = true;
                }
            }
        } while (changed);
        return definitions().stream()
                .filter(definition -> selected.contains(definition.getId()))
                .filter(TemplateSectionDefinition::isRequiresAdditionalInformation)
                .map(TemplateSectionDefinition::getId)
                .toList();
    }

    private String resolveChapterId(TemplateSectionDefinition definition, Map<String, TemplateSectionDefinition> byId) {
        if (definition.isChapter()) {
            return definition.getId();
        }
        String parent = definition.getParentId();
        while (parent != null) {
            TemplateSectionDefinition parentDefinition = byId.get(parent);
            if (parentDefinition == null) {
                return null;
            }
            if (parentDefinition.isChapter()) {
                return parentDefinition.getId();
            }
            parent = parentDefinition.getParentId();
        }
        return null;
    }

    private String effectiveProjectName(ProjectFacts facts, StudentProjectDetails details) {
        if (details != null && details.getProjectTitleOverride() != null
                && !details.getProjectTitleOverride().isBlank()) {
            return details.getProjectTitleOverride().trim();
        }
        return facts.getProjectName() == null || facts.getProjectName().isBlank()
                ? "Global Student Project Report"
                : facts.getProjectName();
    }
}