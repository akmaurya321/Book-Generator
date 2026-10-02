package book.example.services;

import book.example.dto.RepositoryChunk;
import book.example.dto.RepositoryIndexResult;
import book.example.dto.RepositorySnapshot;
import book.example.Entity.DocumentationJob;
import book.example.Repository.JobRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class RepositoryIngestionService {

    private final RepositoryChunker repositoryChunker;
    private final EmbeddingService embeddingService;
    private final ChromaService chromaService;
    private final JobRepository jobRepository;

    public RepositoryIngestionService(RepositoryChunker repositoryChunker,
                                      EmbeddingService embeddingService,
                                      ChromaService chromaService,
                                      JobRepository jobRepository) {
        this.repositoryChunker = repositoryChunker;
        this.embeddingService = embeddingService;
        this.chromaService = chromaService;
        this.jobRepository = jobRepository;
    }

    /**
     * Best-effort indexing. Successful chunks are committed immediately and remain usable.
     * Only chunks that fail after their bounded local retries are returned for later recovery.
     */
    public RepositoryIndexResult ingest(String jobId, RepositorySnapshot snapshot) {
        return ingest(jobId, snapshot, null);
    }

    /** Retry only the supplied failed chunk IDs; null/empty means process the full snapshot. */
    public RepositoryIndexResult ingest(String jobId, RepositorySnapshot snapshot, Set<String> onlyChunkIds) {
        if (jobId == null || jobId.isBlank()) throw new IllegalArgumentException("Job ID is required");
        if (snapshot == null) throw new IllegalArgumentException("Repository snapshot is required");

        List<RepositoryChunk> chunks = repositoryChunker.chunk(snapshot);
        if (chunks == null || chunks.isEmpty()) throw new IllegalStateException("No indexable project content was found.");

        Set<String> filter = onlyChunkIds == null ? Set.of() : new HashSet<>(onlyChunkIds);
        boolean filtered = !filter.isEmpty();
        List<RepositoryChunk> candidates = new ArrayList<>();
        for (RepositoryChunk chunk : chunks) {
            if (chunk == null || chunk.getContent() == null || chunk.getContent().isBlank()) continue;
            if (!filtered || filter.contains(chunk.getChunkId())) candidates.add(chunk);
        }

        if (filtered && candidates.isEmpty()) {
            RepositoryIndexResult result = new RepositoryIndexResult();
            result.setSuccessfulChunks(0);
            result.setTotalChunks(filter.size());
            result.setFailedChunkIds(new ArrayList<>(filter));
            return result;
        }

        String collectionId = chromaService.getOrCreateCollection(jobId);
        RepositoryIndexResult result = new RepositoryIndexResult();
        result.setTotalChunks(candidates.size());

        for (RepositoryChunk chunk : candidates) {
            ensureJobActive(jobId);
            RuntimeException lastFailure = null;
            boolean stored = false;
            for (int attempt = 1; attempt <= 2; attempt++) {
                try {
                    List<Float> embedding = embeddingService.embed(chunk.getContent());
                    chromaService.addChunkToCollection(jobId, collectionId, chunk, embedding);
                    stored = true;
                    lastFailure = null;
                    break;
                } catch (RuntimeException failure) {
                    lastFailure = failure;
                    if (attempt < 2) {
                        try { Thread.sleep(300L * attempt); }
                        catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            lastFailure = new IllegalStateException("Project indexing retry was interrupted.", interrupted);
                            break;
                        }
                    }
                }
            }
            if (stored) {
                result.setSuccessfulChunks(result.getSuccessfulChunks() + 1);
            } else {
                result.getFailedChunkIds().add(chunk.getChunkId());
                result.getFailedFilePaths().add(chunk.getFilePath());
            }
        }

        if (result.getSuccessfulChunks() == 0) {
            throw new IllegalStateException("Project indexing produced no usable Chroma chunks.");
        }
        return result;
    }

    private void ensureJobActive(String jobId) {
        DocumentationJob job = jobRepository.findById(jobId).orElseThrow(() ->
                new IllegalStateException("Documentation job no longer exists: " + jobId));
        if ("CANCELLED".equals(job.getStatus())) {
            throw new JobCancelledException();
        }
    }

    private static final class JobCancelledException extends RuntimeException {
        private JobCancelledException() { super("Documentation job was cancelled."); }
    }
}
