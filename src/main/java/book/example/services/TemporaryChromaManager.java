package book.example.services;

import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class TemporaryChromaManager {

    private static final Logger logger = LoggerFactory.getLogger(TemporaryChromaManager.class);
    private final ChromaService chromaService;

    public TemporaryChromaManager(
            ChromaService chromaService) {

        this.chromaService = chromaService;
    }

    public void cleanup(String jobId) {

        if (jobId == null || jobId.isBlank()) {
            return;
        }

        try {

            chromaService.deleteJobData(jobId);

        } catch (Exception e) {

            /*
             * Cleanup failure should be logged but should not
             * hide the original documentation result.
             */
                logger.warn("Failed to clean temporary Chroma data. jobId={}", jobId, e);
        }
    }

    public void cleanupRequired(String jobId) {
        if (jobId == null || jobId.isBlank()) {
            throw new IllegalArgumentException("Job ID is required for Chroma cleanup.");
        }
        chromaService.deleteJobData(jobId);
    }
}