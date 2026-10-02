package book.example.controllers;

import book.example.Entity.DocumentationJob;
import book.example.Entity.AppUser;
import book.example.Repository.JobRepository;
import book.example.dto.DocumentationStatusResponse;
import book.example.dto.GenerateDocumentationRequest;
import book.example.dto.ProjectAnalysisResponse;
import book.example.dto.DocumentationPlan;
import book.example.dto.GenerationCheckpoint;
import tools.jackson.databind.ObjectMapper;
import book.example.services.GlobalTemplateService;
import book.example.services.DocumentationOrchestrator;
import book.example.services.UserAssetService;
import book.example.services.GenerationPreviewOrderer;
import book.example.services.JobExpirationService;

import org.springframework.core.io.Resource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@RestController
@RequestMapping("/api/documentation")
public class DocumentationFileController {

    private final JobRepository jobRepository;

    private final DocumentationOrchestrator documentationOrchestrator;
    private final GlobalTemplateService globalTemplateService;
    private final UserAssetService userAssetService;
    private final ObjectMapper objectMapper;
    private final GenerationPreviewOrderer generationPreviewOrderer;
    private final JobExpirationService jobExpirationService;


    public DocumentationFileController(
            JobRepository jobRepository,
            DocumentationOrchestrator documentationOrchestrator,
            GlobalTemplateService globalTemplateService,
            UserAssetService userAssetService,
            ObjectMapper objectMapper,
            GenerationPreviewOrderer generationPreviewOrderer,
            JobExpirationService jobExpirationService) {

        this.jobRepository = jobRepository;
        this.documentationOrchestrator = documentationOrchestrator;
        this.globalTemplateService = globalTemplateService;
        this.userAssetService = userAssetService;
        this.objectMapper = objectMapper;
        this.generationPreviewOrderer = generationPreviewOrderer;
        this.jobExpirationService = jobExpirationService;
    }

    @GetMapping("/template")
    public ResponseEntity<?> getGlobalTemplate(
            @AuthenticationPrincipal AppUser authenticatedUser) {
        return ResponseEntity.ok(globalTemplateService.getTemplate());
    }

