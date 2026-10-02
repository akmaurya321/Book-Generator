package book.example.services;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class JobCleanupScheduler {

    private final JobExpirationService
            jobExpirationService;

    public JobCleanupScheduler(
            JobExpirationService jobExpirationService) {

        this.jobExpirationService =
                jobExpirationService;
    }

    @Scheduled(
            fixedRate = 60 * 60 * 1000
    )
    public void cleanupExpiredJobs() {

        jobExpirationService.cleanupExpiredJobs();
    }
}