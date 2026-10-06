package it.northleap.backend.controllers;


import it.northleap.backend.dtos.TimeEntryDto;
import it.northleap.backend.entities.TimeEntry;
import it.northleap.backend.repositories.TimeEntryRepository;
import it.northleap.backend.security.Actor;
import it.northleap.backend.security.CurrentActor;
import it.northleap.backend.services.TimeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.coyote.Response;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// i permessi li risolve TimeService (resource "time"): lo scope OWN/ALL cambia il risultato, non solo l'accesso
@RestController
@RequestMapping("/api/time")
@RequiredArgsConstructor
public class TimeController {

    private final TimeService timeService;

    @PostMapping("/start")
    public ResponseEntity<TimeEntry> start(@CurrentActor Actor actor, @RequestBody Map<String, UUID> body) {
        return ResponseEntity.ok(timeService.start(actor, body.get("recordId")));
    }

    @PostMapping("/stop")
    public ResponseEntity<TimeEntry> stop(@CurrentActor Actor actor) {
        return ResponseEntity.ok(timeService.stop(actor));
    }

    // 204 senza timer: un 404 qui finirebbe in un toast a ogni caricamento della pagina
    @GetMapping("/running")
    public ResponseEntity<TimeEntry> running(@CurrentActor Actor actor) {
        TimeEntry t = timeService.running(actor);
        return t == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(t);
    }

    @PostMapping
    public ResponseEntity<TimeEntry> create(@CurrentActor Actor actor, @Valid @RequestBody TimeEntryDto dto) {
        return ResponseEntity.ok(timeService.create(actor, dto));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<TimeEntry> update(@CurrentActor Actor actor, @PathVariable UUID id, @Valid
                                            @RequestBody TimeEntryDto dto) {
        return ResponseEntity.ok(timeService.update(actor, id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@CurrentActor Actor actor, @PathVariable UUID id) {
        timeService.delete(actor, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<TimeEntry>> list(@CurrentActor Actor actor,
                                                @RequestParam(required = false) UUID recordId,
                                                @RequestParam(required = false) UUID userId,
                                                @RequestParam(required = false) Instant from,
                                                @RequestParam(required = false) Instant to) {
        return ResponseEntity.ok(timeService.list(actor, recordId, userId, from, to));
    }

    @GetMapping("/summary")
    public ResponseEntity<List<TimeEntryRepository.SummaryRow>> summary(@CurrentActor Actor actor, @RequestParam
                                                                        UUID recordId) {
        return ResponseEntity.ok(timeService.summary(actor, recordId));
    }
}
