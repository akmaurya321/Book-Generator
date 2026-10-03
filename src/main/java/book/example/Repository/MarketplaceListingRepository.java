package book.example.Repository;

import book.example.Entity.MarketplaceListing;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

public interface MarketplaceListingRepository extends JpaRepository<MarketplaceListing, UUID> {
    @Query(
            value = "select listing from MarketplaceListing listing " +
                    "where listing.status = 'PUBLISHED' " +
                    "and (:category = '' or lower(listing.category) = lower(:category)) " +
                    "and (:originType = '' or listing.originType = :originType) " +
                    "and (:query = '' or lower(listing.title) like lower(concat('%', :query, '%')) " +
                    "or lower(listing.description) like lower(concat('%', :query, '%'))) ",
            countQuery = "select count(listing) from MarketplaceListing listing " +
                    "where listing.status = 'PUBLISHED' " +
                    "and (:category = '' or lower(listing.category) = lower(:category)) " +
                    "and (:originType = '' or listing.originType = :originType) " +
                    "and (:query = '' or lower(listing.title) like lower(concat('%', :query, '%')) " +
                    "or lower(listing.description) like lower(concat('%', :query, '%')))")
    Page<MarketplaceListing> searchPublished(
            @Param("query") String query,
            @Param("category") String category,
            @Param("originType") String originType,
            Pageable pageable);

    Page<MarketplaceListing> findByStatusAndCategoryIgnoreCase(String status, String category, Pageable pageable);
    Page<MarketplaceListing> findByStatus(String status, Pageable pageable);
    Page<MarketplaceListing> findByStatusAndTitleContainingIgnoreCaseOrStatusAndDescriptionContainingIgnoreCase(
            String firstStatus, String title, String secondStatus, String description, Pageable pageable);
    Optional<MarketplaceListing> findBySlugAndStatus(String slug, String status);
    Optional<MarketplaceListing> findByIdAndOwner_Id(UUID id, UUID ownerId);
    List<MarketplaceListing> findTop100ByOwner_IdOrderByUpdatedAtDesc(UUID ownerId);
    List<MarketplaceListing> findTop100ByStatusOrderBySubmittedAtAsc(String status);
    boolean existsBySlug(String slug);
    boolean existsBySourceJobIdAndStatusNot(String sourceJobId, String status);

    @Query("select distinct listing.category from MarketplaceListing listing where listing.status = 'PUBLISHED' order by listing.category")
    List<String> findPublishedCategories();
}
