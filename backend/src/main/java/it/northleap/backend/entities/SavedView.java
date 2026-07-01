package it.northleap.backend.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

// Vista salvata della record-list: filtri+ordinamento+ricerca+modalità (tabella/board/calendario)
// con un nome, per object type. Personale: ownerId è l'utente che l'ha creata (soft FK, mai
// navigata - stesso pattern di Record.ownerId/Notification.userId). objectKey è una stringa e non
// una FK a ObjectType, come Permission.resource. config è jsonb generico (mai JsonNode).
@Entity
@Table(name = "saved_view")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SavedView {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String objectKey;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private UUID ownerId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> config;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
