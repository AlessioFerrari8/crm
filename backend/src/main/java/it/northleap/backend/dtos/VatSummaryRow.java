package it.northleap.backend.dtos;

import java.math.BigDecimal;

// una riga per aliquota, quello che finisce sia nel riquadro del PDF sia in dati riepilogo della fattura elettronica
public record VatSummaryRow(BigDecimal vatPercent, BigDecimal taxable, BigDecimal vat) {

}
