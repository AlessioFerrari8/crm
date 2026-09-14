package it.northleap.backend.dtos;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class PdfTotalsView {
    private final String linesNet;
    private final String headerDiscount;
    private final boolean hasDiscount; // per un th:if leggibile nel template
    private final String shipping;
    private final boolean hasShipping;
    private final String taxable;
    private final String vat;
    private final String total;
    private final List<PdfVatRowView> vatRows;
}
