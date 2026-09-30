package it.northleap.backend.repositories;

import it.northleap.backend.entities.MailboxConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface MailboxConfigRepository extends JpaRepository<MailboxConfig, UUID> {
    List<MailboxConfig> findByActiveTrue();

    // il lock è una sola UPDATE condizionata: il database la fa una alla volta, quindi fra due
    // scansioni che partono insieme ne vince una. 1 = preso, 0 = qualcun altro sta girando
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("update MailboxConfig m set m.lockedUntil = :until where m.id = :id and (m.lockedUntil is null or m.lockedUntil < :now)")
    int tryLock(@Param("id") UUID id, @Param("now") Instant now, @Param("until") Instant until);
}
