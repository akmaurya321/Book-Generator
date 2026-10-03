package book.example.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "marketplace_audit_events")
public class MarketplaceAuditEvent {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "listing_id", nullable = false)
    private MarketplaceListing listing;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private AppUser actor;

    @Column(nullable = false, length = 40)
    private String action;

    @Column(length = 2000)
    private String details;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected MarketplaceAuditEvent() {
    }

    public MarketplaceAuditEvent(MarketplaceListing listing, AppUser actor, String action, String details) {
        this.id = UUID.randomUUID();
        this.listing = listing;
        this.actor = actor;
        this.action = action;
        this.details = details;
        this.createdAt = LocalDateTime.now();
    }
}
