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


// template di stampa. Uno per tipo di documento, ma niente FK a ObjectType: objectKey
// è una stringa come in Permission.resource, così un template può esistere anche per
// un modulo seedato dopo
@Entity
@Table(name = "pdf_template")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class PdfTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // chiave stabile, referenziata dall'URL: quote-default, invoice-default e via dicendo
    @Column(nullable = false, unique = true, length = 64)
    private String key;

    @Column(nullable = false, length = 120)
    private String label;

    // quale modulo stampa: quote, sales_order, delivery_note, invoice
    @Column(name = "object_key", nullable = false, length = 64)
    private String objectKey;

    // il corpo. text, non varchar: un template serio supera i 5000 caratteri
    @Column(nullable = false, columnDefinition = "text")
    private String html = "";

    // A5 / A4 / Letter, e i margini in millimetri. Nel CSS con @page si potrebbe fare tutto
    // ma tenergli qui li rende modificabili dalla UI senza far scrivere CSS a nessuno
    @Column(name = "page_size", nullable = false, length = 16)
    private String pageSize = "A4";

    @Column(name = "margin_mm", nullable = false)
    private int marginMm = 15;

    // i template di sistema non si cancellano, si copiano
    @Column(name = "is_system", nullable = false)
    private boolean isSystem = false;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

}
