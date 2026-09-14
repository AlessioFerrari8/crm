package it.northleap.backend.services;

import com.openhtmltopdf.extend.FSStreamFactory;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import it.northleap.backend.entities.PdfTemplate;
import it.northleap.backend.exceptions.BadRequestException;
import it.northleap.backend.repositories.DocumentLineRepository;
import it.northleap.backend.repositories.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;

import org.thymeleaf.context.Context;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;

// Rende un PdfTemplate con un modello già pronto. Non conosce il dominio: niente Record, niente
// repository. È quello che lo rende testabile senza database, e il test di sicurezza in fondo al file
// è il motivo per cui la separazione esiste
@Service
public class PdfRenderService {
    private final TemplateEngine engine;

    // costruttore a mano e non @RequiredArgsContructor: serve il @Qualifier sul parametro, e
    // Lombok non copia le annotazioni sul costruttore generato se non gliel'hai detto in lombok.config
    public PdfRenderService(@Qualifier("pdfTemplateEngine") TemplateEngine engine) {
        this.engine = engine;
    }

    public byte[] render(PdfTemplate template, Map<String, Object> model) {
        return render(template.getHtml(), template.getPageSize(), template.getMarginMm(), model);
    }

    // overload con l'HTML nudo: lo usa l'anteprima di un template non salvato
    public byte[] render(String html, String pageSize, int marginMm, Map<String, Object> model) {
        Context ctx = new Context();
        ctx.setVariables(model);

        String rendered;
        try {
            rendered = engine.process(html, ctx);
        } catch (RuntimeException e) {
            // un'espressione sbagliata nel template è un errore dell'utente: deve tornare
            // 400 con il messaggio di Thymeleaf, che dice riga e colonna, non un 500 anonimo
            throw new BadRequestException("Template error: " + rootMessage(e));
        }

        // openhtmltopdf accetta solo XML ben formattato. L'html scritto da un utente non lo è
        // praticamente mai, ci sono problemi di <br> e <img> per esempio senza chiusura, attributi
        // senza apici e via dicendo. jsoup lo normalizza; senza questo passaggio l'errore è un
        // SAXParseException su una riga che all'utente sembra corretta
        Document doc = Jsoup.parse(rendered);
        doc.outputSettings().syntax(Document.OutputSettings.Syntax.xml);

        // formato pagina e margini vengono dall'entità, non dal css scritto a mano; questo permette
        // di cambiarli da un select nella UI. Il blocco va in coda al <head> per vincere su un eventuale
        // @page dell'utente
        doc.head().appendElement("style").text(
                "@page { size: " + safeCss(pageSize) + "; margin: " + marginMm + "mm;" +
                " @bottom-right { content: counter(page) \" / \" counter(pages);" +
                " font-family: \"DejaVu Sans\"; font-size: 7pt; color: #666; } }");

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()){
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withW3cDocument(new W3CDom().fromJsoup(doc), "");

            // i font
            builder.useFont(() -> getClass().getResourceAsStream("/fonts/DejaVuSans.ttf"),
                    "DejaVu Sans");
            builder.useFont(() -> getClass().getResourceAsStream("/fonts/DejaVuSans-Bold.ttf"),
                    "DejaVu Sans", 700, BaseRendererBuilder.FontStyle.NORMAL, true);

            builder.useProtocolsStreamImplementation(BLOCKED, "http", "https", "file", "ftp", "jar", "mailto");

            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception e) {
            throw new BadRequestException("Cannot render the PDF: " + rootMessage(e));
        }

    }

    // ogni risorsa esterna è bloccata: restano solo i dati: URI, che openhtmltopdf gestisce
    // internamente senza passare da qui
    private static final FSStreamFactory BLOCKED = url -> {
        throw new IllegalStateException("External resources are not allowed in PDF templates");
    };

    // solo i valori che @page accetta: nessuna stringa dell'utente entra nel CSS
    // senza passare da qui
    private String safeCss(String pageSize) {
        if (pageSize == null) return "A4";
        return switch (pageSize.toUpperCase()) {
            case "A4", "A5", "A3", "LETTER", "LEGAL" -> pageSize.toUpperCase();
            case "A4 LANDSCAPE" -> "A4 landscape";
            case "A5 LANDSCAPE" -> "A5 landscape";
            default -> "A4";
        };
    }

    private String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) t = t.getCause();
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }


}
