package it.northleap.backend.entities;


// Contatore per (objectKey, anno). Riga separata e non colonna su object_type
// perché va bloccata in scrittura durante l'incremento: con anno = 0 si ottiene la
// serie unica non annuale (i ticket)

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "document_sequence", uniqueConstraints = @UniqueConstraint(columnNames = {"object_key", "year"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DocumentSequence {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "object_key", nullable = false)
    private String objectKey;

    // "year" è una parola comune ma non riservata in postgres, resta così
    @Column(nullable = false)
    private int year;

    @Column(nullable = false)
    private long counter = 0;
}
