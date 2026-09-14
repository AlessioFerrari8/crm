package it.northleap.backend.services;

import it.northleap.backend.dtos.*;
import it.northleap.backend.entities.*;
import it.northleap.backend.entities.Record;
import it.northleap.backend.repositories.DocumentLineRepository;
import it.northleap.backend.repositories.RecordRepository;
import it.northleap.backend.repositories.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// dal Record del documento al modello che il template può leggere
// Nessun HTML qui dentro, nessun PDF: solo dominio
@Slf4j
@Service
@RequiredArgsConstructor
public class PdfContextBuilder {
    private final DocumentLineRepository documentLineRepository;
    private final DocumentTotalService documentTotalService;
    private final RecordRepository recordRepository;
    private final WorkspaceRepository workspaceRepository;
    private final FileStorageService fileStorageService;
    private final MoneyFormatter fmt;
    private final RelationTitleService relationTitleService;

    // chiavi convenzionali dell'intestatario, in ordine di preferenza
    private static final List<String> CUSTOMER_KEYS = List.of("customer", "company", "account", "client", "contact");

    public Map<String, Object> build(Record document, UserPreference pref) {
        List<DocumentLine> lines = documentLineRepository.findByDocumentIdOrderByLineNo(document.getId());
        DocumentTotals totals = documentTotalService.compute(document, lines);

        Map<String, Object> model = new HashMap<>();
        model.put("doc", docView(document, pref));
        model.put("lines", lines.stream().map(l -> lineView(l, pref)).toList());
        model.put("totals", totalsView(totals, pref));
        model.put("company", companyView());
        model.put("today", fmt.date(Instant.now(), pref));
        return model;
    }

    private PdfDocView docView(Record doc, UserPreference pref) {
        ObjectType type = doc.getObjectType();
        Map<String, Object> data = doc.getData() == null ? Map.of() : doc.getData();

        Map<String, String> fields = new HashMap<>();
        for (FieldDef f : type.getFields()) {
            fields.put(f.getKey(), formatField(f, data.get(f.getKey()), pref));
        }

        String customerId = null;
        for (String key : CUSTOMER_KEYS) {
            Object v = data.get(key);
            if (v != null) { customerId = firstId(v); break; }
        }

        // se non c'è nessuna chiave convenzionale, prendo la prima relation.
        // la logica è meglio un intestatario indovinato che un preventivo senza destinatario
        if (customerId == null) {
            for (FieldDef f : type.getFields()) {
                if (f.getType() == FieldType.RELATION && data.get(f.getKey()) != null) {
                    customerId = firstId(data.get(f.getKey()));
                    break;
                }
            }
        }

        Record customer = customerId == null ? null
                : recordRepository.findByIdAndIsDeletedFalse(UUID.fromString(customerId)).orElse(null);

        // ogni documento ha il suo nome per la data: `date` è del solo DDT, il preventivo ha
        // quoteDate, la fattura issueDate, l'ordine orderDate. Sulla data di creazione si cade
        // solo se non è valorizzata nessuna delle quattro
        String docDate = Stream.of("date", "quoteDate", "issueDate", "orderDate")
                .map(fields::get)
                .filter(v -> v != null && !v.isBlank())
                .findFirst()
                .orElseGet(() -> fmt.date(doc.getCreatedAt(), pref));

        return new PdfDocView(
                str(data.get("number")),
                doc.getTitle() == null ? "" : doc.getTitle(),
                type.getLabel(),
                docDate,
                customer == null ? "" : customer.getTitle(),
                addressBlock(customer),
                str(data.get("notes")),
                str(data.get("paymentTerms")),
                data,
                fields
        );
    }

    // l'indirizzo dell'intestatario, una riga per pezzo. Se l'anagrafica non ha questi campi esce una
    // stringa vuota e il template salta il blocco: non è un errore, è un'anagrafica incompleta
    private String addressBlock(Record customer) {
        if (customer == null || customer.getData() == null) return "";
        Map<String, Object> d = customer.getData();
        List<String> parts = new ArrayList<>();
        // `address` è un LONGTEXT libero: oggi CAP, comune, provincia e nazione stanno lì dentro.
        // Diventeranno campi loro con la fatturazione elettronica, che li pretende separati (6.1)
        add(parts, d.get("address"));
        // la chiave è `vat`: in ObjectTypeSeeder non esiste una sola chiave con l'underscore
        Object vat = d.get("vat");
        if (vat != null && !vat.toString().isBlank()) parts.add("P.IVA " + vat);
        return String.join("\n", parts);
    }

    private void add(List<String> parts, Object v) {
        if (v != null && !v.toString().isBlank()) parts.add(v.toString());
    }

