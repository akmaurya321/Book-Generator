package book.example.Repository;

import book.example.Entity.TemplateVersion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface TemplateVersionRepository extends JpaRepository<TemplateVersion, UUID> {
    Optional<TemplateVersion> findByCatalog_TemplateIdAndVersion(String templateId, int version);
    Optional<TemplateVersion> findFirstByCatalog_TemplateIdAndStatusAndAvailableOrderByVersionDesc(
            String templateId, String status, boolean available);
    Optional<TemplateVersion> findFirstByCatalog_TemplateIdAndVersionAndStatusAndAvailable(
            String templateId, int version, String status, boolean available);
    Optional<TemplateVersion> findFirstByCatalog_TemplateIdOrderByVersionDesc(String templateId);
    int countByCatalog_Id(UUID catalogId);
    List<TemplateVersion> findAllByStatusOrderByUpdatedAtDesc(String status);
    List<TemplateVersion> findTop100ByCreatedByOrderByCreatedAtDesc(UUID createdBy);

    @Query(
            value = "select version from TemplateVersion version " +
                    "where version.status = 'PUBLISHED' and version.available = true " +
                    "and (:projectType = '' or version.projectType is null or version.projectType = :projectType) " +
                    "and (:country is null or :country = '' or lower(version.country) like lower(concat('%', :country, '%'))) " +
                    "and (:state is null or :state = '' or lower(version.state) like lower(concat('%', :state, '%'))) " +
                    "and (:department is null or :department = '' or lower(version.department) like lower(concat('%', :department, '%'))) " +
                    "and (:degree is null or :degree = '' or lower(version.degree) like lower(concat('%', :degree, '%'))) " +
                    "and (:query is null or :query = '' or lower(version.college) like lower(concat('%', :query, '%')) " +
                    "or lower(version.university) like lower(concat('%', :query, '%')) " +
                    "or lower(version.city) like lower(concat('%', :query, '%')) " +
                    "or lower(version.state) like lower(concat('%', :query, '%')) " +
                    "or lower(version.country) like lower(concat('%', :query, '%')) " +
                    "or lower(version.department) like lower(concat('%', :query, '%')) " +
                    "or lower(version.degree) like lower(concat('%', :query, '%')) " +
                    "or lower(version.catalog.templateId) like lower(concat('%', :query, '%')) " +
                    "or lower(version.aliasesJson) like lower(concat('%', :query, '%')))",
            countQuery = "select count(version) from TemplateVersion version " +
                    "where version.status = 'PUBLISHED' and version.available = true " +
                    "and (:projectType = '' or version.projectType is null or version.projectType = :projectType) " +
                    "and (:country is null or :country = '' or lower(version.country) like lower(concat('%', :country, '%'))) " +
                    "and (:state is null or :state = '' or lower(version.state) like lower(concat('%', :state, '%'))) " +
                    "and (:department is null or :department = '' or lower(version.department) like lower(concat('%', :department, '%'))) " +
                    "and (:degree is null or :degree = '' or lower(version.degree) like lower(concat('%', :degree, '%'))) " +
                    "and (:query is null or :query = '' or lower(version.college) like lower(concat('%', :query, '%')) " +
                    "or lower(version.university) like lower(concat('%', :query, '%')) " +
                    "or lower(version.city) like lower(concat('%', :query, '%')) " +
                    "or lower(version.state) like lower(concat('%', :query, '%')) " +
                    "or lower(version.country) like lower(concat('%', :query, '%')) " +
                    "or lower(version.department) like lower(concat('%', :query, '%')) " +
                    "or lower(version.degree) like lower(concat('%', :query, '%')) " +
                    "or lower(version.catalog.templateId) like lower(concat('%', :query, '%')) " +
                    "or lower(version.aliasesJson) like lower(concat('%', :query, '%')))")
    Page<TemplateVersion> searchPublished(
            @Param("query") String query,
            @Param("country") String country,
            @Param("state") String state,
            @Param("department") String department,
            @Param("degree") String degree,
            @Param("projectType") String projectType,
            Pageable pageable);
}
