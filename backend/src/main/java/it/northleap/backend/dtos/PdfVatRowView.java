package it.northleap.backend.dtos;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PdfVatRowView {
    private final String vatPercent; // "22%"
    private final String taxable; // "9.054,00 EUR"
    private final String vat; // "1.991,88 EUR"
}
