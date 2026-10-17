package it.northleap.backend.calendar;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

// senza questa tabella ogni sync ricreerebbe tutti gli eventi
@Entity
@Table(name = "event_mapping", uniqueConstraints = @UniqueConstraint(columnNames = {"account_id", "external_event_id"}))
@Getter
@Setter
@NoArgsConstructor
public class EventMapping {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "external_event_id", nullable = false)
    private String externalEventId;

    @Column(name = "record_id", nullable = false)
    private UUID recordId;

    private String etag;
}
