package it.northleap.backend.services;

import it.northleap.backend.dtos.DocumentTotals;
import it.northleap.backend.entities.DocumentLine;
import it.northleap.backend.repositories.DocumentLineRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import it.northleap.backend.entities.Record;

import javax.swing.text.Document;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

// i numeri di un documento: se sbagliano qui, il PDF e la fattura elettronica li stampano sbagliati
// in modo credibile. Le chiavi sono quelle vere del jsonb (discountTotal, shippingCost): "discount/
// shipping" non esistono in nessun ObjectType
@ExtendWith(MockitoExtension.class)
public class DocumentTotalServiceTest {

    @Mock private DocumentLineRepository documentLineRepository;
    @InjectMocks private DocumentTotalService documentTotalService;

    private Record doc(String discount, String shipping) {
        Record r = new Record();
        Map<String, Object> data = new HashMap<>();
        if (discount != null) data.put("discountTotal", discount);
        if (shipping != null) data.put("shippingCost", shipping);
        r.setData(data);
        return r;
    }

    private DocumentLine line(String net, String vat) {
        DocumentLine l = new DocumentLine();
        l.setKind(DocumentLine.LineKind.PRODUCT);
        l.setNetTotal(new BigDecimal(net));
        l.setVatPercent(new BigDecimal(vat));
        return l;
    }

    @Test
    void due_aliquote_diverse_danno_due_righe_di_riepilogo() {
        DocumentTotals t = documentTotalService.compute(doc(null, null),
                List.of(line("100.00", "22"), line("50.00", "10")));

        assertThat(t.vatRows()).hasSize(2);
        assertThat(t.linesNet()).isEqualByComparingTo("150.00");
        assertThat(t.vat()).isEqualByComparingTo("27.00"); // 22 + 5
        assertThat(t.total()).isEqualByComparingTo("177.00");
    }

    // è il test che conta: la somma delle righe del riepilogo deve essere uguale all'imponibile
    // totale. Se lo sconto di testata viene applicato solo al totale e non ripartito, qui la somma
    // non torna, e su una fattura elettronica quello è uno scarto
    @Test
    void lo_sconto_di_testata_e_ripartito_sulle_aliquote() {
        DocumentTotals t = documentTotalService.compute(doc("30.00", null),
                List.of(line("100.00", "22"), line("50.00", "10")));

        BigDecimal sommaRighe = t.vatRows().stream()
                .map(r -> r.taxable())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(sommaRighe).isEqualByComparingTo(t.taxable());
        assertThat(t.taxable()).isEqualByComparingTo("120.00");
        // 80 al 22% = 17.60, 40 al 10% = 4.00
        assertThat(t.vat()).isEqualByComparingTo("21.60");
    }

    @Test
    void una_riga_di_testo_non_entra_in_nessun_totale() {
        DocumentLine text = line("999.0", "22");
        text.setKind(DocumentLine.LineKind.TEXT);

        DocumentTotals t = documentTotalService.compute(doc(null, null),
                List.of(line("100.00", "22"), text));

        assertThat(t.linesNet()).isEqualByComparingTo("100.00");
        assertThat(t.total()).isEqualByComparingTo("122.00");
    }

    @Test
    void il_trasporto_finisce_sull_aliquota_piu_alta() {
        DocumentTotals t = documentTotalService.compute(doc(null, "10.00"), List.of(
                line("100.00", "22"), line("50.00", "10")));

        assertThat(t.vatRows()).hasSize(2);
        // 110 al 22% (100 + 10 di trasporto), 50 al 10%
        assertThat(t.vatRows().stream()
                .filter(r -> r.vatPercent().compareTo(new BigDecimal("22")) == 0)
                .findFirst().orElseThrow().taxable())
                .isEqualByComparingTo("110.00");
        assertThat(t.vat()).isEqualByComparingTo("29.20"); // 24.20 + 5.00
    }

    @Test
    void documento_senza_righe_e_tutto_zero_e_non_divide_per_zero() {
        DocumentTotals t = documentTotalService.compute(doc("50.00", null), List.of());

        assertThat(t.linesNet()).isEqualByComparingTo("0.00");
        assertThat(t.taxable()).isEqualByComparingTo("0.00");
        assertThat(t.total()).isEqualByComparingTo("0.00");
        assertThat(t.vatRows()).isEmpty();
    }

    @Test
    void il_campo_sconto_vuoto_nel_jsonb_non_esplode() {
        // un input html svuotato manda "", non null: new BigDecimal("") è un
        // NumberFormatException, ed è successo davvero altrove
        DocumentTotals t = documentTotalService.compute(doc("", ""), List.of(line("10.00", "22")));

        assertThat(t.total()).isEqualByComparingTo("12.20");
    }

}
