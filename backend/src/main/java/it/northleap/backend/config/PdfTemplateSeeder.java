package it.northleap.backend.config;


import it.northleap.backend.entities.PdfTemplate;
import it.northleap.backend.repositories.PdfTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;

// template di stampa di sistema
@Slf4j
@Component
@Order(6)
@RequiredArgsConstructor
public class PdfTemplateSeeder implements ApplicationRunner {
    private final PdfTemplateRepository pdfTemplateRepository;

    private static final Map<String, String> MODULES = Map.of(
            "quote", "Preventivo standard",
            "sales_order", "Ordine standard",
            "delivery_note", "DDT standard",
            "invoice", "Fattura standard");

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String html = new String(
                new ClassPathResource("pdf/default-document.html")
                        .getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        MODULES.forEach((objectKey, label) -> {
            String key = objectKey + "-default";
            if (pdfTemplateRepository.findByKey(key).isPresent()) return;
            PdfTemplate t = new PdfTemplate();
            t.setKey(key);
            t.setLabel(label);
            t.setObjectKey(objectKey);
            t.setHtml(html);
            t.setPageSize("A4");
            t.setMarginMm(15);
            t.setSystem(true);
            pdfTemplateRepository.save(t);
            log.info("Seeded PDF template {}", key);
        });

    }
}
