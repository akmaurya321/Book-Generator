package book.example.security;

import book.example.Entity.AppUser;
import book.example.Entity.DocumentationJob;
import book.example.Repository.JobRepository;
import book.example.Entity.TemplateCatalogEntry;
import book.example.Repository.TemplateCatalogRepository;
import book.example.Repository.TemplateAuditEventRepository;
import book.example.Repository.TemplateVersionRepository;
import book.example.Repository.UserRepository;
import book.example.dto.GenerateDocumentationRequest;
import book.example.dto.ProjectFacts;
import book.example.services.TemplateFileStorageService;
import book.example.services.DocumentationOrchestrator;
import book.example.services.GlobalTemplateService;
import book.example.services.UserService;
import jakarta.servlet.http.Cookie;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class TemplateLibrarySecurityTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private UserService userService;
    @Autowired private UserRepository userRepository;
    @Autowired private TemplateVersionRepository versionRepository;
    @Autowired private TemplateCatalogRepository catalogRepository;
    @Autowired private TemplateAuditEventRepository auditRepository;
    @Autowired private TemplateFileStorageService storage;
    @Autowired private DocumentationOrchestrator orchestrator;
    @Autowired private GlobalTemplateService globalTemplateService;
    @Autowired private JobRepository jobRepository;
    @Autowired private ObjectMapper objectMapper;
    private String createdVersionId;

    @AfterEach
    void cleanTemplateFiles() {
        if (createdVersionId == null) return;
        versionRepository.findById(java.util.UUID.fromString(createdVersionId)).ifPresent(version -> {
            TemplateCatalogEntry catalog = version.getCatalog();
            storage.removeVersion(catalog.getId(), version.getVersion());
            auditRepository.deleteByTemplateVersionId(version.getId());
            versionRepository.delete(version);
            if (versionRepository.countByCatalog_Id(catalog.getId()) == 0) catalogRepository.delete(catalog);
        });
        createdVersionId = null;
    }

    @Test
    void adminTemplateRequiresValidationReviewApprovalAndPublication() throws Exception {
        String password = "a-long-template-admin-test-123";
        AppUser admin = userService.registerLocalUser("template-admin@example.test", "Template Admin", password);
        admin.setRoles(Set.of("ROLE_USER", "ROLE_ADMIN"));
        userRepository.saveAndFlush(admin);
        Cookie adminCookie = login("template-admin@example.test", password);

        MvcResult submitted = mockMvc.perform(multipart("/api/v1/admin/templates")
                        .file(templateFile())
                        .param("country", "Test Country")
                        .param("state", "Test State")
                        .param("city", "Test City")
                        .param("college", "Test College")
                        .param("department", "Computer Science")
                        .param("degree", "B.Tech")
                        .param("projectType", "MAJOR_FINAL_YEAR_PROJECT")
                        .cookie(adminCookie))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.securityScanStatus").value("CLEAN"))
                .andExpect(jsonPath("$.validationStatus").value("VALID"))
                .andReturn();
        JsonNode created = objectMapper.readTree(submitted.getResponse().getContentAsString());
        String templateId = created.get("templateId").asText();
        String versionId = created.get("id").asText();
        createdVersionId = versionId;

        mockMvc.perform(get("/api/v1/templates").param("query", "Test College"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
        mockMvc.perform(post("/api/v1/admin/templates/{id}/approve", versionId))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/admin/templates/{id}/approve", versionId).cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
        mockMvc.perform(post("/api/v1/admin/templates/{id}/publish", versionId).cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.available").value(true));
        mockMvc.perform(get("/api/v1/templates").param("query", "Test College"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].templateId").value(templateId))
                .andExpect(jsonPath("$.items[0].version").value(1));
        mockMvc.perform(get("/api/v1/templates/{id}", templateId).param("version", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"));
        mockMvc.perform(get("/api/v1/admin/templates/{id}/audit", versionId).cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5));
    }

    @Test
    void unauthenticatedUsersCanSearchPublishedTemplatesButCannotSubmitTemplates() throws Exception {
        mockMvc.perform(get("/api/v1/templates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].templateId").value("universal-template"));
        mockMvc.perform(multipart("/api/v1/template-submissions")
                        .file(templateFile())
                        .param("country", "Test Country")
                        .param("college", "Private College")
                        .param("department", "Computing")
                        .param("degree", "B.Tech")
                        .param("projectType", "CAPSTONE")
                        .param("ownershipConfirmed", "true"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void generationPinsAndSnapshotsTheSelectedPublishedTemplateVersion() throws Exception {
        String password = "a-long-template-snapshot-test-123";
        AppUser owner = userService.registerLocalUser("template-snapshot@example.test", "Template Snapshot", password);
        String jobId = java.util.UUID.randomUUID().toString();
        DocumentationJob job = new DocumentationJob();
        job.setJobId(jobId);
        job.setOwnerId(owner.getId());
        job.setStatus("INDEXING_PROJECT");
        job.setType("GLOBAL_STUDENT_PROJECT");
        job.setCreatedAt(LocalDateTime.now());
        job.setUpdatedAt(LocalDateTime.now());
        job.setExpiresAt(LocalDateTime.now().plusDays(1));
        job.setProjectFactsJson(objectMapper.writeValueAsString(new ProjectFacts()));
        job.setIndexingFailuresJson("[]");
        jobRepository.saveAndFlush(job);

        GenerateDocumentationRequest request = new GenerateDocumentationRequest();
        request.setLibraryTemplateId("universal-template");
        request.setLibraryTemplateVersion(1);
        request.setSelectedSections(java.util.List.of(globalTemplateService.definitions().get(0).getId()));
        globalTemplateService.requiredAdditionalInformation(request.getSelectedSections())
                .forEach(id -> request.getAdditionalInformation().put(id, "Test value"));
        var student = new book.example.dto.StudentProjectDetails();
        student.setName("Snapshot Student");
        student.setCourse("B.Tech");
        student.setDepartment("Computing");
        student.setAcademicYear("2025-2026");
        student.setCollegeName("Example College");
        student.setUniversityName("Example University");
        student.setGuideName("Project Guide");
        request.setStudentDetails(student);

        try {
            var response = orchestrator.startFinalGeneration(jobId, owner.getId(), request);
            assertEquals("WAITING_FOR_INDEXING", response.getStatus());
            DocumentationJob saved = jobRepository.findById(jobId).orElseThrow();
            assertEquals("universal-template", saved.getTemplateId());
            assertEquals(1, saved.getTemplateVersion());
            assertNotNull(saved.getTemplateFormatSnapshotJson());
            assertTrue(saved.getTemplateFormatSnapshotJson().contains("\"pageSize\":\"A4\""));
            assertNotNull(saved.getTemplateFrontPageSnapshotJson());
            assertTrue(saved.getTemplateFrontPageSnapshotJson().contains("Snapshot Student"));
        } finally {
            jobRepository.deleteById(jobId);
        }
    }

    @Test
    void privateFormatIsScopedToItsJobAndSnapshottedOnlyForThatGeneration() throws Exception {
        AppUser owner = userService.registerLocalUser(
                "private-template@example.test", "Private Template", "a-long-private-template-test-123");
        String jobId = java.util.UUID.randomUUID().toString();
        DocumentationJob job = new DocumentationJob();
        job.setJobId(jobId);
        job.setOwnerId(owner.getId());
        job.setStatus("INDEXING_PROJECT");
        job.setType("GLOBAL_STUDENT_PROJECT");
        job.setCreatedAt(LocalDateTime.now());
        job.setUpdatedAt(LocalDateTime.now());
        job.setExpiresAt(LocalDateTime.now().plusDays(1));
        job.setProjectFactsJson(objectMapper.writeValueAsString(new ProjectFacts()));
        job.setIndexingFailuresJson("[]");
        jobRepository.saveAndFlush(job);
        try {
            orchestrator.analyzePrivateTemplateFormat(jobId, owner.getId(), templateFile());
            GenerateDocumentationRequest request = new GenerateDocumentationRequest();
            request.setUsePrivateFormat(true);
            request.setSelectedSections(java.util.List.of(globalTemplateService.definitions().get(0).getId()));
            globalTemplateService.requiredAdditionalInformation(request.getSelectedSections())
                    .forEach(id -> request.getAdditionalInformation().put(id, "Test value"));
            var student = new book.example.dto.StudentProjectDetails();
            student.setName("Private Student");
            student.setCourse("B.Tech");
            student.setDepartment("Computing");
            student.setAcademicYear("2025-2026");
            student.setCollegeName("Private College");
            student.setUniversityName("Private University");
            student.setGuideName("Project Guide");
            request.setStudentDetails(student);

            var response = orchestrator.startFinalGeneration(jobId, owner.getId(), request);
            assertEquals("WAITING_FOR_INDEXING", response.getStatus());
            DocumentationJob saved = jobRepository.findById(jobId).orElseThrow();
            assertEquals("private-upload", saved.getTemplateId());
            assertNull(saved.getTemplateVersion());
            assertNotNull(saved.getPrivateTemplateFormatJson());
            assertNotNull(saved.getTemplateFormatSnapshotJson());
            assertTrue(saved.getTemplateFrontPageSnapshotJson().contains("Private Student"));
        } finally {
            jobRepository.deleteById(jobId);
        }
    }

    @Test
    void adminBootstrapCreatesAndPromotesWithoutReplacingAnExistingPassword() {
        String email = "bootstrap-admin@example.test";
        String password = "a-long-bootstrap-password-123";
        AppUser created = userService.bootstrapAdmin(email, "Bootstrap Admin", password);
        assertTrue(created.getRoles().contains("ROLE_ADMIN"));
        assertTrue(userService.authenticateLocalUser(email, password).isPresent());
        String originalPasswordHash = created.getPasswordHash();

        AppUser promoted = userService.bootstrapAdmin(email, "Bootstrap Admin", "a-different-password-456");
        assertEquals(originalPasswordHash, promoted.getPasswordHash());
        assertTrue(promoted.getRoles().contains("ROLE_ADMIN"));
        assertTrue(userService.authenticateLocalUser(email, password).isPresent());
        assertTrue(userService.authenticateLocalUser(email, "a-different-password-456").isEmpty());
    }

    private MockMultipartFile templateFile() throws Exception {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("Official College Project Format");
            document.write(output);
            return new MockMultipartFile("template", "college-template.docx",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document", output.toByteArray());
        }
    }

    private Cookie login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getCookie("docuai_token");
    }
}
