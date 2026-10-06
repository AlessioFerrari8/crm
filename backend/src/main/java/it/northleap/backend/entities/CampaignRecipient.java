package it.northleap.backend.entities;


import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

// UNIQUE (campaign_id, email): la stessa persona non riceve due volte la stessa campagna, nemmeno se compare due volte nel filtro
@Entity
@Table(name = "campaign_recipient", uniqueConstraints = @UniqueConstraint(columnNames = {"campaign_id", "email"}),
    indexes = @Index(name = "idx_campaign_recipient_record", columnList = "record_id"))
@Getter
@Setter
@NoArgsConstructor
public class CampaignRecipient {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @JsonIgnore // LAZY: serializzarla porterebbe fuori un proxy Hibernate
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id", nullable = false)
    private Campaign campaign;

    @Column(name = "record_id")
    private UUID recordId;

    // null dopo l'anonimizzazione: la riga resta per le stats
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecipientStatus status = RecipientStatus.PENDING;

    // random, mai l'email nell'URL. Uno per destinatario: apertura, click e disiscrizione
    @JsonIgnore // con il token chiunque veda la lista potrebbe disiscrivere gli altri
    @Column(unique = true, length = 43)
    private String token;

    private Instant sentAt;
    private Instant openedAt;
    private Instant clickedAt;

    @Column(name = "fail_reason", length = 500)
    private String failReason;
}
