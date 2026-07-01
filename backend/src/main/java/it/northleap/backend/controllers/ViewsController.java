package it.northleap.backend.controllers;

import it.northleap.backend.dtos.CreateSavedViewDto;
import it.northleap.backend.entities.SavedView;
import it.northleap.backend.security.Actor;
import it.northleap.backend.security.CurrentActor;
import it.northleap.backend.services.SavedViewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// Viste salvate della record-list. Risorsa personale (scoped su ownerId nel service), quindi
// niente @RequirePerm - stesso trattamento di NotificationController.
@RestController
@RequestMapping("/api/views")
@RequiredArgsConstructor
public class ViewsController {

    private final SavedViewService savedViewService;

    @GetMapping
    public ResponseEntity<List<SavedView>> list(@CurrentActor Actor actor, @RequestParam String objectKey) {
        return ResponseEntity.ok(savedViewService.list(actor, objectKey));
    }

    @PostMapping
    public ResponseEntity<SavedView> create(@CurrentActor Actor actor, @Valid @RequestBody CreateSavedViewDto dto) {
        return ResponseEntity.ok(savedViewService.create(actor, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remove(@CurrentActor Actor actor, @PathVariable UUID id) {
        savedViewService.remove(actor, id);
        return ResponseEntity.noContent().build();
    }
}
