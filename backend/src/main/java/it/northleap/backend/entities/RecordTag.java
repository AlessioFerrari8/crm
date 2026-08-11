package it.northleap.backend.entities;

import it.northleap.backend.dtos.Tag;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "record_tag", uniqueConstraints = @UniqueConstraint(columnNames = {"record_id", "tag_id"}),
        indexes = @Index(name = "idx_record_tag_tag", columnList = "tag_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RecordTag {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "record_id", nullable = false)
    private Record record;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tag_id", nullable = false)
    private Tag tag;
}
