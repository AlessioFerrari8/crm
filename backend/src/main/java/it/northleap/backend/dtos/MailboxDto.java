package it.northleap.backend.dtos;

import java.time.Instant;
import java.util.UUID;

// stessa lezione già pagata su Webhook.secret: entità con un segreto -> facciamo un DTO dedicato
// La password cifrata non esce dall'API nemmeno cifata
public record MailboxDto(
        UUID id,
        String label,
        String host,
        int port,
        boolean ssl,
        String username,
        String folder,
        String targetObjectKey,
        UUID defaultOwnerId,
        boolean active,
        long lastUid,
        Instant lastScanAt,
        String lastError
) {
    public static MailboxDto of(it.northleap.backend.entities.MailboxConfig m) {
        return new MailboxDto(m.getId(), m.getLabel(), m.getHost(), m.getPort(), m.isSsl(),
                m.getUsername(), m.getFolder(), m.getTargetObjectKey(), m.getDefaultOwnerId(),
                m.isActive(), m.getLastUid(), m.getLastScanAt(), m.getLastError());
    }
}
