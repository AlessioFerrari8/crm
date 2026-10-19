package it.northleap.backend.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false)
    private String email;

    private String name;

    // salvo hash e non token
    // se il DB viene
    // compromesso un attaccante non deve poter riusare le sessioni
    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private boolean isActive = true;

    private Instant lastLoginAt;

    private String avatarUrl;

    // relazione con Role, da gestire
    // sarà una relazione many to many

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User)) return false;
        User other = (User) o;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    // segreto TOTP, cifrato a riposo (AES-GCM, chiave da MFA_SECRET_KEY)
    // Chi legge il database non deve poter generare i codici: è l'unica cosa che distingue un
    // secondo fattore da una seconda password
    @Column(name = "mfa_secret")
    private String mfaSecret;

    @Column(name = "mfa_enabled", nullable = false)
    @ColumnDefault("false")
    private boolean mfaEnabled = false;

    // hash SHA-256 dei codici di recupero non ancora usati
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "mfa_recovery_codes", columnDefinition = "jsonb")
    private List<String> mfaRecoveryCodes;

    // l'ultimo contatore accettato: è l'anti replay
    @Column(name = "mfa_last_counter")
    private Long mfaLastCounter;

    // tariffa oraria per il time tracking: si copia su ogni registrazione alla chiusura
    @Column(name = "hourly_rate", precision = 10, scale = 2)
    private java.math.BigDecimal hourlyRate;

    // false solo per chi si registra da solo e non ha ancora cliccato il link. Il default è true: gli utenti creati
    // dall'onboarding o da un invito (l'invito è già una prova di possesso dell'email) e quelli già nel db restano
    // verificati
    @Column(name = "email_verified", nullable = false)
    @ColumnDefault("true")
    private boolean emailVerified = true;

    // hash SHA-256 del token mandato per email e sua scadenza. Entrambi null a verifica fatta
    @Column(name = "verification_token_hash")
    private String verificationTokenHash;

    @Column(name = "verification_expires_at")
    private Instant verificationExpiresAt;
}
