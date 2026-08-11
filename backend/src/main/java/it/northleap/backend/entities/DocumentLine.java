package it.northleap.backend.entities;


// riga di documento di vendita (preventivo, ordine, ddt, fattura). Unica tabella per
// tutti e quattro: la riga è identica, cambia solo il documento che la contiene, che è un Record
// qualsiasi -> nessun vincolo di tipo qui.

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "document_line", indexes = @Index(name = "idx_document_line_document", columnList = "document_id, line_no"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DocumentLine {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // il documento: un Record di tipo quote / sales_order / delivery_note / invocie
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private Record document;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    // text = riga descrittiva senza importi
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LineKind kind = LineKind.PRODUCT;

    // l'articolo a catalogo: Record di tipo "product". Null sulle righe libere.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Record product;

    // copiata dall'articolo ma modificabile: il documento deve restare uguale a se stesso
    // anche se domani il catalogo cambia descrizione
    @Column(nullable = false, length = 200)
    private String description = "";

    @Column(precision = 19, scale = 4, nullable = false)
    private BigDecimal quantity = BigDecimal.ONE;

    private String unit;

    @Column(name = "unit_price", precision = 19, scale = 4, nullable = false)
    private BigDecimal unitPrice = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DiscountType discountType = DiscountType.PERCENT;

    @Column(precision = 19, scale = 4, nullable = false)
    private BigDecimal discountValue = BigDecimal.ZERO;

    // aliquota applicata: Record di tipo "vat_rate". La percentuale viene copiata in vatRate al salvataggio
    // così se domani l'aliquota cambia, i documenti vecchi non devono morire
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vat_rate_id")
    private Record vatRate;

    @Column(name = "vat_percent", precision = 5, scale = 2, nullable = false)
    private BigDecimal vatPercent = BigDecimal.ZERO;

    // calcolati e persistiti: servono per ordinare e sommare in SQL
    @Column(name = "net_total", precision = 19, scale = 2, nullable = false)
    private BigDecimal netTotal = BigDecimal.ZERO;

    @Column(name = "vat_total", precision = 19, scale = 2, nullable = false)
    private BigDecimal vatTotal = BigDecimal.ZERO;

    @Column(name = "gross_total", precision = 19, scale = 2, nullable = false)
    private BigDecimal grossTotal = BigDecimal.ZERO;

    public enum LineKind { PRODUCT, TEXT, SUBTOTAL };

    public enum DiscountType { PERCENT, AMOUNT };



}
