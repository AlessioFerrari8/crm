package it.northleap.backend.portal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PortalUserRepository extends JpaRepository<PortalUser, UUID> {
    Optional<PortalUser> findByEmailIgnoreCase(String email);
    Optional<PortalUser> findByInviteTokenHash(String hash);
    List<PortalUser> findAllByOrderByCreatedAtDesc();
}