    /**
     * Stage 1 of the V1 flow: scan the chosen source and return
     * evidence-based section recommendations. No LLM or document generation
     * is triggered here.
     */
    @PostMapping(
            value = "/analyze",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<?> analyzeProject(
            @AuthenticationPrincipal AppUser authenticatedUser,
            @RequestParam(value = "githubUrl", required = false) String githubUrl,
            @RequestParam(value = "projectName", required = false) String projectName,
            @RequestPart(value = "projectZip", required = false) MultipartFile projectZip) {
        try {
            ProjectAnalysisResponse response = documentationOrchestrator.analyzeProject(
                    githubUrl,
                    projectName,
                    projectZip,
                    authenticatedUser.getId()
            );
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
        } catch (IllegalStateException exception) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("message", exception.getMessage()));
        }
    }

    @PostMapping("/{jobId}/context-suggestions")
    public ResponseEntity<?> suggestStudentContext(
            @AuthenticationPrincipal AppUser authenticatedUser,
            @PathVariable String jobId) {
        try {
            return ResponseEntity.ok(documentationOrchestrator.suggestStudentContext(jobId, authenticatedUser.getId()));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", exception.getMessage()));
        } catch (IllegalStateException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", exception.getMessage()));
        }
    }

    @GetMapping("/jobs")
    public ResponseEntity<?> listJobs(
            @AuthenticationPrincipal AppUser authenticatedUser) {
        var jobs = jobRepository.findTop50ByOwnerIdOrderByUpdatedAtDesc(authenticatedUser.getId())
                .stream()
                .map(job -> {
                    Map<String, Object> item = new java.util.LinkedHashMap<>();
                    item.put("jobId", job.getJobId());
                    item.put("projectName", job.getProjectName());
                    item.put("status", job.getStatus());
                    item.put("progress", getProgress(job));
                    item.put("type", job.getType());
                    item.put("createdAt", job.getCreatedAt());
                    item.put("updatedAt", job.getUpdatedAt());
                    item.put("hasDocx", job.getDocumentPath() != null && !job.getDocumentPath().isBlank() && Files.exists(Path.of(job.getDocumentPath())));
                    item.put("hasPdf", job.getPdfPath() != null && !job.getPdfPath().isBlank() && Files.exists(Path.of(job.getPdfPath())));
                    return item;
                })
                .toList();
        return ResponseEntity.ok(Map.of("jobs", jobs));
    }

    @GetMapping("/{jobId}/analysis")
    public ResponseEntity<?> getAnalysis(
            @AuthenticationPrincipal AppUser authenticatedUser,
            @PathVariable String jobId) {
        try {
            return ResponseEntity.ok(documentationOrchestrator.getAnalysis(jobId, authenticatedUser.getId()));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", exception.getMessage()));
        } catch (IllegalStateException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", exception.getMessage()));
        }
    }

    /**
     * Stage 2 of the V1 flow: persist the user's final section selections and
     * centralized details, then start the existing asynchronous generator.
     */
    @PostMapping("/{jobId}/generate-v1")
    public ResponseEntity<?> generateV1(
            @AuthenticationPrincipal AppUser authenticatedUser,
            @PathVariable String jobId,
            @RequestBody GenerateDocumentationRequest request) {
        try {
            return ResponseEntity.ok(documentationOrchestrator.startFinalGeneration(
                    jobId,
                    authenticatedUser.getId(),
                    request
            ));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
        } catch (IllegalStateException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", exception.getMessage()));
        }
    }

    @PostMapping(value = "/{jobId}/assets/front-matter", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadFrontMatterAsset(
            @AuthenticationPrincipal AppUser authenticatedUser,
            @PathVariable String jobId,
            @RequestParam("field") String field,
            @RequestParam("image") MultipartFile image) {
        DocumentationJob job = getJob(jobId, authenticatedUser.getId());
        if (!isConfigurationEditable(job.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Front-matter assets can only be uploaded before generation starts.");
        }
        java.util.Set<String> allowed = java.util.Set.of("collegeLogo", "guideSignature", "hodSignature");
        if (!allowed.contains(field)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported front-matter asset field.");
        }
        var asset = userAssetService.storeImage(jobId, "front_matter_" + field, image, field);
        return ResponseEntity.ok(asset);
    }

    @GetMapping("/{jobId}/assets/front-matter/{assetId}")
    public ResponseEntity<Resource> getFrontMatterAsset(
            @AuthenticationPrincipal AppUser authenticatedUser,
            @PathVariable String jobId,
            @PathVariable String assetId) {
        getJob(jobId, authenticatedUser.getId());
        try {
            Path asset = userAssetService.resolve(jobId, assetId);
            MediaType mediaType = asset.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".png")
                    ? MediaType.IMAGE_PNG
                    : MediaType.IMAGE_JPEG;
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .body(new FileSystemResource(asset));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Front-matter image not found.");
        }
    }

    @PostMapping(value = "/{jobId}/assets/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadImage(
            @AuthenticationPrincipal AppUser authenticatedUser,
            @PathVariable String jobId,
            @RequestParam("sectionId") String sectionId,
            @RequestParam("image") MultipartFile image,
            @RequestParam(value = "caption", required = false) String caption) {
        DocumentationJob job = getJob(jobId, authenticatedUser.getId());
        if (!isConfigurationEditable(job.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Images can only be uploaded before generation starts.");
        }
        if (sectionId == null || sectionId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Section is required.");
        }
        var definition = globalTemplateService.definitions().stream()
                .filter(item -> sectionId.equals(item.getId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown section for the configured template."));
        // The final SectionConfiguration is submitted later with /generate-v1.
        // At this stage there is intentionally no DocumentationPlan persisted yet,
        // so image upload validation must use the authoritative template definition.
        if (definition.getId() == null || definition.getId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid template section.");
        }
        return ResponseEntity.ok(userAssetService.storeImage(jobId, sectionId, image, caption));
    }

    static boolean isConfigurationEditable(String status) {
        return "WAITING_FOR_USER_CONFIGURATION".equals(status)
                || "WAITING_FOR_INDEXING".equals(status)
                || "INDEXING_PROJECT".equals(status);
    }

    // =========================================================
    // JOB STATUS
    // =========================================================

    @PostMapping("/{jobId}/cancel")
    public ResponseEntity<?> cancelJob(
            @AuthenticationPrincipal AppUser authenticatedUser,
            @PathVariable String jobId) {
        try {
            documentationOrchestrator.cancelJob(jobId, authenticatedUser.getId());
            return ResponseEntity.ok(Map.of(
                    "jobId", jobId,
                    "status", "CANCELLED",
                    "message", "Documentation generation has been cancelled."));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", exception.getMessage()));
        } catch (IllegalStateException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", exception.getMessage()));
        }
    }

    @DeleteMapping("/{jobId}")
    public ResponseEntity<?> deleteJob(
            @AuthenticationPrincipal AppUser authenticatedUser,
            @PathVariable String jobId) {
        jobExpirationService.deleteJob(jobId, authenticatedUser.getId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{jobId}/status")
    public ResponseEntity<DocumentationStatusResponse> getStatus(
                        @AuthenticationPrincipal AppUser authenticatedUser,
            @PathVariable String jobId) {

                DocumentationJob job = jobRepository.findByJobIdAndOwnerId(jobId, authenticatedUser.getId())
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND, "Documentation job not found."));

        return ResponseEntity.ok(
                toStatusResponse(job)
        );
    }


    @GetMapping("/{jobId}/preview")
    public ResponseEntity<?> previewGeneratedDocument(
            @AuthenticationPrincipal AppUser authenticatedUser,
            @PathVariable String jobId) {
        DocumentationJob job = getJob(jobId, authenticatedUser.getId());
        try {
            Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("jobId", jobId);
            payload.put("status", job.getStatus());
            payload.put("projectName", job.getProjectName());
            if (job.getGenerationCheckpointJson() == null || job.getGenerationCheckpointJson().isBlank()) {
                payload.put("frontMatter", java.util.List.of());
                payload.put("chapters", java.util.Map.of());
                return ResponseEntity.ok(payload);
            }
            GenerationCheckpoint checkpoint = objectMapper.readValue(job.getGenerationCheckpointJson(), GenerationCheckpoint.class);
            payload.put("frontMatter", checkpoint.getFrontMatter());

            // A chapter may finish out of order when multiple LLM workers run in parallel.
            // Never expose checkpoint insertion order as document order. The plan's section
            // order is the source of truth for the live preview.
            DocumentationPlan plan = null;
            if (job.getPlanJson() != null && !job.getPlanJson().isBlank()) {
                plan = objectMapper.readValue(job.getPlanJson(), DocumentationPlan.class);
            }
            java.util.Map<String, java.util.List<book.example.dto.GeneratedSectionCheckpoint>> orderedChapters =
                    generationPreviewOrderer.order(plan, checkpoint);
            payload.put("chapters", orderedChapters);
            payload.put("completedChapterCount", orderedChapters.size());
            return ResponseEntity.ok(payload);
        } catch (Exception exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", "Live preview is temporarily unavailable."));
        }
    }

    // =========================================================
    // DOWNLOAD DOCX
    // =========================================================

    @GetMapping("/{jobId}/download/docx")
    public ResponseEntity<Resource> downloadDocx(
                        @AuthenticationPrincipal AppUser authenticatedUser,
            @PathVariable String jobId) {

        DocumentationJob job =
                                getJob(jobId, authenticatedUser.getId());

        Path file =
                getRequiredFile(
                        job.getDocumentPath(),
                        "DOCX"
                );

        Resource resource =
                new FileSystemResource(file);

        return ResponseEntity.ok()
                .contentType(
                        MediaType.parseMediaType(
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                        )
                )
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename("documentation.docx")
                                .build()
                                .toString()
                )
                .body(resource);
    }


    // =========================================================
    // VIEW PDF
    // =========================================================

    @GetMapping("/{jobId}/view/pdf")
    public ResponseEntity<Resource> viewPdf(
                        @AuthenticationPrincipal AppUser authenticatedUser,
            @PathVariable String jobId) {

        DocumentationJob job =
                                getJob(jobId, authenticatedUser.getId());

        Path file =
                getRequiredFile(
                        job.getPdfPath(),
                        "PDF"
                );

        Resource resource =
                new FileSystemResource(file);

        return ResponseEntity.ok()
                .contentType(
                        MediaType.APPLICATION_PDF
                )
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline()
                                .filename("documentation.pdf")
                                .build()
                                .toString()
                )
                .body(resource);
    }


    // =========================================================
    // DOWNLOAD PDF
    // =========================================================

    @GetMapping("/{jobId}/download/pdf")
    public ResponseEntity<Resource> downloadPdf(
                        @AuthenticationPrincipal AppUser authenticatedUser,
            @PathVariable String jobId) {

        DocumentationJob job =
                                getJob(jobId, authenticatedUser.getId());

        Path file =
                getRequiredFile(
                        job.getPdfPath(),
                        "PDF"
                );

        Resource resource =
                new FileSystemResource(file);

        return ResponseEntity.ok()
                .contentType(
                        MediaType.APPLICATION_PDF
                )
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename("documentation.pdf")
                                .build()
                                .toString()
                )
                .body(resource);
    }


    // =========================================================
    // FIND JOB
    // =========================================================

    private DocumentationJob getJob(
                        String jobId,
                        java.util.UUID ownerId) {

        return jobRepository
                                .findByJobIdAndOwnerId(jobId, ownerId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Documentation job not found."));
    }


    private DocumentationStatusResponse toStatusResponse(
            DocumentationJob job) {

        String status = job.getStatus();

        String message = getStatusMessage(job);
        if (job.getIndexingFailuresJson() != null && !job.getIndexingFailuresJson().isBlank()
                && !"FAILED".equals(status) && !"COMPLETED".equals(status)) {
            message += " Some project files could not be indexed yet; documentation is continuing with verified available evidence.";
        }
        return new DocumentationStatusResponse(job.getJobId(), status, getProgress(job), message);
    }


    private int getProgress(DocumentationJob job) {
        String status = job.getStatus();
        if (status == null || status.isBlank()) return 0;
        if ("GENERATING_DOCUMENTATION".equals(status)) {
            int total = 0;
            int completed = 0;
            try {
                if (job.getPlanJson() != null) {
                    DocumentationPlan plan = objectMapper.readValue(job.getPlanJson(), DocumentationPlan.class);
                    total = (int) plan.getSections().stream()
                            .map(book.example.dto.DocumentationSection::getChapterId)
                            .filter(java.util.Objects::nonNull)
                            .distinct()
                            .count();
                }
                if (job.getGenerationCheckpointJson() != null) {
                    GenerationCheckpoint checkpoint = objectMapper.readValue(job.getGenerationCheckpointJson(), GenerationCheckpoint.class);
                    completed = checkpoint.getChapters() == null ? 0 : checkpoint.getChapters().size();
                }
            } catch (Exception ignored) {
                // Fall back to the stable stage percentage if checkpoint JSON is temporarily unavailable.
            }
            if (total > 0) return Math.min(78, 50 + Math.round(28f * completed / total));
            return 65;
        }
        return switch (status) {
            case "RECEIVED" -> 5;
            case "ANALYZING_PROJECT" -> 15;
            case "INDEXING_PROJECT" -> 30;
            case "WAITING_FOR_INDEXING", "WAITING_FOR_USER_CONFIGURATION" -> 35;
            case "QUEUED_FOR_GENERATION" -> 50;
            case "RECOVERING", "RECOVERING_INDEXING" -> 65;
            case "VALIDATING_ASSETS" -> 80;
            case "ASSEMBLING_DOCUMENT" -> 88;
            case "VALIDATING_DOCUMENT" -> 93;
            case "PREPARING_PDF" -> 97;
            case "COMPLETED", "FAILED", "CANCELLED" -> 100;
            default -> 0;
        };
    }

    private String getStatusMessage(DocumentationJob job) {
        String status = job.getStatus();
        if (status == null || status.isBlank()) return "Processing your documentation...";
        if ("GENERATING_DOCUMENTATION".equals(status)) {
            try {
                int total = 0;
                int completed = 0;
                if (job.getPlanJson() != null) {
                    DocumentationPlan plan = objectMapper.readValue(job.getPlanJson(), DocumentationPlan.class);
                    total = (int) plan.getSections().stream()
                            .map(book.example.dto.DocumentationSection::getChapterId)
                            .filter(java.util.Objects::nonNull)
                            .distinct()
                            .count();
                }
                if (job.getGenerationCheckpointJson() != null) {
                    GenerationCheckpoint checkpoint = objectMapper.readValue(job.getGenerationCheckpointJson(), GenerationCheckpoint.class);
                    completed = checkpoint.getChapters() == null ? 0 : checkpoint.getChapters().size();
                }
                if (total > 0) return "Generating complete chapters — " + Math.min(completed + 1, total) + " of " + total + " in progress...";
            } catch (Exception ignored) { }
        }
        return switch (status) {
            case "RECEIVED" -> "Documentation job received.";
            case "ANALYZING_PROJECT" -> "Analyzing your project structure and source files...";
            case "INDEXING_PROJECT" -> "Indexing project knowledge in the background. You can configure the report now...";
            case "WAITING_FOR_INDEXING" -> "Your configuration is saved. Finishing project indexing in the background...";
            case "WAITING_FOR_USER_CONFIGURATION" -> "Choose the recommended chapters, sections, images and diagrams.";
            case "QUEUED_FOR_GENERATION" -> "Your saved job is queued and will start shortly...";
            case "GENERATING_DOCUMENTATION" -> "Generating complete chapters with the available LLM workers...";
            case "RECOVERING" -> "A temporary AI service issue occurred. Your saved progress is safe and the job is retrying automatically...";
            case "RECOVERING_INDEXING" -> "Project knowledge indexing is retrying automatically. Your selections and progress are safe...";
            case "VALIDATING_ASSETS" -> "Validating required images and diagrams...";
            case "ASSEMBLING_DOCUMENT" -> "Assembling the final DOCX...";
            case "VALIDATING_DOCUMENT" -> "Validating the final document...";
            case "PREPARING_PDF" -> "Preparing the final PDF...";
            case "COMPLETED" -> "Documentation is ready.";
            case "FAILED" -> "Documentation generation could not be completed.";
            case "CANCELLED" -> "Documentation generation was cancelled by you.";
            default -> "Processing your documentation...";
        };
    }

    // =========================================================
    // VALIDATE FILE
    // =========================================================

    private Path getRequiredFile(
            String filePath,
            String fileType) {

        if (filePath == null ||
                filePath.isBlank()) {

            throw new IllegalStateException(
                    fileType
                            + " file is not available"
            );
        }

        Path path =
                Path.of(filePath);

        if (!Files.exists(path)) {

            throw new IllegalStateException(
                    fileType
                            + " file does not exist: "
                            + filePath
            );
        }

        if (!Files.isRegularFile(path)) {

            throw new IllegalStateException(
                    fileType
                            + " path is not a regular file: "
                            + filePath
            );
        }

        return path;
    }
}