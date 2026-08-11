package it.northleap.backend.repositories;

import it.northleap.backend.entities.RecordTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import it.northleap.backend.entities.Record;

import java.util.List;
import java.util.UUID;

public interface RecordTagRepository extends JpaRepository<RecordTag, UUID> {
    List<RecordTag> findByRecordId(UUID recordId);

    // per la vista "record con questo tag", attraverso i moduli
    @Query("select rt.record from RecordTag rt where rt.tag.id = :tagId and rt.record.isDeleted = false")
    List<Record> findRecordsByTagId(@Param("tagId") UUID tagId);

    void deleteByRecordIdAndTagId(UUID recordId, UUID tagId);

    // usata dal merge duplicati e dall'eliminaizone definitiva
    void deleteByRecordId(UUID recordId);

    // cancellare il tag porta via le associazioni: sono etichette, non dati del record
    void deleteByTagId(UUID tagId);
}
