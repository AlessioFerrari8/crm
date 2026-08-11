package it.northleap.backend.repositories;


import it.northleap.backend.entities.DocumentSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import java.util.UUID;

public interface DocumentSequenceRepository extends JpaRepository<DocumentSequence, UUID> {

    // PESSIMISTIC_WRITE = SELECT ... FOR UPDATE: due richieste simultanee sullo stesso
    // (modulo, anno) si mettono in fila invece di leggere lo stesso valore e produrre
    // due documenti con lo stesso numero
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<DocumentSequence> findByObjectKeyAndYear(String objectKey, int year);

}
