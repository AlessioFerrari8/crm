package it.northleap.backend.calendar;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "calendar_account", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "account_email"}))
@Getter
@Setter
@NoArgsConstructor
public class CalendarAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 20)
    private String provider = "GOOGLE";

    @Column(name = "account_email", nullable = false)
    private String accountEmail;

    // cifrato con SecretCipher: è una chiave d'accesso alla vita privata di una persona
    @Column(name = "refresh_token_enc", columnDefinition = "text", nullable = false)
    private String refreshTokenEnc;

    @Column(name = "access_token_enc", columnDefinition = "text")
    private String accessTokenEnc;

    private Instant accessTokenExpiresAt;

    @Column(name = "calendar_id", nullable = false)
    private String calendarId = "primary";

    // null = il prossimo giro è completo (primo giro, o dopo un 410)
    @Column(name = "sync_token", columnDefinition = "text")
    private String syncToken;

    private Instant lastSyncAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp
    private Instant createdAt;
}
