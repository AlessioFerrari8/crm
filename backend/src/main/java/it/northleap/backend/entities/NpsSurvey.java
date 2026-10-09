package it.northleap.backend.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "nps_survey")
@Getter
@Setter
@NoArgsConstructor
public class NpsSurvey {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 300)
    private String question = "Quanto consiglieresti la nostra azienda a un collega?";

    @Column(nullable = false)
    private boolean active = true;

    // non richiedere due volte alla stessa persona entro N giorni: è il campo che evita di bruciare la lista
    @Column(name = "cooldown_days", nullable = false)
    private int cooldownDays = 90;

    @CreationTimestamp
    private Instant createdAt;
}
