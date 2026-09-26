package it.northleap.backend.repositories;

import it.northleap.backend.entities.MailboxConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MailboxConfigRepository extends JpaRepository<MailboxConfig, UUID> {
    List<MailboxConfig> findByActiveTrue();
}
