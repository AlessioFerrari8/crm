package it.northleap.backend.entities;


import com.fasterxml.jackson.annotation.JsonIgnore;
import it.northleap.backend.dtos.FilterGroup;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

// la lista marketing non è un'entità: è targetObjectKey + targetFilter, lo stesso FilterGroup delle viste.
// Si materializzano solo i destinatari, e solo al momento dell'invio
@Entity
@Table(name = "campaign")
@Getter
@Setter
@NoArgsConstructor
public class Campaign {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String subject;

    // il testo che l'anteprima della casella mostra accanto all'oggetto
    private String preheader;

    // Thymeleaf, variabili da `r` (il jsonb del destinatario)
    @Column(name = "body_html", columnDefinition = "text", nullable = false)
    private String bodyHtml;

    // non opzionale: un multipart senza parte testuale prende punti di spam da solo
    @Column(name = "body_text", columnDefinition = "text", nullable = false)
    private String bodyText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CampaignStatus status = CampaignStatus.DRAFT;

    @Column(name = "target_object_key", nullable = false)
    private String targetObjectKey = "contact";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "target_filter", columnDefinition = "jsonb")
    private FilterGroup targetFilter;

    // firma dei link di click: per campagna, così un segreto vale per una campagna sola
    @JsonIgnore // l'entità esce nuda dall'API: senza, chiunque legga la campagna sa firmare link
    @Column(name = "tracking_secret", nullable = false, length = 64)
    private String trackingSecret;

    @Column(name = "created_by")
    private UUID createdBy;

    private Instant startedAt;
    private Instant finishedAt;

    @CreationTimestamp private Instant createdAt;
    @UpdateTimestamp private Instant updatedAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Campaign other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
