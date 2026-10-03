package book.example.security;

import book.example.Entity.AppUser;
import book.example.Entity.DocumentationJob;
import book.example.Repository.JobRepository;
import book.example.Repository.UserRepository;
import book.example.services.UsageLedgerService;
import book.example.services.UserService;
import book.example.services.UserProfileImageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.mock.web.MockMultipartFile;

import jakarta.servlet.http.Cookie;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@org.springframework.test.context.TestPropertySource(properties = "app.security.google.enabled=true")
class AuthSecurityTest {

    @Autowired private ApplicationContext applicationContext;
    @Autowired private UserService userService;
    @Autowired private UserRepository userRepository;
    @Autowired private JobRepository jobRepository;
    @Autowired private UsageLedgerService usageLedgerService;
    @Autowired private UserProfileImageService userProfileImageService;
    @Autowired private MockMvc mockMvc;

    @Test
    void applicationStartsAndSecurityContextLoads() {
        assertThat(applicationContext).isNotNull();
        assertThat(applicationContext.getBeanDefinitionNames()).isNotEmpty();
    }

    @Test
    void oauthUserUpsertPersistsRequiredFieldsAndRoles() {
        String email = "oauth-test@example.test";
        OAuth2User oauthUser = new DefaultOAuth2User(
                Set.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("sub", "google-test-sub", "email", email, "name", "Test User"),
                "sub");

        AppUser savedUser = userService.upsertOAuth2User(oauthUser);
        AppUser reloadedUser = userRepository.findByEmail(email).orElseThrow();

        assertThat(savedUser.getId()).isNotNull();
        assertThat(reloadedUser.getPasswordHash()).isEmpty();
        assertThat(reloadedUser.getDisplayName()).isEqualTo("Test User");
        assertThat(reloadedUser.getRole()).isEqualTo("ROLE_USER");
        assertThat(reloadedUser.getRoles()).contains("ROLE_USER");
    }

    @Test
    void documentationJobsAreVisibleOnlyToTheirOwner() throws Exception {
        String password = "a-long-test-password-456";
        AppUser owner = userService.registerLocalUser("job-owner@example.test", "Job Owner", password);
        AppUser otherUser = userService.registerLocalUser("different-user@example.test", "Different User", password);

        String jobId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        DocumentationJob job = new DocumentationJob();
        job.setJobId(jobId);
        job.setOwnerId(owner.getId());
        job.setProjectName("Private Project");
        job.setStatus("RECEIVED");
        job.setType("DOCUMENTATION");
        job.setCreatedAt(now);
        job.setUpdatedAt(now);
        job.setExpiresAt(now.plusDays(1));
        jobRepository.save(job);
        usageLedgerService.recordGeneration(owner.getId(), jobId);

        Cookie ownerCookie = loginAndGetCookie("job-owner@example.test", password);
        Cookie otherUserCookie = loginAndGetCookie("different-user@example.test", password);

        mockMvc.perform(get("/api/documentation/{jobId}/status", jobId).cookie(ownerCookie))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/documentation/{jobId}/status", jobId).cookie(otherUserCookie))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/auth/usage").cookie(ownerCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].jobId").value(jobId))
                .andExpect(jsonPath("$[0].units").value(1));
        mockMvc.perform(get("/api/auth/usage").cookie(otherUserCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void protectedDocumentationEndpointRejectsAnonymousRequests() throws Exception {
        mockMvc.perform(multipart("/api/documentation/analyze"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void crossOriginApiMutationIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .header("Origin", "https://evil.example")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@example.com\",\"password\":\"a-long-test-password-123\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void protectedGenerationEndpointRejectsAnonymousRequests() throws Exception {
        mockMvc.perform(post("/api/documentation/job-1/generate-v1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registrationAndLoginIssueCookieBackedJwt() throws Exception {
        String email = "local-auth-test@example.test";
        String password = "a-long-test-password-123";
        userService.registerLocalUser(email, "Local Auth Test", password);

        loginAndGetCookie(email, password);
    }

    @Test
    void sixCharacterPasswordCanRegisterAndAuthenticate() {
        String email = "six-character-password@example.test";
        String password = "sixsix";
        userService.registerLocalUser(email, "Six Character Password", password);
        assertThat(userService.authenticateLocalUser(email, password)).isPresent();
    }

    @Test
    void authenticatedUserCanUploadAndReadProfileAvatar() throws Exception {
        String email = "avatar-test@example.test";
        String password = "a-long-test-password-123";
        AppUser user = userService.registerLocalUser(email, "Avatar Test", password);
        Cookie jwtCookie = loginAndGetCookie(email, password);

        ByteArrayOutputStream imageBytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", imageBytes);
        MockMultipartFile image = new MockMultipartFile(
                "image", "profile.png", MediaType.IMAGE_PNG_VALUE, imageBytes.toByteArray());

        MvcResult uploadResult = mockMvc.perform(multipart("/api/auth/avatar")
                        .file(image)
                        .cookie(jwtCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarUrl").value(org.hamcrest.Matchers.startsWith("/api/auth/avatar?v=")))
                .andReturn();

        Path avatarPath = userProfileImageService.resolveAvatar(user.getId());
        try {
            mockMvc.perform(get("/api/auth/avatar").cookie(jwtCookie))
                    .andExpect(status().isOk())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                            .contentType(MediaType.IMAGE_PNG))
                    .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray()).isNotEmpty());
            assertThat(uploadResult.getResponse().getContentAsString()).contains("avatarUrl");
            assertThat(userRepository.findById(user.getId()).orElseThrow().getAvatarUrl())
                    .isEqualTo("/api/auth/avatar");
        } finally {
            Files.deleteIfExists(avatarPath);
            Files.deleteIfExists(avatarPath.getParent());
        }
    }

    @Test
    void passwordResetEndpointsAreDisabledByDefault() throws Exception {
        mockMvc.perform(post("/api/auth/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@example.com\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"abc\",\"password\":\"a-long-test-password-123\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void usageSummaryIsAuthenticated() throws Exception {
        String email = "usage-summary@example.test";
        String password = "a-long-test-password-789";
        userService.registerLocalUser(email, "Usage Summary", password);
        Cookie jwtCookie = loginAndGetCookie(email, password);

        mockMvc.perform(get("/api/auth/usage/summary").cookie(jwtCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.used").value(0))
                .andExpect(jsonPath("$.monthlyLimit").value(-1))
                .andExpect(jsonPath("$.remaining").value(-1));
    }

    private Cookie loginAndGetCookie(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("docuai_token"))
                .andReturn();
        return result.getResponse().getCookie("docuai_token");
    }
}