    // righe
    private PdfLineView lineView(DocumentLine l, UserPreference pref) {
        boolean text = l.getKind() != DocumentLine.LineKind.PRODUCT;
        String discount = "";
        if (!text && l.getDiscountType() != null && l.getDiscountValue().signum() != 0) {
            discount = l.getDiscountType() == DocumentLine.DiscountType.PERCENT
                    ? fmt.percent(l.getDiscountValue(), pref)
                    : fmt.money(l.getDiscountValue(), pref);
        }

        // il codice articolo: LAZY, ma qui la transazione è aperta e lo leggiamo una volta a riga
        // non dentro il template
        String code = "";
        if (l.getProduct() != null && l.getProduct().getData() != null) {
            code = str(l.getProduct().getData().get("code"));
        }
        return new PdfLineView(
                l.getLineNo(),
                text,
                l.getDescription() == null ? "" : l.getDescription(),
                code,
                text ? "" : fmt.number(l.getQuantity().stripTrailingZeros(), pref, 4),
                l.getUnit() == null ? "" : l.getUnit(),
                text ? "" : fmt.money(l.getUnitPrice(), pref),
                discount,
                text ? "" : fmt.percent(l.getVatPercent(), pref),
                text ? "" : fmt.money(l.getNetTotal(), pref),
                text ? BigDecimal.ZERO : l.getNetTotal());
    }

    // totali
    private PdfTotalsView totalsView(DocumentTotals t, UserPreference pref) {
        List<PdfVatRowView> rows = new ArrayList<>();
        for(VatSummaryRow r : t.vatRows()) {
            rows.add(new PdfVatRowView(fmt.percent(r.vatPercent(), pref),
                    fmt.money(r.taxable(), pref),
                    fmt.money(r.vat(), pref)));
        }

        return new PdfTotalsView(
                fmt.money(t.linesNet(), pref),
                fmt.money(t.headerDiscount(), pref), t.headerDiscount().signum() != 0,
                fmt.money(t.shipping(), pref), t.shipping().signum() != 0,
                fmt.money(t.taxable(), pref),
                fmt.money(t.vat(), pref),
                fmt.money(t.total(), pref),
                rows);
    }

    // azienda
    private PdfCompanyView companyView() {
        Workspace w = workspaceRepository.findAll().stream().findFirst().orElse(null);
        if (w == null) {
            return new PdfCompanyView("", "", "", "", "", "", false);
        }
        String logo = logoDataUri(w);
        return new PdfCompanyView(
                w.getLegalName() == null || w.getLegalName().isBlank()
                    ? w.getName() : w.getLegalName(),
                nz(w.getAddress()), nz(w.getVatNumber()), nz(w.getTaxCode()),
                nz(w.getContactLine()),
                logo, !logo.isEmpty());
    }

    // il logo diventa un data: uri qui, non nel template. Il renderer ha le risorse esterne bloccate
    // <img src="/api/files/..."> nel template non verrebbe caricato, e non è una semplice svista da
    // aggirare
    private String logoDataUri(Workspace w) {
        String url = w.getLogoUrl();
        if (url == null || url.isBlank()) return "";
        try {
            // logoUrl è del tipo /api/files/<uuid>: prendiamo l'ultimo segmento
            String last = url.substring(url.lastIndexOf('/') + 1);
            FileObject file = fileStorageService.get(UUID.fromString(last));
            byte[] bytes = Files.readAllBytes(fileStorageService.resolveOnDisk(file));
            if (bytes.length > 2_000_000) { // 2 MB di logo: no
                log.warn("Workspace logo is too large for a PDF: {} bytes", bytes.length);
                return "";
            }
            return "data:" + file.getMime() + ";base64," + Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e) {
            // un logo mancante non deve impedire di stampare una fattura
            log.warn("Cannot inline the workspace logo: {}", e.getMessage());
            return "";
        }
    }

    // helper
    private String formatField(FieldDef f, Object value, UserPreference pref) {
        if (value == null) return "";
        return switch (f.getType()) {
            case CURRENCY, DECIMAL -> fmt.money(new BigDecimal(value.toString()), pref);
            case PERCENT -> fmt.percent(new BigDecimal(value.toString()), pref);
            case NUMBER, INTEGER, BIGINT, FLOAT ->
                    fmt.number(new BigDecimal(value.toString()), pref, 0);
            case DATE, DATETIME, TIMESTAMP -> formatDate(value, pref);
            case BOOLEAN -> Boolean.parseBoolean(value.toString()) ? "Si" : "No";
            case RELATION -> relationTitles(value);
            default -> value.toString();
        };
    }

    private String formatDate(Object value, UserPreference pref) {
        try {
            return fmt.date(Instant.parse(value.toString()), pref);
        } catch (Exception e) {
            return value.toString(); // è una data "YYYY-MM-DD"
        }
    }

    // relation: il jsonb contiene un UUID o una lista di UUID quando config.multiple è true
    // Chi legge deve gestire entrambi i rami: è la trappola già pagata in RecordValidator.coerceRelation
    private String relationTitles(Object value) {
        Set<UUID> ids = new LinkedHashSet<>();
        relationTitleService.collectIds(value, ids);
        if (ids.isEmpty()) return String.valueOf(value);

        Map<String, String> titles = relationTitleService.titlesOf(ids);
        return ids.stream()
                .map(id -> titles.getOrDefault(id.toString(), id.toString()))
                .collect(Collectors.joining(", "));
    }

    private String firstId(Object v) {
        if (v instanceof Collection<?> c) {
            return c.isEmpty() ? null : String.valueOf(c.iterator().next());
        }
        String s = String.valueOf(v);
        return s.isBlank() ? null : s;
    }

    private String str(Object v) { return v == null ? "" : v.toString(); }
    private String nz(String s) { return s == null ? "" : s; }
}
