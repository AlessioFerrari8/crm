package it.northleap.backend.entities;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "e_invoice")
public class EInvoice {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    private Record invoice;

    @Column(nullable = false, unique = true)
    private String filename; // IT{piva}_{progressivo}.xml

    // si conserva: è il documento fiscale, non un output rigenerabile. Così se un domani correggo un bug nel
    // generatore, la fattura già trasmessa deve restare identica a quella trasmessa
    @Column(columnDefinition = "text", nullable = false)
    private String xml;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EInvoiceStatus status; // DRAFT, READY, SENT, DELIVERED, REJECTED, ACCEPTED

    private String sdiIdentifier;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<Map<String, Object>> notifications;

    private Instant sentAt;
    @CreationTimestamp private Instant createdAt;
    @UpdateTimestamp private Instant updatedAt;
}
