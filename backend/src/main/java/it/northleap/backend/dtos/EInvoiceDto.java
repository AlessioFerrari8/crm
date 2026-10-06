package it.northleap.backend.dtos;


import it.northleap.backend.entities.EInvoice;
import it.northleap.backend.entities.EInvoiceStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// l'XML non viaggia qui: si scarica da /xml, la lista resta leggera
public record EInvoiceDto(
        UUID id,
        UUID invoiceId,
        String invoiceTitle,
        String filename,
        EInvoiceStatus status,
        String sdiIdentifier,
        List<Map<String, Object>> notifications,
        Instant sentAt,
        Instant createdAt
) {
    public static EInvoiceDto from(EInvoice e) {
        return new EInvoiceDto(e.getId(), e.getInvoice().getId(), e.getInvoice().getTitle(), e.getFilename(),
                e.getStatus(), e.getSdiIdentifier(), e.getNotifications(), e.getSentAt(), e.getCreatedAt());
    }
}
