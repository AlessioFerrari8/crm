package it.northleap.backend.controllers;

import it.northleap.backend.dtos.PdfTemplateDto;
import it.northleap.backend.entities.PdfTemplate;
import it.northleap.backend.exceptions.BadRequestException;
import it.northleap.backend.exceptions.NotFoundException;
import it.northleap.backend.repositories.PdfTemplateRepository;
import it.northleap.backend.security.PermAction;
import it.northleap.backend.security.RequirePerm;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// gestione dei template di stampa. Tutto sotto il permesso "page", come le altre pagine di
// configurazione: chi può cambiare la struttura dell'app può cambiare la carta intestata, chi non può
// non deve
@RestController
@RequestMapping("/api/pdf-templates")
@RequiredArgsConstructor
public class PdfTemplateController {
    private final PdfTemplateRepository pdfTemplateRepository;

    @GetMapping
    @RequirePerm(resource = "page", action = PermAction.READ)
    public ResponseEntity<List<PdfTemplate>> list(
            @RequestParam(required = false) String objectKey) {
        return ResponseEntity.ok(objectKey == null || objectKey.isBlank()
                ? pdfTemplateRepository.findAll()
                : pdfTemplateRepository.findByObjectKeyOrderByLabelAsc(objectKey));
    }

    @PostMapping
    @RequirePerm(resource = "page", action = PermAction.WRITE)
    public ResponseEntity<PdfTemplate> create(@Valid @RequestBody PdfTemplateDto dto) {
        if (pdfTemplateRepository.existsByKey(dto.key())) {
            throw new BadRequestException("A template with key " + dto.key() + " already exists");
        }
        PdfTemplate t = new PdfTemplate();
        t.setKey(dto.key());
        apply(t, dto);
        return ResponseEntity.ok(pdfTemplateRepository.save(t));
    }

    @PutMapping("/{id}")
    @RequirePerm(resource = "page", action = PermAction.WRITE)
    public ResponseEntity<PdfTemplate> update(@PathVariable UUID id, @Valid @RequestBody PdfTemplateDto dto) {
        PdfTemplate t = find(id);
        // i template di sistema non si modificano, si duplicano, altrimenti il seeder e l'utente si contendono
        // la stessa riga a ogni boot, e vince sempre chi ha riavviato per ultimo
        if (t.isSystem()) {
            throw new BadRequestException("System templates cannot be edited: duplicate it first");
        }
        apply(t, dto);
        return ResponseEntity.ok(pdfTemplateRepository.save(t));
    }

    @PostMapping("/{id}/duplicate")
    @RequirePerm(resource = "page", action = PermAction.WRITE)
    public ResponseEntity<PdfTemplate> duplicate(@PathVariable UUID id) {
        PdfTemplate src = find(id);
        PdfTemplate copy = new PdfTemplate();
        copy.setKey(uniqueKey(src.getKey()));
        copy.setLabel(src.getLabel() + " (copia)");
        copy.setObjectKey(src.getObjectKey());
        copy.setHtml(src.getHtml());
        copy.setPageSize(src.getPageSize());
        copy.setMarginMm(src.getMarginMm());
        copy.setSystem(false);
        return ResponseEntity.ok(pdfTemplateRepository.save(copy));
    }

    @DeleteMapping("/{id}")
    @RequirePerm(resource = "page", action = PermAction.WRITE)
    public ResponseEntity<Void> remove(@PathVariable UUID id) {
        PdfTemplate t = find(id);
        if (t.isSystem()) {
            throw new BadRequestException("System templates cannot be deleted");
        }
        pdfTemplateRepository.delete(t);
        return ResponseEntity.noContent().build();
    }

    private PdfTemplate find(UUID id) {
        return pdfTemplateRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Template not found"));
    }

    private void apply(PdfTemplate t, PdfTemplateDto dto) {
        t.setLabel(dto.label());
        t.setObjectKey(dto.objectKey());
        t.setHtml(dto.html());
        t.setPageSize(dto.pageSize() == null ? "A4" : dto.pageSize());
        t.setMarginMm(dto.marginMm() == null ? 15 : dto.marginMm());
    }

    private String uniqueKey(String base) {
        String candidate = base + "-copy";
        int i = 2;
        while (pdfTemplateRepository.existsByKey(candidate)) {
            candidate = base + "-copy-" + i++;
        }
        return candidate;
    }
}
