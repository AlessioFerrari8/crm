package it.northleap.backend.controllers;

import it.northleap.backend.entities.Tag;
import it.northleap.backend.security.Actor;
import it.northleap.backend.security.CurrentActor;
import it.northleap.backend.services.TagService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/records")
@RequiredArgsConstructor
public class RecordTagController {

    private final TagService tagService;

    @GetMapping("/{recordId}/tags")
    public ResponseEntity<List<Tag>> list(@CurrentActor Actor actor, @PathVariable UUID recordId) {
        return ResponseEntity.ok(tagService.ofRecord(actor, recordId));
    }

    // metto put invece che post sull'attach, essendo idempotente
    @PutMapping("/{recordId}/tags/{tagId}")
    public ResponseEntity<Void> attach(@CurrentActor Actor actor, @PathVariable UUID recordId, @PathVariable UUID tagId) {
        tagService.attach(actor, recordId, tagId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{recordId}/tags/{tagId}")
    public ResponseEntity<Void> detach(@CurrentActor Actor actor, @PathVariable UUID recordId, @PathVariable UUID tagId) {
        tagService.detach(actor, recordId, tagId);
        return ResponseEntity.noContent().build();
    }
}
