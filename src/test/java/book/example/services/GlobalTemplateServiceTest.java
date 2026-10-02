package book.example.services;

import book.example.dto.ProjectFacts;
import book.example.dto.SectionConfiguration;
import book.example.dto.StudentProjectDetails;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GlobalTemplateServiceTest {

    private GlobalTemplateService service() {
        return new GlobalTemplateService(new ObjectMapper());
    }

    private ProjectFacts facts() {
        ProjectFacts facts = new ProjectFacts();
        facts.setProjectName("Test Project");
        facts.setDescription("A verified project description.");
        return facts;
    }

    @Test
    void loadsStableGlobalTemplateAndDependencyGraph() {
        var service = service();
        assertEquals(GlobalTemplateService.TEMPLATE_ID, service.getTemplate().getId());
        assertEquals(100, service.definitions().size());
        assertEquals(100, service.definitions().stream().map(d -> d.getId()).distinct().count());
        assertTrue(service.definitions().stream().anyMatch(d -> "system_design".equals(d.getId())));
    }

    @Test
    void rejectsUnknownSelectedSectionsAndDiagramSections() {
        var service = service();
        StudentProjectDetails details = new StudentProjectDetails();
        assertThrows(IllegalArgumentException.class, () -> service.buildPlan(
                facts(), List.of("does-not-exist"), List.of(), List.of(), details, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> service.buildPlan(
                facts(), List.of("introduction"), List.of("does-not-exist"), List.of(), details, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> service.buildPlan(
                facts(), List.of("introduction"), List.of("introduction"), List.of(), details, Map.of()));
    }

    @Test
    void childSelectionWithoutEnabledParentIsRejected() {
        var service = service();
        assertThrows(IllegalArgumentException.class, () -> service.buildPlan(
                facts(), List.of("domain_overview"), List.of(), List.of(), new StudentProjectDetails(), Map.of()));
    }

    @Test
    void dependenciesAreOnlyRecordedWhenBothChaptersAreSelected() {
        var service = service();
        var partialPlan = service.buildPlan(
                facts(), List.of("requirements_analysis", "system_design"), List.of(), List.of(), new StudentProjectDetails(), Map.of());
        assertEquals(List.of(), partialPlan.getChapterDependencies().get("requirements_analysis"));
        assertEquals(List.of(), partialPlan.getChapterDependencies().get("system_design"));

        var completePlan = service.buildPlan(
                facts(), List.of("introduction", "requirements_analysis"), List.of(), List.of(), new StudentProjectDetails(), Map.of());
        assertEquals(List.of("introduction"), completePlan.getChapterDependencies().get("requirements_analysis"));
    }

    @Test
    void imageAndDiagramConfigurationMustMatchTemplateCapabilities() {
        var service = service();
        StudentProjectDetails details = new StudentProjectDetails();
        SectionConfiguration invalidImage = new SectionConfiguration();
        invalidImage.setSectionId("system_architecture");
        invalidImage.setContentEnabled(true);
        invalidImage.setImageEnabled(false);
        invalidImage.setImageIds(List.of("IMG-001"));
        assertThrows(IllegalArgumentException.class, () -> service.buildPlan(
                facts(), List.of("system_design", "system_architecture"), List.of(), List.of(invalidImage), details, Map.of()));

        SectionConfiguration invalidDiagram = new SectionConfiguration();
        invalidDiagram.setSectionId("system_modules");
        invalidDiagram.setContentEnabled(true);
        invalidDiagram.setDiagramEnabled(true);
        assertThrows(IllegalArgumentException.class, () -> service.buildPlan(
                facts(), List.of("system_design", "system_modules"), List.of(), List.of(invalidDiagram), details, Map.of()));
    }
    @Test
    void assignsEvidenceModesForProjectHumanAndResearchChapters() {
        var service = service();
        assertEquals("PROJECT", service.definitions().stream().filter(d -> "system_design".equals(d.getId())).findFirst().orElseThrow().getEvidenceMode());
        assertEquals("RESEARCH", service.definitions().stream().filter(d -> "background_related_work".equals(d.getId())).findFirst().orElseThrow().getEvidenceMode());
        assertEquals("HYBRID", service.definitions().stream().filter(d -> "introduction".equals(d.getId())).findFirst().orElseThrow().getEvidenceMode());
        assertEquals("HYBRID", service.definitions().stream().filter(d -> "conclusion_future_work".equals(d.getId())).findFirst().orElseThrow().getEvidenceMode());
    }

    @Test
    void allSupportedTemplateVariantsProduceDistinctExpectedFormats() {
        var service = service();
        for (String templateId : List.of(
                GlobalTemplateService.TEMPLATE_ID,
                "academic_college_v2",
                "university_formal_v2",
                "technical_report_v2")) {
            assertTrue(service.isSupportedTemplateId(templateId), templateId);
            var plan = service.buildPlan(
                    facts(), List.of("cover_page", "abstract", "introduction", "requirements_analysis", "system_analysis", "system_design", "system_architecture"),
                    List.of("system_architecture"), List.of(), new StudentProjectDetails(), Map.of());
            service.applyTemplateVariant(plan, templateId);
            assertNotNull(plan.getFormat());
            assertTrue(plan.getFormat().getDefaultFontSize() > 0);
            assertTrue(plan.getFormat().getLineSpacing() > 0);
        }
    }

    @Test
    void imageDiagramContentConfigurationCombinationsAreValidatedIndependently() {
        var service = service();
        SectionConfiguration contentOnly = new SectionConfiguration();
        contentOnly.setSectionId("system_architecture");
        contentOnly.setContentEnabled(true);
        contentOnly.setImageEnabled(false);
        contentOnly.setDiagramEnabled(false);
        var contentPlan = service.buildPlan(
                facts(), List.of("introduction", "requirements_analysis", "system_analysis", "system_design", "system_architecture"), List.of(),
                List.of(contentOnly), new StudentProjectDetails(), Map.of());
        var contentSection = contentPlan.getSections().stream().filter(s -> "system_architecture".equals(s.getId())).findFirst().orElseThrow();
        assertTrue(contentSection.isContentEnabled());
        assertFalse(contentSection.isImageEnabled());
        assertFalse(contentSection.isDiagramEnabled());

        SectionConfiguration diagramOnly = new SectionConfiguration();
        diagramOnly.setSectionId("system_architecture");
        diagramOnly.setContentEnabled(false);
        diagramOnly.setImageEnabled(false);
        diagramOnly.setDiagramEnabled(true);
        var diagramPlan = service.buildPlan(
                facts(), List.of("introduction", "requirements_analysis", "system_analysis", "system_design", "system_architecture"), List.of("system_architecture"),
                List.of(diagramOnly), new StudentProjectDetails(), Map.of());
        var diagramSection = diagramPlan.getSections().stream().filter(s -> "system_architecture".equals(s.getId())).findFirst().orElseThrow();
        assertFalse(diagramSection.isContentEnabled());
        assertFalse(diagramSection.isImageEnabled());
        assertTrue(diagramSection.isDiagramEnabled());
    }

}
