package it.northleap.backend.repositories;

import it.northleap.backend.entities.DocumentLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DocumentLineRepository extends JpaRepository<DocumentLine, UUID> {
    List<DocumentLine> findByDocumentIdOrderByLineNo(UUID documentId);
    void deleteByDocumentId(UUID documentId);
}
