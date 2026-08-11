package it.northleap.backend.services;

import it.northleap.backend.entities.DocumentLine;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;


// il calcolo di una riga, isolato apposta: è l'unico pezzo del CRM dove un arrotondamento sbagliato
// produce un documento fiscale sbagliato, e va testato da solo.

// ORDINE DELLE OPERAZIONI
//   1. imponibile lordo   = quantità x prezzo unitario
//   2. sconto             = % dell'imponibile, oppure valore assoluto
//   3. imponibile netto   = lordo - sconto            -> arrotondato a 2 decimali
//   4. IVA                = netto x aliquota          -> arrotondato a 2 decimali
//   5. totale riga        = netto + IVA
// L'arrotondamento avviene dopo lo sconto e dopo l'IVA, mai prima: arrotondare il prezzo unitario
// propaga l'errore su tutta la quantità
@Component
public class DocumentLineCalculator {
    private static final int MONEY_SCALE = 2;
    // HALF_UP è l'arrotondamento commerciale italiano (0,005 -> 0,01)
    // HALF_EVEN, il default di BigDecimal e in certi contesti, qui non va bene
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    public void recalculate(DocumentLine line) {
        if (line.getKind() == DocumentLine.LineKind.TEXT) {
            line.setNetTotal(BigDecimal.ZERO);
            line.setVatTotal(BigDecimal.ZERO);
            line.setGrossTotal(BigDecimal.ZERO);
            return;
        }


        BigDecimal gross = line.getQuantity().multiply(line.getUnitPrice());

        BigDecimal discount = line.getDiscountType() == DocumentLine.DiscountType.PERCENT
                ? gross.multiply(line.getDiscountValue()).divide(BigDecimal.valueOf(100), 6, ROUNDING)
                : line.getDiscountValue();

        // uno sconto in valore maggiore dell'imponibile non deve produrre un imponibile
        // negativo, è quasi sempre un errore di battitura
        if (discount.compareTo(gross) > 0) discount = gross;

        BigDecimal net = gross.subtract(discount).setScale(MONEY_SCALE, ROUNDING);
        BigDecimal vat = net.multiply(line.getVatPercent())
                .divide(BigDecimal.valueOf(100), MONEY_SCALE, ROUNDING);

        line.setNetTotal(net);
        line.setVatTotal(vat);
        line.setGrossTotal(net.add(vat));
    }
}
