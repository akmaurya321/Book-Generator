package book.example.services;

import book.example.dto.ProjectFacts;
import book.example.dto.RagSearchResult;
import book.example.dto.SectionRecommendation;
import book.example.dto.TemplateSectionDefinition;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class SectionRecommendationService {
    private static final Logger log = LoggerFactory.getLogger(SectionRecommendationService.class);

    public SectionRecommendationService() {
    }


    public List<SectionRecommendation> recommend(
            String jobId,
            ProjectFacts facts,
            List<TemplateSectionDefinition> definitions) {

        // Initial recommendations must be available immediately, before the
        // asynchronous repository embedding pass completes. ProjectFacts already
        // contains deterministic file/type signals needed for V1 recommendations.
        List<RagSearchResult> evidence = List.of();
        List<SectionRecommendation> recommendations = new ArrayList<>();
        java.util.Map<String, Recommendation> byId = new java.util.LinkedHashMap<>();
        for (TemplateSectionDefinition definition : definitions) {
            if (!definition.isChapter()) {
                byId.put(definition.getId(), recommendOne(facts, definition, evidence));
            }
        }
        for (TemplateSectionDefinition definition : definitions) {
            Recommendation result;
            if (definition.isChapter()) {
                boolean childRecommended = definitions.stream()
                        .filter(child -> definition.getId().equals(child.getParentId()))
                        .anyMatch(child -> {
                            Recommendation r = byId.get(child.getId());
                            return r != null && r.recommended();
                        });
                result = new Recommendation(definition.isDefaultEnabled() || childRecommended,
                        childRecommended ? "Recommended because the analyzed project contains evidence relevant to this chapter." : "Available global chapter; select it when required by your college/report.");
            } else {
                result = byId.get(definition.getId());
            }
            boolean diagram = definition.getDiagramType() != null && result.recommended() &&
                    (has(facts.getDatabaseFiles()) || has(facts.getApiFiles()) || hasFrontend(facts));
            boolean image = result.recommended() && hasFrontend(facts) &&
                    (definition.getId().contains("ui") || definition.getId().contains("screen") ||
                     definition.getId().contains("implementation") || definition.getId().contains("result"));
            boolean content = !"INDEX".equalsIgnoreCase(definition.getElementType()) && result.contentRecommended();
            recommendations.add(new SectionRecommendation(definition.getId(), result.recommended(),
                    content, image, diagram, result.reason()));
        }
        return recommendations;
    }

    private Recommendation recommendOne(ProjectFacts facts, TemplateSectionDefinition definition, List<RagSearchResult> evidence) {
        String id = definition.getId();
        boolean hasDatabase = has(facts.getDatabaseFiles());
        boolean hasApi = has(facts.getApiFiles());
        boolean hasTests = has(facts.getTestFiles());
        boolean hasConfig = has(facts.getConfigurationFiles());
        boolean hasFrontend = hasFrontend(facts);
        boolean hasSecurity = has(facts.getSecurityFiles());
        boolean hasAi = containsAny(facts.getTechnologies(), "Python", "Java", "AI", "Machine Learning", "LLM", "Spring AI", "Ollama", "LangChain")
                || containsAny(facts.getFrameworks(), "TensorFlow / Keras", "PyTorch", "Spring AI", "LangChain")
                || hasAiEvidenceFromFacts(facts);
        boolean hasDocs = has(facts.getDocumentationFiles());

        return switch (id) {
            case "cover_page", "table_of_contents", "abstract", "project_background", "problem_statement", "objectives",
                    "scope", "system_overview", "system_architecture", "conclusion" ->
                    new Recommendation(true, "Core project-report section.");
            case "technical_background", "technology_stack", "project_structure", "backend_implementation", "configuration" ->
                    new Recommendation(true, "Supported by analyzed repository content.");
            case "api_design", "api_implementation" -> new Recommendation(hasApi,
                    hasApi ? "Actual API route/controller evidence was detected in source content." : "No verified API route/controller evidence was detected.");
            case "database_design", "er_diagram" -> new Recommendation(hasDatabase,
                    hasDatabase ? "Actual database/schema/entity/connection evidence was detected in source content." : "No verified database/schema evidence was detected.");
            case "testing_overview", "test_cases" -> new Recommendation(hasTests,
                    hasTests ? "Test source files were detected." : "No test source files were detected.");
            case "test_results", "performance_testing" -> new Recommendation(false,
                    "Test source is not evidence that tests passed or that performance was measured. Enable only when execution results are supplied or verifiably present in the repository.");
            case "frontend_implementation", "ui_ux_design" -> new Recommendation(hasFrontend,
                    hasFrontend ? "Frontend source files were detected." : "No verified frontend source files were detected.");
            case "authentication_flow" -> new Recommendation(hasSecurity,
                    hasSecurity ? "Security/authentication implementation evidence was detected in source content." : "No verified authentication/security implementation evidence was detected.");
            case "deployment", "deployment_architecture", "docker_deployment" -> new Recommendation(hasConfig && hasDeploymentEvidenceFromFacts(facts),
                    hasConfig ? "Deployment configuration files exist; deployment-specific content is required before enabling this section." : "No deployment configuration files were detected.");
            case "ai_rag_pipeline", "ai_ml_implementation" -> new Recommendation(hasAi,
                    hasAi ? "Verified AI/ML technology evidence was detected." : "No verified AI/ML implementation evidence was detected.");
            case "references" -> new Recommendation(hasDocs, hasDocs ? "Repository documentation sources exist." : "No repository documentation sources were detected.");
            case "literature_review", "research_gap", "experimental_results", "performance_results", "user_evaluation" ->
                    new Recommendation(false, "Optional section; enable only when evidence or user-provided information exists.");
            default -> new Recommendation(definition.isDefaultEnabled(), definition.isDefaultEnabled() ? "Included as a general student-report section." : "Optional until supported by verified evidence.");
        };
    }
    private boolean hasDeploymentEvidenceFromFacts(ProjectFacts facts) {
        if (facts == null || facts.getAllFiles() == null) {
            return false;
        }

        return facts.getAllFiles().stream()
                .filter(Objects::nonNull)
                .map(String::toLowerCase)
                .anyMatch(path ->
                        path.contains("dockerfile")
                                || path.contains("docker-compose")
                                || path.contains("compose.yml")
                                || path.contains("compose.yaml")
                                || path.contains("kubernetes")
                                || path.contains("k8s/")
                                || path.contains("deployment.yml")
                                || path.contains("deployment.yaml")
                                || path.contains("application.yml")
                                || path.contains("application.yaml")
                );
    }

    private boolean hasAiEvidenceFromFacts(ProjectFacts facts) {
        if (facts == null || facts.getAllFiles() == null) return false;
        return facts.getAllFiles().stream().map(path -> path == null ? "" : path.toLowerCase(Locale.ROOT))
                .anyMatch(path -> path.contains("embedding") || path.contains("vector") || path.contains("llm")
                        || path.contains("ollama") || path.contains("langchain") || path.contains("pytorch")
                        || path.contains("tensorflow"));
    }

    private boolean containsAny(String text, String... terms) {
        if (text == null || text.isBlank()) return false;
        for (String term : terms) {
            if (term != null && text.contains(term.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private boolean hasFrontend(ProjectFacts facts) {
        return containsAny(facts.getTechnologies(), "JavaScript", "TypeScript", "JavaScript / React", "TypeScript / React")
                || facts.getAllFiles().stream().anyMatch(path -> {
                    String lower = path.toLowerCase(Locale.ROOT);
                    return lower.endsWith(".html") || lower.endsWith(".css") || lower.endsWith(".jsx") || lower.endsWith(".tsx") || lower.endsWith(".vue") || lower.endsWith(".svelte");
                });
    }

    private boolean containsAny(List<String> values, String... terms) {
        if (values == null) {
            return false;
        }
        return values.stream().anyMatch(value -> {
            String lower = value == null ? "" : value.toLowerCase(Locale.ROOT);
            for (String term : terms) {
                if (lower.contains(term)) {
                    return true;
                }
            }
            return false;
        });
    }

    private boolean has(List<String> values) {
        return values != null && !values.isEmpty();
    }

    private record Recommendation(boolean recommended, String reason, boolean contentRecommended, boolean imageRecommended, boolean diagramRecommended) {
        private Recommendation(boolean recommended, String reason) {
            this(recommended, reason, true, false, false);
        }
    }
}