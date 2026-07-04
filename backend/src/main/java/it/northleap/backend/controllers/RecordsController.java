package it.northleap.backend.controllers;

import it.northleap.backend.dtos.BulkRecordsRequest;
import it.northleap.backend.dtos.BulkResult;
import it.northleap.backend.dtos.QueryRecordsDto;
import it.northleap.backend.dtos.RecordDetailResponse;
import it.northleap.backend.dtos.RecordQueryResponse;
import it.northleap.backend.dtos.UpsertRecordDto;
import it.northleap.backend.entities.Record;
import it.northleap.backend.security.Actor;
import it.northleap.backend.security.CurrentActor;
import it.northleap.backend.services.RecordsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// Porting di records.controller.ts: un solo controller, {key} parametrico seleziona l'ObjectType.
// L'RBAC sulla risorsa dinamica è gestito dentro RecordsService (vedi nota lì), non qui via
// @RequirePerm.
@RestController
@RequestMapping("/api/records")
@RequiredArgsConstructor
public class RecordsController {

    private final RecordsService recordsService;

    @GetMapping("/search")
    public ResponseEntity<List<Record>> search(@CurrentActor Actor actor, @RequestParam("q") String q) {
        return ResponseEntity.ok(recordsService.globalSearch(actor, q));
    }

    @GetMapping("/{key}")
    public ResponseEntity<RecordQueryResponse> query(
            @CurrentActor Actor actor,
            @PathVariable String key,
            @ModelAttribute QueryRecordsDto q
    ) {
        return ResponseEntity.ok(recordsService.query(actor, key, q));
    }

    // Query avanzata: filtri AND/OR annidati + multi-sort nel body
    @PostMapping("/{key}/query")
    public ResponseEntity<RecordQueryResponse> queryAdvanced(
            @CurrentActor Actor actor,
            @PathVariable String key,
            @RequestBody QueryRecordsDto q
    ) {
        return ResponseEntity.ok(recordsService.query(actor, key, q));
    }

    @PostMapping("/{key}/bulk")
    public ResponseEntity<BulkResult> bulk(
            @CurrentActor Actor actor,
            @PathVariable String key,
            @Valid @RequestBody BulkRecordsRequest body,
            HttpServletRequest request
    ) {
        return ResponseEntity.ok(recordsService.bulk(actor, key, body, request.getRemoteAddr()));
    }

    // cestino del tipo (il segmento letterale "trash" vince su /{key}/{id} nel routing di Spring)
    @GetMapping("/{key}/trash")
    public ResponseEntity<List<Record>> trash(@CurrentActor Actor actor, @PathVariable String key) {
        return ResponseEntity.ok(recordsService.trash(actor, key));
    }

    @PostMapping("/{key}/{id}/restore")
    public ResponseEntity<Record> restore(
            @CurrentActor Actor actor,
            @PathVariable String key,
            @PathVariable UUID id,
            HttpServletRequest request
    ) {
        return ResponseEntity.ok(recordsService.restore(actor, key, id, request.getRemoteAddr()));
    }

    @GetMapping("/{key}/{id}")
    public ResponseEntity<RecordDetailResponse> findOne(
            @CurrentActor Actor actor,
            @PathVariable String key,
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(recordsService.findOne(actor, key, id));
    }

    @PostMapping("/{key}")
    public ResponseEntity<Record> create(
            @CurrentActor Actor actor,
            @PathVariable String key,
            @Valid @RequestBody UpsertRecordDto dto,
            HttpServletRequest request
    ) {
        return ResponseEntity.ok(recordsService.create(actor, key, dto, request.getRemoteAddr()));
    }

    @PatchMapping("/{key}/{id}")
    public ResponseEntity<Record> update(
            @CurrentActor Actor actor,
            @PathVariable String key,
            @PathVariable UUID id,
            @Valid @RequestBody UpsertRecordDto dto,
            HttpServletRequest request
    ) {
        return ResponseEntity.ok(recordsService.update(actor, key, id, dto, request.getRemoteAddr()));
    }

    @DeleteMapping("/{key}/{id}")
    public ResponseEntity<Void> remove(
            @CurrentActor Actor actor,
            @PathVariable String key,
            @PathVariable UUID id,
            HttpServletRequest request
    ) {
        recordsService.remove(actor, key, id, request.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }
}
