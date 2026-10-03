package book.example.services;

import book.example.Entity.TemplateCatalogEntry;
import book.example.Entity.TemplateVersion;
import book.example.Repository.TemplateCatalogRepository;
import book.example.Repository.TemplateVersionRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class UniversalTemplateInitializer implements ApplicationRunner {
    private static final UUID CATALOG_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID VERSION_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final TemplateCatalogRepository catalogRepository;
    private final TemplateVersionRepository versionRepository;
    private final GlobalTemplateService globalTemplateService;
    private final ObjectMapper objectMapper;

    public UniversalTemplateInitializer(
            TemplateCatalogRepository catalogRepository,
            TemplateVersionRepository versionRepository,
            GlobalTemplateService globalTemplateService,
            ObjectMapper objectMapper) {
        this.catalogRepository = catalogRepository;
        this.versionRepository = versionRepository;
        this.globalTemplateService = globalTemplateService;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        if (catalogRepository.findByTemplateId("universal-template").isPresent()) return;
        LocalDateTime now = LocalDateTime.now();
        TemplateCatalogEntry catalog = catalogRepository.saveAndFlush(
                new TemplateCatalogEntry(CATALOG_ID, "universal-template", now));
        TemplateVersion version = new TemplateVersion();
        version.setId(VERSION_ID);
        version.setCatalog(catalog);
        version.setVersion(1);
        version.setAliasesJson(objectMapper.writeValueAsString(List.of("universal", "generic", "custom")));
        version.setFrontPageConfigJson(objectMapper.writeValueAsString(Map.of()));
        version.setFormatSchemaJson(objectMapper.writeValueAsString(globalTemplateService.getTemplate().getFormat()));
        version.setAnalysisMetadataJson(objectMapper.writeValueAsString(Map.of(
                "source", "SYSTEM_DEFAULT_FORMAT",
                "confidence", "EXPLICIT_CONFIGURATION")));
        version.setPreviewText("Universal project-documentation format. Customize college and student details on the front page.");
        version.setSourceType("SYSTEM");
        version.setLicense("DocGen AI Universal Template");
        version.setStatus("PUBLISHED");
        version.setVerificationStatus("VERIFIED");
        version.setSecurityScanStatus("NOT_REQUIRED");
        version.setValidationStatus("VALID");
        version.setAvailable(true);
        version.setCreatedAt(now);
        version.setUpdatedAt(now);
        version.setPublishedAt(now);
        versionRepository.save(version);
    }
}
