package book.example.services;

import book.example.Analyzer.ProjectAnalyzer;
import book.example.Entity.DocumentationJob;
import book.example.Repository.JobRepository;
import book.example.dto.DocumentationPlan;
import book.example.dto.DocumentationResponse;
import book.example.dto.DocumentFormatDefinition;
import book.example.dto.GenerateDocumentationRequest;
import book.example.dto.GeneratedDocumentation;
import book.example.dto.ProjectAnalysisResponse;
import book.example.dto.ProjectFacts;
import book.example.dto.RepositorySnapshot;
import book.example.dto.SectionConfiguration;
import book.example.dto.StudentProjectDetails;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipFile;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Service
public class DocumentationOrchestrator {

    private static final org.slf4j.Logger LOGGER =
            org.slf4j.LoggerFactory.getLogger(DocumentationOrchestrator.class);
    @Value("${app.generation.allow-invalidated-sections:false}")
    private boolean allowInvalidatedSections;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final JobRepository jobRepository;
    private final GitHubService gitHubService;
    private final ProjectAnalyzer projectAnalyzer;
    private final RepositoryIngestionService repositoryIngestionService;
    private final GlobalTemplateService globalTemplateService;
    private final SectionRecommendationService sectionRecommendationService;
    private final ChapterGenerationService chapterGenerationService;
    private final DocumentAssembler documentAssembler;
    private final PdfConversionService pdfConversionService;
    private final TemporaryChromaManager temporaryChromaManager;
    private final DocumentationJobRunner documentationJobRunner;
    private final StudentContextSuggestionService studentContextSuggestionService;
    private final TemplateLibraryService templateLibraryService;
    private final TemplateFileStorageService templateFileStorageService;
    private final FormatAnalyzer formatAnalyzer;

    @Value("${app.storage.retention-days:30}")
    private int storageRetentionDays;
    private final UsageLedgerService usageLedgerService;

    public DocumentationOrchestrator(
            JobRepository jobRepository,
            GitHubService gitHubService,
            ProjectAnalyzer projectAnalyzer,
            RepositoryIngestionService repositoryIngestionService,
            GlobalTemplateService globalTemplateService,
            SectionRecommendationService sectionRecommendationService,
            ChapterGenerationService chapterGenerationService,
            DocumentAssembler documentAssembler,
            PdfConversionService pdfConversionService,
            TemporaryChromaManager temporaryChromaManager,
            UsageLedgerService usageLedgerService,
            @Lazy DocumentationJobRunner documentationJobRunner,
            StudentContextSuggestionService studentContextSuggestionService,
            TemplateLibraryService templateLibraryService,
            TemplateFileStorageService templateFileStorageService,
            FormatAnalyzer formatAnalyzer) {
        this.jobRepository = jobRepository;
        this.gitHubService = gitHubService;
        this.projectAnalyzer = projectAnalyzer;
        this.repositoryIngestionService = repositoryIngestionService;
        this.globalTemplateService = globalTemplateService;
        this.sectionRecommendationService = sectionRecommendationService;
        this.chapterGenerationService = chapterGenerationService;
        this.documentAssembler = documentAssembler;
        this.pdfConversionService = pdfConversionService;
        this.temporaryChromaManager = temporaryChromaManager;
        this.usageLedgerService = usageLedgerService;
        this.documentationJobRunner = documentationJobRunner;
        this.studentContextSuggestionService = studentContextSuggestionService;
        this.templateLibraryService = templateLibraryService;
        this.templateFileStorageService = templateFileStorageService;
        this.formatAnalyzer = formatAnalyzer;
    }

    public ProjectAnalysisResponse analyzeProject(String githubUrl, String projectName,
                                                  MultipartFile projectZip, UUID ownerId) {
        boolean hasGithub = githubUrl != null && !githubUrl.isBlank();
        boolean hasZip = projectZip != null && !projectZip.isEmpty();
        if (hasGithub == hasZip) throw new IllegalArgumentException("Choose either a GitHub repository or a ZIP project.");

        DocumentationJob job = new DocumentationJob();
        String jobId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        job.setJobId(jobId);
        job.setOwnerId(ownerId);
        job.setType("GLOBAL_STUDENT_PROJECT");
        job.setStatus("ANALYZING_PROJECT");
        job.setCreatedAt(now);
        job.setUpdatedAt(now);
        job.setExpiresAt(now.plusDays(Math.max(1, storageRetentionDays)));
        jobRepository.save(job);

        try {
            RepositorySnapshot snapshot = hasGithub
                    ? gitHubService.cloneRepository(githubUrl.trim())
                    : gitHubService.scanZip(projectZip);
            ProjectFacts facts = projectAnalyzer.analyze(snapshot);
            String normalizedName = normalizeProjectName(projectName);
            if (normalizedName != null) facts.setProjectName(normalizedName);

            job.setGithubUrl(snapshot.getRepositoryUrl());
            job.setProjectName(facts.getProjectName());
            job.setStatus("INDEXING_PROJECT");
            job.setProjectFactsJson(OBJECT_MAPPER.writeValueAsString(facts));
            job.setRepositorySnapshotJson(serializeRepositorySnapshot(snapshot));
            job.setUpdatedAt(LocalDateTime.now());
            jobRepository.save(job);

            // Do NOT block the user on full embedding/indexing. The project facts
            // are enough for the initial template/section recommendation. Chroma
            // indexing continues asynchronously while the user configures the report.
            job.setIndexingFailuresJson(OBJECT_MAPPER.writeValueAsString(List.of()));
            job.setStatus("INDEXING_PROJECT");
            job.setUpdatedAt(LocalDateTime.now());
            jobRepository.save(job);

            documentationJobRunner.resumeAnalysis(jobId);

            var recommendations = sectionRecommendationService.recommend(jobId, facts, globalTemplateService.definitions());
            ProjectAnalysisResponse response = new ProjectAnalysisResponse(jobId, GlobalTemplateService.TEMPLATE_ID,
                    "INDEXING_PROJECT", facts, globalTemplateService.getTemplate(), recommendations);
            response.setIndexingPartial(false);
            response.setFailedIndexingChunks(0);
            return response;
        } catch (Exception e) {
            job.setStatus("FAILED");
            job.setUpdatedAt(LocalDateTime.now());
            jobRepository.save(job);
            // Indexing may have partially populated Chroma before failing.
            // Remove it now rather than waiting for the three-day job expiry.
            temporaryChromaManager.cleanup(jobId);
            throw new IllegalStateException("Project analysis/indexing failed: " + safeMessage(e), e);
        }
    }

