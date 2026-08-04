package it.northleap.backend.controllers;

import it.northleap.backend.dtos.UserPreferenceDto;
import it.northleap.backend.entities.UserPreference;
import it.northleap.backend.security.Actor;
import it.northleap.backend.security.CurrentActor;
import it.northleap.backend.services.UserPreferenceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/me/preferences")
@RequiredArgsConstructor
public class UserPreferenceController {
    private final UserPreferenceService userPreferenceService;

    @GetMapping
    public ResponseEntity<UserPreference> get(@CurrentActor Actor actor) {
        return ResponseEntity.ok(userPreferenceService.get(actor));
    }

    @PutMapping
    public ResponseEntity<UserPreference> save(@CurrentActor Actor actor, @Valid @RequestBody UserPreferenceDto dto) {
        return ResponseEntity.ok(userPreferenceService.save(actor, dto));
    }
}
