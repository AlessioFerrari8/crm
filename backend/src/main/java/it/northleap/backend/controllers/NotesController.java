package it.northleap.backend.controllers;

import it.northleap.backend.dtos.CreateNoteDto;
import it.northleap.backend.entities.RecordNote;
import it.northleap.backend.security.Actor;
import it.northleap.backend.security.CurrentActor;
import it.northleap.backend.services.NotesService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// Note sui record, nidificate sotto /api/records come RelationsController: il segmento letterale
// "notes" vince sui path-variable di RecordsController nel routing di Spring, nessuna collisione.
@RestController
@RequestMapping("/api/records")
@RequiredArgsConstructor
public class NotesController {

    private final NotesService notesService;

    @GetMapping("/{recordId}/notes")
    public ResponseEntity<List<RecordNote>> list(@CurrentActor Actor actor, @PathVariable UUID recordId) {
        return ResponseEntity.ok(notesService.list(actor, recordId));
    }

    @PostMapping("/{recordId}/notes")
    public ResponseEntity<RecordNote> create(
            @CurrentActor Actor actor,
            @PathVariable UUID recordId,
            @Valid @RequestBody CreateNoteDto dto
    ) {
        return ResponseEntity.ok(notesService.create(actor, recordId, dto));
    }

    @DeleteMapping("/{recordId}/notes/{noteId}")
    public ResponseEntity<Void> remove(@CurrentActor Actor actor, @PathVariable UUID recordId, @PathVariable UUID noteId) {
        notesService.remove(actor, noteId);
        return ResponseEntity.noContent().build();
    }
}
