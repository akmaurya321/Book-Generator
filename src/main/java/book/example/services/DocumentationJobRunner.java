package book.example.services;

import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class DocumentationJobRunner {
    private static final Logger log = LoggerFactory.getLogger(DocumentationJobRunner.class);
    private final DocumentationOrchestrator documentationOrchestrator;
    public DocumentationJobRunner(@Lazy DocumentationOrchestrator documentationOrchestrator) { this.documentationOrchestrator = documentationOrchestrator; }

    @Async("documentationTaskExecutor")
    public void resumeFinalGeneration(String jobId) {
        try {
            documentationOrchestrator.resumeFinalGeneration(jobId);
        } catch (RuntimeException exception) {
            // The orchestrator has already persisted the durable terminal/recovery
            // state. Do not leak an exception from an @Async void method into
            // Spring's SimpleAsyncUncaughtExceptionHandler.
            log.debug("Background final-generation pass ended for jobId={} (state persisted by orchestrator).", jobId, exception);
        }
    }

    @Async("documentationTaskExecutor")
    public void resumeAnalysis(String jobId) {
        try {
            documentationOrchestrator.resumeAnalysis(jobId);
        } catch (RuntimeException exception) {
            log.debug("Background analysis/indexing pass ended for jobId={} (state persisted by orchestrator).", jobId, exception);
        }
    }

    @Async("documentationTaskExecutor")
    public void retryFailedIndexing(String jobId) {
        try {
            documentationOrchestrator.retryFailedIndexing(jobId);
        } catch (RuntimeException exception) {
            log.debug("Background indexing retry ended for jobId={}.", jobId, exception);
        }
    }
}
