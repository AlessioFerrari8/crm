package it.northleap.backend.controllers;

import it.northleap.backend.security.Actor;
import it.northleap.backend.security.CurrentActor;
import it.northleap.backend.services.QuoteRevisionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import it.northleap.backend.entities.Record;

import java.util.UUID;

// Sta sotto /api/records come RelationsController e MergeController: "revisiona" è
// un'operazione su un record, non un modulo a sé.
// Niente @RequirePerm: il permesso è su "quote", che è una chiave dinamica come tutte le
// altre, e lo risolve QuoteRevisionService (stesso schema di RecordsService)
@RestController
@RequestMapping("/api/records")
@RequiredArgsConstructor
public class QuoteRevisionController {

    private final QuoteRevisionService quoteRevisionService;

    @PostMapping("/quote/{id}/revise")
    public ResponseEntity<Record> revise(@CurrentActor Actor actor, @PathVariable UUID id) {
        return ResponseEntity.ok(quoteRevisionService.revise(actor, id));
    }
}
