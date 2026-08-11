package it.northleap.backend.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "report")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String title;

    // bar / pie / grid, come la colonna TIPO della lista report
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportRender render;

    @Column(name = "object_key", nullable = false)
    private String objectKey;

    // cartella come stringa e non entità
    private String folder;

    @Column(name = "owner_id")
    private UUID ownerId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> query;

    public enum ReportRender { BAR, LINE, PIE, GRID } 
}
