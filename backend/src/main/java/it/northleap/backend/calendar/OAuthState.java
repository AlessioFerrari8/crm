package it.northleap.backend.calendar;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

// il parametro state di OAuth: random, lato server, monouso, 10 min. Senza, la callback è un CSRF che collega il
// calendario dell'attaccante all'account della vittima
@Entity
@Table(name = "oauth_state")
@Getter
@Setter
@NoArgsConstructor
public class OAuthState {
    @Id
    @Column(length = 64)
    private String stateHash;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private Instant expiresAt;
}
