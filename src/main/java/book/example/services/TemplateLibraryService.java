package book.example.services;

import book.example.Entity.AppUser;
import book.example.Entity.TemplateAuditEvent;
import book.example.Entity.TemplateCatalogEntry;
import book.example.Entity.TemplateVersion;
import book.example.Repository.TemplateAuditEventRepository;
import book.example.Repository.TemplateCatalogRepository;
import book.example.Repository.TemplateVersionRepository;
import book.example.dto.DocumentFormatDefinition;
import book.example.dto.TemplateVersionResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class TemplateLibraryService {
    private static final Set<String> PROJECT_TYPES = Set.of(
            "MINOR_PROJECT", "MAJOR_FINAL_YEAR_PROJECT", "CAPSTONE", "BACHELOR_PROJECT", "BACHELOR_THESIS");
    private static final String UNIVERSAL_TEMPLATE_ID = "universal-template";

    private final TemplateCatalogRepository catalogRepository;
    private final TemplateVersionRepository versionRepository;
    private final TemplateAuditEventRepository auditRepository;
    private final TemplateFileStorageService storage;
    private final MarketplaceFileService securityScanner;
    private final FormatAnalyzer formatAnalyzer;
    private final GlobalTemplateService globalTemplateService;
    private final ObjectMapper objectMapper;

    public TemplateLibraryService(
            TemplateCatalogRepository catalogRepository,
            TemplateVersionRepository versionRepository,
            TemplateAuditEventRepository auditRepository,
            TemplateFileStorageService storage,
            MarketplaceFileService securityScanner,
            FormatAnalyzer formatAnalyzer,
            GlobalTemplateService globalTemplateService,
            ObjectMapper objectMapper) {
        this.catalogRepository = catalogRepository;
        this.versionRepository = versionRepository;
        this.auditRepository = auditRepository;
        this.storage = storage;
        this.securityScanner = securityScanner;
        this.formatAnalyzer = formatAnalyzer;
        this.globalTemplateService = globalTemplateService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Page<TemplateVersionResponse> search(
            String query, String country, String state, String department,
            String degree, String projectType, int page, int size) {
        String type = normalize(projectType);
        if (!type.isEmpty() && !PROJECT_TYPES.contains(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select one of the supported project types.");
        }
        Page<TemplateVersion> results = versionRepository.searchPublished(
                bounded(query, 180), bounded(country, 120), bounded(state, 120),
                bounded(department, 180), bounded(degree, 120), type,
                PageRequest.of(Math.max(0, page), Math.clamp(size, 1, 40),
                        Sort.by(Sort.Direction.ASC, "college").and(Sort.by(Sort.Direction.DESC, "version"))));
        return results.map(version -> TemplateVersionResponse.from(version, objectMapper));
    }

    @Transactional(readOnly = true)
    public TemplateVersionResponse published(String templateId, Integer version) {
        TemplateVersion selected;
        if (version == null) {
            selected = versionRepository.findFirstByCatalog_TemplateIdAndStatusAndAvailableOrderByVersionDesc(
                            templateId, "PUBLISHED", true)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Published template not found."));
        } else {
            selected = versionRepository.findFirstByCatalog_TemplateIdAndVersionAndStatusAndAvailable(
                            templateId, version, "PUBLISHED", true)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Published template version not found."));
        }
        return TemplateVersionResponse.from(selected, objectMapper);
    }

    @Transactional(readOnly = true)
    public List<TemplateVersionResponse> mySubmissions(AppUser seller) {
        if (seller == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in to view template submissions.");
        return versionRepository.findTop100ByCreatedByOrderByCreatedAtDesc(seller.getId()).stream()
                .filter(version -> "COMMUNITY".equals(version.getSourceType()))
                .map(version -> TemplateVersionResponse.from(version, objectMapper))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TemplateVersionResponse> adminList(AppUser admin, String status, String query) {
        requireAdmin(admin);
        String normalizedStatus = normalize(status);
        List<TemplateVersion> versions;
        if (normalizedStatus.isEmpty()) {
            versions = versionRepository.findAll(Sort.by(Sort.Direction.DESC, "updatedAt"));
        } else {
            validateStatus(normalizedStatus);
            versions = versionRepository.findAllByStatusOrderByUpdatedAtDesc(normalizedStatus);
        }
        String normalizedQuery = normalize(query).toLowerCase(Locale.ROOT);
        return versions.stream()
                .filter(version -> normalizedQuery.isEmpty()
                        || contains(version.getCatalog().getTemplateId(), normalizedQuery)
                        || contains(version.getCollege(), normalizedQuery)
                        || contains(version.getUniversity(), normalizedQuery)
                        || contains(version.getDepartment(), normalizedQuery)
                        || contains(version.getCountry(), normalizedQuery)
                        || contains(version.getProjectType(), normalizedQuery))
                .limit(200)
                .map(version -> TemplateVersionResponse.from(version, objectMapper))
                .toList();
    }

    @Transactional(readOnly = true)
    public TemplateVersionResponse adminGet(String templateId, int version, AppUser admin) {
        requireAdmin(admin);
        TemplateVersion found = versionRepository.findByCatalog_TemplateIdAndVersion(templateId, version)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Template version not found."));
        return TemplateVersionResponse.from(found, objectMapper);
    }

    @Transactional
    public TemplateVersionResponse createAdminTemplate(
            AppUser admin, String templateId, String country, String state, String region, String city,
            String college, String university, String aliases, String department, String degree,
            String projectType, String sourceUrl, String license, MultipartFile file, MultipartFile preview) {
        requireAdmin(admin);
        validateMetadata(country, state, city, college, department, degree, projectType);
        String stableId = templateId == null || templateId.isBlank()
                ? makeTemplateId(country, state, college, department, degree, projectType)
                : normalizeTemplateId(templateId);
        if (catalogRepository.existsByTemplateId(stableId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This template ID already exists; create a new version instead.");
        }
        LocalDateTime now = LocalDateTime.now();
        TemplateCatalogEntry catalog = catalogRepository.saveAndFlush(
                new TemplateCatalogEntry(UUID.randomUUID(), stableId, now));
        return createVersion(catalog, 1, admin, "ADMIN", country, state, region, city, college, university,
                aliases, department, degree, projectType, sourceUrl, license, null, false, file, preview, now);
    }

    @Transactional
    public TemplateVersionResponse createVersion(
            AppUser admin, String templateId,
            String country, String state, String region, String city, String college, String university,
            String aliases, String department, String degree, String projectType, String sourceUrl, String license,
            MultipartFile file, MultipartFile preview) {
        requireAdmin(admin);
        validateMetadata(country, state, city, college, department, degree, projectType);
        TemplateCatalogEntry catalog = catalogRepository.findByTemplateId(templateId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Template catalog entry not found."));
        TemplateVersion previous = versionRepository.findFirstByCatalog_TemplateIdOrderByVersionDesc(templateId)
                .orElseThrow(() -> new IllegalStateException("Template catalog has no version history."));
        int nextVersion = previous.getVersion() + 1;
        return createVersion(catalog, nextVersion, admin, "ADMIN", country, state, region, city, college, university,
                aliases, department, degree, projectType, sourceUrl, license, null, false, file, preview,
                LocalDateTime.now());
    }

    @Transactional
    public TemplateVersionResponse submitCommunityTemplate(
            AppUser seller, String country, String state, String region, String city, String college,
            String university, String aliases, String department, String degree, String projectType,
            String sourceUrl, String license, boolean ownershipConfirmed, MultipartFile file, MultipartFile preview) {
        if (seller == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in to submit a template.");
        if (!ownershipConfirmed) throw new IllegalArgumentException("Confirm that you have permission to share this template.");
        validateMetadata(country, state, city, college, department, degree, projectType);
        LocalDateTime now = LocalDateTime.now();
        String stableId = makeTemplateId(country, state, college, department, degree, projectType);
        TemplateCatalogEntry catalog = catalogRepository.findByTemplateId(stableId).orElseGet(() ->
                catalogRepository.saveAndFlush(new TemplateCatalogEntry(UUID.randomUUID(), stableId, now)));
        int nextVersion = versionRepository.countByCatalog_Id(catalog.getId()) + 1;
        String declaration = "User confirmed rights to share template submission.";
        return createVersion(catalog, nextVersion, seller, "COMMUNITY", country, state, region, city, college,
                university, aliases, department, degree, projectType, sourceUrl, license, declaration, true,
                file, preview, now);
    }

    @Transactional
    public TemplateVersionResponse decide(AppUser admin, UUID id, boolean approve, String reason) {
        requireAdmin(admin);
        TemplateVersion version = getVersion(id);
        requireStatus(version, "UNDER_REVIEW");
        String old = version.getStatus();
        if (approve) {
            if (!"CLEAN".equals(version.getSecurityScanStatus()) || !"VALID".equals(version.getValidationStatus())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "A template must pass security scanning and validation before approval.");
            }
            version.setStatus("APPROVED");
            version.setVerificationStatus("VERIFIED");
            version.setVerifiedBy(admin.getId());
            version.setVerifiedAt(LocalDateTime.now());
            audit(version, admin, "TEMPLATE_APPROVED", old, "APPROVED", null);
        } else {
            version.setStatus("REJECTED");
            version.setVerificationStatus("REJECTED");
            audit(version, admin, "TEMPLATE_REJECTED", old, "REJECTED", bounded(reason, 2000));
        }
        version.setUpdatedAt(LocalDateTime.now());
        return TemplateVersionResponse.from(versionRepository.save(version), objectMapper);
    }

    @Transactional
    public TemplateVersionResponse publish(AppUser admin, UUID id) {
        requireAdmin(admin);
        TemplateVersion version = getVersion(id);
        requireStatus(version, "APPROVED");
        if (!"VERIFIED".equals(version.getVerificationStatus())
                || !"CLEAN".equals(version.getSecurityScanStatus())
                || !"VALID".equals(version.getValidationStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only verified, clean, validated templates can be published.");
        }
        version.setStatus("PUBLISHED");
        version.setAvailable(true);
        version.setPublishedAt(LocalDateTime.now());
        version.setUpdatedAt(LocalDateTime.now());
        audit(version, admin, "TEMPLATE_PUBLISHED", "APPROVED", "PUBLISHED", null);
        return TemplateVersionResponse.from(versionRepository.save(version), objectMapper);
    }

    @Transactional
    public TemplateVersionResponse reject(AppUser admin, UUID id, String reason) {
        return decide(admin, id, false, reason);
    }

    @Transactional
    public TemplateVersionResponse suspend(AppUser admin, UUID id, String reason) {
        requireAdmin(admin);
        TemplateVersion version = getVersion(id);
        requireStatus(version, "PUBLISHED");
        version.setStatus("SUSPENDED");
        version.setAvailable(false);
        version.setSuspendedAt(LocalDateTime.now());
        version.setUpdatedAt(LocalDateTime.now());
        audit(version, admin, "TEMPLATE_SUSPENDED", "PUBLISHED", "SUSPENDED", bounded(reason, 2000));
        return TemplateVersionResponse.from(versionRepository.save(version), objectMapper);
    }

    @Transactional
    public TemplateVersionResponse archive(AppUser admin, UUID id, String reason) {
        requireAdmin(admin);
        TemplateVersion version = getVersion(id);
        if (!Set.of("PUBLISHED", "SUSPENDED", "REJECTED").contains(version.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This template version cannot be archived in its current state.");
        }
        String old = version.getStatus();
        version.setStatus("ARCHIVED");
        version.setAvailable(false);
        version.setUpdatedAt(LocalDateTime.now());
        audit(version, admin, "TEMPLATE_ARCHIVED", old, "ARCHIVED", reason == null ? null : bounded(reason, 2000));
        return TemplateVersionResponse.from(versionRepository.save(version), objectMapper);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> auditLog(AppUser admin, UUID id) {
        requireAdmin(admin);
        TemplateVersion version = getVersion(id);
        return auditRepository.findTop200ByTemplateVersion_IdOrderByCreatedAtDesc(version.getId())
                .stream().map(event -> Map.<String, Object>of(
                        "actorId", event.getActorId() == null ? "" : event.getActorId().toString(),
                        "action", event.getAction(),
                        "oldStatus", event.getOldStatus() == null ? "" : event.getOldStatus(),
                        "newStatus", event.getNewStatus() == null ? "" : event.getNewStatus(),
                        "reason", event.getReason() == null ? "" : event.getReason(),
                        "createdAt", event.getCreatedAt()))
                .toList();
    }

    public Path previewFile(String templateId, int version) {
        TemplateVersion selected = versionRepository.findFirstByCatalog_TemplateIdAndVersionAndStatusAndAvailable(
                        templateId, version, "PUBLISHED", true)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Published template preview not found."));
        if (selected.getPreviewStorageKey() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No preview file is available for this template.");
        }
        return storage.resolve(selected.getPreviewStorageKey());
    }

    public Path adminFile(AppUser admin, UUID versionId) {
        requireAdmin(admin);
        TemplateVersion version = getVersion(versionId);
        if (version.getTemplateStorageKey() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No source template file is available.");
        }
        return storage.resolve(version.getTemplateStorageKey());
    }

    private TemplateVersionResponse createVersion(
            TemplateCatalogEntry catalog, int versionNumber, AppUser actor, String sourceType,
            String country, String state, String region, String city, String college, String university,
            String aliases, String department, String degree, String projectType, String sourceUrl, String license,
            String ownershipDeclaration, boolean ownershipConfirmed, MultipartFile file, MultipartFile preview,
            LocalDateTime now) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Upload a DOCX or PDF template file.");
        UUID versionId = UUID.randomUUID();
        TemplateVersion version = new TemplateVersion();
        version.setId(versionId);
        version.setCatalog(catalog);
        version.setVersion(versionNumber);
        version.setCountry(bounded(country, 120));
        version.setState(bounded(state, 120));
        version.setRegion(bounded(region, 120));
        version.setCity(bounded(city, 120));
        version.setCollege(bounded(college, 255));
        version.setUniversity(bounded(university, 255));
        version.setAliasesJson(writeAliases(aliases));
        version.setDepartment(bounded(department, 180));
        version.setDegree(bounded(degree, 120));
        version.setProjectType(normalize(projectType));
        version.setSourceType(sourceType);
        version.setSourceUrl(bounded(sourceUrl, 2048));
        version.setLicense(bounded(license, 500));
        version.setOwnershipDeclaration(ownershipDeclaration);
        version.setOwnershipConfirmedAt(ownershipConfirmed ? now : null);
        version.setCreatedBy(actor == null ? null : actor.getId());
        version.setCreatedAt(now);
        version.setUpdatedAt(now);
        version.setStatus("PROCESSING");
        version.setVerificationStatus(ownershipConfirmed ? "PENDING" : "UNVERIFIED");
        version.setSecurityScanStatus("PENDING");
        version.setValidationStatus("PENDING");
        version.setAvailable(false);
        versionRepository.saveAndFlush(version);
        audit(version, actor, sourceType.equals("COMMUNITY") ? "TEMPLATE_SUBMITTED" : "TEMPLATE_CREATED",
                null, "PROCESSING", null);
        try {
            String key = storage.storeTemplate(catalog.getId(), versionNumber, file);
            version.setTemplateStorageKey(key);
            audit(version, actor, "TEMPLATE_UPLOADED", "PROCESSING", "PROCESSING", null);
            FormatAnalyzer.Analysis analysis = formatAnalyzer.analyze(storage.resolve(key));
            version.setFormatSchemaJson(objectMapper.writeValueAsString(analysis.format()));
            version.setAnalysisMetadataJson(objectMapper.writeValueAsString(analysis.properties()));
            version.setPreviewText(analysis.previewText());
            version.setSecurityScanStatus("CLEAN");
            version.setValidationStatus("VALID");
            audit(version, actor, "TEMPLATE_VALIDATED", "PROCESSING", "UNDER_REVIEW",
                    "Security scan passed; format extraction completed.");
            if (preview != null && !preview.isEmpty()) {
                version.setPreviewStorageKey(storage.storePreview(catalog.getId(), versionNumber, preview));
            }
            version.setStatus("UNDER_REVIEW");
            version.setUpdatedAt(LocalDateTime.now());
            catalog.setUpdatedAt(version.getUpdatedAt());
            catalogRepository.save(catalog);
            return TemplateVersionResponse.from(versionRepository.save(version), objectMapper);
        } catch (RuntimeException exception) {
            storage.removeVersion(catalog.getId(), versionNumber);
            throw exception;
        } catch (Exception exception) {
            storage.removeVersion(catalog.getId(), versionNumber);
            throw new IllegalStateException("Template analysis metadata could not be stored.", exception);
        }
    }

    private String writeAliases(String aliases) {
        List<String> values = aliases == null ? List.of() : aliases.lines()
                .flatMap(line -> List.of(line.split("[,;]")).stream())
                .map(String::trim).filter(value -> !value.isEmpty()).distinct().limit(40).toList();
        try {
            return objectMapper.writeValueAsString(values);
        } catch (Exception exception) {
            throw new IllegalStateException("Template aliases could not be stored.", exception);
        }
    }

    private String makeTemplateId(String country, String state, String college, String department, String degree, String type) {
        String base = slug(String.join("-", country, state, college, department, degree, type));
        if (base.isBlank()) throw new IllegalArgumentException("Template metadata must produce a valid ID.");
        return base.length() <= 180 ? base : base.substring(0, 180);
    }

    private String normalizeTemplateId(String value) {
        String result = slug(value);
        if (result.isBlank() || result.length() > 180) throw new IllegalArgumentException("Template ID is invalid.");
        return result;
    }

    private String slug(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    private void validateMetadata(
            String country, String state, String city, String college, String department,
            String degree, String projectType) {
        if (bounded(country, 120) == null || bounded(college, 255) == null
                || bounded(department, 180) == null || bounded(degree, 120) == null) {
            throw new IllegalArgumentException("Country, college, department, and degree are required.");
        }
        String type = normalize(projectType);
        if (!PROJECT_TYPES.contains(type)) {
            throw new IllegalArgumentException("Select a supported project type.");
        }
    }

    private String bounded(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        if (trimmed.length() > max) throw new IllegalArgumentException("Template metadata exceeds the allowed length.");
        return trimmed;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private void validateStatus(String status) {
        try {
            TemplateVersion.Status.valueOf(status);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown template status.");
        }
    }

    private void requireAdmin(AppUser user) {
        if (user == null || user.getRoles().stream().noneMatch("ROLE_ADMIN"::equals)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Template administration requires ROLE_ADMIN.");
        }
    }

    private TemplateVersion getVersion(UUID id) {
        return versionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Template version not found."));
    }

    private void requireStatus(TemplateVersion version, String expected) {
        if (!expected.equals(version.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Template must be " + expected + " before this action.");
        }
    }

    private void audit(TemplateVersion version, AppUser actor, String action,
                       String oldStatus, String newStatus, String reason) {
        auditRepository.save(new TemplateAuditEvent(
                version, actor == null ? null : actor.getId(), action, oldStatus, newStatus, reason));
    }
}
