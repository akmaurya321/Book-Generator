package book.example.Repository;

import book.example.Entity.DocumentationJob;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class JobRepositoryTransactionTest {

    @Autowired
    private JobRepository jobRepository;

    @Test
    void claimForRecovery_shouldWorkWithoutCallerTransaction() {
        DocumentationJob job = new DocumentationJob();
        job.setJobId("test-job-claim-" + System.nanoTime());
        job.setStatus("RECEIVED");
        job.setUpdatedAt(LocalDateTime.now().minusMinutes(31));
        job.setExpiresAt(LocalDateTime.now().plusMinutes(30));
        job.setAttemptCount(0);
        jobRepository.saveAndFlush(job);

        int claimed = jobRepository.claimForRecovery(
                job.getJobId(),
                List.of("RECEIVED", "ANALYZING_PROJECT"),
                LocalDateTime.now().minusMinutes(30),
                LocalDateTime.now(),
                3
        );

        assertThat(claimed).isEqualTo(1);
        DocumentationJob updated = jobRepository.findById(job.getJobId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo("RECOVERING");
        assertThat(updated.getAttemptCount()).isEqualTo(1);
    }

    @Test
    void generationClaimIsRejectedUntilAllIndexingFailuresAreCleared() {
        DocumentationJob waitingJob = new DocumentationJob();
        waitingJob.setJobId("test-job-index-gate-" + System.nanoTime());
        waitingJob.setStatus("WAITING_FOR_USER_CONFIGURATION");
        waitingJob.setIndexingFailuresJson("[\"chunk-1\"]");
        waitingJob.setUpdatedAt(LocalDateTime.now());
        waitingJob.setExpiresAt(LocalDateTime.now().plusMinutes(30));
        jobRepository.saveAndFlush(waitingJob);

        assertThat(jobRepository.claimForGeneration(
                waitingJob.getJobId(),
                LocalDateTime.now())).isZero();
        assertThat(jobRepository.claimForGenerationAfterIndexing(
                waitingJob.getJobId(),
                LocalDateTime.now())).isZero();

        assertThat(jobRepository.updateIndexingFailuresIfWaiting(
                waitingJob.getJobId(),
                "[]",
                LocalDateTime.now())).isEqualTo(1);
        assertThat(jobRepository.markWaitingForIndexing(
                waitingJob.getJobId(),
                LocalDateTime.now())).isEqualTo(1);
        assertThat(jobRepository.claimForGenerationAfterIndexing(
                waitingJob.getJobId(),
                LocalDateTime.now())).isEqualTo(1);
    }
}
