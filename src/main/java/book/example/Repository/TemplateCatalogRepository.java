package book.example.Repository;

import book.example.Entity.TemplateCatalogEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TemplateCatalogRepository extends JpaRepository<TemplateCatalogEntry, UUID> {
    Optional<TemplateCatalogEntry> findByTemplateId(String templateId);
    boolean existsByTemplateId(String templateId);
}
