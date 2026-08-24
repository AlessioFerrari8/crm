package it.northleap.backend.services;

import it.northleap.backend.entities.DocumentLine;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

public class DocumentLineCalculatorTest {

    private final DocumentLineCalculator documentLineCalculator = new DocumentLineCalculator();

    private DocumentLine line(String qty, String price, String vat, DocumentLine.DiscountType type, String discount) {
        DocumentLine l = new DocumentLine();
        l.setKind(DocumentLine.LineKind.PRODUCT);
        l.setQuantity(new BigDecimal(qty));
        l.setUnitPrice(new BigDecimal(price));
        l.setVatPercent(new BigDecimal(vat));
        l.setDiscountType(type);
        l.setDiscountValue(new BigDecimal(discount));
        return l;
    }

    @Test
    void sconto_percentuale() {
        DocumentLine l = line("1", "100.00", "22", DocumentLine.DiscountType.PERCENT, "10");
        documentLineCalculator.recalculate(l);
        assertThat(l.getNetTotal()).isEqualByComparingTo("90.00");
        assertThat(l.getVatTotal()).isEqualByComparingTo("19.80");
        assertThat(l.getGrossTotal()).isEqualByComparingTo("109.80");
    }

    @Test
    void sconto_in_valore_maggiore_dell_imponibile_non_va_sotto_zero() {
        DocumentLine l = line("1", "100.00", "22", DocumentLine.DiscountType.AMOUNT, "150");
        documentLineCalculator.recalculate(l);
        assertThat(l.getNetTotal()).isEqualByComparingTo("0.00");
        assertThat(l.getGrossTotal()).isEqualByComparingTo("0.00");
    }

    @Test
    void arrotondamento_sui_terzi() {
        DocumentLine l = line("3", "0.33", "22", DocumentLine.DiscountType.PERCENT, "0");
        documentLineCalculator.recalculate(l);
        assertThat(l.getNetTotal()).isEqualByComparingTo("0.99");
        assertThat(l.getVatTotal()).isEqualByComparingTo("0.22");
        assertThat(l.getGrossTotal()).isEqualByComparingTo("1.21");
    }

    @Test
    void riga_di_solo_testo_non_ha_importi() {
        DocumentLine l = line("1", "999.00", "22", DocumentLine.DiscountType.PERCENT, "0");
        l.setKind(DocumentLine.LineKind.TEXT);
        documentLineCalculator.recalculate(l);
        assertThat(l.getNetTotal()).isEqualByComparingTo("0");
        assertThat(l.getVatTotal()).isEqualByComparingTo("0");
        assertThat(l.getGrossTotal()).isEqualByComparingTo("0");
    }

}
