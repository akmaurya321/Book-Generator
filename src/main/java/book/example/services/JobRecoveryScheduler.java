package book.example.services;

import book.example.Entity.DocumentationJob;
import book.example.Repository.JobRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@ConditionalOnProperty(prefix = "app.jobs.recovery", name = "enabled", havingValue = "true", matchIfMissing = true)
public class JobRecoveryScheduler {
    private static final List<String> ACTIVE_STATUSES = List.of(
            "ANALYZING_PROJECT", "INDEXING_PROJECT", "WAITING_FOR_INDEXING", "QUEUED_FOR_GENERATION", "GENERATING_DOCUMENTATION",
            "VALIDATING_ASSETS", "ASSEMBLING_DOCUMENT", "VALIDATING_DOCUMENT", "PREPARING_PDF", "RECOVERING", "RECOVERING_INDEXING");

    private final JobRepository jobRepository;
    private final DocumentationJobRunner jobRunner;
    private final UsageLedgerService usageLedgerService;

    @Value("${app.jobs.stale-threshold-minutes:30}") private int staleThresholdMinutes;
    @Value("${app.jobs.recovery-retry-delay-millis:30000}") private long recoveryRetryDelayMillis;

    public JobRecoveryScheduler(JobRepository jobRepository, DocumentationJobRunner jobRunner, UsageLedgerService usageLedgerService) {
        this.jobRepository = jobRepository;
        this.jobRunner = jobRunner;
        this.usageLedgerService = usageLedgerService;
    }

    @Scheduled(fixedDelayString = "${app.jobs.recovery-interval-millis:60000}")
    public void recoverStalledJobs() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime staleBefore = now.minusMinutes(staleThresholdMinutes);
        List<DocumentationJob> staleJobs = jobRepository.findByStatusInAndUpdatedAtBefore(
                ACTIVE_STATUSES.stream().filter(status -> !"RECOVERING".equals(status) && !"RECOVERING_INDEXING".equals(status)).toList(), staleBefore);
        processCandidates(staleJobs, staleBefore, now);

        // RECOVERING is a deliberate short-lived state used for transient LLM/network
        // failures. It must not wait for the long stalled-job threshold (normally 30m).
        LocalDateTime recoveringBefore = now.minusNanos(Math.max(1_000L, recoveryRetryDelayMillis) * 1_000_000L);
        List<DocumentationJob> recoveringJobs = jobRepository.findByStatusAndUpdatedAtBefore(
                "RECOVERING", recoveringBefore);
        processCandidates(recoveringJobs, recoveringBefore, now);
        List<DocumentationJob> recoveringIndexingJobs = jobRepository.findByStatusAndUpdatedAtBefore(
                "RECOVERING_INDEXING", recoveringBefore);
        processCandidates(recoveringIndexingJobs, recoveringBefore, now);
    }

    private void processCandidates(List<DocumentationJob> candidates, LocalDateTime staleBefore, LocalDateTime now) {
        for (DocumentationJob candidate : candidates) {
            int claimed;
            if ("RECOVERING".equals(candidate.getStatus())) {
                // A transient provider/network recovery is deliberately unbounded
                // until the job completes or the user cancels it.
                claimed = jobRepository.claimRecovering(candidate.getJobId(), staleBefore, now);
            } else if ("RECOVERING_INDEXING".equals(candidate.getStatus())) {
                claimed = jobRepository.claimRecoveringIndexing(candidate.getJobId(), staleBefore, now);
            } else {
                // A stalled active job is still a live user job. Never turn it into
                // a terminal failure merely because the scheduler has retried it a
                // certain number of times. Permanent failures are decided by the
                // orchestration/validation layer; recovery itself continues until
                // completion or explicit user cancellation.
                claimed = jobRepository.claimForRecovery(candidate.getJobId(), ACTIVE_STATUSES, staleBefore, now, 3);
            }
            if (claimed == 0) continue;
            jobRepository.findById(candidate.getJobId()).ifPresent(job -> {
                try {
                    if (("INDEXING_PROJECT".equals(candidate.getStatus()) || "WAITING_FOR_INDEXING".equals(candidate.getStatus()) || "RECOVERING_INDEXING".equals(candidate.getStatus()))
                            && job.getProjectFactsJson() != null
                            && job.getRepositorySnapshotJson() != null) {
                        jobRunner.resumeAnalysis(job.getJobId());
                    } else if (job.getProjectFactsJson() != null && job.getPlanJson() != null) {
                        jobRunner.resumeFinalGeneration(job.getJobId());
                    } else {
                        markFailed(job.getJobId());
                    }
                } catch (RuntimeException ignored) {
                    // Async runners persist their own retryable state.
                }
            });
        }
    }

    private void markFailed(String jobId) {
        jobRepository.findById(jobId).ifPresent(job -> {
            job.setStatus("FAILED"); job.setUpdatedAt(LocalDateTime.now()); jobRepository.save(job);
            try { usageLedgerService.updateGenerationStatus(jobId, "FAILED"); } catch (Exception ignored) {}
        });
    }
}
