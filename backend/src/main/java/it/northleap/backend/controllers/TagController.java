package it.northleap.backend.controllers;

import it.northleap.backend.dtos.CreateTagDto;
import it.northleap.backend.entities.Tag;
import it.northleap.backend.security.Actor;
import it.northleap.backend.security.CurrentActor;
import it.northleap.backend.services.TagService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import it.northleap.backend.entities.Record;


import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

    @GetMapping
    public ResponseEntity<List<Tag>> list(@CurrentActor Actor actor) {
        return ResponseEntity.ok(tagService.list(actor));
    }

    @PostMapping
    public ResponseEntity<Tag> create(@CurrentActor Actor actor, @Valid @RequestBody CreateTagDto dto) {
        return ResponseEntity.ok(tagService.create(actor, dto));
    }

    @DeleteMapping("/{tagId}")
    public ResponseEntity<Void> remove(@CurrentActor Actor actor, @PathVariable UUID tagId) {
        tagService.remove(actor, tagId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{tagId}/records")
    public ResponseEntity<List<Record>> records(@CurrentActor Actor actor, @PathVariable UUID tagId) {
        return ResponseEntity.ok(tagService.recordsWithTag(actor, tagId));
    }
}
