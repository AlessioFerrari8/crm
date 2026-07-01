package it.northleap.backend.repositories;

import it.northleap.backend.entities.SavedView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SavedViewRepository extends JpaRepository<SavedView, UUID> {
    List<SavedView> findByOwnerIdAndObjectKeyOrderByCreatedAtAsc(UUID ownerId, String objectKey);
    Optional<SavedView> findByIdAndOwnerId(UUID id, UUID ownerId);
}
