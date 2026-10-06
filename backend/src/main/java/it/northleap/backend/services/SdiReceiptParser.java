package it.northleap.backend.services;


import it.northleap.backend.entities.EInvoice;
import it.northleap.backend.entities.EInvoiceStatus;
import it.northleap.backend.exceptions.BadRequestException;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;


// legge le ricevute dello SDI. Questo XML arriva da fuori, niente DOCTYPE, niente entità esterne
@Component
public class SdiReceiptParser {
    public record Receipt(
            String type,
            String filename,
            String sdiIdentifier,
            EInvoiceStatus status,
            String message
    ) { }

    public Receipt parse(byte[] bytes) {
        Document doc = read(bytes);
        Element root = doc.getDocumentElement();
        String fileName = first(root, "NomeFile");
        String id = first(root, "Identificativo SdI");

        return switch (root.getLocalName()) {
            case "RicevutaConsegna" ->
                    new Receipt("RC", fileName, id, EInvoiceStatus.DELIVERED, "Consegnata al destinatario");
            // mc: lo SDI non è riuscito a consegnare, la fattura è nel cassetto fiscale del cliente ed è comunque
            // emessa. Va detto al cliente, non ritrasmessa
            case "NotificaMancataConsegna" -> new Receipt("MC", fileName, id, EInvoiceStatus.DELIVERED, "Mancata " +
                    "consegna: disponibile nel cassetto fiscale del cliente");
            case "NotificaScarto" -> new Receipt("NS", fileName, id, EInvoiceStatus.REJECTED, errors(root));
            case "NotificaEsito" -> {
                boolean ok = "EC01".equals(first(root, "Esito"));
                yield new Receipt("NE", fileName, id, ok ? EInvoiceStatus.ACCEPTED : EInvoiceStatus.REJECTED, ok ?
                        "Accettata dalla PA" : "Rifiutata dalla PA: " + first(root, "Descrizione"));
            }

            case "NotificaDecorrenzaTermini" -> new Receipt("DT", fileName, id, EInvoiceStatus.ACCEPTED,
                    "Decorrenza termini: la PA non ha risposto in 15 giorni");
            default -> throw new BadRequestException("Ricevuta SDI non riconosciuta: " + root.getLocalName());
        };


    }

    // lo scarto va mostrato per inero: codice, descrizione e suggerimento sono l'unica cosa che dice cosa correggere
    private String errors(Element root) {
        List<String> out = new ArrayList<>();
        NodeList list = root.getElementsByTagNameNS("*", "Errore");
        for (int i = 0; i < list.getLength(); i++) {
            Element e = (Element) list.item(i);
            String s = first(e, "Codice") + " - " + first(e, "Descrizione");
            String hint = first(e, "Suggerimento");
            out.add(hint == null ? s : s + " (" + hint + ")");
        }
        return out.isEmpty() ? "Scartata" : String.join("\n", out);
    }

    private Document read(byte[] bytes) {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            f.setXIncludeAware(false);
            f.setExpandEntityReferences(false);
            return f.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
        } catch (Exception e) {
            throw new BadRequestException("Il file non e' un XML leggibile: " + e.getMessage());
        }
    }

    // gli elementi delle ricevute sono non qualificati o col prefisso "types:": si cerca per nome locale
    private String first(Element parent, String name) {
        NodeList n = parent.getElementsByTagNameNS("*", name);
        if (n.getLength() == 0) n = parent.getElementsByTagName(name);
        return n.getLength() == 0 ? null : n.item(0).getTextContent().trim();
    }


}
