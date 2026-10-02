package book.example.services;

import book.example.Entity.AppUser;
import book.example.Entity.UsageEvent;
import book.example.Repository.UsageEventRepository;
import book.example.Repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@Service
public class UsageLedgerService {

    private final UsageEventRepository usageEventRepository;
    private final UserRepository userRepository;

    public UsageLedgerService(UsageEventRepository usageEventRepository,
                              UserRepository userRepository) {
        this.usageEventRepository = usageEventRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public void assertGenerationAllowed(UUID ownerId) {
        if (!userRepository.existsById(ownerId)) {
            throw new IllegalStateException("Authenticated user was not found.");
        }
    }

    @Transactional
    public void recordGeneration(UUID ownerId, String jobId) {
        usageEventRepository.save(new UsageEvent(ownerId, jobId));
    }

    @Transactional
    public void updateGenerationStatus(String jobId, String status) {
        usageEventRepository.findByJobId(jobId).ifPresent(event -> {
            event.setStatus(status);
            usageEventRepository.save(event);
        });
    }

    @Transactional(readOnly = true)
    public List<UsageEvent> getRecentUsage(UUID ownerId) {
        return usageEventRepository.findTop100ByOwnerIdOrderByCreatedAtDesc(ownerId);
    }

    @Transactional(readOnly = true)
    public long getMonthlyUsage(UUID ownerId) {
        LocalDateTime monthStart = YearMonth.now().atDay(1).atStartOfDay();
        return usageEventRepository.countByOwnerIdAndStatusNotAndCreatedAtGreaterThanEqual(ownerId, "FAILED", monthStart);
    }


}
