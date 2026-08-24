package it.northleap.backend.services;

import it.northleap.backend.dtos.DocumentLineDto;
import it.northleap.backend.entities.DocumentLine;
import it.northleap.backend.entities.Record;
import it.northleap.backend.exceptions.NotFoundException;
import it.northleap.backend.exceptions.RbacDeniedException;
import it.northleap.backend.repositories.DocumentLineRepository;
import it.northleap.backend.repositories.RecordRepository;
import it.northleap.backend.security.Actor;
import it.northleap.backend.security.PermAction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;


// le righe si salvano sempre tutte insieme (replaceAll), mai una per volta: i totali del documento
// dipendono da tutte e un salvataggio parziale lascerebbe testata e righe disallineate anche
// se per un solo istante
@Service
@RequiredArgsConstructor
public class DocumentLineService {

    private final DocumentLineRepository documentLineRepository;
    private final RecordRepository recordRepository;
    private final DocumentLineCalculator documentLineCalculator;
    private final RbacService rbacService;

    public List<DocumentLine> list(Actor actor, UUID documentId) {
        assertPerm(actor, documentId, PermAction.READ);
        return documentLineRepository.findByDocumentIdOrderByLineNo(documentId);
    }


    @Transactional
    public List<DocumentLine> replaceAll(Actor actor, UUID documentId, List<DocumentLineDto> dtos) {
        Record document = assertPerm(actor, documentId, PermAction.WRITE);

        // cancella e riscrive: il diff per riga costerebbe più codice di quanto valga
        // e le righe di un documento sono poche decine
        documentLineRepository.deleteByDocumentId(documentId);
        documentLineRepository.flush();

        int lineNo = 1;
        for (DocumentLineDto dto : dtos) {
            DocumentLine line = new DocumentLine();
            line.setDocument(document);
            line.setLineNo(lineNo++);
            line.setKind(dto.kind());
            line.setDescription(dto.description() != null ? dto.description() : "");
            line.setQuantity(dto.quantity() != null ? dto.quantity() : BigDecimal.ONE);
            line.setUnit(dto.unit());
            line.setUnitPrice(dto.unitPrice() != null ? dto.unitPrice() : BigDecimal.ZERO);
            line.setDiscountType(dto.discountType());
            line.setDiscountValue(dto.discountValue() != null ? dto.discountValue() : BigDecimal.ZERO);

            if (dto.productId() != null) {
                line.setProduct(recordRepository.getReferenceById(dto.productId()));
            }
            if (dto.vatRateId() != null) {
                Record rate = recordRepository.findById(dto.vatRateId())
                        .orElseThrow(() -> new NotFoundException("VAT rate not found"));
                line.setVatRate(rate);
                // la percentuale viene copiata sulla riga: se domani l'aliquota
                // viene modificata, i documenti già emessi non devono cambiare
                line.setVatPercent(new BigDecimal(String.valueOf(
                        rate.getData().getOrDefault("rate", "0"))));
            }

            documentLineCalculator.recalculate(line);
            documentLineRepository.save(line);
        }

        List<DocumentLine> saved = documentLineRepository.findByDocumentIdOrderByLineNo(documentId);
        writeTotals(document, saved);
        return saved;
    }

    // I totali finiscono dentro Record.data del documento: devono essere colonne di lista filtrabili e
    // ordinabili ("Totale parziale", "Sub totale" negli screenshot).
    // Ricalcolati a ogni salvataggio delle righe
    private void writeTotals(Record document, List<DocumentLine> lines) {
        BigDecimal subtotal = lines.stream().map(DocumentLine::getNetTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal vatTotal = lines.stream().map(DocumentLine::getVatTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> data = new LinkedHashMap<>(
                document.getData() != null ? document.getData() : Map.of());

        // sconto e trasporto a livello documento: li scrive l'utente sulla testata,
        // qui li rileggo per il totale finale
        BigDecimal docDiscount = decimal(data.get("discountTotal"));
        BigDecimal shipping = decimal(data.get("shippingCost"));

        data.put("subtotal", subtotal);
        data.put("vatTotal", vatTotal);
        data.put("total", subtotal.subtract(docDiscount).add(vatTotal).add(shipping));

        document.setData(data);
        recordRepository.save(document);
    }

    private BigDecimal decimal(Object raw) {
        if (raw == null) return BigDecimal.ZERO;
        try {
            return new BigDecimal(String.valueOf(raw));
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    // usata dalle revisioni e dalla conversione preventivo
    @Transactional
    public void copyLines(UUID fromDocumentId, UUID toDocumentId) {
        Record target = recordRepository.getReferenceById(toDocumentId);
        List<DocumentLine> source = documentLineRepository.findByDocumentIdOrderByLineNo(fromDocumentId);
        for (DocumentLine original : source) {
            DocumentLine copy = new DocumentLine();
            copy.setDocument(target);
            copy.setLineNo(original.getLineNo());
            copy.setKind(original.getKind());
            copy.setProduct(original.getProduct());
            copy.setDescription(original.getDescription());
            copy.setQuantity(original.getQuantity());
            copy.setUnit(original.getUnit());
            copy.setUnitPrice(original.getUnitPrice());
            copy.setDiscountType(original.getDiscountType());
            copy.setDiscountValue(original.getDiscountValue());
            copy.setVatRate(original.getVatRate());
            copy.setVatPercent(original.getVatPercent());
            copy.setNetTotal(original.getNetTotal());
            copy.setVatTotal(original.getVatTotal());
            copy.setGrossTotal(original.getGrossTotal());
            documentLineRepository.save(copy);
        }
        writeTotals(target, documentLineRepository.findByDocumentIdOrderByLineNo(toDocumentId));
    }

    private Record assertPerm(Actor actor, UUID documentId, PermAction action) {
        Record document = recordRepository.findById(documentId)
                .orElseThrow(() -> new NotFoundException("Document not found"));
        if (!rbacService.resolve(actor.roleIds(),
                document.getObjectType().getKey(), action).allowed()) {
            throw new RbacDeniedException("Not allowed on " + document.getObjectType().getKey());
        }
        return document;
    }
}