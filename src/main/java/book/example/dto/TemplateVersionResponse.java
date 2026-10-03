package book.example.dto;

import book.example.Entity.TemplateVersion;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record TemplateVersionResponse(
        UUID id,
        String templateId,
        int version,
        String country,
        String state,
        String region,
        String city,
        String college,
        String university,
        List<String> aliases,
        String department,
        String degree,
        String projectType,
        String sourceType,
        String sourceUrl,
        String license,
        String status,
        String verificationStatus,
        boolean available,
        String securityScanStatus,
        String validationStatus,
        Map<String, Object> formatSchema,
        Map<String, Object> analysisMetadata,
        String frontPageConfig,
        String previewText,
        LocalDateTime createdAt,
        LocalDateTime publishedAt) {

    public static TemplateVersionResponse from(TemplateVersion version, ObjectMapper mapper) {
        return new TemplateVersionResponse(
                version.getId(), version.getCatalog().getTemplateId(), version.getVersion(),
                version.getCountry(), version.getState(), version.getRegion(), version.getCity(),
                version.getCollege(), version.getUniversity(), readAliases(version.getAliasesJson(), mapper),
                version.getDepartment(), version.getDegree(), version.getProjectType(), version.getSourceType(),
                version.getSourceUrl(), version.getLicense(), version.getStatus(), version.getVerificationStatus(),
                version.isAvailable(), version.getSecurityScanStatus(), version.getValidationStatus(),
                readMap(version.getFormatSchemaJson(), mapper), readMap(version.getAnalysisMetadataJson(), mapper),
                version.getFrontPageConfigJson(), version.getPreviewText(), version.getCreatedAt(), version.getPublishedAt());
    }

    private static List<String> readAliases(String json, ObjectMapper mapper) {
        try {
            return mapper.readValue(json, mapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception exception) {
            throw new IllegalStateException("Template aliases are invalid.", exception);
        }
    }

    private static Map<String, Object> readMap(String json, ObjectMapper mapper) {
        try {
            JsonNode node = mapper.readTree(json == null ? "{}" : json);
            return mapper.convertValue(node, mapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
        } catch (Exception exception) {
            throw new IllegalStateException("Template metadata is invalid.", exception);
        }
    }
}
