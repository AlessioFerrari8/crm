package it.northleap.backend.controllers;

import it.northleap.backend.dtos.EInvoiceDto;
import it.northleap.backend.entities.EInvoice;
import it.northleap.backend.entities.EInvoiceStatus;
import it.northleap.backend.security.Actor;
import it.northleap.backend.security.CurrentActor;
import it.northleap.backend.security.PermAction;
import it.northleap.backend.security.RequirePerm;
import it.northleap.backend.services.EInvoiceService;
import it.northleap.backend.services.PdfService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;


// resource "invoice": chi può scrivere fatture può generarle e trasmetterle
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class EInvoiceController {

    private final EInvoiceService eInvoiceService;
    private final PdfService pdfService;

    @PostMapping("/invoices/{recordId}/e-invoice")
    @RequirePerm(resource = "invoice", action = PermAction.WRITE)
    public ResponseEntity<EInvoiceDto> generate(@CurrentActor Actor actor, @PathVariable UUID recordId,
                                                HttpServletRequest request) {
        return ResponseEntity.ok(eInvoiceService.generate(actor, recordId, request.getRemoteAddr()));
    }

    // il filtro per stato è la funzione principale: "fammi vedere quelle non consegnate"
    @GetMapping("/e-invoices")
    @RequirePerm(resource = "invoice", action = PermAction.READ)
    public ResponseEntity<List<EInvoiceDto>> list(@RequestParam(required = false) EInvoiceStatus status) {
        return ResponseEntity.ok(eInvoiceService.list(status));
    }

    @GetMapping("/e-invoices/{id}")
    @RequirePerm(resource = "invoice", action = PermAction.READ)
    public ResponseEntity<EInvoiceDto> one(@PathVariable UUID id) {
        return ResponseEntity.ok(eInvoiceService.one(id));
    }

    @GetMapping("/e-invoices/{id}/xml")
    @RequirePerm(resource = "invoice", action = PermAction.READ)
    public ResponseEntity<byte[]> xml(@PathVariable UUID id) {
        EInvoice e = eInvoiceService.raw(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(e.getFilename(), StandardCharsets.UTF_8).build().toString())
                .body(e.getXml().getBytes(StandardCharsets.UTF_8));
    }

    // copia di cortesia: il PDF della fattura, con gli stessi permessi della stampa
    @GetMapping("/e-invoices/{id}/pdf")
    @RequirePerm(resource = "invoice", action = PermAction.READ)
    public ResponseEntity<byte[]> pdf(@CurrentActor Actor actor, @PathVariable UUID id) {
        PdfService.Rendered r = pdfService.printOne(actor, eInvoiceService.raw(id).getInvoice().getId(), null);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(r.filename(),
                        StandardCharsets.UTF_8).build().toString())
                .body(r.bytes());
    }

    
 }
