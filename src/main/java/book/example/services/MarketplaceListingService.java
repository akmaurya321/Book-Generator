package book.example.services;

import book.example.Entity.AppUser;
import book.example.Entity.DocumentationJob;
import book.example.Entity.MarketplaceAuditEvent;
import book.example.Entity.MarketplaceListing;
import book.example.Repository.JobRepository;
import book.example.Repository.MarketplaceAuditEventRepository;
import book.example.Repository.MarketplaceListingRepository;
import book.example.dto.MarketplaceListingResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class MarketplaceListingService {
    private static final Set<String> LISTING_TYPES = Set.of(
            "PROJECT_AND_DOCUMENTATION", "PROJECT_ONLY", "DOCUMENTATION_ONLY");
    private static final Set<String> ADMIN_ROLES = Set.of("ROLE_ADMIN");

    private final MarketplaceListingRepository listingRepository;
    private final MarketplaceAuditEventRepository auditRepository;
    private final JobRepository jobRepository;
    private final MarketplaceFileService fileService;

    public MarketplaceListingService(
            MarketplaceListingRepository listingRepository,
            MarketplaceAuditEventRepository auditRepository,
            JobRepository jobRepository,
            MarketplaceFileService fileService) {
        this.listingRepository = listingRepository;
        this.auditRepository = auditRepository;
        this.jobRepository = jobRepository;
        this.fileService = fileService;
    }

    @Transactional
    public MarketplaceListingResponse submit(
            AppUser seller,
            String listingType,
            String title,
            String description,
            String category,
            String technologies,
            boolean ownershipConfirmed,
            MultipartFile project,
            MultipartFile document) {
        validateMetadata(listingType, title, description, category, technologies, ownershipConfirmed);
        boolean hasProject = project != null && !project.isEmpty();
        boolean hasDocument = document != null && !document.isEmpty();
        validateFilesForType(listingType, hasProject, hasDocument);

        UUID id = UUID.randomUUID();
        MarketplaceListing listing = new MarketplaceListing();
        initialize(listing, id, seller, listingType, title, description, category, technologies, "COMMUNITY");
        listing.setOwnershipConfirmed(true);
        listing.setOwnershipConfirmedAt(LocalDateTime.now());
        listing.setStatus("PROCESSING");
        listingRepository.saveAndFlush(listing);

        try {
            if (hasProject) {
                listing.setProjectFilePath(fileService.storeProject(id, project).toString());
            }
            if (hasDocument) {
                listing.setDocumentFilePath(fileService.storeDocument(id, document).toString());
            }
            detectDocGenOrigin(listing, document);
            listing.setPreviewText(buildPreview(listing));
            listing.setStatus("UNDER_REVIEW");
            listing.setSubmittedAt(LocalDateTime.now());
            listing.setUpdatedAt(LocalDateTime.now());
            listingRepository.save(listing);
            audit(listing, seller, "SUBMITTED", "Ownership and redistribution rights confirmed.");
            return MarketplaceListingResponse.from(listing, true);
        } catch (RuntimeException exception) {
            fileService.removeListingFiles(id);
            throw exception;
        }
    }

    @Transactional
    public MarketplaceListingResponse publishGeneratedDocument(
            AppUser seller,
            String jobId,
            String title,
            String description,
            String category,
            String technologies,
            boolean ownershipConfirmed) {
        validateMetadata("DOCUMENTATION_ONLY", title, description, category, technologies, ownershipConfirmed);
        DocumentationJob job = jobRepository.findByJobIdAndOwnerId(jobId, seller.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Generated project not found."));
        if (!"COMPLETED".equals(job.getStatus()) || job.getDocumentPath() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only completed documentation can be shared.");
        }
        Path document = fileService.resolveStoredFile(job.getDocumentPath());
        if (!Files.isRegularFile(document)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The generated document is no longer available.");
        }

        UUID id = UUID.randomUUID();
        MarketplaceListing listing = new MarketplaceListing();
        initialize(listing, id, seller, "DOCUMENTATION_ONLY", title, description, category, technologies, "DOCGEN");
        listing.setSourceJobId(jobId);
        listing.setProjectName(job.getProjectName());
        listing.setDocumentFilePath(document.toString());
        listing.setOwnershipConfirmed(true);
        listing.setOwnershipConfirmedAt(LocalDateTime.now());
        listing.setStatus("UNDER_REVIEW");
        listing.setSubmittedAt(LocalDateTime.now());
        listing.setPreviewText(buildPreview(listing));
        listingRepository.save(listing);
        audit(listing, seller, "SUBMITTED", "Published from completed DocGen generation " + jobId);
        return MarketplaceListingResponse.from(listing, true);
    }

    @Transactional(readOnly = true)
    public Page<MarketplaceListingResponse> browse(String query, String category, String originType, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(0, page), Math.clamp(size, 1, 40),
                Sort.by(Sort.Direction.DESC, "publishedAt"));
        String normalizedQuery = query == null ? "" : query.trim();
        String normalizedCategory = category == null ? "" : category.trim();
        String normalizedOrigin = originType == null ? "" : originType.trim().toUpperCase(Locale.ROOT);
        if (!normalizedOrigin.isEmpty() && !Set.of("DOCGEN", "COMMUNITY").contains(normalizedOrigin)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select a valid Marketplace origin filter.");
        }
        Page<MarketplaceListing> results = listingRepository.searchPublished(
                normalizedQuery, normalizedCategory, normalizedOrigin, pageable);
        return results.map(listing -> MarketplaceListingResponse.from(listing, false));
    }

    @Transactional(readOnly = true)
    public List<String> categories() {
        return listingRepository.findPublishedCategories();
    }

    @Transactional(readOnly = true)
    public MarketplaceListingResponse publicListing(String slug) {
        return listingRepository.findBySlugAndStatus(slug, "PUBLISHED")
                .map(listing -> MarketplaceListingResponse.from(listing, false))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Marketplace listing not found."));
    }

    @Transactional(readOnly = true)
    public List<MarketplaceListingResponse> sellerListings(AppUser seller) {
        return listingRepository.findTop100ByOwner_IdOrderByUpdatedAtDesc(seller.getId())
                .stream().map(listing -> MarketplaceListingResponse.from(listing, true)).toList();
    }

    @Transactional(readOnly = true)
    public List<MarketplaceListingResponse> reviewQueue(AppUser admin) {
        requireAdmin(admin);
        return listingRepository.findTop100ByStatusOrderBySubmittedAtAsc("UNDER_REVIEW")
                .stream().map(listing -> MarketplaceListingResponse.from(listing, true)).toList();
    }

    @Transactional
    public MarketplaceListingResponse moderate(AppUser admin, UUID id, boolean approve, String reason) {
        requireAdmin(admin);
        MarketplaceListing listing = listingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Marketplace listing not found."));
        if (!"UNDER_REVIEW".equals(listing.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This listing is not awaiting review.");
        }
        LocalDateTime now = LocalDateTime.now();
        listing.setStatus(approve ? "APPROVED" : "REJECTED");
        listing.setRejectionReason(approve ? null : boundedReason(reason));
        listing.setPublishedAt(null);
        listing.setUpdatedAt(now);
        audit(listing, admin, approve ? "APPROVED" : "REJECTED", approve ? null : listing.getRejectionReason());
        return MarketplaceListingResponse.from(listingRepository.save(listing), true);
    }

    @Transactional
    public MarketplaceListingResponse publishApproved(AppUser seller, UUID id) {
        MarketplaceListing listing = listingRepository.findByIdAndOwner_Id(id, seller.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Marketplace listing not found."));
        if (!"APPROVED".equals(listing.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only approved listings can be published.");
        }
        LocalDateTime now = LocalDateTime.now();
        listing.setStatus("PUBLISHED");
        listing.setPublishedAt(now);
        listing.setUpdatedAt(now);
        audit(listing, seller, "PUBLISHED", "Seller published the approved listing.");
        return MarketplaceListingResponse.from(listingRepository.save(listing), true);
    }

    @Transactional
    public MarketplaceListingResponse archive(AppUser seller, UUID id) {
        MarketplaceListing listing = listingRepository.findByIdAndOwner_Id(id, seller.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Marketplace listing not found."));
        if (!"PUBLISHED".equals(listing.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only published listings can be archived.");
        }
        listing.setStatus("ARCHIVED");
        listing.setUpdatedAt(LocalDateTime.now());
        audit(listing, seller, "ARCHIVED", "Seller archived the listing.");
        return MarketplaceListingResponse.from(listingRepository.save(listing), true);
    }

    @Transactional
    public MarketplaceListingResponse suspend(AppUser admin, UUID id, String reason) {
        requireAdmin(admin);
        MarketplaceListing listing = listingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Marketplace listing not found."));
        if (!"PUBLISHED".equals(listing.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only published listings can be suspended.");
        }
        listing.setStatus("SUSPENDED");
        listing.setRejectionReason(boundedReason(reason));
        listing.setUpdatedAt(LocalDateTime.now());
        audit(listing, admin, "SUSPENDED", listing.getRejectionReason());
        return MarketplaceListingResponse.from(listingRepository.save(listing), true);
    }

    @Transactional
    public MarketplaceListingResponse updateMetadata(
            AppUser seller,
            UUID id,
            String title,
            String description,
            String category,
            String technologies) {
        MarketplaceListing listing = listingRepository.findByIdAndOwner_Id(id, seller.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Marketplace listing not found."));
        if (!Set.of("REJECTED", "PUBLISHED").contains(listing.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This listing cannot be edited in its current state.");
        }
        validateText(title, description, category, technologies);
        listing.setTitle(title.trim());
        listing.setDescription(description.trim());
        listing.setCategory(category.trim());
        listing.setTechnologies(trimToNull(technologies));
        listing.setSlug(uniqueSlug(title));
        listing.setStatus("UNDER_REVIEW");
        listing.setSubmittedAt(LocalDateTime.now());
        listing.setPublishedAt(null);
        listing.setRejectionReason(null);
        listing.setPreviewText(buildPreview(listing));
        listing.setUpdatedAt(LocalDateTime.now());
        audit(listing, seller, "RESUBMITTED", "Listing metadata changed; awaiting moderation.");
        return MarketplaceListingResponse.from(listingRepository.save(listing), true);
    }

    @Transactional(readOnly = true)
    public Path download(String slug, String kind) {
        MarketplaceListing listing = listingRepository.findBySlugAndStatus(slug, "PUBLISHED")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Marketplace listing not found."));
        String path = switch (kind) {
            case "project" -> listing.getProjectFilePath();
            case "documentation" -> listing.getDocumentFilePath();
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown download type.");
        };
        try {
            return fileService.resolveStoredFile(path);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "The requested file is unavailable.", exception);
        }
    }

    private void detectDocGenOrigin(MarketplaceListing listing, MultipartFile document) {
        if (document == null || document.isEmpty()) return;
        byte[] uploaded;
        try {
            uploaded = document.getBytes();
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to verify the uploaded document origin.", exception);
        }
        String uploadedHash = sha256(uploaded);
        for (DocumentationJob job : jobRepository.findTop50ByOwnerIdAndStatusOrderByUpdatedAtDesc(
                listing.getOwner().getId(), "COMPLETED")) {
            for (String generatedPath : new String[]{job.getDocumentPath(), job.getPdfPath()}) {
                if (generatedPath == null || generatedPath.isBlank()) continue;
                try {
                    Path file = fileService.resolveStoredFile(generatedPath);
                    if (Files.isRegularFile(file) && uploadedHash.equals(sha256(Files.readAllBytes(file)))) {
                        listing.setOriginType("DOCGEN");
                        listing.setSourceJobId(job.getJobId());
                        listing.setProjectName(job.getProjectName());
                        return;
                    }
                } catch (IOException | IllegalArgumentException | IllegalStateException ignored) {
                    // A stale artifact is not a match; continue checking the user's recent jobs.
                }
            }
        }
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private void validateMetadata(
            String listingType,
            String title,
            String description,
            String category,
            String technologies,
            boolean ownershipConfirmed) {
        if (!LISTING_TYPES.contains(listingType)) {
            throw new IllegalArgumentException("Select a supported Marketplace content type.");
        }
        validateText(title, description, category, technologies);
        if (!ownershipConfirmed) {
            throw new IllegalArgumentException("Confirm that you have rights to share this content.");
        }
    }

    private void validateFilesForType(String type, boolean hasProject, boolean hasDocument) {
        if (type.equals("PROJECT_AND_DOCUMENTATION") && !(hasProject && hasDocument)
                || type.equals("PROJECT_ONLY") && (!hasProject || hasDocument)
                || type.equals("DOCUMENTATION_ONLY") && (!hasDocument || hasProject)) {
            throw new IllegalArgumentException("Uploaded files do not match the selected content type.");
        }
    }

    private void validateText(String title, String description, String category, String technologies) {
        if (title == null || title.isBlank() || title.trim().length() > 180) {
            throw new IllegalArgumentException("Title is required and must be 180 characters or fewer.");
        }
        if (description == null || description.isBlank() || description.trim().length() > 5000) {
            throw new IllegalArgumentException("Description is required and must be 5,000 characters or fewer.");
        }
        if (category == null || category.isBlank() || category.trim().length() > 80) {
            throw new IllegalArgumentException("Category is required and must be 80 characters or fewer.");
        }
        if (technologies != null && technologies.length() > 2000) {
            throw new IllegalArgumentException("Technology metadata is too long.");
        }
    }

    private void initialize(
            MarketplaceListing listing,
            UUID id,
            AppUser seller,
            String type,
            String title,
            String description,
            String category,
            String technologies,
            String origin) {
        LocalDateTime now = LocalDateTime.now();
        listing.setId(id);
        listing.setOwner(seller);
        listing.setSlug(uniqueSlug(title));
        listing.setListingType(type);
        listing.setOriginType(origin);
        listing.setTitle(title.trim());
        listing.setDescription(description.trim());
        listing.setCategory(category.trim());
        listing.setTechnologies(trimToNull(technologies));
        listing.setCreatedAt(now);
        listing.setUpdatedAt(now);
    }

    private String uniqueSlug(String title) {
        String slugBase = title.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (slugBase.isBlank()) slugBase = "project";
        slugBase = slugBase.substring(0, Math.min(130, slugBase.length()));
        return slugBase + "-" + UUID.randomUUID().toString().substring(0, 12);
    }

    private String buildPreview(MarketplaceListing listing) {
        StringBuilder preview = new StringBuilder(listing.getDescription().replaceAll("\\s+", " ").trim());
        try {
            if (listing.getDocumentFilePath() != null) {
                preview.append("\n\nDocument excerpt:\n")
                        .append(fileService.createDocumentPreview(
                                fileService.resolveStoredFile(listing.getDocumentFilePath())));
            } else if (listing.getProjectFilePath() != null) {
                preview.append("\n\n")
                        .append(fileService.createProjectPreview(
                                fileService.resolveStoredFile(listing.getProjectFilePath())));
            }
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new IllegalStateException("A safe Marketplace preview could not be generated.", exception);
        }
        String value = preview.toString();
        return value.length() <= 2600 ? value : value.substring(0, 2600);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String boundedReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Provide a reason when rejecting a listing.");
        }
        return reason.trim().substring(0, Math.min(1000, reason.trim().length()));
    }

    private void requireAdmin(AppUser user) {
        if (user == null || user.getRoles().stream().noneMatch(ADMIN_ROLES::contains)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Marketplace moderator access is required.");
        }
    }

    private void audit(MarketplaceListing listing, AppUser actor, String action, String details) {
        auditRepository.save(new MarketplaceAuditEvent(listing, actor, action, details));
    }
}
