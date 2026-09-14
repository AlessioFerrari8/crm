package it.northleap.backend.dtos;


import java.math.BigDecimal;
import java.util.List;

public record DocumentTotals(BigDecimal linesNet, // somma dei netti riga
                             BigDecimal headerDiscount, // sconto di testata
                             BigDecimal shipping, // spese di trasporto
                             BigDecimal taxable, // imponibile finale
                             BigDecimal vat, // IVA totale
                             BigDecimal total, // da pagare
                             List<VatSummaryRow> vatRows) { }
