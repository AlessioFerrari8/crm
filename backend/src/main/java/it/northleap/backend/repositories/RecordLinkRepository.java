package it.northleap.backend.repositories;

import it.northleap.backend.entities.RecordLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RecordLinkRepository extends JpaRepository<RecordLink, UUID> {
    List<RecordLink> findBySource_Id(UUID sourceId);
    List<RecordLink> findByTarget_Id(UUID targetId);
    Optional<RecordLink> findBySource_IdAndTarget_IdAndRelationKey(UUID sourceId, UUID targetId, String relationKey);

    // Varianti per la lettura: un record cestinato non deve comparire fra i collegamenti di
    // chi lo punta, ma il link resta a DB - il ripristino dal cestino lo deve ritrovare intatto.
    // Filtro in lettura invece di cancellazione, perche' la cancellazione non e' reversibile.
    // L'asimmetria e' voluta: sui link in uscita si filtra il target, su quelli in entrata il
    // source, in entrambi i casi si nasconde l'altro record, non quello che si sta guardando.
    List<RecordLink> findBySource_IdAndTarget_IsDeletedFalse(UUID sourceId);
    List<RecordLink> findByTarget_IdAndSource_IsDeletedFalse(UUID targetId);
}
