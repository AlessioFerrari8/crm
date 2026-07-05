package it.northleap.backend.repositories;

import it.northleap.backend.entities.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SessionRepository extends JpaRepository<Session, UUID> {
    Optional<Session> findByRefreshHash(String refreshHash);
    List<Session> findByUser_IdAndRevokedAtIsNull(UUID userId);
    // pulizia schedulata (SessionCleanupJob): sessioni ormai inutilizzabili da tempo
    long deleteByExpiresAtBefore(Instant cutoff);
    long deleteByRevokedAtBefore(Instant cutoff);
}
