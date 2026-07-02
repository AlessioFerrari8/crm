package it.northleap.backend.services;

import it.northleap.backend.dtos.CreateNoteDto;
import it.northleap.backend.entities.Record;
import it.northleap.backend.entities.RecordNote;
import it.northleap.backend.exceptions.NotFoundException;
import it.northleap.backend.exceptions.RbacDeniedException;
import it.northleap.backend.repositories.RecordNoteRepository;
import it.northleap.backend.repositories.RecordRepository;
import it.northleap.backend.repositories.UserRepository;
import it.northleap.backend.security.Actor;
import it.northleap.backend.security.ActorType;
import it.northleap.backend.security.PermAction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

// Note sui record. RBAC dinamico come RelationsService: la risorsa è l'objectType.key del record,
// quindi niente @RequirePerm — leggere le note richiede READ sul tipo, scriverne WRITE.
// L'eliminazione è riservata all'autore (una nota è una voce personale della timeline).
@Service
@RequiredArgsConstructor
public class NotesService {

    private final RecordNoteRepository recordNoteRepository;
    private final RecordRepository recordRepository;
    private final UserRepository userRepository;
    private final RbacService rbacService;

    public List<RecordNote> list(Actor actor, UUID recordId) {
        assertPerm(actor, recordId, PermAction.READ);
        return recordNoteRepository.findByRecordIdOrderByCreatedAtDesc(recordId);
    }

    @Transactional
    public RecordNote create(Actor actor, UUID recordId, CreateNoteDto dto) {
        assertPerm(actor, recordId, PermAction.WRITE);
        RecordNote note = new RecordNote();
        note.setRecordId(recordId);
        note.setAuthorId(actor.id());
        note.setAuthorName(resolveAuthorName(actor));
        note.setBody(dto.getBody().trim());
        return recordNoteRepository.save(note);
    }

    @Transactional
    public void remove(Actor actor, UUID noteId) {
        RecordNote note = recordNoteRepository.findById(noteId)
                .orElseThrow(() -> new NotFoundException("Nota non trovata"));
        if (!actor.id().equals(note.getAuthorId())) {
            throw new RbacDeniedException("Puoi eliminare solo le tue note");
        }
        recordNoteRepository.delete(note);
    }

    private void assertPerm(Actor actor, UUID recordId, PermAction action) {
        Record record = recordRepository.findByIdAndIsDeletedFalse(recordId)
                .orElseThrow(() -> new NotFoundException("Record non trovato"));
        RbacService.Resolution resolution =
                rbacService.resolve(actor.roleIds(), record.getObjectType().getKey(), action);
        if (!resolution.allowed()) {
            throw new RbacDeniedException();
        }
    }

    // nome visualizzato: nome dell'utente se attore USER, altrimenti email o etichetta API key
    private String resolveAuthorName(Actor actor) {
        if (actor.type() == ActorType.USER) {
            return userRepository.findById(actor.id())
                    .map(u -> u.getName() != null && !u.getName().isBlank() ? u.getName() : u.getEmail())
                    .orElse(actor.email() != null ? actor.email() : "Utente");
        }
        return actor.email() != null ? actor.email() : "API key";
    }
}
