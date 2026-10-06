package it.northleap.backend.services;

import it.northleap.backend.dtos.DocumentTotals;
import it.northleap.backend.dtos.EInvoiceDto;
import it.northleap.backend.entities.DocumentLine;
import it.northleap.backend.entities.EInvoice;
import it.northleap.backend.entities.EInvoiceStatus;
import it.northleap.backend.entities.Record;
import it.northleap.backend.entities.Workspace;
import it.northleap.backend.exceptions.BadRequestException;
import it.northleap.backend.exceptions.NotFoundException;
import it.northleap.backend.repositories.DocumentLineRepository;
import it.northleap.backend.repositories.EInvoiceRepository;
import it.northleap.backend.repositories.RecordRepository;
import it.northleap.backend.repositories.WorkspaceRepository;
import it.northleap.backend.security.Actor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class EInvoiceService {

    // vari servizi
    private final EInvoiceRepository eInvoiceRepository;
    private final RecordsService recordsService;
    private final RecordRepository recordRepository;
    private final DocumentLineRepository documentLineRepository;
    private final DocumentTotalService documentTotalService;
    private final WorkspaceRepository workspaceRepository;
    private final FatturaPaXmlBuilder fatturaPaXmlBuilder;
    private final FatturaPaValidator fatturaPaValidator;
    private final SdiProgressiveService sdiProgressiveService;
    private final SdiReceiptParser sdiReceiptParser;
    private final List<SdiTransport> transports;
    private final AuditService auditService;

    @Value("${app.sdi.provider:manual}")
    private String provider;

    // genera (o rigenera, se DRAFT) e valida. Un XML che non passa l'XSD non viene salvato
    @Transactional
    public EInvoiceDto generate(Actor actor, UUID invoiceId, String ip) {
        // findOne applica permessi e scope OWN: la fattura di un altro qui dà come risposta 403
        Record invoice = recordsService.findOne(actor, "invoice", invoiceId).record();

        EInvoice current = eInvoiceRepository.findFirstByInvoice_IdOrderByCreatedAtDesc(invoiceId).orElse(null);
        if (current != null && EInvoiceStatus.LOCKING.contains(current.getStatus())) {
            throw new BadRequestException("Esiste già una fattura elettronica " + current.getStatus()
                + " per questo documento", "EINVOICE_EXISTS");
        }

        String tipo = Optional.ofNullable(FatturaPaXmlBuilder.str(invoice, "documentType")).orElse("TD01");
        Record original = relation(invoice, "originalInvoice");
        if ("TD04".equals(tipo) && original == null) {
            throw new BadRequestException("Una nota di credito deve indicare la fattura che rettifica");
        }

        Workspace ws = workspaceRepository.findTopByOrderByCreatedAtAsc()
                .orElseThrow(() -> new IllegalStateException("Workspace not initialized"));
        List<DocumentLine> lines = documentLineRepository.findByDocumentIdOrderByLineNo(invoiceId);
        DocumentTotals totals = documentTotalService.compute(invoice, lines);

        // DRAFT si riscrive con lo stesso file: dopo uno scarto, REJECTED, serve un file nuovo, ovvero un
        // progressivo nuovo, e la vecchia riga resta come storico dello scarto
        EInvoice e = current != null && current.getStatus() == EInvoiceStatus.DRAFT ? current : new EInvoice();
        String progressive = e.getFilename() != null
                ? e.getFilename().substring(e.getFilename().indexOf('_') + 1, e.getFilename().indexOf('.'))
                : sdiProgressiveService.next();

        String xml = fatturaPaXmlBuilder.build(new FatturaPaXmlBuilder.Input(ws, invoice, customer(invoice), lines,
                totals, relation(invoice, "paymentMethod"), progressive, tipo, original));
        fatturaPaValidator.validate(xml);

        e.setInvoice(invoice);
        e.setXml(xml);
        e.setStatus(EInvoiceStatus.DRAFT);
        e.setFilename(filename(ws, progressive));
        eInvoiceRepository.save(e);
        auditService.log(actor, "einvoice.generate", "invoice", invoiceId.toString(),
                Map.of("filename", e.getFilename()), ip);
        return EInvoiceDto.from(e);
    }

    @Transactional
    public EInvoiceDto send(Actor actor, UUID id, String ip) {
        EInvoice e = get(id);
        if (e.getStatus() != EInvoiceStatus.DRAFT && e.getStatus() != EInvoiceStatus.READY) {
            throw new BadRequestException("Si trasmette solo una fattura DRAFT o READY, questa e' " + e.getStatus());
        }
        SdiTransport.SdiSendResult r = transport().send(e);
        if (r.sent()) {
            e.setStatus(EInvoiceStatus.SENT);
            e.setSentAt(Instant.now());
            e.setSdiIdentifier(r.sdiIdentifier());
        } else {
            e.setStatus(EInvoiceStatus.READY);
        }
        auditService.log(actor, "einvoice.send", "invoice", e.getInvoice().getId().toString(),
                Map.of("status", e.getStatus().name()), ip);
        return EInvoiceDto.from(e);
    }

    // trasporto manuale: l'utente ha caricato il file sul portale e lo dice qui
    @Transactional
    public EInvoiceDto markSent(Actor actor, UUID id, String ip) {
        EInvoice e = get(id);
        if (e.getStatus() != EInvoiceStatus.READY) {
            throw new BadRequestException("Si segna come inviata solo una fattura READY");
        }
        e.setStatus(EInvoiceStatus.SENT);
        e.setSentAt(Instant.now());
        auditService.log(actor, "einvoice.sent", "invoice", e.getInvoice().getId().toString(), null, ip);
        return EInvoiceDto.from(e);
    }

    @Transactional
    public EInvoiceDto addReceipt(Actor actor, UUID id, MultipartFile file, String ip) {
        EInvoice e = get(id);
        if (e.getStatus() == EInvoiceStatus.DRAFT) {
            throw new BadRequestException("La fattura non risulta ancora trasmessa");
        }
        SdiReceiptParser.Receipt r;
        try {
            r = sdiReceiptParser.parse(file.getBytes());
        } catch (IOException ex) {
            throw new BadRequestException("File non leggibile");
        }
        // la ricevuta dice a quale file si riferisce: caricarla sulla fattura sbagliata e' facilissimo
        if (r.filename() != null && !r.filename().startsWith(e.getFilename().replace(".xml", ""))) {
            throw new BadRequestException("La ricevuta riguarda " + r.filename() + ", non " + e.getFilename());
        }
        Map<String, Object> n = new LinkedHashMap<>();
        n.put("type", r.type());
        n.put("receivedAt", Instant.now().toString());
        n.put("message", r.message());
        List<Map<String, Object>> all = new ArrayList<>(Optional.ofNullable(e.getNotifications()).orElse(List.of()));
        all.add(n);
        e.setNotifications(all);
        e.setStatus(r.status());
        if (e.getSdiIdentifier() == null) e.setSdiIdentifier(r.sdiIdentifier());
        if (e.getSentAt() == null) e.setSentAt(Instant.now());
        auditService.log(actor, "einvoice.receipt", "invoice", e.getInvoice().getId().toString(),
                Map.of("type", r.type()), ip);
        return EInvoiceDto.from(e);
    }

    @Transactional(readOnly = true)
    public List<EInvoiceDto> list(EInvoiceStatus status) {
        List<EInvoice> rows = status == null
                ? eInvoiceRepository.findAllByOrderByCreatedAtDesc()
                : eInvoiceRepository.findByStatusOrderByCreatedAtDesc(status);
        return rows.stream().map(EInvoiceDto::from).toList();
    }

    @Transactional(readOnly = true)
    public EInvoiceDto one(UUID id) {
        return EInvoiceDto.from(get(id));
    }

    @Transactional(readOnly = true)
    public EInvoice raw(UUID id) {
        EInvoice e = get(id);
        e.getInvoice().getId(); // l'id del proxy non inizializza, ma basta al PDF di cortesia
        return e;
    }

    private EInvoice get(UUID id) {
        return eInvoiceRepository.findById(id).orElseThrow(() -> new NotFoundException("Fattura elettronica non trovata"));
    }

    private SdiTransport transport() {
        return transports.stream().filter(t -> t.name().equalsIgnoreCase(provider)).findFirst()
                .orElseThrow(() -> new IllegalStateException("app.sdi.provider='" + provider + "' sconosciuto"));
    }

    // il cliente è l'azienda, o il contatto per i privati
    private Record customer(Record invoice) {
        Record c = relation(invoice, "company");
        return c != null ? c : relation(invoice, "contact");
    }

    private Record relation(Record r, String key) {
        Object v = r.getData() == null ? null : r.getData().get(key);
        if (v instanceof Collection<?> c) v = c.isEmpty() ? null : c.iterator().next();
        if (v == null || v.toString().isBlank()) return null;
        return recordRepository.findByIdAndIsDeletedFalse(UUID.fromString(v.toString())).orElse(null);
    }

    private String filename(Workspace ws, String progressive) {
        String country = ws.getCountryCode() == null ? "IT" : ws.getCountryCode().toUpperCase();
        String vat = ws.getVatNumber().replaceAll("\\s", "").toUpperCase();
        if (vat.startsWith(country)) vat = vat.substring(2);
        return country + vat + "_" + progressive + ".xml";
    }
}
