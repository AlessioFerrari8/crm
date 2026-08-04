package it.northleap.backend.repositories;

import it.northleap.backend.entities.Record;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RecordRepository extends JpaRepository<Record, UUID> {
    Optional<Record> findByIdAndIsDeletedFalse(UUID id);
    List<Record> findByIsDeletedFalseAndTitleContainingOrderByUpdatedAtDesc(String title);
    List<Record> findByIdInAndObjectType_IdAndIsDeletedFalse(List<UUID> ids, UUID objectTypeId);
    List<Record> findByIdInAndObjectType_IdAndOwnerIdAndIsDeletedFalse(List<UUID> ids, UUID objectTypeId, UUID ownerId);
    List<Record> findByObjectType_IdAndIsDeletedFalse(UUID objectTypeId);
    // cestino: ultimi eliminati del tipo (variante con owner per lo scope OWN)
    List<Record> findTop50ByObjectType_IdAndIsDeletedTrueOrderByUpdatedAtDesc(UUID objectTypeId);
    List<Record> findTop50ByObjectType_IdAndOwnerIdAndIsDeletedTrueOrderByUpdatedAtDesc(UUID objectTypeId, UUID ownerId);

    // per invetorySeeder
    boolean existsByObjectTypeIdAndTitleAndIsDeletedFalse(UUID objectTypeId, String title);
}
