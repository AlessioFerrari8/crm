package it.northleap.backend.controllers;


import it.northleap.backend.dtos.DocumentLineDto;
import it.northleap.backend.dtos.DocumentLineResponse;
import it.northleap.backend.entities.DocumentLine;
import it.northleap.backend.security.Actor;
import it.northleap.backend.security.CurrentActor;
import it.northleap.backend.services.DocumentLineService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/records/{documentId}/lines")
@RequiredArgsConstructor
public class DocumentLineController {

    private final DocumentLineService documentLineService;

    @GetMapping
    public ResponseEntity<List<DocumentLineResponse>> list(@CurrentActor Actor actor,
                                                           @PathVariable UUID documentId) {
        return ResponseEntity.ok(documentLineService.list(actor, documentId)
                .stream().map(DocumentLineResponse::of).toList());
    }

    @PutMapping
    public ResponseEntity<List<DocumentLineResponse>> replace(@CurrentActor Actor actor,
                                                              @PathVariable UUID documentId,
                                                              @Valid @RequestBody List<DocumentLineDto> lines) {
        return ResponseEntity.ok(documentLineService.replaceAll(actor, documentId, lines)
                .stream().map(DocumentLineResponse::of).toList());
    }



}
