package it.northleap.backend.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "mailbox_config")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class MailboxConfig {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 120)
    private String label; // "assistenza"

    @Column(nullable = false) private String host;
    @Column(nullable = false) private int port = 993;
    @Column(nullable = false) private boolean ssl = true;
    @Column(nullable = false) private String username;

    // non in chiaro
    @Column(name = "password_enc", nullable = false)
    private String passwordEnc;

    @Column(nullable = false) private String folder = "INBOX";

    // dove finiscono i messaggi: di solito "ticket", ma niente lo impone
    @Column(name = "target_object_key", nullable = false, length = 64)
    private String targetObjectKey = "ticket";

    // proprietario dei record creati: senza, con scope OWN sono indivisibili. È la trappola dei seeder,
    // identica
    @Column(name = "default_owner_id")
    private UUID defaultOwnerId;

    @Column(nullable = false) private boolean active = true;

    // uid dell'ultimo messaggio processato. Imap li assegna crescenti e stabili per folder. È il modo
    // giusto di dire "da qui in poi"
    @Column(name = "last_uid", nullable = false)
    @ColumnDefault("0")
    private long lastUid = 0;

    // lock: chi scansiona scrive qui adesso + 5min. Chi trova un valore futuro, salta il giro
    // costa solo una colonna e rende in più il polling sicuro anche con due istanze, che oggi
    // non ci sono ma domani sì
    @Column(name = "locked_until")
    private Instant lockedUntil;

    private Instant lastScanAt;
    private String lastError;

}
