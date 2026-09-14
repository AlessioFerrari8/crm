package it.northleap.backend.services;

import it.northleap.backend.entities.PdfTemplate;
import it.northleap.backend.exceptions.BadRequestException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Niente Spring, niente database: il renderer non conosce il dominio, e questo test è il
// motivo per cui è stato separato
class PdfRenderServiceTest {

    private final PdfRenderService service = new PdfRenderService(engine());

    private static TemplateEngine engine() {
        StringTemplateResolver r = new StringTemplateResolver();
        r.setTemplateMode(TemplateMode.HTML);
        r.setCacheable(false);
        TemplateEngine e = new TemplateEngine();
        e.setTemplateResolver(r);
        return e;
    }

    private PdfTemplate template(String html) {
        PdfTemplate t = new PdfTemplate();
        t.setHtml(html);
        t.setPageSize("A4");
        t.setMarginMm(15);
        return t;
    }

    private String textOf(byte[] pdf) throws Exception {
        try (PDDocument d = Loader.loadPDF(pdf)) {     // PDFBox 3.x
            return new PDFTextStripper().getText(d);
        }
    }

    @Test
    void produce_un_pdf_valido() {
        byte[] pdf = service.render(template("<html><body>ciao</body></html>"), Map.of());

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
    }

    @Test
    void il_modello_finisce_davvero_nel_testo() throws Exception {
        byte[] pdf = service.render(
                template("<html><body><p th:text=\"${totale}\">x</p></body></html>"),
                Map.of("totale", "10.845,88 EUR"));

        assertThat(textOf(pdf)).contains("10.845,88");
    }

    @Test
    void le_accentate_e_l_euro_non_diventano_quadratini() throws Exception {
        byte[] pdf = service.render(
                template("<html><body><p style=\"font-family: 'DejaVu Sans'\">perche' si', 12,00 €, citta'</p></body></html>"),
                Map.of());

        // se il font non è registrato qui esce testo vuoto o mutilato
        assertThat(textOf(pdf)).contains("€").contains("citta");
    }

    // il renderer non deve seguire risorse esterne
    // Se un giorno qualcuno toglie useProtocolsStreamImplementation "per far vedere il logo
    // remoto", questo test diventa rosso e spiega perché
    @Test
    void una_immagine_remota_fa_fallire_la_stampa() {
        assertThatThrownBy(() -> service.render(
                template("<html><body><img src=\"http://169.254.169.254/latest/meta-data/\"/></body></html>"),
                Map.of()))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void un_file_locale_fa_fallire_la_stampa() {
        assertThatThrownBy(() -> service.render(
                template("<html><body><img src=\"file:///etc/passwd\"/></body></html>"),
                Map.of()))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void una_espressione_sbagliata_e_un_errore_dell_utente_non_un_500() {
        assertThatThrownBy(() -> service.render(
                template("<html><body><p th:text=\"${doc.nonEsiste()}\">x</p></body></html>"),
                Map.of()))
                .isInstanceOf(BadRequestException.class);
    }
}
