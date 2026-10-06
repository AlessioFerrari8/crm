package it.northleap.backend.services;

import it.northleap.backend.exceptions.BadRequestException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

// valida l'XML generato contro l'XSD ufficiale. Lo schema si compila una volta (costa) ed è thread-safe
// il Validator no, se ne crea uno per chiamata
@Component
public class FatturaPaValidator {

    private static final String XSD = "xsd/Schema_del_file_xml_FatturaPA_v1.2.2.xsd";
    private static final String DSIG = "xsd/xmldsig-core-schema.xsd";

    private final Schema schema;

    public FatturaPaValidator() throws SAXException, IOException {
        SchemaFactory f = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        // l'XSD importa lo schema della firma da w3.org: senza questo resolver ogni avvio fa una
        // richiesta HTTP, e senza rete, il backend non parte. Si serve la copia nel classpath
        f.setResourceResolver(new ClasspathResolver());
        // nessun DTO esterno, mai. Il DOCTYPE di xmldsig lo toglie il resolver qui sotto
        f.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        try (InputStream in = new ClassPathResource(XSD).getInputStream()) {
            this.schema = f.newSchema(new StreamSource(in, XSD));
        }
    }

    // tutti gli errori, non solo il primo: è il messaggio che l'utente legge per correggere
    public void validate(String xml) {
        Validator v = schema.newValidator();
        List<String> errors = new ArrayList<>();
        v.setErrorHandler(new ErrorHandler() {
            @Override
            public void warning(SAXParseException e) { }

            @Override
            public void error(SAXParseException e) { errors.add("riga " + e.getLineNumber() + ": " + e.getMessage()); }

            @Override
            public void fatalError(SAXParseException e) throws SAXException { throw e; }
        });
        try {
            v.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            v.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            v.validate(new StreamSource(new StringReader(xml)));
        } catch (SAXException | IOException e) {
            errors.add(e.getMessage());
        }

        if (!errors.isEmpty()) {
            throw new BadRequestException("XML non valido per l'XSD FatturaPA 1.2.2:\n" + String.join("\n", errors), "EINVOICE_XSD");
        }
    }

    private static final class ClasspathResolver implements LSResourceResolver {
        @Override
        public LSInput resolveResource(String type, String ns, String publicId, String systemId, String baseUri) {
            if (systemId == null || !systemId.endsWith("xmldsig-core-schema.xsd")) return null;
            // xmldsig-core-schema.xsd apre con un DOCTYPE verso w3.org/2001/XMLSchema.dtd: non serve a
            // compilare lo schema, ma il parser prova a scaricarlo e ACCESS_EXTERNAL_DTD lo blocca.
            // Il file resta quello ufficiale, il DOCTYPE si toglie in lettura
            try (InputStream in = new ClassPathResource(DSIG).getInputStream()) {
                String xsd = new String(in.readAllBytes(), StandardCharsets.UTF_8)
                        .replaceFirst("(?s)<!DOCTYPE.*?]>", "");
                return new Input(publicId, systemId, xsd);
            } catch (IOException ioe) {
                throw new IllegalStateException("Missing " + DSIG + " on the classpath", ioe);
            }
        }
    }

    private record Input(String publicId, String systemId, String stringData) implements LSInput {
        @Override public Reader getCharacterStream() { return null; }
        @Override public void setCharacterStream(Reader r) { }
        @Override public InputStream getByteStream() { return null; }
        @Override public void setByteStream(InputStream s) { }
        @Override public String getStringData() { return stringData; }
        @Override public void setStringData(String s) { }
        @Override public String getSystemId() { return systemId; }
        @Override public void setSystemId(String s) { }
        @Override public String getPublicId() { return publicId; }
        @Override public void setPublicId(String s) { }
        @Override public String getBaseURI() { return null; }
        @Override public void setBaseURI(String s) { }
        @Override public String getEncoding() { return null; }
        @Override public void setEncoding(String s) { }
        @Override public boolean getCertifiedText() { return false; }
        @Override public void setCertifiedText(boolean b) { }
    }
}
