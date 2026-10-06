package it.northleap.backend.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// Tabella vera e non Record+jsonb: somme per record e un timer "in corso" per utente
@Entity
@Table(name = "time_entry", indexes = {
        @Index(name = "idx_time_entry_record", columnList = "record_id"),
        @Index(name = "idx_time_entry_user_started", columnList = "user_id, started_at")
})
@Getter
@Setter
@NoArgsConstructor
public class TimeEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "record_id", nullable = false)
    private UUID recordId;

    // denormalizzato: filtrare per modulo senza login
    @Column(name = "object_key", nullable = false, length = 64)
    private String objectKey;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    // null = il timer sta girando
    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(nullable = false)
    private int minutes;

    @Column(nullable = false)
    private boolean billable = true;

    @Column(length = 500)
    private String description;

    // copiata alla chiusura, come vatPercent sulle righe: il consuntivo non cambia se cambia la tariffa
    @Column(name = "hourly_rate", precision = 10, scale = 2)
    private BigDecimal hourlyRate;

    // chiuso dal job notturno e non dall'utente: il numero di minuti è una stima, va rivisto
    @Column(name = "auto_closed", nullable = false)
    private boolean autoClosed = false;

    @CreationTimestamp
    private Instant createdAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TimeEntry other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
