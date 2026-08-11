package it.northleap.backend.entities;


import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "dashboard_widget")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DashboardWidget {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tab_id", nullable = false)
    @JsonIgnore
    private DashboardTab tab;

    @Column(nullable = false)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WidgetKind kind;

    // colore semantico del KPI tile: blu neutro, verde ok, arancio attenzione, rosso critico
    private String color;

    private String icon;

    // posizione nella griglia: 1..4 colonne
    @Column(name = "grid_span", nullable = false)
    private int gridSpan = 1;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    // la ReportQuery serializzata
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> query;

    public enum WidgetKind { KPI, KPI_SPLIT, CHART, TABLE, PIVOT, RECORD_LIST }

}
