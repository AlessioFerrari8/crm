package it.northleap.backend.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_preference")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserPreference {
    @Id
    @Column(name = "user_id")
    private UUID userId;

    // iso 4217, non il simbolo: il simbolo lo deriva il frontend da Intl
    @Column(nullable = false)
    private String currency = "EUR";

    // "." o "," o " " o "" (nessun raggruppamento)
    @Column(nullable = false)
    private String groupSeparator = ".";

    @Column(nullable = false)
    private String decimalSeparator = ",";

    // BEFORE = "€ 1,00", AFTER = "1,00 €"
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SymbolPlacement symbolPlacement = SymbolPlacement.AFTER;

    @Column(nullable = false)
    private int currencyDecimals = 2;

    // tronca 0 successivi dello screenshot: 1,50 -> 1,5 e 1,00 -> 1
    @Column(nullable = false)
    private boolean truncateTrailingZeros = false;

    // formato data mostrato in UI (il dato resta ISO `YYYY-MM-DD` nel jsonb)
    @Column(nullable = false)
    private String dateFormat = "dd/MM/yyyy";

    @UpdateTimestamp
    private Instant updatedAt;

    public enum SymbolPlacement { BEFORE, AFTER }
}
