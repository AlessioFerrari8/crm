package it.northleap.backend.repositories;

import it.northleap.backend.entities.UserRole;
import it.northleap.backend.entities.UserRoleId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {
    List<UserRole> findByUser_Id(UUID userId);
    void deleteByUser_Id(UUID userId);
    boolean existsByRole_Id(UUID roleId);
}
