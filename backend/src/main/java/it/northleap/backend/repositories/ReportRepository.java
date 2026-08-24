package it.northleap.backend.repositories;

import it.northleap.backend.entities.Report;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReportRepository extends JpaRepository<Report, UUID> {
    // i report che un utente vede: i condivisi (ownerId null) più i suoi. Stessa regola dei tag, stesso motivo
    // "personale" qui vuol dire "solo mio", non "privato" in senso di sicurezza
    List<Report> findByOwnerIdIsNullOrOwnerIdOrderByFolderAscTitleAsc(UUID ownerId);

    List<Report> findByObjectKeyOrderByTitleAsc(String objectKey);

    // idempotenza del seeder
    boolean existsByTitle(String title);
}
