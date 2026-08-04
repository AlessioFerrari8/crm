package it.northleap.backend.repositories;

import it.northleap.backend.entities.FieldDef;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FieldDefRepository extends JpaRepository<FieldDef, UUID> {
    Optional<FieldDef> findByObjectType_IdAndKey(UUID objectTypeId, String key);
    List<FieldDef> findByObjectTypeId(UUID objectTypeId);
}
