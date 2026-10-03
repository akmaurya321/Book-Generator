package book.example.controllers;

import book.example.Entity.AppUser;
import book.example.dto.TemplateVersionResponse;
import book.example.services.TemplateLibraryService;
import jakarta.validation.constraints.Size;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class TemplateLibraryController {
    private final TemplateLibraryService templateLibraryService;

    public TemplateLibraryController(TemplateLibraryService templateLibraryService) {
        this.templateLibraryService = templateLibraryService;
    }

    @GetMapping("/templates")
    public Map<String, Object> search(
            @RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "") String country,
            @RequestParam(defaultValue = "") String state,
            @RequestParam(defaultValue = "") String department,
            @RequestParam(defaultValue = "") String degree,
            @RequestParam(defaultValue = "") String projectType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var results = templateLibraryService.search(query, country, state, department, degree, projectType, page, size);
        return Map.of("items", results.getContent(), "page", results.getNumber(),
                "size", results.getSize(), "totalItems", results.getTotalElements(),
                "totalPages", results.getTotalPages());
    }

    @GetMapping("/templates/{templateId}")
    public TemplateVersionResponse getPublished(
            @PathVariable String templateId,
            @RequestParam(required = false) Integer version) {
        return templateLibraryService.published(templateId, version);
    }

    @GetMapping("/templates/{templateId}/preview/{version}")
    public ResponseEntity<Resource> preview(
            @PathVariable String templateId,
            @PathVariable int version) {
        Path file = templateLibraryService.previewFile(templateId, version);
        String name = file.getFileName().toString().toLowerCase();
        MediaType media = name.endsWith(".pdf") ? MediaType.APPLICATION_PDF
                : name.endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noCache())
                .contentType(media).body(new FileSystemResource(file));
    }

    @PostMapping("/template-submissions")
    public ResponseEntity<?> submitCommunity(
            @AuthenticationPrincipal AppUser seller,
            @RequestParam String country,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String city,
            @RequestParam String college,
            @RequestParam(required = false) String university,
            @RequestParam(required = false) String aliases,
            @RequestParam String department,
            @RequestParam String degree,
            @RequestParam String projectType,
            @RequestParam(required = false) String sourceUrl,
            @RequestParam(required = false) String license,
            @RequestParam(defaultValue = "false") boolean ownershipConfirmed,
            @RequestParam("template") MultipartFile template,
            @RequestParam(required = false) MultipartFile preview) {
        try {
            TemplateVersionResponse response = templateLibraryService.submitCommunityTemplate(
                    seller, country, state, region, city, college, university, aliases, department, degree,
                    projectType, sourceUrl, license, ownershipConfirmed, template, preview);
            return ResponseEntity.status(201).body(response);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
        }
    }

    @GetMapping("/template-submissions/my")
    public List<TemplateVersionResponse> mySubmissions(@AuthenticationPrincipal AppUser seller) {
        return templateLibraryService.mySubmissions(seller);
    }

    @GetMapping("/admin/templates")
    public List<TemplateVersionResponse> adminList(
            @AuthenticationPrincipal AppUser admin,
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "") String query) {
        return templateLibraryService.adminList(admin, status, query);
    }

    @PostMapping(value = "/admin/templates", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> create(
            @AuthenticationPrincipal AppUser admin,
            @RequestParam(required = false) String templateId,
            @RequestParam String country,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String city,
            @RequestParam String college,
            @RequestParam(required = false) String university,
            @RequestParam(required = false) String aliases,
            @RequestParam String department,
            @RequestParam String degree,
            @RequestParam String projectType,
            @RequestParam(required = false) String sourceUrl,
            @RequestParam(required = false) String license,
            @RequestParam("template") MultipartFile template,
            @RequestParam(required = false) MultipartFile preview) {
        try {
            return ResponseEntity.status(201).body(templateLibraryService.createAdminTemplate(
                    admin, templateId, country, state, region, city, college, university, aliases,
                    department, degree, projectType, sourceUrl, license, template, preview));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
        }
    }

    @PostMapping(value = "/admin/templates/{templateId}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> createVersion(
            @AuthenticationPrincipal AppUser admin,
            @PathVariable String templateId,
            @RequestParam String country,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String city,
            @RequestParam String college,
            @RequestParam(required = false) String university,
            @RequestParam(required = false) String aliases,
            @RequestParam String department,
            @RequestParam String degree,
            @RequestParam String projectType,
            @RequestParam(required = false) String sourceUrl,
            @RequestParam(required = false) String license,
            @RequestParam("template") MultipartFile template,
            @RequestParam(required = false) MultipartFile preview) {
        try {
            return ResponseEntity.status(201).body(templateLibraryService.createVersion(
                    admin, templateId, country, state, region, city, college, university, aliases,
                    department, degree, projectType, sourceUrl, license, template, preview));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
        }
    }

    @GetMapping("/admin/templates/{id}/audit")
    public List<Map<String, Object>> audit(@AuthenticationPrincipal AppUser admin, @PathVariable UUID id) {
        return templateLibraryService.auditLog(admin, id);
    }

    @GetMapping("/admin/templates/{id}/source")
    public ResponseEntity<Resource> source(@AuthenticationPrincipal AppUser admin, @PathVariable UUID id) {
        Path file = templateLibraryService.adminFile(admin, id);
        String contentType = file.getFileName().toString().endsWith(".pdf")
                ? MediaType.APPLICATION_PDF_VALUE
                : "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(file.getFileName().toString()).build().toString())
                .body(new FileSystemResource(file));
    }

    @PostMapping("/admin/templates/{id}/approve")
    public TemplateVersionResponse approve(@AuthenticationPrincipal AppUser admin, @PathVariable UUID id) {
        return templateLibraryService.decide(admin, id, true, null);
    }

    @PostMapping("/admin/templates/{id}/reject")
    public ResponseEntity<?> reject(
            @AuthenticationPrincipal AppUser admin,
            @PathVariable UUID id,
            @RequestParam @Size(max = 2000) String reason) {
        try {
            return ResponseEntity.ok(templateLibraryService.reject(admin, id, reason));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
        }
    }

    @PostMapping("/admin/templates/{id}/publish")
    public TemplateVersionResponse publish(@AuthenticationPrincipal AppUser admin, @PathVariable UUID id) {
        return templateLibraryService.publish(admin, id);
    }

    @PostMapping("/admin/templates/{id}/suspend")
    public ResponseEntity<?> suspend(
            @AuthenticationPrincipal AppUser admin,
            @PathVariable UUID id,
            @RequestParam @Size(max = 2000) String reason) {
        try {
            return ResponseEntity.ok(templateLibraryService.suspend(admin, id, reason));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
        }
    }

    @PostMapping("/admin/templates/{id}/archive")
    public TemplateVersionResponse archive(
            @AuthenticationPrincipal AppUser admin,
            @PathVariable UUID id,
            @RequestParam(required = false) @Size(max = 2000) String reason) {
        return templateLibraryService.archive(admin, id, reason);
    }
}
