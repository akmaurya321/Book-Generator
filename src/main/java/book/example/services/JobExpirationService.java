package book.example.services;

import book.example.Entity.DocumentationJob;
import book.example.Repository.JobRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class JobExpirationService {

    private final JobRepository jobRepository;
    private final TemporaryChromaManager temporaryChromaManager;
    @Value("${app.storage.root:generated}")
    private String storageRoot;

    public JobExpirationService(
            JobRepository jobRepository,
            TemporaryChromaManager temporaryChromaManager) {

        this.jobRepository = jobRepository;
        this.temporaryChromaManager =
                temporaryChromaManager;
    }

    @Transactional
    public void cleanupExpiredJobs() {

        LocalDateTime now =
                LocalDateTime.now();

        List<DocumentationJob> expiredJobs =
                jobRepository.findByExpiresAtBefore(now);

        for (DocumentationJob job : expiredJobs) {
            // Active jobs are durable until the user explicitly cancels them.
            // Expiration is only an artifact-retention mechanism for terminal jobs.
            if (!isTerminal(job.getStatus())) continue;
            cleanupJob(job);
        }
    }

    @Transactional
    public void deleteJob(String jobId, java.util.UUID ownerId) {
        DocumentationJob job = jobRepository.findByJobIdAndOwnerId(jobId, ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Documentation job not found."));
        if (!isTerminal(job.getStatus()) && !"WAITING_FOR_USER_CONFIGURATION".equals(job.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cancel or wait for this active project before deleting it.");
        }
        temporaryChromaManager.cleanupRequired(jobId);
        Path jobDirectory = Path.of(storageRoot, "jobs", jobId).toAbsolutePath().normalize();
        deleteProjectFileStrict(job.getDocumentPath(), jobDirectory);
        deleteProjectFileStrict(job.getPdfPath(), jobDirectory);
        deleteDirectoryStrict(jobDirectory);
        // Usage events are retained as accounting records so deleting a job cannot refund quota.
        jobRepository.delete(job);
        jobRepository.flush();
    }

    private boolean isTerminal(String status) {
        return "COMPLETED".equals(status) || "FAILED".equals(status) || "CANCELLED".equals(status);
    }

    private void cleanupJob(
            DocumentationJob job) {

        String jobId = job.getJobId();

        /*
         * Safety cleanup in case anything was left behind.
         */
        temporaryChromaManager.cleanup(jobId);

        deleteFile(job.getDocumentPath());
        deleteFile(job.getPdfPath());
        deleteDirectory(Path.of(storageRoot, "jobs", jobId));

        jobRepository.delete(job);
    }

    private void deleteDirectory(Path directory) {
        if (!Files.exists(directory)) return;
        try (var stream = Files.walk(directory)) {
            stream.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException ignored) {}
            });
        } catch (IOException ignored) {}
    }

    private void deleteFile(String filePath) {

        if (filePath == null ||
                filePath.isBlank()) {
            return;
        }

        try {

            Files.deleteIfExists(
                    Path.of(filePath)
            );

        } catch (IOException e) {

            System.err.println(
                    "Failed to delete generated file "
                            + filePath
                            + ": "
                            + e.getMessage()
            );
        }
    }

    private void deleteDirectoryStrict(Path directory) {
        if (!Files.exists(directory)) return;
        try (var stream = Files.walk(directory)) {
            for (Path path : stream.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to remove project files.", e);
        }
    }

    private void deleteProjectFileStrict(String filePath, Path jobDirectory) {
        if (filePath == null || filePath.isBlank()) return;
        Path file = Path.of(filePath).toAbsolutePath().normalize();
        if (!file.startsWith(jobDirectory)) {
            throw new IllegalStateException("Project output path is outside its storage directory.");
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to remove generated project files.", e);
        }
    }
}