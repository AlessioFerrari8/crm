package it.northleap.backend.services;

import it.northleap.backend.dtos.DocumentTotals;
import it.northleap.backend.dtos.VatSummaryRow;
import it.northleap.backend.entities.DocumentLine;
import it.northleap.backend.entities.Record;
import it.northleap.backend.repositories.DocumentLineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class DocumentTotalService {

    private final DocumentLineRepository documentLineRepository;

    private static final int SCALE = 2;
    private static final RoundingMode RM = RoundingMode.HALF_UP;

    // aliquota su cui mettere il trasporto quando il documento non ha righe
    private static final BigDecimal FALLBACK_RATE = new BigDecimal("22");

    public DocumentTotals compute(Record document) {
        return compute(document, documentLineRepository.findByDocumentIdOrderByLineNo(document.getId()));
    }

    public DocumentTotals compute(Record document, List<DocumentLine> lines) {

        // le righe TEXT non entrano nei totali
        List<DocumentLine> billable = lines.stream()
                .filter(l -> l.getKind() == DocumentLine.LineKind.PRODUCT)
                .toList();

        BigDecimal linesNet = billable.stream()
                .map(DocumentLine::getNetTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(SCALE, RM);

        // sconto e trasporto stanno nel jsonb del documento, non fra le righe
        BigDecimal headerDiscount = money(document, "discountTotal");
        BigDecimal shipping = money(document, "shippingCost");

        // raggruppa i netti per aliquota
        TreeMap<BigDecimal, BigDecimal> byRate = new TreeMap<>();
        for (DocumentLine l : billable) {
            byRate.merge(l.getVatPercent(), l.getNetTotal(), BigDecimal::add);
        }

        // il trasporto è un imponibile a sé e va sull'aliquota più alta presente nel documento:
        // regola classica delle spese accessorie. Senza righe non c'è un'aliquota da cui dedurlo
        if (shipping.signum() != 0) {
            BigDecimal rate = byRate.isEmpty() ? FALLBACK_RATE : byRate.lastKey();
            byRate.merge(rate, shipping, BigDecimal::add);
        }

        // base su cui ripartire lo sconto di testata: tutto l'imponibile lordo
        BigDecimal base = linesNet.add(shipping);

        List<VatSummaryRow> rows = new ArrayList<>();
        BigDecimal vatTotal = BigDecimal.ZERO;
        BigDecimal taxableTotal = BigDecimal.ZERO;

        for(Map.Entry<BigDecimal, BigDecimal> e : byRate.entrySet()) {
            BigDecimal share = base.signum() == 0
                    ? BigDecimal.ZERO
                    : headerDiscount.multiply(e.getValue().divide(base, 6, RM));

            BigDecimal rowTaxable = e.getValue().subtract(share).setScale(SCALE, RM);
            BigDecimal rowVat = rowTaxable.multiply(e.getKey())
                    .divide(BigDecimal.valueOf(100), SCALE, RM);
            rows.add(new VatSummaryRow(e.getKey(), rowTaxable, rowVat));
            taxableTotal = taxableTotal.add(rowTaxable);
            vatTotal = vatTotal.add(rowVat);
        }

        taxableTotal = taxableTotal.setScale(SCALE, RM);
        vatTotal = vatTotal.setScale(SCALE, RM);

        return new DocumentTotals(linesNet, headerDiscount, shipping, taxableTotal,
                vatTotal, taxableTotal.add(vatTotal), rows);
    }

    private BigDecimal money(Record document, String key) {
        Object v = document.getData() == null ? null : document.getData().get(key);
        if (v == null) return BigDecimal.ZERO;
        String s = v.toString().trim();
        if (s.isEmpty()) return BigDecimal.ZERO;
        // non `new BigDecimal(double)`: 0.1 diventerebbe 0.1000000000000000055511151231257827. Il jsonb restituisce
        // Double o Integer
        return new BigDecimal(s).setScale(SCALE, RM);
    }
}
