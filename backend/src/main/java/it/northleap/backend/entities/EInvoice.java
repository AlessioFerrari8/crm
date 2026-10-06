package it.northleap.backend.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "e_invoice", indexes = @Index(name = "idx_e_invoice", columnList = "invoice_id"))
@Getter
@Setter
@NoArgsConstructor
public class EInvoice {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Record invoice;

    @Column(nullable = false, unique = true)
    private String filename; // IT{piva}_{progressivo}.xml

    // si conserva: è il documento fiscale, non un output rigenerabile. Così se un domani correggo un bug nel
    // generatore, la fattura già trasmessa deve restare identica a quella trasmessa
    @Column(columnDefinition = "text", nullable = false)
    private String xml;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EInvoiceStatus status;

    private String sdiIdentifier;

    // una voce per ricevuta: tipo (RC/NS/MC/NE), data, messaggio. L'ultimo NS va mostrato per intero
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<Map<String, Object>> notifications = new ArrayList<>();

    private Instant sentAt;
    @CreationTimestamp private Instant createdAt;
    @UpdateTimestamp private Instant updatedAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EInvoice other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
