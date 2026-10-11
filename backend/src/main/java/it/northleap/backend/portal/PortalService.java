package it.northleap.backend.portal;

import it.northleap.backend.entities.*;
import it.northleap.backend.entities.Record;
import it.northleap.backend.events.RecordCreatedEvent;
import it.northleap.backend.exceptions.BadRequestException;
import it.northleap.backend.exceptions.NotFoundException;
import it.northleap.backend.repositories.*;
import it.northleap.backend.security.Actor;
import it.northleap.backend.security.ActorType;
import it.northleap.backend.services.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.swing.filechooser.FileView;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

// quello che il cliente vede e fa. Ogni metodo parte da PortalPrincipal e passa da PortalAccessService
@Service
@RequiredArgsConstructor
public class PortalService {

    private final PortalAccessService access;
    private final RecordRepository recordRepository;
    private final ObjectTypeRepository objectTypeRepository;
    private final RecordNoteRepository noteRepository;
    private final RecordValidator recordValidator;
    private final DocumentNumberService documentNumberService;
    private final DocumentLineRepository documentLineRepository;
    private final DocumentTotalService documentTotalService;
    private final FileStorageService fileStorageService;
    private final FileObjectRepository fileObjectRepository;
    private final PdfService pdfService;
    private final QuoteAcceptService quoteAcceptService;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;

    // dto e non entità: il jsonb intero contiene campi interni (assegnatario, note interne, IP di accettazione)
    public record TicketView(UUID id, String number, String subject, String status, Instant createdAt) { }
    public record MessageView(String author, String body, Instant at, boolean mine) { }
    public record TicketDetail(TicketView ticket, String description, List<MessageView> messages, List<FileView> files) { }
    public record FileView(UUID id, String name, long size) { }
    public record DocumentView(UUID id, String kind, String number, String date, BigDecimal total, String status,
                               boolean acceptable) { }

    public List<TicketView> tickets(PortalPrincipal p) {
        return access.owned(p, PortalAccessService.TICKET).stream().map(this::ticketView).toList();
    }

    public TicketDetail ticket(PortalPrincipal p, UUID id) {
        Record t = access.assertOwned(p, id, PortalAccessService.TICKET);
        List<MessageView> messages = noteRepository.findByRecordIdOrderByCreatedAtDesc(id).stream()
                .map(n -> new MessageView(n.getAuthorName(), n.getBody(), n.getCreatedAt(), n.getAuthorId().equals(p.portalUserId())))
                .toList();
        List<FileView> files = fileStorageService.listByRecord(id).stream()
                .map(f -> new FileView(f.getId(), f.getFilename(), f.getSize())).toList();
        return new TicketDetail(ticketView(t), str(t, "description"), messages, files);
    }

    // il ticket nasce come quelli via email: numerazione, validazione, evento per i workflow. Non passa da
    // RecordsService (che vuole un Actor con ruoli): stessi passi, a mano
    @Transactional
    public TicketView openTicket(PortalPrincipal p, String subject, String description, String ip) {
        if (subject == null || subject.isBlank()) throw new BadRequestException("Scrivi l'oggetto");
        ObjectType type = objectTypeRepository.findByKey("ticket").orElseThrow();
        Map<String, Object> raw = new HashMap<>();
        raw.put("subject", subject.strip());
        raw.put("description", description == null ? "" : description.strip());
        raw.put("status", "open");
        raw.put("contact", p.contactId().toString());
        UUID company = access.companyOf(p);
        if (company != null) raw.put("company", company.toString());
        raw.put("fromEmail", p.email());
        Map<String, Object> data = recordValidator.validate(type.getFields(), raw);
        String number = documentNumberService.next(type);
        if (number != null) data.put("number", number);

        Record r = new Record();
        r.setObjectType(type);
        r.setData(data);
        r.setTitle(recordValidator.deriveTitle(type.getFields(), data));
        r.setStatus("open");
        recordRepository.save(r);
        auditService.log(actor(p), "portal.ticket", "ticket", r.getId().toString(), null, ip);
        events.publishEvent(new RecordCreatedEvent("ticket", r));
        return ticketView(r);
    }

    @Transactional
    public MessageView reply(PortalPrincipal p, UUID ticketId, String body) {
        Record t = access.assertOwned(p, ticketId, PortalAccessService.TICKET);
        if (body == null || body.isBlank()) throw new BadRequestException("Il messaggio è vuoto");
        RecordNote n = new RecordNote();
        n.setRecordId(t.getId());
        n.setAuthorId(p.portalUserId());
        n.setAuthorName("Cliente - " + p.email());
        n.setBody(body.strip().length() > 4000 ? body.strip().substring(0, 4000) : body.strip());
        n.setCustomerVisible(true);
        noteRepository.save(n);
        // il cliente ha risposto: un ticket in attesa o risolto torna aperto
        if (List.of("pending", "resolved").contains(t.getStatus())) t.setStatus("open");
        return new MessageView(n.getAuthorName(), n.getBody(), n.getCreatedAt(), true);
    }

    public List<DocumentView> documents(PortalPrincipal p) {
        return access.owned(p, PortalAccessService.DOCUMENTS).stream()
                // le bozze non si mostrano: il cliente vede quello che gli è stato mandato
                .filter(r -> !"draft".equals(r.getStatus()))
                .map(r -> new DocumentView(r.getId(), r.getObjectType().getLabel(), str(r, "number"),
                        firstNonBlank(str(r, "issueDate"), str(r, "quoteDate"), str(r, "orderDate"), str(r, "date")),
                        documentTotalService.compute(r, documentLineRepository.findByDocumentIdOrderByLineNo(r.getId())).total(),
                        r.getStatus(),
                        "quote".equals(r.getObjectType().getKey()) && r.getData().get("acceptedAt") == null))
                .toList();
    }

    @Transactional(readOnly = true)
    public PdfService.Rendered pdf(PortalPrincipal p, UUID id) {
        return pdfService.printPublic(access.assertOwned(p, id, PortalAccessService.DOCUMENTS));
    }

    @Transactional
    public void acceptQuote(PortalPrincipal p, UUID id, String note, String ip) {
        Record q = access.assertOwned(p, id, Set.of("quote"));
        quoteAcceptService.accept(q, note, ip);
    }

    // FileController non controlla l'appartenenza: riusarlo qui sarebbe il buco più facile
    public FileObject file(PortalPrincipal p, UUID fileId) {
        FileObject f = fileObjectRepository.findById(fileId).orElseThrow(() -> new NotFoundException("Non trovato"));
        if (f.getRecordId() == null) throw new NotFoundException("Non trovato");
        Set<String> all = new HashSet<>(PortalAccessService.TICKET);
        all.addAll(PortalAccessService.DOCUMENTS);
        access.assertOwned(p, f.getRecordId(), all);
        return f;
    }

    private TicketView ticketView(Record t) {
        return new TicketView(t.getId(), str(t, "number"), str(t, "subject"), t.getStatus(), t.getCreatedAt());
    }

    private Actor actor(PortalPrincipal p) {
        return new Actor(p.portalUserId(), ActorType.PORTAL, p.email(), List.of());
    }

    private static String str(Record r, String k) {
        Object v = r.getData() == null ? null : r.getData().get(k);
        return v == null ? null : v.toString();
    }

    private static String firstNonBlank(String... v) {
        for (String s : v) if (s != null && !s.isBlank()) return s;
        return null;
    }

}
