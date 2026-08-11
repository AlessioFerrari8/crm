package it.northleap.backend.dtos;

import it.northleap.backend.entities.DocumentLine;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record DocumentLineDto(
        DocumentLine.LineKind kind,
        UUID productId,
        @Size(max = 2000) String description,
        @DecimalMin("0.0") BigDecimal quantity,
        String unit,
        @DecimalMin("0.0") BigDecimal unitPrice,
        DocumentLine.DiscountType discountType,
        @DecimalMin("0.0") BigDecimal discountValue,
        UUID vatRateId
) {
}



