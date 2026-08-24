package it.northleap.backend.dtos;

import it.northleap.backend.entities.DocumentLine;

import java.math.BigDecimal;
import java.util.UUID;

// quello che serve davvero alla griglia. Le tre relazioni escono come id + etichetta:
// il client non ha mai bisogno del documento dentro la sua stessa riga,
// e senza questo DTO ogni riga se lo porta dietro tutto
public record DocumentLineResponse(
        UUID id,
        int lineNo,
        DocumentLine.LineKind kind,
        UUID productId,
        String productLabel,
        String description,
        BigDecimal quantity,
        String unit,
        BigDecimal unitPrice,
        DocumentLine.DiscountType discountType,
        BigDecimal discountValue,
        UUID vatRateId,
        BigDecimal vatPercent,
        BigDecimal netTotal,
        BigDecimal vatTotal,
        BigDecimal grossTotal
) {
    public static DocumentLineResponse of(DocumentLine l) {
        return new DocumentLineResponse(
                l.getId(), l.getLineNo(), l.getKind(),
                l.getProduct() != null ? l.getProduct().getId() : null,
                l.getProduct() != null ? l.getProduct().getTitle() : null,
                l.getDescription(), l.getQuantity(), l.getUnit(), l.getUnitPrice(),
                l.getDiscountType(), l.getDiscountValue(),
                l.getVatRate() != null ? l.getVatRate().getId() : null,
                l.getVatPercent(), l.getNetTotal(), l.getVatTotal(), l.getGrossTotal());
    }
}


// ----------------------------------- ATTENTION -----------------------------------
//`l.getProduct().getTitle()` su un proxy LAZY fa una query in più per riga.
// Con poche decine di righe va bene ed è dentro la transazione; se un giorno le righe
// diventato centinaia, la strada è un join fetch nel repository
// ---------------------------------------------------------------------------------
