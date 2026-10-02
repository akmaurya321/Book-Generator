package book.example.Repository;

import book.example.Entity.DocumentationJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobRepository
        extends JpaRepository<DocumentationJob, String> {

        @Transactional(readOnly = true)
        List<DocumentationJob> findByExpiresAtBefore(
            LocalDateTime time
    );

                @Transactional(readOnly = true)
        Optional<DocumentationJob> findByJobIdAndOwnerId(String jobId, UUID ownerId);

        @Transactional(readOnly = true)
        List<DocumentationJob> findTop50ByOwnerIdOrderByUpdatedAtDesc(UUID ownerId);

                        @Transactional(readOnly = true)
            List<DocumentationJob> findByStatusInAndUpdatedAtBefore(
                    List<String> statuses, LocalDateTime updatedBefore);

        @Transactional(readOnly = true)
        List<DocumentationJob> findByStatusAndUpdatedAtBefore(
                String status, LocalDateTime updatedBefore);

            @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update DocumentationJob job set job.status = 'QUEUED_FOR_GENERATION', job.updatedAt = :now " +
            "where job.jobId = :jobId and job.status = 'WAITING_FOR_USER_CONFIGURATION' " +
            "and (job.indexingFailuresJson is null or job.indexingFailuresJson = '' or job.indexingFailuresJson = '[]')")
    int claimForGeneration(@Param("jobId") String jobId, @Param("now") LocalDateTime now);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update DocumentationJob job set job.status = 'QUEUED_FOR_GENERATION', job.updatedAt = :now " +
            "where job.jobId = :jobId and job.status in ('WAITING_FOR_INDEXING','RECOVERING_INDEXING','RECOVERING') " +
            "and (job.indexingFailuresJson is null or job.indexingFailuresJson = '' or job.indexingFailuresJson = '[]')")
    int claimForGenerationAfterIndexing(@Param("jobId") String jobId, @Param("now") LocalDateTime now);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update DocumentationJob job set job.indexingFailuresJson = :failures, job.updatedAt = :now " +
            "where job.jobId = :jobId and job.status in ('INDEXING_PROJECT','WAITING_FOR_USER_CONFIGURATION','WAITING_FOR_INDEXING','RECOVERING_INDEXING','QUEUED_FOR_GENERATION','GENERATING_DOCUMENTATION','RECOVERING')")
    int updateIndexingFailuresIfWaiting(@Param("jobId") String jobId,
                                        @Param("failures") String failures,
                                        @Param("now") LocalDateTime now);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update DocumentationJob job set job.status = 'WAITING_FOR_INDEXING', job.updatedAt = :now " +
            "where job.jobId = :jobId and job.status in ('INDEXING_PROJECT','WAITING_FOR_USER_CONFIGURATION','WAITING_FOR_INDEXING','RECOVERING_INDEXING','RECOVERING')")
    int markWaitingForIndexing(@Param("jobId") String jobId, @Param("now") LocalDateTime now);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update DocumentationJob job set job.planJson = :planJson, job.updatedAt = :now " +
            "where job.jobId = :jobId and job.status in ('WAITING_FOR_INDEXING','RECOVERING_INDEXING','RECOVERING') " +
            "and (job.indexingFailuresJson is null or job.indexingFailuresJson = '' or job.indexingFailuresJson = '[]')")
    int updatePlanAfterIndexing(@Param("jobId") String jobId,
                                @Param("planJson") String planJson,
                                @Param("now") LocalDateTime now);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update DocumentationJob job set job.status = 'RECOVERING', " +
            "job.updatedAt = :now, job.attemptCount = job.attemptCount + 1 " +
            "where job.jobId = :jobId and job.status in :statuses " +
            "and job.updatedAt < :updatedBefore")
    int claimForRecovery(@Param("jobId") String jobId,
                         @Param("statuses") List<String> statuses,
                         @Param("updatedBefore") LocalDateTime updatedBefore,
                         @Param("now") LocalDateTime now, int i);

    /**
     * RECOVERING is an explicit durable retry state. It is intentionally not
     * capped by the normal stale-job attempt limit: transient LLM/network
     * failures must keep retrying until the job completes or the user explicitly
     * cancels it.
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update DocumentationJob job set job.status = 'RECOVERING', " +
            "job.updatedAt = :now, job.attemptCount = job.attemptCount + 1 " +
            "where job.jobId = :jobId and job.status = 'RECOVERING' " +
            "and job.updatedAt < :updatedBefore")
    int claimRecovering(@Param("jobId") String jobId,
                        @Param("updatedBefore") LocalDateTime updatedBefore,
                        @Param("now") LocalDateTime now);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update DocumentationJob job set job.status = 'RECOVERING_INDEXING', " +
            "job.updatedAt = :now, job.attemptCount = job.attemptCount + 1 " +
            "where job.jobId = :jobId and job.status = 'RECOVERING_INDEXING' " +
            "and job.updatedAt < :updatedBefore")
    int claimRecoveringIndexing(@Param("jobId") String jobId,
                                @Param("updatedBefore") LocalDateTime updatedBefore,
                                @Param("now") LocalDateTime now);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update DocumentationJob job set job.status = 'CANCELLED', job.updatedAt = :now " +
            "where job.jobId = :jobId and job.ownerId = :ownerId " +
            "and job.status not in ('COMPLETED','FAILED','CANCELLED')")
    int cancelJob(@Param("jobId") String jobId, @Param("ownerId") UUID ownerId, @Param("now") LocalDateTime now);
}