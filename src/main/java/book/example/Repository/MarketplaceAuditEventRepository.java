package book.example.Repository;

import book.example.Entity.MarketplaceAuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MarketplaceAuditEventRepository extends JpaRepository<MarketplaceAuditEvent, UUID> {
}
