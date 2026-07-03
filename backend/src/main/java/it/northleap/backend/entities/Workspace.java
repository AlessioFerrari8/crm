package it.northleap.backend.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workspace")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Workspace {

    // chiave
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String brandColor = "#0A84FF";

    private String logoUrl;

    // link pubblico di prenotazione Cal.com (es. https://cal.com/tuonome): mostrato nella pagina
    // Calendario come embed per fissare appuntamenti con persone esterne
    private String calcomUrl;

    @Column(nullable = false)
    private boolean onboarded = false;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Workspace other)) return false;
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

}
