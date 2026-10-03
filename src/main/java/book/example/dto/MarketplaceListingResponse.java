package book.example.dto;

import book.example.Entity.MarketplaceListing;

import java.time.LocalDateTime;
import java.util.UUID;

public record MarketplaceListingResponse(
        UUID id,
        String slug,
        String listingType,
        String originType,
        String title,
        String description,
        String category,
        String technologies,
        String projectName,
        String status,
        String rejectionReason,
        String sellerName,
        String previewText,
        LocalDateTime publishedAt,
        boolean projectAvailable,
        boolean documentAvailable) {

    public static MarketplaceListingResponse from(MarketplaceListing listing, boolean includePrivateStatus) {
        return new MarketplaceListingResponse(
                listing.getId(),
                listing.getSlug(),
                listing.getListingType(),
                listing.getOriginType(),
                listing.getTitle(),
                listing.getDescription(),
                listing.getCategory(),
                listing.getTechnologies(),
                listing.getProjectName(),
                includePrivateStatus ? listing.getStatus() : "PUBLISHED",
                includePrivateStatus ? listing.getRejectionReason() : null,
                listing.getOwner().getDisplayName(),
                listing.getPreviewText(),
                listing.getPublishedAt(),
                listing.getProjectFilePath() != null,
                listing.getDocumentFilePath() != null);
    }
}
