package it.northleap.backend.entities;


import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;


// Un tab ("Lead", "Vendite", "Supporto") Entità separata e non un jsonb dentro Dashboard
// i widget si spostano fra tab, si duplicano e si cancellano singolarmente, e con un jsonb
// ogni operazione riscriverebbe tutto il documento
@Entity
@Table(name = "dashboard_tab")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DashboardTab {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dashboard_id", nullable = false)
    @JsonIgnore
    private Dashboard dashboard;

    @Column(nullable = false)
    private String label;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;
}