    public void resumeAnalysis(String jobId) {
        DocumentationJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalStateException("Documentation job not found: " + jobId));
        boolean recoveryRun = "RECOVERING".equals(job.getStatus()) || "RECOVERING_INDEXING".equals(job.getStatus());
        if ("CANCELLED".equals(job.getStatus()) || "COMPLETED".equals(job.getStatus()) || "FAILED".equals(job.getStatus())) {
            return;
        }
        if (!recoveryRun && !"INDEXING_PROJECT".equals(job.getStatus()) && !"WAITING_FOR_INDEXING".equals(job.getStatus())) {
            return;
        }
        if (job.getProjectFactsJson() == null || job.getRepositorySnapshotJson() == null) {
            if (recoveryRun) {
                job.setStatus("RECOVERING");
                job.setUpdatedAt(LocalDateTime.now());
                jobRepository.save(job);
            } else {
                markFailed(job);
            }
            throw new IllegalStateException("Persisted analysis snapshot is incomplete for job: " + jobId);
        }
        try {
            RepositorySnapshot snapshot = readRepositorySnapshot(job.getRepositorySnapshotJson());
            if (snapshot.getFiles() == null || snapshot.getFiles().isEmpty()) {
                throw new IllegalStateException("Persisted repository snapshot contains no files.");
            }
            Set<String> failedIds = readIndexingFailures(job);
            var indexResult = failedIds.isEmpty()
                    ? repositoryIngestionService.ingest(jobId, snapshot)
                    : repositoryIngestionService.ingest(jobId, snapshot, failedIds);
            int updated = jobRepository.updateIndexingFailuresIfWaiting(
                    jobId,
                    OBJECT_MAPPER.writeValueAsString(indexResult.getFailedChunkIds()),
                    LocalDateTime.now());
            if (updated == 0) return;

            DocumentationJob current = jobRepository.findById(jobId)
                    .orElseThrow(() -> new IllegalStateException("Documentation job no longer exists: " + jobId));
            if (current.getPlanJson() != null && !current.getPlanJson().isBlank()) {
                if (jobRepository.markWaitingForIndexing(jobId, LocalDateTime.now()) == 0) return;
                if (indexResult.getFailedChunkIds().isEmpty()) {
                    continueGenerationAfterIndexing(jobId);
                } else {
                    documentationJobRunner.retryFailedIndexing(jobId);
                }
            } else {
                current.setStatus("WAITING_FOR_USER_CONFIGURATION");
                current.setUpdatedAt(LocalDateTime.now());
                jobRepository.save(current);
                if (indexResult.isPartial()) documentationJobRunner.retryFailedIndexing(jobId);
            }
        } catch (Exception e) {
            if ("CANCELLED".equals(jobRepository.findById(jobId).map(DocumentationJob::getStatus).orElse(null))) {
                return;
            }
            // Indexing is a background dependency. A temporary embedding/Chroma
            // outage must not destroy the job while the user is configuring it.
            // RECOVERING is durable and the scheduler retries it until completion,
            // expiry, or explicit cancellation.
            job.setStatus("RECOVERING_INDEXING");
            job.setUpdatedAt(LocalDateTime.now());
            jobRepository.save(job);
            return;
        }
    }

    /** Retry only chunks that previously failed; never re-embeds successful chunks. */
    public void retryFailedIndexing(String jobId) {
        DocumentationJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null || job.getRepositorySnapshotJson() == null) return;
        try {
            RepositorySnapshot snapshot = readRepositorySnapshot(job.getRepositorySnapshotJson());
            for (int attempt = 1; attempt <= 3; attempt++) {
                job = jobRepository.findById(jobId).orElse(null);
                if (job == null || !("WAITING_FOR_USER_CONFIGURATION".equals(job.getStatus())
                        || "WAITING_FOR_INDEXING".equals(job.getStatus())
                        || "QUEUED_FOR_GENERATION".equals(job.getStatus())
                        || "GENERATING_DOCUMENTATION".equals(job.getStatus())
                        || "RECOVERING".equals(job.getStatus())
                        || "RECOVERING_INDEXING".equals(job.getStatus()))) return;
                Set<String> failedIds = readIndexingFailures(job);
                if (failedIds.isEmpty()) return;
                var result = repositoryIngestionService.ingest(jobId, snapshot, failedIds);
                String failuresJson = OBJECT_MAPPER.writeValueAsString(result.getFailedChunkIds());
                int updated = jobRepository.updateIndexingFailuresIfWaiting(jobId, failuresJson, LocalDateTime.now());
                // Generation may have claimed the job while indexing was in flight.
                // Never let a stale retry entity overwrite QUEUED_FOR_GENERATION.
                if (updated == 0) return;
                if (result.getFailedChunkIds().isEmpty()) {
                    continueGenerationAfterIndexing(jobId);
                    return;
                }
                if (attempt < 3) {
                    try { Thread.sleep(1000L * attempt); }
                    catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return; }
                }
            }
        } catch (Exception exception) {
            LOGGER.warn("Unable to retry failed project indexing for job {}.", jobId, exception);
        }
    }

    private void continueGenerationAfterIndexing(String jobId) {
        DocumentationJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null
                || !Set.of("WAITING_FOR_INDEXING", "RECOVERING_INDEXING", "RECOVERING")
                        .contains(job.getStatus())
                || !readIndexingFailures(job).isEmpty()
                || job.getPlanJson() == null
                || job.getPlanJson().isBlank()) {
            return;
        }

        try {
            DocumentationPlan plan = OBJECT_MAPPER.readValue(job.getPlanJson(), DocumentationPlan.class);
            plan.setProjectEvidencePartial(false);
            plan.setFailedEvidenceChunks(0);
            int updated = jobRepository.updatePlanAfterIndexing(
                    jobId,
                    OBJECT_MAPPER.writeValueAsString(plan),
                    LocalDateTime.now());
            if (updated == 0) return;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to update the final plan after project indexing completed.", exception);
        }

        int claimed = jobRepository.claimForGenerationAfterIndexing(jobId, LocalDateTime.now());
        if (claimed == 0) return;
        DocumentationJob queued = jobRepository.findById(jobId).orElseThrow(() ->
                new IllegalStateException("Documentation job disappeared after indexing completed: " + jobId));
        try {
            usageLedgerService.recordGeneration(queued.getOwnerId(), jobId);
        } catch (RuntimeException ledgerFailure) {
            LOGGER.warn("Usage ledger could not record generation for job {}. Continuing generation.", jobId, ledgerFailure);
        }
        documentationJobRunner.resumeFinalGeneration(jobId);
    }

    private Set<String> readIndexingFailures(DocumentationJob job) {
        if (job.getIndexingFailuresJson() == null || job.getIndexingFailuresJson().isBlank()) return Set.of();
        try {
            List<String> ids = OBJECT_MAPPER.readValue(job.getIndexingFailuresJson(), List.class);
            return new LinkedHashSet<>(ids);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Unable to verify whether all project chunks were indexed for job: " + job.getJobId(),
                    e);
        }
    }

    public book.example.dto.StudentContext suggestStudentContext(String jobId, UUID ownerId) {
        DocumentationJob job = jobRepository.findByJobIdAndOwnerId(jobId, ownerId)
                .orElseThrow(() -> new IllegalArgumentException("Documentation job not found."));
        if (job.getProjectFactsJson() == null) throw new IllegalStateException("Project analysis is not available yet.");
        return studentContextSuggestionService.suggest(readProjectFacts(job));
    }

    public ProjectAnalysisResponse getAnalysis(String jobId, UUID ownerId) {
        DocumentationJob job = jobRepository.findByJobIdAndOwnerId(jobId, ownerId)
                .orElseThrow(() -> new IllegalArgumentException("Documentation job not found."));
        if (job.getProjectFactsJson() == null) {
            throw new IllegalStateException("Project analysis is not available yet.");
        }

        ProjectFacts facts = readProjectFacts(job);
        var recommendations = sectionRecommendationService.recommend(jobId, facts, globalTemplateService.definitions());
        ProjectAnalysisResponse response = new ProjectAnalysisResponse(jobId, GlobalTemplateService.TEMPLATE_ID,
                job.getStatus(), facts, globalTemplateService.getTemplate(), recommendations);
        response.setIndexingPartial(!readIndexingFailures(job).isEmpty());
        response.setFailedIndexingChunks(readIndexingFailures(job).size());
        return response;
    }

    public Map<String, Object> analyzePrivateTemplateFormat(
            String jobId, UUID ownerId, MultipartFile upload) {
        DocumentationJob job = jobRepository.findByJobIdAndOwnerId(jobId, ownerId)
                .orElseThrow(() -> new IllegalArgumentException("Documentation job not found."));
        if (!Set.of("WAITING_FOR_USER_CONFIGURATION", "INDEXING_PROJECT", "WAITING_FOR_INDEXING")
                .contains(job.getStatus())) {
            throw new IllegalStateException("A private format can only be added before generation starts.");
        }
        FormatAnalyzer.Analysis analysis = templateFileStorageService.analyzePrivate(upload, formatAnalyzer);
        validateResolvedFormat(analysis.format());
        try {
            job.setPrivateTemplateFormatJson(OBJECT_MAPPER.writeValueAsString(analysis.format()));
            jobRepository.save(job);
            return Map.of(
                    "formatSchema", analysis.format(),
                    "analysisMetadata", analysis.properties(),
                    "message", "Private format analyzed for this project only.");
        } catch (Exception exception) {
            throw new IllegalStateException("The private format could not be saved for this project.", exception);
        }
    }

    public DocumentationResponse startFinalGeneration(String jobId, UUID ownerId, GenerateDocumentationRequest request) {
        if (request == null) throw new IllegalArgumentException("Generation request is required.");
        DocumentationJob job = jobRepository.findByJobIdAndOwnerId(jobId, ownerId)
                .orElseThrow(() -> new IllegalArgumentException("Documentation job not found."));
        if ("CANCELLED".equals(job.getStatus())) {
            throw new IllegalStateException("This documentation job has been cancelled.");
        }
        if (!"WAITING_FOR_USER_CONFIGURATION".equals(job.getStatus())
                && !"INDEXING_PROJECT".equals(job.getStatus())
                && !"WAITING_FOR_INDEXING".equals(job.getStatus())) {
            throw new IllegalStateException("The project is not waiting for final configuration.");
        }
        boolean indexingInProgress = "INDEXING_PROJECT".equals(job.getStatus())
                || "WAITING_FOR_INDEXING".equals(job.getStatus());
        if (!globalTemplateService.isSupportedTemplateId(request.getTemplateId())) {
            throw new IllegalArgumentException("Unsupported documentation template: " + request.getTemplateId());
        }
        if (request.isUsePrivateFormat() && request.getLibraryTemplateId() != null) {
            throw new IllegalArgumentException("Choose either a published template or a private format.");
        }
        validateStudentDetails(request.getStudentDetails());
        if (request.getSelectedSections() == null || request.getSelectedSections().size() > 100) {
            throw new IllegalArgumentException("At most 100 report sections may be selected.");
        }
        List<SectionConfiguration> configurations = request.getSectionConfigurations() == null ? List.of() : request.getSectionConfigurations();
        if (configurations.size() > 100) throw new IllegalArgumentException("Too many section configurations were supplied.");
        Map<String, String> additionalInformation = request.getAdditionalInformation();
        if (additionalInformation != null) {
            if (additionalInformation.size() > 50) throw new IllegalArgumentException("Too many additional information fields were supplied.");
            additionalInformation.forEach((key, value) -> {
                if (key == null || key.length() > 100 || value != null && value.length() > 4000) {
                    throw new IllegalArgumentException("Additional information contains an oversized field.");
                }
            });
        }
        for (String requiredId : globalTemplateService.requiredAdditionalInformation(request.getSelectedSections())) {
            if (additionalInformation == null || additionalInformation.get(requiredId) == null || additionalInformation.get(requiredId).isBlank()) {
                throw new IllegalArgumentException("Additional information is required for: " + requiredId);
            }
        }
        ProjectFacts facts = readProjectFacts(job);
        validateStudentContext(request.getStudentContext());
        DocumentationPlan plan = globalTemplateService.buildPlan(
                facts, request.getSelectedSections(), request.getSelectedDiagrams(), configurations,
                request.getStudentDetails(), additionalInformation);
        plan.setStudentContext(request.getStudentContext());
        plan.setTemplateId(request.getTemplateId());
        globalTemplateService.applyTemplateVariant(plan, request.getTemplateId());
        applyRequestedTemplate(job, plan, request);
        Set<String> currentIndexFailures = readIndexingFailures(job);
        boolean indexingIncomplete = indexingInProgress || !currentIndexFailures.isEmpty();
        plan.setProjectEvidencePartial(!currentIndexFailures.isEmpty());
        plan.setFailedEvidenceChunks(currentIndexFailures.size());
        boolean generationClaimed = false;
        try {
            String planJson = OBJECT_MAPPER.writeValueAsString(plan);
            if (indexingIncomplete) {
                // Save the user's configuration, but do not claim or start generation
                // until every repository chunk has been embedded and stored.
                job.setStatus("WAITING_FOR_INDEXING");
                job.setUpdatedAt(LocalDateTime.now());
                job.setPlanJson(planJson);
                jobRepository.save(job);
                if (!currentIndexFailures.isEmpty()) {
                    documentationJobRunner.retryFailedIndexing(jobId);
                }

                return new DocumentationResponse(jobId, "WAITING_FOR_INDEXING",
                        "Your configuration is saved. Generation will start automatically after all project files are embedded and indexed.");
            }

            int claimed = jobRepository.claimForGeneration(jobId, LocalDateTime.now());
            if (claimed == 0) {
                throw new IllegalStateException("Generation has already started for this job.");
            }
            generationClaimed = true;
            // claimForGeneration clears the persistence context; keep this detached
            // instance aligned with the DB claim before saving the final plan.
            job.setStatus("QUEUED_FOR_GENERATION");
            job.setUpdatedAt(LocalDateTime.now());
            job.setPlanJson(planJson);
            jobRepository.save(job);
            try {
                usageLedgerService.recordGeneration(ownerId, jobId);
            } catch (RuntimeException ledgerFailure) {
                org.slf4j.LoggerFactory.getLogger(DocumentationOrchestrator.class)
                        .warn("Usage ledger could not record generation for job {}. Continuing generation.", jobId, ledgerFailure);
            }
            documentationJobRunner.resumeFinalGeneration(jobId);
            return new DocumentationResponse(jobId, "QUEUED_FOR_GENERATION", "Your documentation job is queued and will start shortly.");
        } catch (Exception e) {
            if (generationClaimed) {
                job.setStatus("FAILED");
                job.setUpdatedAt(LocalDateTime.now());
                jobRepository.save(job);
            }
            throw new IllegalStateException("Unable to start final document generation.", e);
        }
    }

    private void applyRequestedTemplate(
            DocumentationJob job, DocumentationPlan plan, GenerateDocumentationRequest request) {
        if (request.isUsePrivateFormat()) {
            if (job.getPrivateTemplateFormatJson() == null || job.getPrivateTemplateFormatJson().isBlank()) {
                throw new IllegalArgumentException("Upload a private DOCX or PDF format before selecting it.");
            }
            try {
                DocumentFormatDefinition format = OBJECT_MAPPER.readValue(
                        job.getPrivateTemplateFormatJson(), DocumentFormatDefinition.class);
                validateResolvedFormat(format);
                plan.setFormat(format);
                job.setTemplateId("private-upload");
                job.setTemplateVersion(null);
                job.setTemplateFormatSnapshotJson(job.getPrivateTemplateFormatJson());
                Map<String, Object> frontPage = new LinkedHashMap<>();
                frontPage.put("source", "PRIVATE_UPLOAD");
                frontPage.put("studentDetails", request.getStudentDetails());
                job.setTemplateFrontPageSnapshotJson(OBJECT_MAPPER.writeValueAsString(frontPage));
            } catch (Exception exception) {
                throw new IllegalStateException("The private template format could not be applied.", exception);
            }
            return;
        }
        if (request.getLibraryTemplateId() == null || request.getLibraryTemplateId().isBlank()) {
            job.setPrivateTemplateFormatJson(null);
            return;
        }
        job.setPrivateTemplateFormatJson(null);
        if (request.getLibraryTemplateVersion() == null || request.getLibraryTemplateVersion() < 1) {
            throw new IllegalArgumentException("Select a valid published template version.");
        }
        var selected = templateLibraryService.published(
                request.getLibraryTemplateId().trim(), request.getLibraryTemplateVersion());
        try {
            DocumentFormatDefinition format = OBJECT_MAPPER.convertValue(
                    selected.formatSchema(), DocumentFormatDefinition.class);
            validateResolvedFormat(format);
            plan.setFormat(format);
            job.setTemplateId(selected.templateId());
            job.setTemplateVersion(selected.version());
            job.setTemplateFormatSnapshotJson(OBJECT_MAPPER.writeValueAsString(format));
            Map<String, Object> frontPage = new LinkedHashMap<>();
            frontPage.put("templateConfig", OBJECT_MAPPER.readTree(selected.frontPageConfig()));
            frontPage.put("studentDetails", request.getStudentDetails());
            job.setTemplateFrontPageSnapshotJson(OBJECT_MAPPER.writeValueAsString(frontPage));
        } catch (Exception exception) {
            throw new IllegalStateException("The published template format could not be applied.", exception);
        }
    }

    private void validateResolvedFormat(DocumentFormatDefinition format) {
        if (format == null
                || !Set.of("A4", "LETTER", "LEGAL", "A3", "A5").contains(format.getPageSize())
                || !Set.of("PORTRAIT", "LANDSCAPE").contains(format.getOrientation())
                || format.getDefaultFont() == null || format.getDefaultFont().isBlank()
                || format.getDefaultFont().length() > 100
                || format.getDefaultFontSize() < 8 || format.getDefaultFontSize() > 24
                || format.getTitleFontSize() < 12 || format.getTitleFontSize() > 40
                || format.getHeading1FontSize() < 10 || format.getHeading1FontSize() > 30
                || format.getHeading2FontSize() < 9 || format.getHeading2FontSize() > 26
                || format.getHeading3FontSize() < 8 || format.getHeading3FontSize() > 24
                || format.getMarginTopTwips() < 360 || format.getMarginTopTwips() > 3600
                || format.getMarginBottomTwips() < 360 || format.getMarginBottomTwips() > 3600
                || format.getMarginLeftTwips() < 360 || format.getMarginLeftTwips() > 3600
                || format.getMarginRightTwips() < 360 || format.getMarginRightTwips() > 3600
                || !Double.isFinite(format.getLineSpacing())
                || format.getLineSpacing() < 1 || format.getLineSpacing() > 3) {
            throw new IllegalArgumentException("The selected template contains unsupported formatting values.");
        }
    }

    public void resumeFinalGeneration(String jobId) {
        DocumentationJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalStateException("Documentation job not found: " + jobId));
        if ("CANCELLED".equals(job.getStatus()) || "COMPLETED".equals(job.getStatus()) || "FAILED".equals(job.getStatus())) {
            return;
        }
        if (!"QUEUED_FOR_GENERATION".equals(job.getStatus()) && !"RECOVERING".equals(job.getStatus())) {
            return;
        }
        if (job.getProjectFactsJson() == null || job.getPlanJson() == null) {
            throw new IllegalStateException("Persisted project analysis or final configuration is missing.");
        }
        try {
            ProjectFacts facts = OBJECT_MAPPER.readValue(job.getProjectFactsJson(), ProjectFacts.class);
            DocumentationPlan plan = OBJECT_MAPPER.readValue(job.getPlanJson(), DocumentationPlan.class);
            runFinalDocumentGeneration(job, facts, plan);
        } catch (Exception e) {
            if (!"RECOVERING".equals(job.getStatus())) {
                markFailed(job);
            }
            throw new IllegalStateException("Final document generation failed for job: " + jobId, e);
        }
    }

    private void runFinalDocumentGeneration(DocumentationJob job, ProjectFacts facts, DocumentationPlan plan) {
        boolean recoveryRun = "RECOVERING".equals(job.getStatus());
        try {
            if (isCancelled(job.getJobId())) return;
            updateStatus(job, "GENERATING_DOCUMENTATION");
            GeneratedDocumentation documentation = chapterGenerationService.generate(job.getJobId(), facts, plan);
            if (isCancelled(job.getJobId())) return;
            validateGeneratedDocumentation(documentation, plan, facts);
            updateStatus(job, "VALIDATING_ASSETS");
            validateAssets(documentation);
            if (isCancelled(job.getJobId())) return;
            updateStatus(job, "ASSEMBLING_DOCUMENT");
            Path documentPath = documentAssembler.assemble(documentation, job.getJobId());
            if (isCancelled(job.getJobId())) return;
            validateFile(documentPath, "DOCX");
            validateAssembledDocx(documentPath, plan);
            job.setDocumentPath(documentPath.toString());
            updateStatus(job, "VALIDATING_DOCUMENT");
            validateFile(documentPath, "DOCX");
            updateStatus(job, "PREPARING_PDF");
            Path pdfPath = convertToPdfWithRetry(documentPath);
            if (isCancelled(job.getJobId())) return;
            validateFile(pdfPath, "PDF");
            job.setPdfPath(pdfPath.toString());
            job.setStatus("COMPLETED");
            job.setUpdatedAt(LocalDateTime.now());
            job.setExpiresAt(LocalDateTime.now().plusDays(Math.max(1, storageRetentionDays)));
            jobRepository.save(job);
            try {
                usageLedgerService.updateGenerationStatus(job.getJobId(), "COMPLETED");
            } catch (RuntimeException ledgerFailure) {
                org.slf4j.LoggerFactory.getLogger(DocumentationOrchestrator.class)
                        .warn("Usage ledger status update failed after successful generation. jobId={}", job.getJobId(), ledgerFailure);
            }
            try {
                temporaryChromaManager.cleanup(job.getJobId());
            } catch (RuntimeException cleanupFailure) {
                org.slf4j.LoggerFactory.getLogger(DocumentationOrchestrator.class)
                        .warn("Temporary Chroma cleanup failed after successful generation. jobId={}", job.getJobId(), cleanupFailure);
            }
        } catch (Exception e) {
            if (isCancelled(job.getJobId())) {
                return;
            }
            if (recoveryRun || containsTransientLlmFailure(e)) {
                // A transient provider/network failure must not become a permanent
                // user-visible FAILED job. The persisted chapter checkpoint remains
                // intact, and JobRecoveryScheduler will resume the unfinished work
                // after the provider has had time to recover.
                job.setStatus("RECOVERING");
                job.setUpdatedAt(LocalDateTime.now());
                jobRepository.save(job);
            } else {
                markFailed(job);
            }
            throw new IllegalStateException("Final documentation generation failed", e);
        }
    }

    private boolean containsTransientLlmFailure(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof LlmProviderException providerException) {
                return providerException.isTransientFailure();
            }
            current = current.getCause();
        }
        return false;
    }

    private Path convertToPdfWithRetry(Path documentPath) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                return pdfConversionService.convertToPdf(documentPath);
            } catch (RuntimeException failure) {
                last = failure;
                if (attempt < 2) {
                    try { Thread.sleep(750L); }
                    catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw failure; }
                }
            }
        }
        throw last == null ? new IllegalStateException("PDF conversion failed") : last;
    }

    private void validateGeneratedDocumentation(GeneratedDocumentation documentation, DocumentationPlan plan, ProjectFacts facts) {
        if (documentation == null || documentation.getSections() == null) {
            throw new IllegalStateException("Generated documentation contains no sections.");
        }
        java.util.Map<String, book.example.dto.DocumentationSection> expected = new java.util.LinkedHashMap<>();
        for (book.example.dto.DocumentationSection section : plan.getSections()) {
            if (section == null || !section.isContentEnabled()) continue;
            if ("INDEX".equalsIgnoreCase(section.getGenerationMode()) || "TEMPLATE".equalsIgnoreCase(section.getGenerationMode())) continue;
            expected.put(section.getId(), section);
        }
        java.util.Set<String> generatedKeys = documentation.getSections().stream()
                .filter(java.util.Objects::nonNull)
                .map(s -> s.getOrder() + "|" + s.getTitle())
                .collect(java.util.stream.Collectors.toSet());
        for (book.example.dto.DocumentationSection section : expected.values()) {
            String key = section.getOrder() + "|" + section.getTitle();
            if (!generatedKeys.contains(key)) {
                throw new IllegalStateException("Generated documentation is missing required selected section: " + section.getId());
            }
            book.example.dto.GeneratedSection generated = documentation.getSections().stream()
                    .filter(s -> s != null && s.getOrder() == section.getOrder() && java.util.Objects.equals(s.getTitle(), section.getTitle()))
                    .findFirst().orElse(null);
            if (generated == null) {
                throw new IllegalStateException("Generated documentation contains no output for selected section: " + section.getId());
            }
            boolean chapterHeading = section.getId() != null && section.getId().equals(section.getChapterId());
            if (!chapterHeading && (generated.getContent() == null || generated.getContent().isBlank())) {
                throw new IllegalStateException("Generated documentation contains empty content for selected section: " + section.getId());
            }
            if (!chapterHeading
                    && !(allowInvalidatedSections
                    && SectionQualityValidator.isInvalidatedSection(generated.getContent()))) {
                SectionQualityValidator.ValidationResult quality = SectionQualityValidator.validate(generated.getContent(), section, facts);
                if (!quality.valid()) throw new IllegalStateException("Final documentation quality validation failed for " + section.getId() + ": " + quality.reason());
            }
        }
    }

    private void validateAssets(GeneratedDocumentation documentation) {
        if (documentation == null || documentation.getSections() == null) throw new IllegalStateException("Generated documentation is missing.");
        documentation.getSections().forEach(section -> {
            if ("REQUIRED".equals(section.getDiagramStatus())) {
                if (section.getDiagramImage() == null || section.getDiagramImage().getData() == null || section.getDiagramImage().getData().length == 0) {
                    throw new IllegalStateException("Required diagram asset is missing for: " + section.getTitle());
                }
            }
            if (section.getImagePaths() != null) section.getImagePaths().forEach(path -> {
                if (path == null || !Files.isRegularFile(Path.of(path))) throw new IllegalStateException("Required image asset is missing for: " + section.getTitle());
            });
        });
    }

    private void validateAssembledDocx(Path path, DocumentationPlan plan) throws IOException {
        try (org.apache.poi.xwpf.usermodel.XWPFDocument document = new org.apache.poi.xwpf.usermodel.XWPFDocument(Files.newInputStream(path))) {
            StringBuilder text = new StringBuilder();
            document.getParagraphs().forEach(paragraph -> text.append(' ').append(paragraph.getText()));
            document.getTables().forEach(table -> table.getRows().forEach(row -> row.getTableCells().forEach(cell -> text.append(' ').append(cell.getText()))));
            String normalized = text.toString().replaceAll("\\s+", " ").trim().toLowerCase(java.util.Locale.ROOT);
            if (normalized.isBlank()) throw new IllegalStateException("Generated DOCX contains no readable text.");
            for (var section : plan.getSections()) {
                if (section == null || !section.isContentEnabled()) continue;
                if ("INDEX".equalsIgnoreCase(section.getGenerationMode())) continue;
                String title = section.getTitle() == null ? "" : section.getTitle().trim().toLowerCase(java.util.Locale.ROOT);
                String normalizedTitle = title
                        .replaceFirst("(?i)^chapter\\s+\\d+\\s*[-:.)—–]?\\s*", "")
                        .replaceFirst("^\\d+(?:\\.\\d+)*\\.?\\s+", "")
                        .trim();
                String headingToFind = normalizedTitle.isBlank() ? title : normalizedTitle;
                if (!headingToFind.isBlank() && !normalized.contains(headingToFind)) {
                    throw new IllegalStateException("Generated DOCX is missing selected section heading: " + section.getId());
                }
            }
        }
    }

    private void validateFile(Path path, String type) throws Exception {
        if (path == null || !Files.isRegularFile(path) || Files.size(path) == 0) {
            throw new IllegalStateException(type + " file is invalid.");
        }
        if ("DOCX".equalsIgnoreCase(type)) {
            try (ZipFile zip = new ZipFile(path.toFile())) {
                if (zip.getEntry("[Content_Types].xml") == null || zip.getEntry("word/document.xml") == null) {
                    throw new IllegalStateException("Generated DOCX is structurally invalid.");
                }
            }
        } else if ("PDF".equalsIgnoreCase(type)) {
            byte[] header = new byte[5];
            try (InputStream input = Files.newInputStream(path)) {
                int read = input.read(header);
                if (read != 5 || !"%PDF-".equals(new String(header, java.nio.charset.StandardCharsets.US_ASCII))) {
                    throw new IllegalStateException("Generated PDF is structurally invalid.");
                }
            }
        }
    }

    private String serializeRepositorySnapshot(RepositorySnapshot snapshot) {
        try {
            byte[] json = OBJECT_MAPPER.writeValueAsBytes(snapshot);
            ByteArrayOutputStream compressed = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(compressed)) {
                gzip.write(json);
            }
            return "gzip-base64:" + Base64.getEncoder().encodeToString(compressed.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("Unable to persist repository snapshot.", e);
        }
    }

    private RepositorySnapshot readRepositorySnapshot(String persisted) {
        if (persisted == null || persisted.isBlank()) {
            throw new IllegalStateException("Repository snapshot is unavailable.");
        }
        try {
            if (persisted.startsWith("gzip-base64:")) {
                byte[] compressed = Base64.getDecoder().decode(persisted.substring("gzip-base64:".length()));
                try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed));
                     ByteArrayOutputStream json = new ByteArrayOutputStream()) {
                    gzip.transferTo(json);
                    return OBJECT_MAPPER.readValue(json.toByteArray(), RepositorySnapshot.class);
                }
            }
            // Backward compatibility for jobs created before compressed snapshots.
            return OBJECT_MAPPER.readValue(persisted, RepositorySnapshot.class);
        } catch (Exception e) {
            throw new IllegalStateException("Persisted repository snapshot is corrupted.", e);
        }
    }

    private ProjectFacts readProjectFacts(DocumentationJob job) {
        try { return OBJECT_MAPPER.readValue(job.getProjectFactsJson(), ProjectFacts.class); }
        catch (Exception e) { throw new IllegalStateException("Project analysis data is unavailable.", e); }
    }

    private void validateStudentContext(book.example.dto.StudentContext context) {
        if (context == null) return;
        if (context.getMotivation().length() > 6000 || context.getProblemStatement().length() > 6000
                || context.getTargetUsers().length() > 3000 || context.getExpectedBenefits().length() > 4000
                || context.getLimitations().length() > 4000 || context.getFutureIdeas().length() > 6000
                || context.getAdditionalNotes().length() > 4000) {
            throw new IllegalArgumentException("Project context contains an oversized field.");
        }
        if (context.getObjectives() != null && context.getObjectives().size() > 20) {
            throw new IllegalArgumentException("At most 20 project objectives may be supplied.");
        }
        if (context.getObjectives() != null) {
            for (String objective : context.getObjectives()) {
                if (objective != null && objective.length() > 1000) throw new IllegalArgumentException("A project objective is too long.");
            }
        }
    }

    private void validateStudentDetails(StudentProjectDetails details) {
        if (details == null) throw new IllegalArgumentException("Student and institution details are required.");
        require(details.getName(), "Student name", 200); require(details.getCourse(), "Course or degree", 200);
        require(details.getDepartment(), "Department", 200); require(details.getAcademicYear(), "Academic year", 100);
        require(details.getCollegeName(), "College name", 300); require(details.getUniversityName(), "University name", 300);
        require(details.getGuideName(), "Guide name", 200);
        optional(details.getRollNumber(), "Roll number", 100);
        optional(details.getEnrollmentNumber(), "Enrollment number", 100);
        optional(details.getGuideDesignation(), "Guide designation", 200);
        optional(details.getProjectTitleOverride(), "Project title", 300);
        if (details.getTeamMembers() != null) {
            if (details.getTeamMembers().size() > 20) throw new IllegalArgumentException("At most 20 team members may be supplied.");
            details.getTeamMembers().forEach(member -> optional(member, "Team member", 200));
        }
    }
    private void require(String value, String label, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required.");
        if (value.length() > max) throw new IllegalArgumentException(label + " is too long.");
    }
    private void optional(String value, String label, int max) {
        if (value != null && value.length() > max) throw new IllegalArgumentException(label + " is too long.");
    }
    private boolean isCancelled(String jobId) {
        return jobRepository.findById(jobId).map(job -> "CANCELLED".equals(job.getStatus())).orElse(true);
    }

    public void cancelJob(String jobId, UUID ownerId) {
        int cancelled = jobRepository.cancelJob(jobId, ownerId, LocalDateTime.now());
        if (cancelled == 0) {
            DocumentationJob job = jobRepository.findByJobIdAndOwnerId(jobId, ownerId)
                    .orElseThrow(() -> new IllegalArgumentException("Documentation job not found."));
            if ("CANCELLED".equals(job.getStatus())) return;
            if ("COMPLETED".equals(job.getStatus())) {
                throw new IllegalStateException("Documentation is already complete.");
            }
            throw new IllegalStateException("This documentation job cannot be cancelled in its current state.");
        }
        try {
            temporaryChromaManager.cleanup(jobId);
        } catch (RuntimeException cleanupFailure) {
            org.slf4j.LoggerFactory.getLogger(DocumentationOrchestrator.class)
                    .warn("Temporary Chroma cleanup failed after cancellation. jobId={}", jobId, cleanupFailure);
        }
        try {
            usageLedgerService.updateGenerationStatus(jobId, "CANCELLED");
        } catch (RuntimeException ignored) {
            // Cancellation must remain durable even if usage bookkeeping is unavailable.
        }
    }

    private void updateStatus(DocumentationJob job, String status) { job.setStatus(status); job.setUpdatedAt(LocalDateTime.now()); jobRepository.save(job); }
    private void markFailed(DocumentationJob job) { job.setStatus("FAILED"); job.setUpdatedAt(LocalDateTime.now()); jobRepository.save(job); try { usageLedgerService.updateGenerationStatus(job.getJobId(), "FAILED"); } catch (Exception ignored) {} }
    private String normalizeProjectName(String value) { if (value == null || value.isBlank()) return null; if (value.length() > 300) throw new IllegalArgumentException("Project name is too long."); return value.trim().replaceAll("[^a-zA-Z0-9 ._()\\-]", "").replaceAll("\\s+", " ").trim(); }
    private String safeMessage(Exception e) { return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(); }
}
