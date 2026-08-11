package it.northleap.backend.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// "order" è parola riservata in Postgres -> campo/colonna rinominati sortOrder/sort_order
@Entity
@Table(name = "object_type")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ObjectType {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String key;

    @Column(nullable = false)
    private String label;

    @Column(nullable = false)
    private String pluralLabel;

    private String icon;

    private String color;

    @Column(nullable = false)
    private boolean isSystem = false;

    @Column(nullable = false)
    private boolean isEnabled = true;

    // modulo pubblico di lead capture (/f/{key} + /api/public/forms/{key}): spento di default,
    // l'admin lo abilita per i tipi che vuole esporre (es. lead).
    // @ColumnDefault: senza, ddl-auto=update genera "ADD COLUMN ... NOT NULL" SENZA default e
    // l'ALTER fallisce sulle tabelle già popolate -> seeders in errore -> app in crash-loop
    // (successo davvero al primo deploy; il default Java new ObjectType() non finisce nel DDL)
    @Column(nullable = false)
    @ColumnDefault("false")
    private boolean publicForm = false;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @OneToMany(mappedBy = "objectType", fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC")
    private List<FieldDef> fields = new ArrayList<>();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ObjectType other)) return false;
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

    // numerazione automatica: se numberingField è valorizzato, alla creazione di un record
    // quel campo viene riempito con il progressivo. Null = nessuna numerazione (il caso di quasi tutti i moduli)
    // @ColumnDefault su tutte le not null: object_type è popolata, senza default l'alter di ddl-auto fallisce
    // e l'app va in crash-loop
    private String numberingField;

    private String numberingPrefix;

    @Column(nullable = false)
    @ColumnDefault("false")
    private boolean numberingUseYear = false;

    @Column(nullable = false)
    @ColumnDefault("0")
    private int numberingPadding = 0;

    // separatore fra anno e progressivo: "2025-47" -> "-"
    @Column(nullable = false)
    @ColumnDefault("'-'")
    private String numberingSeparator = "-";


}
