package it.northleap.backend.dtos;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;


// una riga come la vede il template. Piatta: nessun riferimento alle entità. Se passassi
// documentLine, `line.product.title` dentro il template scatenerebbe una query per riga
@Getter
@AllArgsConstructor
public class PdfLineView {
    private final int lineNo;
    private final boolean text; // riga descrittiva, senza importi
    private final String description;
    private final String productCode; // codice a catalogo, "" se riga libera
    private final String quantity; // formattata
    private final String unit;
    private final String unitPrice; // formattata
    private final String discount; // "10%" / "5,00 EUR" / "" se assente
    private final String vatPercent; // "22%"
    private final String netTotal; // formattata
    private final BigDecimal netTotalRows; // per chi deve confrontare
}
