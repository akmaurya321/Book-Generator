package book.example.services;

import book.example.Entity.DocumentationJob;
import book.example.Repository.JobRepository;
import book.example.Repository.MarketplaceListingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class JobExpirationServiceTest {
    @TempDir
    Path storageRoot;

    private JobRepository jobRepository;
    private TemporaryChromaManager chromaManager;
    private MarketplaceListingRepository marketplaceListingRepository;
    private JobExpirationService service;

    @BeforeEach
    void setUp() {
        jobRepository = mock(JobRepository.class);
        chromaManager = mock(TemporaryChromaManager.class);
        marketplaceListingRepository = mock(MarketplaceListingRepository.class);
        service = new JobExpirationService(jobRepository, chromaManager, marketplaceListingRepository);
        ReflectionTestUtils.setField(service, "storageRoot", storageRoot.toString());
    }

    @Test
    void deletesDatabaseJobAndAllOwnedProjectFilesAfterChromaCleanup() throws Exception {
        String jobId = UUID.randomUUID().toString();
        UUID ownerId = UUID.randomUUID();
        DocumentationJob job = job(jobId, "COMPLETED");
        Path jobDirectory = storageRoot.resolve("jobs").resolve(jobId);
        Path generated = Files.createDirectories(jobDirectory.resolve("assets")).resolve("project.pdf");
        Path pdf = Files.createFile(generated);
        job.setPdfPath(pdf.toString());
        when(jobRepository.findByJobIdAndOwnerId(jobId, ownerId)).thenReturn(Optional.of(job));

        service.deleteJob(jobId, ownerId);

        verify(chromaManager).cleanupRequired(jobId);
        verify(jobRepository).delete(job);
        verify(jobRepository).flush();
        assertFalse(Files.exists(jobDirectory));
    }

    @Test
    void keepsDatabaseJobWhenChromaCleanupFails() {
        String jobId = UUID.randomUUID().toString();
        UUID ownerId = UUID.randomUUID();
        DocumentationJob job = job(jobId, "FAILED");
        when(jobRepository.findByJobIdAndOwnerId(jobId, ownerId)).thenReturn(Optional.of(job));
        doThrow(new IllegalStateException("Chroma is unavailable"))
                .when(chromaManager).cleanupRequired(jobId);

        assertThrows(IllegalStateException.class, () -> service.deleteJob(jobId, ownerId));

        verify(jobRepository, never()).delete(any());
    }

    @Test
    void refusesToDeleteAnActiveGeneration() {
        String jobId = UUID.randomUUID().toString();
        UUID ownerId = UUID.randomUUID();
        DocumentationJob job = job(jobId, "GENERATING_DOCUMENTATION");
        when(jobRepository.findByJobIdAndOwnerId(jobId, ownerId)).thenReturn(Optional.of(job));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.deleteJob(jobId, ownerId));

        assertEquals(409, error.getStatusCode().value());
        verifyNoInteractions(chromaManager);
        verify(jobRepository, never()).delete(any());
    }

    @Test
    void refusesToDeleteGenerationReferencedByAnUnarchivedMarketplaceListing() {
        String jobId = UUID.randomUUID().toString();
        UUID ownerId = UUID.randomUUID();
        DocumentationJob job = job(jobId, "COMPLETED");
        when(jobRepository.findByJobIdAndOwnerId(jobId, ownerId)).thenReturn(Optional.of(job));
        when(marketplaceListingRepository.existsBySourceJobIdAndStatusNot(jobId, "ARCHIVED")).thenReturn(true);

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.deleteJob(jobId, ownerId));

        assertEquals(409, error.getStatusCode().value());
        verifyNoInteractions(chromaManager);
        verify(jobRepository, never()).delete(any());
    }

    @Test
    void refusesToDeleteFilesOutsideTheProjectDirectory() {
        String jobId = UUID.randomUUID().toString();
        UUID ownerId = UUID.randomUUID();
        DocumentationJob job = job(jobId, "COMPLETED");
        job.setPdfPath(storageRoot.resolve("unrelated.pdf").toString());
        when(jobRepository.findByJobIdAndOwnerId(jobId, ownerId)).thenReturn(Optional.of(job));

        assertThrows(IllegalStateException.class, () -> service.deleteJob(jobId, ownerId));

        verify(jobRepository, never()).delete(any());
    }

    private DocumentationJob job(String jobId, String status) {
        DocumentationJob job = new DocumentationJob();
        job.setJobId(jobId);
        job.setStatus(status);
        return job;
    }
}
