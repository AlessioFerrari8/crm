package it.northleap.backend.controllers;


import it.northleap.backend.dtos.HistoryEntry;
import it.northleap.backend.security.Actor;
import it.northleap.backend.security.CurrentActor;
import it.northleap.backend.services.RecordHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

// i permessi li applica il service passando da RecordsService.findOne: gli stessi della scheda
@RestController
@RequestMapping("/api/records/{key}")
@RequiredArgsConstructor
public class RecordHistoryController {

    private final RecordHistoryService historyService;

    @GetMapping("/{id}/history")
    public HistoryEntry.Page history(@CurrentActor Actor actor, @PathVariable String key, @PathVariable UUID id,
                                     @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return historyService.history(actor, key, id, page, size);
    }
}
