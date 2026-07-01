package it.northleap.backend.repositories;

import it.northleap.backend.entities.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, UUID> {
    List<Permission> findByRole_IdInAndResource(List<UUID> roleIds, String resource);
    Optional<Permission> findByRole_IdAndResource(UUID roleId, String resource);
    List<Permission> findByRole_Id(UUID roleId);
    void deleteByRole_Id(UUID roleId);
}
