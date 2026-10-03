package book.example.Repository;

import book.example.Entity.TemplateAuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

public interface TemplateAuditEventRepository extends JpaRepository<TemplateAuditEvent, UUID> {
    List<TemplateAuditEvent> findTop200ByTemplateVersion_IdOrderByCreatedAtDesc(UUID templateVersionId);
    @Modifying
    @Transactional
    @Query("delete from TemplateAuditEvent event where event.templateVersion.id = :versionId")
    void deleteByTemplateVersionId(@Param("versionId") UUID templateVersionId);
}
