package it.northleap.backend.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "nps_response", indexes = @Index(name = "idx_nps_response_record", columnList = "record_id, sent_at"))
@Getter
@Setter
@NoArgsConstructor
public class NpsResponse {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "survey_id", nullable = false)
    private NpsSurvey survey;

    // il contatto che risponde
    @Column(name = "record_id", nullable = false)
    private UUID recordId;

    @JsonIgnore
    @Column(nullable = false, unique = true, length = 43)
    private String token;

    // null finché non risponde
    private Integer score;

    @Column(length = 2000)
    private String comment;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    private Instant answeredAt;

    @JsonIgnore
    @Column(length = 45)
    private String remoteIp;
}
