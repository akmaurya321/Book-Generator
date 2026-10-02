package book.example.Repository;

import book.example.Entity.UsageEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UsageEventRepository extends JpaRepository<UsageEvent, UUID> {
    Optional<UsageEvent> findByJobId(String jobId);
    List<UsageEvent> findTop100ByOwnerIdOrderByCreatedAtDesc(UUID ownerId);
    long countByOwnerIdAndStatusNotAndCreatedAtGreaterThanEqual(UUID ownerId, String status, LocalDateTime createdAt);
}
