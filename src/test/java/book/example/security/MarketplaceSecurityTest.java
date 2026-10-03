package book.example.security;

import book.example.Entity.AppUser;
import book.example.Entity.DocumentationJob;
import book.example.Repository.JobRepository;
import book.example.Repository.UserRepository;
import book.example.services.MarketplaceFileService;
import book.example.services.UserService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import jakarta.servlet.http.Cookie;
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

import java.nio.file.Path;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class MarketplaceSecurityTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private UserService userService;
    @Autowired private UserRepository userRepository;
    @Autowired private JobRepository jobRepository;
    @Autowired private MarketplaceFileService fileService;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void publicCanBrowseButCannotSubmitWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/marketplace"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());
        mockMvc.perform(multipart("/api/v1/marketplace/seller/listings")
                        .param("listingType", "DOCUMENTATION_ONLY")
                        .param("title", "Private attempt")
                        .param("description", "Should require a logged-in seller.")
                        .param("category", "Education")
                        .param("ownershipConfirmed", "true"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void reviewedListingIsPubliclyDownloadableForFree() throws Exception {
        String password = "a-long-test-password-789";
        AppUser seller = userService.registerLocalUser("market-seller@example.test", "Market Seller", password);
        Cookie sellerCookie = login("market-seller@example.test", password);
        byte[] pdf = createPdf("Free community report");
        MockMultipartFile document = new MockMultipartFile("document", "report.pdf", MediaType.APPLICATION_PDF_VALUE, pdf);

        MvcResult submitted = mockMvc.perform(multipart("/api/v1/marketplace/seller/listings")
                        .file(document)
                        .param("listingType", "DOCUMENTATION_ONLY")
                        .param("title", "Community Report")
                        .param("description", "A sample report for public learning.")
                        .param("category", "Education")
                        .param("technologies", "Java")
                        .param("ownershipConfirmed", "true")
                        .cookie(sellerCookie))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"))
                .andReturn();
        JsonNode listing = objectMapper.readTree(submitted.getResponse().getContentAsString());
        String id = listing.get("id").asText();
        String slug = listing.get("slug").asText();

        AppUser admin = userService.registerLocalUser("market-admin@example.test", "Market Admin", password);
        admin.setRoles(Set.of("ROLE_USER", "ROLE_ADMIN"));
        userRepository.saveAndFlush(admin);
        Cookie adminCookie = login("market-admin@example.test", password);
        mockMvc.perform(get("/api/v1/marketplace/moderation/queue").cookie(sellerCookie))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/marketplace/moderation/queue").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("UNDER_REVIEW"));
        mockMvc.perform(post("/api/v1/marketplace/moderation/{id}/decision", id)
                        .cookie(adminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approve\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
        mockMvc.perform(post("/api/v1/marketplace/seller/listings/{id}/publish", id)
                        .cookie(sellerCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        mockMvc.perform(get("/api/v1/marketplace/{slug}", slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originType").value("COMMUNITY"));
        mockMvc.perform(get("/api/v1/marketplace").param("query", "Community"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].slug").value(slug));
        mockMvc.perform(get("/api/v1/marketplace").param("originType", "COMMUNITY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].originType").value("COMMUNITY"));
        mockMvc.perform(get("/api/v1/marketplace").param("originType", "DOCGEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
        mockMvc.perform(get("/api/v1/marketplace/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("Education"));
        mockMvc.perform(get("/api/v1/marketplace/{slug}/download/documentation", slug))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray()).containsExactly(pdf));
        assertThat(listing.get("projectAvailable").asBoolean()).isFalse();

        Path stored = fileService.resolveStoredFile(
                Path.of("generated", "marketplace", id, "documentation.pdf").toAbsolutePath().toString());
        fileService.removeListingFiles(java.util.UUID.fromString(id));
        assertThat(stored).doesNotExist();
    }

    @Test
    void completedDocGenDocumentCanBeSubmittedWithoutUploadingItAgain() throws Exception {
        String password = "a-long-test-password-456";
        AppUser seller = userService.registerLocalUser("market-docgen@example.test", "DocGen Seller", password);
        Cookie sellerCookie = login("market-docgen@example.test", password);
        String jobId = UUID.randomUUID().toString();
        Path document = Path.of("generated", "jobs", jobId, "documentation.docx").toAbsolutePath().normalize();
        Files.createDirectories(document.getParent());
        try (XWPFDocument generated = new XWPFDocument()) {
            generated.createParagraph().createRun().setText("Generated document fixture");
            try (var output = Files.newOutputStream(document)) {
                generated.write(output);
            }
        }

        DocumentationJob job = new DocumentationJob();
        job.setJobId(jobId);
        job.setOwnerId(seller.getId());
        job.setStatus("COMPLETED");
        job.setProjectName("Generated Project");
        job.setDocumentPath(document.toString());
        job.setCreatedAt(LocalDateTime.now());
        job.setUpdatedAt(LocalDateTime.now());
        job.setExpiresAt(LocalDateTime.now().plusDays(1));
        jobRepository.saveAndFlush(job);

        try {
            mockMvc.perform(post("/api/v1/marketplace/seller/docgen/{jobId}", jobId)
                            .cookie(sellerCookie)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"title":"Generated Guide","description":"Shared from DocGen.",
                                    "category":"Education","technologies":"Java","ownershipConfirmed":true}
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.originType").value("DOCGEN"))
                    .andExpect(jsonPath("$.projectName").value("Generated Project"))
                    .andExpect(jsonPath("$.documentAvailable").value(true));
            assertThat(document).exists();
        } finally {
            Files.deleteIfExists(document);
            Files.deleteIfExists(document.getParent());
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

    private byte[] createPdf(String text) throws Exception {
        try (PDDocument document = new PDDocument();
             java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(50, 700);
                content.showText(text);
                content.endText();
            }
            document.save(output);
            return output.toByteArray();
        }
    }
}
