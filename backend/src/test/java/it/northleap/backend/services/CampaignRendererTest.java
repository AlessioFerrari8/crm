package it.northleap.backend.services;

import it.northleap.backend.config.PdfTemplateConfig;
import it.northleap.backend.entities.Campaign;
import it.northleap.backend.entities.Record;
import org.junit.jupiter.api.Test;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
class CampaignRendererTest {

    private final CampaignRenderer renderer = new CampaignRenderer(new PdfTemplateConfig().pdfTemplateEngine());

    @Test
    void linksAreRewrittenAndSignedAndHeadersAreSet() {
        Campaign c = campaign("<p>Ciao <span th:text=\"${r.firstName}\">x</span></p><a href=\"https://northleap.it/offerta?a=1&b=2\">Vai</a>");

        CampaignRenderer.Rendered out = renderer.render(c, record(Map.of("firstName", "Mario")), "tok");

        assertThat(out.html()).contains("Ciao <span>Mario</span>").contains("/api/public/c/tok/o.gif")
                .doesNotContain("href=\"https://northleap.it");
        Matcher m = Pattern.compile("/l\\?u=([^&\"]+)&amp;s=([0-9a-f]+)").matcher(out.html());
        assertThat(m.find()).isTrue();
        String url = URLDecoder.decode(m.group(1), StandardCharsets.UTF_8);
        assertThat(url).isEqualTo("https://northleap.it/offerta?a=1&b=2");
        assertThat(m.group(2)).isEqualTo(CampaignRenderer.sign(c, url));
        // un url diverso con la stessa firma non passa: e' il controllo che ferma l'open redirect
        assertThat(CampaignRenderer.sign(c, "https://evil.example")).isNotEqualTo(m.group(2));
        assertThat(out.headers()).containsEntry("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");
        assertThat(out.text()).contains("/api/public/c/tok/unsubscribe");
    }

    @Test
    void emptyVariablesAreCountedBeforeSending() {
        Campaign c = campaign("<p th:text=\"${r.firstName}\"></p>");
        assertThat(CampaignRenderer.missesVariables(c, record(Map.of("firstName", "")))).isTrue();
        assertThat(CampaignRenderer.missesVariables(c, record(Map.of("firstName", "Anna")))).isFalse();
    }

    private Campaign campaign(String html) {
        Campaign c = new Campaign();
        c.setSubject("Novita'");
        c.setBodyHtml(html);
        c.setBodyText("Ciao");
        c.setTrackingSecret("secret");
        return c;
    }

    private Record record(Map<String, Object> data) {
        Record r = new Record();
        r.setData(new HashMap<>(data));
        return r;
    }
}
