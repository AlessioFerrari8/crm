package it.northleap.backend.repositories;

import it.northleap.backend.entities.Invite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface InviteRepository extends JpaRepository<Invite, UUID> {
    Optional<Invite> findByTokenHash(String tokenHash);
    boolean existsByRole_Id(UUID roleId);
}
