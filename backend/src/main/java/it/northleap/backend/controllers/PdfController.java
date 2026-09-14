package it.northleap.backend.controllers;

import it.northleap.backend.dtos.PdfBatchDto;
import it.northleap.backend.dtos.PdfPreviewDto;
import it.northleap.backend.security.Actor;
import it.northleap.backend.security.CurrentActor;
import it.northleap.backend.security.PermAction;
import it.northleap.backend.security.RequirePerm;
import it.northleap.backend.services.PdfService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/api/pdf")
@RequiredArgsConstructor
public class PdfController {
    private final PdfService pdfService;

    // stampa di un documento. non serve @RequirePerm, dato che pdfService risolve il Record passando
    // da RecordsService.findOne(actor, ...), che applica già permessi e scope OWN
    @GetMapping("/{recordId}")
    public ResponseEntity<byte[]> print(@CurrentActor Actor actor, @PathVariable UUID recordId,
                                        @RequestParam(required = false) String template) {
        return pdf(pdfService.printOne(actor, recordId, template));
    }

    // anteprima di un template non salvato, per la pagina di editing. Prende l'html dal body
    // è il modo per vedere le modifiche prima di salvarle. uso @RequirePerm sul resource "page", come
    // tutta la gestione della configurazione
    @PostMapping("/preview")
    @RequirePerm(resource = "page", action = PermAction.WRITE)
    public ResponseEntity<byte[]> preview(@CurrentActor Actor actor, @Valid @RequestBody PdfPreviewDto dto) {
        byte[] bytes = pdfService.preview(actor, dto);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                // inline e non attachment: questa risposta finisce in un <iframe>, non in un download
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=preview.pdf")
                .body(bytes);
    }

    // stampa multipla: un solo PDF con dentro N documenti
    @PostMapping("/batch")
    public ResponseEntity<byte[]> batch(@CurrentActor Actor actor, @Valid @RequestBody PdfBatchDto dto) {
        return pdf(pdfService.batch(actor, dto));
    }

    private ResponseEntity<byte[]> pdf(PdfService.Rendered r) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(r.filename(), StandardCharsets.UTF_8)
                        .build().toString())
                .body(r.bytes());
    }
}
