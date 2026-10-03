package book.example.controllers;

import book.example.Entity.AppUser;
import book.example.dto.AuthUserResponse;
import book.example.services.JwtCookieService;
import book.example.services.JwtService;
import book.example.services.PasswordResetService;
import book.example.services.UsageLedgerService;
import book.example.services.UserService;
import book.example.services.UserProfileImageService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.beans.factory.annotation.Value;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.List;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final JwtService jwtService;
    private final JwtCookieService jwtCookieService;
    private final UsageLedgerService usageLedgerService;
    private final PasswordResetService passwordResetService;
    private final UserProfileImageService userProfileImageService;

    @Value("${app.security.google.enabled:false}")
    private boolean googleAuthEnabled;

    @Value("${app.security.password-reset.enabled:false}")
    private boolean passwordResetEnabled;

    public AuthController(UserService userService, JwtService jwtService,
                          JwtCookieService jwtCookieService,
                          UsageLedgerService usageLedgerService,
                          PasswordResetService passwordResetService,
                          UserProfileImageService userProfileImageService) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.jwtCookieService = jwtCookieService;
        this.usageLedgerService = usageLedgerService;
        this.passwordResetService = passwordResetService;
        this.userProfileImageService = userProfileImageService;
    }

    @GetMapping("/me")
    public ResponseEntity<AuthUserResponse> me() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication.getPrincipal() == null || "anonymousUser".equals(authentication.getPrincipal().toString())) {
            return ResponseEntity.ok(new AuthUserResponse(false, null));
        }

        AppUser user = (AppUser) authentication.getPrincipal();
        return ResponseEntity.ok(new AuthUserResponse(true,
            userSummary(user)));
    }

    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadAvatar(
            @AuthenticationPrincipal AppUser user,
            @RequestParam("image") MultipartFile image) {
        try {
            return ResponseEntity.ok(Map.of("avatarUrl", userProfileImageService.updateAvatar(user, image)));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
        }
    }

    @GetMapping("/avatar")
    public ResponseEntity<Resource> getAvatar(@AuthenticationPrincipal AppUser user) {
        try {
            Path image = userProfileImageService.resolveAvatar(user.getId());
            MediaType mediaType = image.getFileName().toString().endsWith(".png")
                    ? MediaType.IMAGE_PNG
                    : MediaType.IMAGE_JPEG;
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .contentType(mediaType)
                    .body(new FileSystemResource(image));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/config")
    public ResponseEntity<Map<String, Boolean>> config() {
        return ResponseEntity.ok(Map.of(
                "googleAuthEnabled", googleAuthEnabled,
                "passwordResetEnabled", passwordResetEnabled));
    }

    @GetMapping("/login")
    public ResponseEntity<Map<String, String>> login() {
        return ResponseEntity.ok(Map.of(
                "message", "Use POST /api/auth/login or sign in with Google.",
                "provider", "google"
        ));
    }

    @GetMapping("/usage")
    public ResponseEntity<List<UsageRecord>> usage(@AuthenticationPrincipal AppUser user) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        List<UsageRecord> records = usageLedgerService.getRecentUsage(user.getId()).stream()
                .map(event -> new UsageRecord(event.getJobId(), event.getOperation(),
                        event.getUnits(), event.getStatus(), event.getCreatedAt(), event.getUpdatedAt()))
                .toList();
        return ResponseEntity.ok(records);
    }

    @GetMapping("/usage/summary")
    public ResponseEntity<UsageSummary> usageSummary(@AuthenticationPrincipal AppUser user) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        long used = usageLedgerService.getMonthlyUsage(user.getId());
        return ResponseEntity.ok(new UsageSummary(
                used, -1, -1));
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request,
                                      HttpServletResponse response) {
        try {
            AppUser user = userService.registerLocalUser(request.email(), request.name(), request.password());
            return authenticatedResponse(user, response, HttpStatus.CREATED);
        } catch (IllegalStateException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", exception.getMessage()));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", exception.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> authenticate(@Valid @RequestBody LoginRequest request,
                                          HttpServletResponse response) {
        Optional<AppUser> user = userService.authenticateLocalUser(request.email(), request.password());
        if (user.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Email or password is incorrect."));
        }
        return authenticatedResponse(user.get(), response, HttpStatus.OK);
    }

    @PostMapping("/password/forgot")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        if (!passwordResetEnabled) {
            return ResponseEntity.notFound().build();
        }
        try {
            passwordResetService.requestReset(request.email());
            // Do not reveal whether an account exists.
            return ResponseEntity.ok(Map.of("message", "If an account exists for that email, a reset link has been sent."));
        } catch (IllegalStateException exception) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("message", "Password reset email is temporarily unavailable."));
        }
    }

    @PostMapping("/password/reset")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        if (!passwordResetEnabled) {
            return ResponseEntity.notFound().build();
        }
        try {
            passwordResetService.resetPassword(request.token(), request.password());
            return ResponseEntity.ok(Map.of("message", "Password has been reset successfully."));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
        }
    }

    private ResponseEntity<AuthUserResponse> authenticatedResponse(AppUser user,
                                                                    HttpServletResponse response,
                                                                    HttpStatus status) {
        jwtCookieService.writeToken(response, jwtService.generateToken(user));
        AuthUserResponse.UserSummary summary = userSummary(user);
        return ResponseEntity.status(status).body(new AuthUserResponse(true, summary));
    }

    private AuthUserResponse.UserSummary userSummary(AppUser user) {
        String avatarUrl = user.getAvatarUrl();
        if ("/api/auth/avatar".equals(avatarUrl)) {
            avatarUrl += "?v=" + user.getUpdatedAt().toEpochSecond(java.time.ZoneOffset.UTC);
        }
        return new AuthUserResponse.UserSummary(
                user.getId().toString(),
                user.getName(),
                user.getEmail(),
                user.getProvider(),
                avatarUrl,
                user.getRoles());
    }

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 2, max = 80) String name,
            @NotBlank @Size(min = 12, max = 72) String password) {
    }

    public record LoginRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 72) String password) {
    }

        public record ForgotPasswordRequest(
            @NotBlank @Email @Size(max = 254) String email) {
        }

        public record ResetPasswordRequest(
            @NotBlank String token,
            @NotBlank @Size(min = 12, max = 72) String password) {
        }

    public record UsageRecord(String jobId, String operation, int units,
                              String status, java.time.LocalDateTime createdAt,
                              java.time.LocalDateTime updatedAt) {
    }

    public record UsageSummary(long used, int monthlyLimit, long remaining) {
    }
}
