package it.northleap.backend.services;

import it.northleap.backend.entities.Record;
import it.northleap.backend.events.RecordUpdatedEvent;
import it.northleap.backend.exceptions.BadRequestException;
import it.northleap.backend.exceptions.NotFoundException;
import it.northleap.backend.repositories.DocumentLineRepository;
import it.northleap.backend.repositories.RecordRepository;
import it.northleap.backend.security.Actor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

// il preventivo che il cliente accetta da un link. Nessuna tabella: quattro campi sul preventivo (acceptToken,
// sentAt, acceptedAt, acceptNote). Il lato pubblico carica il record per token e non passa mai da RecordsService
// Lì serve un Actor, e inventarne uno "di sistema" con scope ALL vorrebbe dire una porta che legge qualuque record
// del CRM
@Service
@RequiredArgsConstructor
public class QuoteAcceptService {

    private final RecordsService recordsService;
    private final RecordRepository recordRepository;
    private final DocumentLineRepository documentLineRepository;
    private final DocumentTotalService documentTotalService;
    private final PdfService pdfService;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;

    @Value("${app.quote.accept-secret")
    private String secret;

    @Value("${app.frontend.base-url")
    private String frontendBaseUrl;

    public record PublicQuote(String number, String customer, String total, String validUntil, boolean accepted) { }

    // lato interno: genera (o rigenera, revocando il vecchio) il link da mandare al cliente
    @Transactional
    public String createLink(Actor actor, UUID quoteId) {
        Record quote = recordsService.findOne(actor, "quote", quoteId).record();
        if (quote.getData().get("acceptedAt") != null) throw new BadRequestException("Preventivo già accettato");
        String token = CampaignService.randomToken();
        Map<String, Object> data = new LinkedHashMap<>(quote.getData());
        data.put("acceptToken", token);
        data.put("sentAt", Instant.now().toString());
        quote.setData(data);
        if (quote.getStatus() == null || "draft".equals(quote.getStatus())) quote.setStatus("sent");
        auditService.log(actor, "quote.link", "quote", quoteId.toString(), null, null);
        return frontendBaseUrl + "/q/" + quoteId + "?t=" + token + "&s=" + sign(quoteId, token);
    }

    @Transactional(readOnly = true)
    public PublicQuote view(UUID id, String token, String sig) {
        Record q = load(id, token, sig);
        var totals = documentTotalService.compute(q, documentLineRepository.findByDocumentIdOrderByLineNo(id));
        Object valid = q.getData().get("validUntil");
        return new PublicQuote(str(q.getData().get("number")), customerName(q), totals.total().toPlainString(),
                valid == null ? null : valid.toString(), q.getData().get("acceptedAt") != null);
    }

    @Transactional(readOnly = true)
    public PdfService.Rendered pdf(UUID id, String token, String sig) {
        return pdfService.printPublic(load(id, token, sig));
    }

    // un click, timestamp, IP. Poi partono i workflow che esistono già (conversione in ordine, notifiche)
    @Transactional
    public void accept(UUID id, String token, String sig, String note, String ip) {
        accept(load(id, token, sig), note, ip);
    }

    // stessa accettazione dal portale clienti: lì l'appartenenza l'ha già verificata PortalAccessService.assertOwned, qui
    // non si ricontrolla
    @Transactional
    public void accept(Record q, String note, String ip) {
        if (q.getData().get("acceptedAt") != null) return; // doppio click: idempotente, non un errore
        Object valid = q.getData().get("validUntil");
        if (valid != null && java.time.LocalDate.parse(valid.toString().substring(0, 10)).isBefore(java.time.LocalDate.now())) {
            throw new BadRequestException("Il preventivo è scaduto: chiedi un'offerta aggiornata");
        }
        Map<String, Object> before = q.getData();
        String beforeStatus = q.getStatus();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("acceptedAt", Instant.now().toString());
        data.put("acceptIp", ip);
        if (note != null && !note.isBlank()) data.put("acceptNote", note.strip().substring(0, Math.min(note.strip().length(), 1000)));
        data.remove("acceptToken"); // il link ha fatto il suo lavoro
        q.setData(data);
        q.setStatus("accepted");
        recordRepository.save(q);
        // lo stesso evento di RecordsService.update: è quello che fa scattare i workflow su field.changed
        events.publishEvent(new RecordUpdatedEvent("quote", q, before, beforeStatus));
    }

    // 404 per tutto: token sbagliato, firma sbagliata, record inesistente. Un 403 confermerebbe che esiste
    private Record load(UUID id, String token, String sig) {
        if (token == null || sig == null) throw new NotFoundException("Link non valido");
        if (!MessageDigest.isEqual(sign(id, token).getBytes(StandardCharsets.UTF_8), sig.getBytes(StandardCharsets.UTF_8))) {
            throw new NotFoundException("Link non valido");
        }
        Record q = recordRepository.findByIdAndIsDeletedFalse(id)
                .filter(r -> "quote".equals(r.getObjectType().getKey()))
                .orElseThrow(() -> new NotFoundException("Link non valido"));
        Object current = q.getData() == null ? null : q.getData().get("acceptToken");
        boolean accepted = q.getData() != null && q.getData().get("acceptedAt") != null;
        // dopo l'accettazione il token non c'è più, ma il link deve continuare a mostrare "accettato"
        if (!accepted && (current == null || !MessageDigest.isEqual(current.toString().getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8)))) {
            throw new NotFoundException("Link non valido");
        }
        return q;
    }

    private String sign(UUID id, String token) {
        return HmacUtil.sha256Hex(secret, id + "." + token);
    }

    private String customerName(Record q) {
        for (String key : new String[]{"company", "contact"}) {
            Object v = q.getData().get(key);
            if (v == null || v.toString().isBlank()) continue;
            return recordRepository.findByIdAndIsDeletedFalse(UUID.fromString(v.toString())).map(Record::getTitle).orElse("");
        }
        return "";
    }

    private String str(Object o) { return o == null ? "" : o.toString(); }
}
