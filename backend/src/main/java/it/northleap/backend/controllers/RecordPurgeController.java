package it.northleap.backend.controllers;


import it.northleap.backend.security.Actor;
import it.northleap.backend.security.CurrentActor;
import it.northleap.backend.services.RecordPurgeService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

// il segmento letterale "trash" vince su /{key}/{id} nel routing, come GET /{key}/trash di RecordsController
@RestController
@RequestMapping("/api/records/{key}/trash")
@RequiredArgsConstructor
public class RecordPurgeController {

    private final RecordPurgeService purgeService;

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> purge(@CurrentActor Actor actor, @PathVariable String key, @PathVariable UUID id, HttpServletRequest request) {
        purgeService.purge(actor, key, id, request.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

}
