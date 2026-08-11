package it.northleap.backend.controllers;

import it.northleap.backend.dtos.DuplicateCandidateDto;
import it.northleap.backend.dtos.MergeDto;
import it.northleap.backend.security.Actor;
import it.northleap.backend.security.CurrentActor;
import it.northleap.backend.services.DuplicateFinderService;
import it.northleap.backend.services.MergeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import it.northleap.backend.entities.Record;


import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/records")
@RequiredArgsConstructor
public class MergeController {

    private final MergeService mergeService;
    private final DuplicateFinderService duplicateFinderService;

    // candidati duplicati di un modulo, ordinati per somiglianza decrescente
    @GetMapping("/duplicates/{objectKey}")
    public ResponseEntity<List<DuplicateCandidateDto>> duplicates(
            @CurrentActor Actor actor,
            @PathVariable String objectKey,
            @RequestParam(defaultValue = "0.6") double threshold) {
        return ResponseEntity.ok(duplicateFinderService.find(actor, objectKey, threshold));
    }

    @PostMapping("/{winnerId}/merge/{loserId}")
    public ResponseEntity<Record> merge(@CurrentActor Actor actor,
                                        @PathVariable UUID winnerId,
                                        @PathVariable UUID loserId,
                                        @RequestBody MergeDto dto) {
        return ResponseEntity.ok(mergeService.merge(actor, winnerId, loserId, dto.fieldsFromLoser()));
    }
}
