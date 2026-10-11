package it.northleap.backend.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

// Nota/commento su un Record: la timeline collaborativa del CRM. recordId è una soft FK mai
// navigata (stesso pattern di FileObject.recordId/Record.ownerId); authorName è denormalizzato
// alla creazione così la lista non deve joinare users a ogni lettura.
@Entity
@Table(name = "record_note", indexes = @Index(name = "idx_record_note_record", columnList = "record_id, created_at"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RecordNote {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "record_id", nullable = false)
    private UUID recordId;

    @Column(name = "author_id")
    private UUID authorId;

    @Column(nullable = false)
    private String authorName;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    // il portale mostra al cliente solo le note marcate così. Default false: una nota interna che finisce davanti
    // al cliente è l'incidente peggiore che il portale possa fare
    @Column(name = "customer_visible", nullable = false)
    private boolean customerVisible = false;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
