package it.northleap.backend.services;

import it.northleap.backend.entities.Campaign;
import it.northleap.backend.entities.Record;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// da campagna + record a HTML pronto: variabili, preheader, link firmati, pixel e disiscrizione. Stesso motore
// Thymeleaf del PDF, quindi le stesse restrizioni di sicurezza
@Component
public class CampaignRenderer {

    // le variabili che il corpo usa: r.firstName, r.company... Servono a contare chi le ha vuote
    private static final Pattern VARS = Pattern.compile("\\br\\.([a-zA-Z0-9_]+)");

    private final TemplateEngine templateEngine;

    @Value("${app.public-api-url:http://localhost:8080}")
    private String publicApiUrl;

    public CampaignRenderer(@Qualifier("pdfTemplateEngine") TemplateEngine engine) {
        this.templateEngine = engine;
    }

    public record Rendered(String html, String text, Map<String, String> headers) { }

    public Rendered render(Campaign c, Record record, String token) {
        Context ctx = new Context(Locale.ITALIAN);
        ctx.setVariable("r", record.getData() == null ? Map.of() : record.getData());
        String html = templateEngine.process(c.getBodyHtml(), ctx);
        String text = templateEngine.process(c.getBodyText(), textContext(record));

        String base = publicApiUrl + "/api/public/c/" + token;
        Document doc = Jsoup.parse(html);

        // i link diventano /l?u=...&s=...: la firma è quella che impedisce di usare il dominio come open redirect
        for (Element a : doc.select("a[href]")) {
            String href = a.attr("href");
            if (!href.startsWith("http://") && !href.startsWith("https://")) continue;
            a.attr("href", base + "/l?u=" + URLEncoder.encode(href, StandardCharsets.UTF_8) + "&s=" + sign(c, href));
        }
        if (c.getPreheader() != null && !c.getPreheader().isBlank()) {
            doc.body().prependElement("div")
                    .attr("style", "display:none;max-height:0;overflow:hidden;opacity:0")
                    .text(c.getPreheader());
        }
        doc.body().appendElement("p").attr("style", "font-size:12px;color:#888")
                .html("Ricevi questa email perché sei nei nostri contatti. <a href=\"" + base
                        + "/unsubscribe\">Non voglio piu' ricevere queste email</a>.");
        doc.body().appendElement("img").attr("src", base + "/o.gif").attr("width", "1").attr("height", "1").attr("alt", "");

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("List-Unsubscribe", "<" + base + "/unsubscribe>");
        // one-click (RFC 8058): Gmail e Yahoo lo pretendono dal 2024 per chi manda in volume
        headers.put("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");
        return new Rendered(doc.outerHtml(), text + "\n\n--\nDisiscriviti: " + base + "/unsubscribe", headers);
    }

    public static String sign(Campaign c, String url) {
        return HmacUtil.sha256Hex(c.getTrackingSecret(), url);
    }

    // chiavi usate dal corpo e vuote su questo record: "Ciao ," si conta PRIMA di mandarlo a mille persone
    public static boolean missesVariables(Campaign c, Record r) {
        Matcher m = VARS.matcher(c.getBodyHtml() + " " + c.getBodyText() + " " + c.getSubject());
        Map<String, Object> d = r.getData() == null ? Map.of() : r.getData();
        while (m.find()) {
            Object v = d.get(m.group(1));
            if (v == null || v.toString().isBlank()) return true;
        }
        return false;
    }

    private Context textContext(Record record) {
        Context ctx = new Context(Locale.ITALIAN);
        ctx.setVariable("r", record.getData() == null ? Map.of() : record.getData());
        return ctx;
    }

}
