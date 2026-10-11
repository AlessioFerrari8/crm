package it.northleap.backend.portal;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

// non è un User con un ruolo in meno: due tabelle, due login, due filtri. Se fossero la stessa entità, il giorno che
// qualcuno cambia PermissionSeeder il cliente si troverebbe permessi che nessuno gli ha dato
@Entity
@Table(name = "portal_user")
@Getter
@Setter
@NoArgsConstructor
public class PortalUser {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    private String passwordHash; // null finché l'invito non è accettato

    // il contatto (Record) da cui parte ogni autorizzazione del portale
    @Column(name = "contact_record_id", nullable = false)
    private UUID contactRecordId;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "invite_token_hash", length = 64)
    private String inviteTokenHash;

    private Instant inviteExpiresAt;

    // i token emessi prima di questo istante non valgono più: cambio password = logout ovunque
    private Instant tokensValidAfter;

    private Instant lastLoginAt;

    @CreationTimestamp
    private Instant createdAt;
}
