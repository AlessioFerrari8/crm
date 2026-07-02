package it.northleap.backend.repositories;

import it.northleap.backend.entities.RecordNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RecordNoteRepository extends JpaRepository<RecordNote, UUID> {
    List<RecordNote> findByRecordIdOrderByCreatedAtDesc(UUID recordId);
}
