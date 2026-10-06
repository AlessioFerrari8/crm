package it.northleap.backend.services;

import it.northleap.backend.entities.DocumentLine;
import it.northleap.backend.entities.ObjectType;
import it.northleap.backend.entities.Record;
import it.northleap.backend.entities.Workspace;
import it.northleap.backend.exceptions.BadRequestException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// il test che conta è il primo: l'XML generato passa l'XSD ufficiale. Tutto il resto sono i punti
// dove lo SDI scarta
class FatturaPaXmlBuilderTest {

    private static FatturaPaValidator validator;
    private final FatturaPaXmlBuilder builder = new FatturaPaXmlBuilder();
    private final DocumentTotalService totals = new DocumentTotalService(null);

    @BeforeAll
    static void schema() throws Exception {
        validator = new FatturaPaValidator();
    }

    @Test
    void invoiceWithDiscountShippingAndZeroRatePassesTheXsd() {
        Record invoice = record("invoice", Map.of("number", "FT2026-0001", "issueDate", "2026-09-25", "discountTotal",
                "30", "shippingCost", "10"));
        List<DocumentLine> lines = List.of(
                line(1, "Consulenza “strategica” - settembre €", "2", "75", "22", null),
                line(2, "Marca da bollo", "1", "2", "0", "N1"));

        String xml = build(invoice, company(), lines);

        validator.validate(xml);
        assertThat(xml).contains("<FormatoTrasmissione>FPR12</FormatoTrasmissione>")
                .contains("<CodiceDestinatario>0000000</CodiceDestinatario>")
                .contains("Consulenza \"strategica\" - settembre EUR")
                .contains("<Natura>N1</Natura>")
                .contains("<Descrizione>Sconto</Descrizione>")
                .doesNotContain(",00");
    }

    @Test
    void privateCustomerGetsNameAndSurname() {
        Map<String, Object> d = new HashMap<>(Map.of("firstName", "Mario", "lastName", "Rossi",
                "taxCode", "RSSMRA80A01H501U", "street", "Via Roma 1", "zipCode", "38068", "city", "Rovereto", "province", "tn"));
        String xml = build(record("invoice", Map.of("number", "FT2026-0002", "issueDate", "2026-09-25")),
                record("contact", d), List.of(line(1, "Servizio", "1", "100", "22", null)));

        validator.validate(xml);
        assertThat(xml).contains("<Nome>Mario</Nome>").contains("<Provincia>TN</Provincia>");
    }

    @Test
    void missingDataIsReportedAllAtOnceWithTheRecord() {
        Record customer = record("company", Map.of("name", "Rossi Srl"));
        assertThatThrownBy(() -> build(record("invoice", Map.of("number", "X")), customer,
                List.of(line(1, "A", "1", "1", "0", null))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Partita IVA o codice fiscale mancante su \"Rossi Srl\"")
                .hasMessageContaining("CAP mancante")
                .hasMessageContaining("Riga 1: aliquota 0% senza Natura SDI");
    }

    @Test
    void oldNatureCodesAreRefused() {
        assertThatThrownBy(() -> build(record("invoice", Map.of("number", "X")), company(),
                List.of(line(1, "A", "1", "1", "0", "N2"))))
                .hasMessageContaining("Natura \"N2\" non valida");
    }

    // vari helper
    private String build(Record invoice, Record customer, List<DocumentLine> lines) {
        lines.forEach(l -> l.setDocument(invoice));
        return builder.build(new FatturaPaXmlBuilder.Input(issuer(), invoice, customer, lines,
                totals.compute(invoice, lines), null, "00001", "TD01", null));
    }

    private Workspace issuer() {
        Workspace w = new Workspace();
        w.setName("NorthLeap");
        w.setLegalName("NorthLeap S.r.l.");
        w.setVatNumber("IT01234567890");
        w.setStreet("Via Esempio 10");
        w.setZipCode("38068");
        w.setCity("Rovereto");
        w.setProvince("TN");
        return w;
    }

    private Record company() {
        return record("company", new HashMap<>(Map.of("name", "Rossi Srl", "vat", "09876543210",
                "street", "Corso Italia 5", "zipCode", "20100", "city", "Milano", "province", "MI")));
    }

    private Record record(String key, Map<String, Object> data) {
        ObjectType t = new ObjectType();
        t.setKey(key);
        Record r = new Record();
        r.setId(UUID.randomUUID());
        r.setObjectType(t);
        r.setTitle(String.valueOf(data.getOrDefault("name", key)));
        r.setData(new HashMap<>(data));
        return r;
    }

    private DocumentLine line(int no, String desc, String qty, String price, String vat, String nature) {
        DocumentLine l = new DocumentLine();
        l.setLineNo(no);
        l.setDescription(desc);
        l.setQuantity(new BigDecimal(qty));
        l.setUnitPrice(new BigDecimal(price));
        l.setVatPercent(new BigDecimal(vat));
        BigDecimal net = l.getQuantity().multiply(l.getUnitPrice()).setScale(2);
        l.setNetTotal(net);
        if (nature != null) l.setVatRate(record("vat_rate", Map.of("sdiNature", nature)));
        return l;
    }
}
