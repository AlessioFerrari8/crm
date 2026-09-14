package it.northleap.backend.repositories;

import it.northleap.backend.entities.PdfTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PdfTemplateRepository extends JpaRepository<PdfTemplate, UUID> {
    Optional<PdfTemplate> findByKey(String key);

    List<PdfTemplate> findByObjectKeyOrderByLabelAsc(String objectKey);

    boolean existsByKey(String key);

    long countByObjectKeyAndIsSystemTrue(String objectKey);
}
