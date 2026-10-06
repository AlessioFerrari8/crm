package it.northleap.backend.services;

import it.northleap.backend.dtos.DocumentTotals;
import it.northleap.backend.dtos.VatSummaryRow;
import it.northleap.backend.entities.DocumentLine;
import it.northleap.backend.entities.Record;
import it.northleap.backend.entities.Workspace;
import it.northleap.backend.exceptions.BadRequestException;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.*;

// da fattura + righe + totali a XML FatturaPA 1.2.2. Dom del JDK e non JAXB: il tracciato è fisso e
// scritto a mano si legge dall'alto in basso nello stesso ordine dell'XSD, che è l'unica cosa che conta
@Component
public class FatturaPaXmlBuilder {

    static final String NS = "http://ivaservizi.agenziaentrate.gov.it/docs/xsd/fatture/v1.2";

    public record Input(
            Workspace issuer,
            Record invoice,
            Record customer,
            List<DocumentLine> lines,
            DocumentTotals totals,
            Record paymentMethod,
            String progressivo,
            String tipoDocumento,
            Record originalInvoice
    ) { }

    public String build(Input in) {
        List<String> missing = new ArrayList<>();
        checkIssuer(in.issuer(), missing);
        checkCustomer(in.customer(), missing);
        String number = str(in.invoice(), "number");
        if (number == null) missing.add("Numero mancante sulla fattura");
        Map<BigDecimal, String> natures = natures(in.lines(), missing);
        if (!missing.isEmpty()) {
            // tutti insieme: correggere un campo alla volta e rigenerare dieci volte è il modo in
            // cui si odia una funzione
            throw new BadRequestException(String.join("\n", missing), "EINVOICE_MISSSING_DATA");
        }

        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            Document doc = f.newDocumentBuilder().newDocument();

            String sdiCode = upper(str(in.customer(), "sdiCode"));
            boolean pa = sdiCode != null && sdiCode.length() == 6;

            // radice qualificata, figli non qualificati: l'XSD non ha elementFormDefault="qualified"
            // Un xmlns di default sulla radice mette tutti i figli nel namespaace e il file non passa
            Element root = doc.createElementNS(NS, "p:FatturaElettronica");
            root.setAttribute("versione", pa ? "FPA12" : "FPR12");
            doc.appendChild(root);

            header(doc, root, in, sdiCode, pa);
            body(doc, root, in, number, natures);

            Transformer t = TransformerFactory.newInstance().newTransformer();
            t.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            t.setOutputProperty(OutputKeys.INDENT, "yes");
            StringWriter out = new StringWriter();
            t.transform(new DOMSource(doc), new StreamResult(out));
            return out.toString();
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("FatturaPA generation failed", e);
        }
    }

    // FatturaElettronicaHeader

    private void header(Document d, Element root, Input in, String sdiCode, boolean pa) {
        Workspace w = in.issuer();
        Record c = in.customer();
        Element h = el(d, root, "FatturaElettronicaHeader");

        Element tr = el(d, h, "DatiTrasmissione");
        Element idT = el(d, tr, "IdTrasmittente");
        text(d, idT, "IdPaese", country(w.getCountryCode()));
        text(d, idT, "IdCodice", vatDigits(w.getVatNumber()));
        text(d, tr, "ProgressivoInvio", in.progressivo());
        text(d, tr, "FormatoTrasmissione", pa ? "FPA12" : "FPR12");
        // senza codice nè PEC, il valore è 0000000: legale, la fattura finisce nel cassetto fiscale
        text(d, tr, "CodiceDestinatario", sdiCode != null ? sdiCode : "0000000");
        String pec = str(c, "pec");
        if (sdiCode == null && pec != null) text(d, tr, "PECDestinatario", pec);

        Element ced = el(d, h, "CedentePrestatore");
        Element da = el(d, ced, "DatiAnagrafici");
        Element iva = el(d, da, "IdFiscaleIVA");
        text(d, iva, "IdPaese", country(w.getCountryCode()));
        text(d, iva, "IdCodice", vatDigits(w.getVatNumber()));
        if (blank(w.getTaxCode()) != null) text(d, da, "CodiceFiscale", upper(w.getTaxCode()));
        Element an = el(d, da, "Anagrafica");
        text(d, an, "Denominazione", latin(nz(w.getLegalName(), w.getName()), 80));
        text(d, da, "RegimeFiscale", nz(w.getFiscalRegime(), "RF01"));
        sede(d, ced, w.getStreet(), w.getZipCode(), w.getCity(), w.getProvince(), w.getCountryCode());

        Element ces = el(d, h, "CessionarioCommittente");
        Element cda = el(d, ces, "DatiAnagrafici");
        String cVat = str(c, "vat");
        if (cVat != null) {
            Element civa = el(d, cda, "IdFiscaleIVA");
            text(d, civa, "IdPaese", country(str(c, "country")));
            text(d, civa, "IdCodice", vatDigits(cVat));
        }
        String cf = upper(str(c, "taxCode"));
        if (cf != null) text(d, cda, "CodiceFiscale", cf);
        Element can = el(d, cda, "Anagrafica");
        if (isPerson(c)) {
            text(d, can, "Nome", latin(str(c, "firstName"), 60));
            text(d, can, "Cognome", latin(str(c, "lastName"), 60));
        } else {
            text(d, can, "Denominazione", latin(nz(str(c, "name"), c.getTitle()), 80));
        }
        sede(d, ces, str(c, "street"), str(c, "zipCode"), str(c, "city"), str(c, "province"), str(c, "country"));
    }

    private void sede(Document d, Element parent, String street, String zip, String city, String prov, String country) {
        Element s = el(d, parent, "Sede");
        text(d, s, "Indirizzo", latin(street, 60));
        text(d, s, "CAP", zip.trim());
        text(d, s, "Comune", latin(city, 60));
        if (blank(prov) != null) text(d, s, "Provincia", upper(prov));
        text(d, s, "Nazione", country(country));
    }

    // FatturaElettronicaBody
    private void body(Document d, Element root, Input in, String number, Map<BigDecimal, String> natures) {
        Element b = el(d, root, "FatturaElettronicaBody");

        Element dg = el(d, b, "DatiGenerali");
        Element dgd = el(d, dg, "DatiGeneraliDocumento");
        text(d, dgd, "TipoDocumento", in.tipoDocumento());
        text(d, dgd, "Divisa", "EUR");
        text(d, dgd, "Data", date(in.invoice(), "issueDate").toString());
        text(d, dgd, "Numero", latin(number, 20));
        text(d, dgd, "ImportoTotaleDocumento", money(in.totals().total()));
        if (in.originalInvoice() != null) {
            // nota di credito: il riferimento alla fattura che rettifica
            Element fc = el(d, dg, "DatiFattureCollegate");
            text(d, fc, "IdDocumento", latin(str(in.originalInvoice(), "number"), 20));
            text(d, fc, "Data", date(in.originalInvoice(), "issueDate").toString());
        }

        Element dbs = el(d, b, "DatiBeniServizi");
        int n = 1;
        Map<BigDecimal, BigDecimal> byRate = new TreeMap<>();
        for (DocumentLine l : in.lines()) {
            // le righe TEXT e SUBTOTAL non hanno importi: nel tracciato ogni riga vuole prezzo e aliquota
            if (l.getKind() != DocumentLine.LineKind.PRODUCT) continue;
            Element dl = el(d, dbs, "DettaglioLinee");
            text(d, dl, "NumeroLinea", String.valueOf(n++));
            String code = l.getProduct() == null ? null : str(l.getProduct(), "code");
            if (code != null) {
                Element ca = el(d, dl, "CodiceArticolo");
                text(d, ca, "CodiceTipo", "INTERNO");
                text(d, ca, "CodiceValore", latin(code, 35));
            }
            text(d, dl, "Descrizione", latin(nz(l.getDescription(), "-"), 1000));
            text(d, dl, "Quantita", price(l.getQuantity()));
            if (blank(l.getUnit()) != null) text(d, dl, "UnitaMisura", latin(l.getUnit(), 10));
            text(d, dl, "PrezzoUnitario", price(l.getUnitPrice()));
            discount(d, dl, l);
            text(d, dl, "PrezzoTotale", money(l.getNetTotal()));
            text(d, dl, "AliquotaIVA", rate(l.getVatPercent()));
            if (l.getVatPercent().signum() == 0) text(d, dl, "Natura", natures.get(key(l.getVatPercent())));
            byRate.merge(key(l.getVatPercent()), l.getNetTotal(), BigDecimal::add);
        }

        DocumentTotals t = in.totals();
        // trasporto e sconto di testata diventano RIGHE. Lo SDI controlla che ImponibileImporto di ogni
        // aliquota sia la somma delle PrezzoTotale di quell'aliquota (scarto 00422): uno sconto messo solo
        // in ScontoMaggiorazione di testata non entra in quella somma, e il file viene scartato
        if (t.shipping().signum() != 0) {
            BigDecimal r = byRate.isEmpty() ? new BigDecimal("22") : Collections.max(byRate.keySet());
            n = extraLine(d, dbs, n, "Spese di trasporto", t.shipping(), r, natures);
            byRate.merge(key(r), t.shipping(), BigDecimal::add);
        }
        // una riga di sconto per aliquota: la differenza fra quello che le righe sommano e quello che
        // DocumentTotalService ha calcolato
        for (VatSummaryRow row : t.vatRows()) {
            BigDecimal diff = row.taxable().subtract(byRate.getOrDefault(key(row.vatPercent()), BigDecimal.ZERO));
            if (diff.signum() != 0) n = extraLine(d, dbs, n, "Sconto", diff, row.vatPercent(), natures);
        }

        for (VatSummaryRow row : t.vatRows()) {
            Element r = el(d, dbs, "DatiRiepilogo");
            text(d, r, "AliquotaIVA", rate(row.vatPercent()));
            boolean zero = row.vatPercent().signum() == 0;
            if (zero) text(d, r, "Natura", natures.get(key(row.vatPercent())));
            text(d, r, "ImponibileImporto", money(row.taxable()));
            text(d, r, "Imposta", money(row.vat()));
            // I = esigibilità immediata
            // Split payment (S) e differita (D) non sono gestite
            if (!zero) text(d, r, "EsigibilitaIVA", "I");
        }

        Record pm = in.paymentMethod();
        String mp = pm == null ? null : upper(str(pm, "sdiCode"));
        if (mp != null) {
            Element dp = el(d, b, "DatiPagamento");
            text(d, dp, "CondizioniPagamento", "TP02"); // pagamento completo
            Element det = el(d, dp, "DettaglioPagamento");
            text(d, det, "ModalitaPagamento", mp);
            if (str(in.invoice(), "dueDate") != null) {
                text(d, det, "DataScadenzaPagamento", date(in.invoice(), "dueDate").toString());
            }
            text(d, det, "ImportoPagamento", money(t.total()));
        }

    }

    private int extraLine(Document d, Element dbs, int n, String label, BigDecimal amount, BigDecimal vat,
                          Map<BigDecimal, String> natures) {
        Element dl = el(d, dbs, "DettaglioLinee");
        text(d, dl, "NumeroLinea", String.valueOf(n));
        text(d, dl, "Descrizione", label);
        // senza Quantità, PrezzoTotale deve essere uguale a PrezzoUnitario
        text(d, dl, "PrezzoUnitario", price(amount));
        text(d, dl, "PrezzoTotale", money(amount));
        text(d, dl, "AliquotaIVA", rate(vat));
        if (vat.signum() == 0) text(d, dl, "Natura", natures.get(key(vat)));
        return n + 1;
    }

    private void discount(Document d, Element dl, DocumentLine l) {
        if (l.getDiscountValue() == null || l.getDiscountValue().signum() == 0) return;
        Element sm = el(d, dl, "ScontoMaggiorazione");
        text(d, sm, "Tipo", "SC");
        if (l.getDiscountType() == DocumentLine.DiscountType.PERCENT
                && l.getDiscountValue().stripTrailingZeros().scale() <= 2) {
            text(d, sm, "Percentuale", rate(l.getDiscountValue()));
        } else {
            // nel tracciato l'importo dello sconto è unitario, da noi è sulla riga intera
            BigDecimal perUnit = l.getDiscountType() == DocumentLine.DiscountType.PERCENT
                    ? l.getUnitPrice().multiply(l.getDiscountValue()).divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP)
                    : l.getDiscountValue().divide(l.getQuantity(), 8, RoundingMode.HALF_UP);
            text(d, sm, "Importo", price(perUnit));
        }
    }

    // controlli

    private void checkIssuer(Workspace w, List<String> missing) {
        String who = "Impostazioni > Azienda";
        if (blank(w.getVatNumber()) == null) missing.add("Partita IVA mancante in " + who);
        if (blank(w.getStreet()) == null) missing.add("Indirizzo (via) mancante in " + who);
        if (w.getZipCode() == null || !w.getZipCode().trim().matches("\\d{5}")) missing.add("CAP mancante o non valido in " + who);
        if (blank(w.getCity()) == null) missing.add("Comune mancante in " + who);
    }

    private void checkCustomer(Record c, List<String> missing) {
        if (c == null) {
            missing.add("La fattura non ha un cliente (campo Azienda o Contatto)");
            return;
        }
        String who = "\"" + c.getTitle() + "\" (record " + c.getId() + ")";
        if (str(c, "vat") == null && str(c, "taxCode") == null) missing.add("Partita IVA o codice fiscale mancante su " + who);
        if (isPerson(c) && (str(c, "firstName") == null || str(c, "lastName") == null)) missing.add("Nome e cognome servono entrambi su " + who);
        if (str(c, "street") == null) missing.add("Indirizzo (via) mancante su " + who);
        String zip = str(c, "zipCode");
        if (zip == null || ("IT".equals(country(str(c, "country"))) && !zip.matches("\\d{5}"))) missing.add("CAP mancante o non valido su " + who);
        if (str(c, "city") == null) missing.add("Comune mancante su " + who);
        String sdi = upper(str(c, "sdiCode"));
        if (sdi != null && !sdi.matches("[A-Z0-9]{6,7}")) missing.add("Codice destinatario non valido su " + who + ": 7 caratteri, 6 per la PA");
    }

    // aliquota 0 senza Natura = scarto La natura sta sull'aliquota (vat_rate.sdiNature), non sulla riga
    private Map<BigDecimal, String> natures(List<DocumentLine> lines, List<String> missing) {
        Map<BigDecimal, String> out = new HashMap<>();
        for (DocumentLine l : lines) {
            if (l.getKind() != DocumentLine.LineKind.PRODUCT || l.getVatPercent().signum() != 0) continue;
            String nat = l.getVatRate() == null ? null : upper(str(l.getVatRate(), "sdiNature"));
            if (nat == null) {
                missing.add("Riga " + l.getLineNo() + ": aliquota 0% senza Natura SDI (Inventario > Aliquote IVA)");
                continue;
            }
            // dal 2021 N2, N3 e N6 da sole non esistono più: servono i sottocodici (N2.1, N3.1, N6.9...)
            if (!nat.matches("N1|N2\\.[12]|N3\\.[1-6]|N4|N5|N6\\.[1-9]|N7")) {
                missing.add("Riga " + l.getLineNo() + ": Natura \"" + nat + "\" non valida per la 1.2.2");
            }
            String prev = out.putIfAbsent(key(BigDecimal.ZERO), nat);
            // DocumentTotals raggruppa per percentuale: due nature diverse a 0% finirebbero nella stessa
            // riga di riepilogo, che invece è una per coppia aliquota/natura
            if (prev != null && !prev.equals(nat)) missing.add("Due nature diverse sulle righe a 0% (" + prev + ", " + nat + "): non ancora gestito");
        }
        return out;
    }

    // formati

    // toPlainString non dipende dal Locale: String.format con Locale italiano darebbe 1.234,56
    static String money(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    // prezzi e quantità: da 2 a 8 decimali
    static String price(BigDecimal v) {
        int scale = Math.max(2, Math.min(8, v.stripTrailingZeros().scale()));
        return v.setScale(scale, RoundingMode.HALF_UP).toPlainString();
    }

    static String rate(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    // i campi testo dell'XSD accettano SOLO Basic Latin + Latin-1: un euro, un apostrofo curvo o un
    // trattino lungo incollati da Word bastano a far fallire la validazione
    static String latin(String s, int max) {
        if (s == null) return null;
        String out = s.replace('‘', '\'').replace('’', '\'')
                .replace('“', '"').replace('”', '"')
                .replace('–', '-').replace('—', '-')
                .replace("€", "EUR").replace("…", "...");
        StringBuilder sb = new StringBuilder();
        for (char ch : Normalizer.normalize(out, Normalizer.Form.NFC).toCharArray()) {
            sb.append(ch <= 0xFF && (ch >= 0x20 || ch == '\n' || ch == '\t') ? ch : '?');
        }
        String r = sb.toString().trim();
        return r.length() > max ? r.substring(0, max) : r;
    }

    private static BigDecimal key(BigDecimal rate) {
        return rate.setScale(2, RoundingMode.HALF_UP);
    }

    private static boolean isPerson(Record c) {
        return "contact".equals(c.getObjectType().getKey());
    }

    private static String vatDigits(String vat) {
        String v = vat.replaceAll("\\s", "").toUpperCase();
        return v.matches("^[A-Z]{2}.*") ? v.substring(2) : v;
    }

    private static String country(String c) {
        String v = blank(c);
        return v == null ? "IT" : v.toUpperCase();
    }

    private static LocalDate date(Record r, String key) {
        String v = str(r, key);
        return v == null ? LocalDate.now() : LocalDate.parse(v.substring(0, 10));
    }

    static String str(Record r, String key) {
        if (r == null || r.getData() == null) return null;
        Object v = r.getData().get(key);
        return v == null ? null : blank(v.toString());
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String upper(String s) {
        return blank(s) == null ? null : s.trim().toUpperCase();
    }

    private static String nz(String a, String b) {
        return blank(a) != null ? a : b;
    }

    private static Element el(Document d, Element parent, String name) {
        Element e = d.createElement(name);
        parent.appendChild(e);
        return e;
    }

    private static void text(Document d, Element parent, String name, String value) {
        el(d, parent, name).setTextContent(value);
    }
}
