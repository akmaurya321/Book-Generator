package book.example.controllers;

import book.example.Entity.AppUser;
import book.example.dto.MarketplaceListingResponse;
import book.example.services.MarketplaceFileService;
import book.example.services.MarketplaceListingService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/marketplace")
public class MarketplaceController {
    private static final String DOCX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private final MarketplaceListingService listingService;

    public MarketplaceController(MarketplaceListingService listingService) {
        this.listingService = listingService;
    }

    @GetMapping
    public Map<String, Object> browse(
            @RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "") String category,
            @RequestParam(defaultValue = "") String originType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<MarketplaceListingResponse> listings = listingService.browse(query, category, originType, page, size);
        return Map.of(
                "items", listings.getContent(),
                "page", listings.getNumber(),
                "size", listings.getSize(),
                "totalItems", listings.getTotalElements(),
                "totalPages", listings.getTotalPages());
    }

    @GetMapping("/categories")
    public List<String> categories() {
        return listingService.categories();
    }

    @GetMapping("/{slug}")
    public MarketplaceListingResponse getPublicListing(@PathVariable String slug) {
        return listingService.publicListing(slug);
    }

    @GetMapping("/{slug}/download/{kind}")
    public ResponseEntity<Resource> download(@PathVariable String slug, @PathVariable String kind) {
        Path file = listingService.download(slug, kind);
        String filename = kind.equals("project") ? "project.zip" : file.getFileName().toString();
        MediaType contentType = kind.equals("project")
                ? MediaType.APPLICATION_OCTET_STREAM
                : filename.endsWith(".pdf") ? MediaType.APPLICATION_PDF : MediaType.parseMediaType(DOCX_CONTENT_TYPE);
        return ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(new FileSystemResource(file));
    }

    @PostMapping(value = "/seller/listings", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> submit(
            @AuthenticationPrincipal AppUser seller,
            @RequestParam String listingType,
            @RequestParam @NotBlank @Size(max = 180) String title,
            @RequestParam @NotBlank @Size(max = 5000) String description,
            @RequestParam @NotBlank @Size(max = 80) String category,
            @RequestParam(required = false) @Size(max = 2000) String technologies,
            @RequestParam(defaultValue = "false") boolean ownershipConfirmed,
            @RequestParam(required = false) MultipartFile project,
            @RequestParam(required = false) MultipartFile document) {
        try {
            MarketplaceListingResponse listing = listingService.submit(
                    seller, listingType, title, description, category, technologies,
                    ownershipConfirmed, project, document);
            return ResponseEntity.status(HttpStatus.CREATED).body(listing);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
        }
    }

    @PostMapping("/seller/docgen/{jobId}")
    public ResponseEntity<?> publishGeneratedDocument(
            @AuthenticationPrincipal AppUser seller,
            @PathVariable String jobId,
            @Valid @org.springframework.web.bind.annotation.RequestBody GeneratedListingRequest request) {
        try {
            MarketplaceListingResponse listing = listingService.publishGeneratedDocument(
                    seller, jobId, request.title(), request.description(), request.category(),
                    request.technologies(), request.ownershipConfirmed());
            return ResponseEntity.status(HttpStatus.CREATED).body(listing);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
        }
    }

    @GetMapping("/seller/listings")
    public List<MarketplaceListingResponse> sellerListings(@AuthenticationPrincipal AppUser seller) {
        return listingService.sellerListings(seller);
    }

    @PutMapping("/seller/listings/{id}")
    public ResponseEntity<?> updateMetadata(
            @AuthenticationPrincipal AppUser seller,
            @PathVariable UUID id,
            @Valid @org.springframework.web.bind.annotation.RequestBody UpdateListingRequest request) {
        try {
            return ResponseEntity.ok(listingService.updateMetadata(
                    seller, id, request.title(), request.description(), request.category(), request.technologies()));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
        }
    }

    @PostMapping("/seller/listings/{id}/publish")
    public MarketplaceListingResponse publishApproved(
            @AuthenticationPrincipal AppUser seller,
            @PathVariable UUID id) {
        return listingService.publishApproved(seller, id);
    }

    @PostMapping("/seller/listings/{id}/archive")
    public MarketplaceListingResponse archive(
            @AuthenticationPrincipal AppUser seller,
            @PathVariable UUID id) {
        return listingService.archive(seller, id);
    }

    @GetMapping("/moderation/queue")
    public List<MarketplaceListingResponse> moderationQueue(@AuthenticationPrincipal AppUser admin) {
        return listingService.reviewQueue(admin);
    }

    @PostMapping("/moderation/{id}/decision")
    public ResponseEntity<?> moderate(
            @AuthenticationPrincipal AppUser admin,
            @PathVariable UUID id,
            @Valid @org.springframework.web.bind.annotation.RequestBody ModerationRequest request) {
        try {
            return ResponseEntity.ok(listingService.moderate(admin, id, request.approve(), request.reason()));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
        } catch (ResponseStatusException exception) {
            return ResponseEntity.status(exception.getStatusCode())
                    .body(Map.of("message", exception.getReason() == null ? "Request rejected." : exception.getReason()));
        }
    }

    @PostMapping("/moderation/{id}/suspend")
    public ResponseEntity<?> suspend(
            @AuthenticationPrincipal AppUser admin,
            @PathVariable UUID id,
            @Valid @org.springframework.web.bind.annotation.RequestBody ModerationRequest request) {
        try {
            return ResponseEntity.ok(listingService.suspend(admin, id, request.reason()));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
        }
    }

    public record GeneratedListingRequest(
            @NotBlank @Size(max = 180) String title,
            @NotBlank @Size(max = 5000) String description,
            @NotBlank @Size(max = 80) String category,
            @Size(max = 2000) String technologies,
            boolean ownershipConfirmed) {
    }

    public record UpdateListingRequest(
            @NotBlank @Size(max = 180) String title,
            @NotBlank @Size(max = 5000) String description,
            @NotBlank @Size(max = 80) String category,
            @Size(max = 2000) String technologies) {
    }

    public record ModerationRequest(boolean approve, String reason) {
    }
}
