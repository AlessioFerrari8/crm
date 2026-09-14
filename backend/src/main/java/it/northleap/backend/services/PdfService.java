package it.northleap.backend.services;

import it.northleap.backend.dtos.PdfBatchDto;
import it.northleap.backend.dtos.PdfPreviewDto;
import it.northleap.backend.entities.PdfTemplate;
import it.northleap.backend.entities.Record;
import it.northleap.backend.entities.UserPreference;
import it.northleap.backend.exceptions.BadRequestException;
import it.northleap.backend.exceptions.NotFoundException;
import it.northleap.backend.repositories.PdfTemplateRepository;
import it.northleap.backend.repositories.RecordRepository;
import it.northleap.backend.security.Actor;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PdfService {
    private final RecordRepository recordRepository;
    private final RecordsService recordsService;
    private final PdfTemplateRepository pdfTemplateRepository;
    private final PdfContextBuilder pdfContextBuilder;
    private final PdfRenderService pdfRenderService;
    private final UserPreferenceService userPreferenceService;

    // quello che il controller restituisce: byte + nome del file
    public record Rendered(byte[] bytes, String filename) { }

    // singola
    @Transactional(readOnly = true)
    public Rendered printOne(Actor actor, UUID recordId, String templateKey) {
        Record doc = load(actor, recordId);
        PdfTemplate template = pick(doc.getObjectType().getKey(), templateKey);
        UserPreference pref = userPreferenceService.get(actor);
        byte[] bytes = pdfRenderService.render(template, pdfContextBuilder.build(doc, pref));
        return new Rendered(bytes, filename(doc));
    }

    // anteprima
    @Transactional(readOnly = true)
    public byte[] preview(Actor actor, PdfPreviewDto dto) {
        Record doc = dto.recordId() != null
                ? load(actor, dto.recordId())
                : lastDocumentOf(actor, dto.objectKey());
        UserPreference pref = userPreferenceService.get(actor);
        return pdfRenderService.render(dto.html(), dto.pageSizeOrDefault(),
                dto.marginOrDefault(), pdfContextBuilder.build(doc, pref));
    }

    // batch
    @Transactional(readOnly = true)
    public Rendered batch(Actor actor, PdfBatchDto dto) {
        UserPreference pref = userPreferenceService.get(actor);
        List<byte[]> parts = new ArrayList<>();
        for (UUID id : dto.recordIds()) {
            Record doc = load(actor, id); // RBAC su ogni documento, non solo sul primo
            PdfTemplate t = pick(doc.getObjectType().getKey(), dto.template());
            parts.add(pdfRenderService.render(t, pdfContextBuilder.build(doc, pref)));
        }

        return new Rendered(merge(parts), "documenti-" + parts.size() + ".pdf");
    }

    private byte[] merge(List<byte[]> parts) {
        if (parts.size() == 1) return parts.get(0);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDFMergerUtility merger = new PDFMergerUtility();
            merger.setDestinationStream(out);
            for (byte[] one : parts) {
                merger.addSource(new RandomAccessReadBuffer(one));
            }
            merger.mergeDocuments(IOUtils.createMemoryOnlyStreamCache());
            return out.toByteArray();
        } catch (Exception e) {
            throw new BadRequestException("Cannot merge the documents");
        }
    }

    // helper
    // RBAC si passa sempre da RecordsService.findOne, che applica permessi e scope OWN/TEAM/ALL
    // Il repository serve solo a sapere di che tipo è il record, perché findOne vuole l'objectKey
    // e l'url della stampa ha solo l'id
    private Record load(Actor actor, UUID recordId) {
        Record raw = recordRepository.findByIdAndIsDeletedFalse(recordId)
                .orElseThrow(() -> new NotFoundException("Record not found"));
        return recordsService.findOne(actor, raw.getObjectType().getKey(), recordId)
                .record();
    }

    private PdfTemplate pick(String objectKey, String templateKey) {
        if (templateKey != null && !templateKey.isBlank()) {
            PdfTemplate t = pdfTemplateRepository.findByKey(templateKey)
                    .orElseThrow(() -> new NotFoundException("Template not found"));
            // un template di un altro modulo su questo documento produce un PDF vuoto e nessun errore
            if (!t.getObjectKey().equals(objectKey)) {
                throw new BadRequestException("Template " + templateKey + " belong to " + t.getObjectKey() + ", not " + objectKey);
            }

            return t;
        }

        return pdfTemplateRepository.findByObjectKeyOrderByLabelAsc(objectKey).stream()
                .findFirst()
                .orElseThrow(() -> new BadRequestException("No PDF template for " + objectKey));
    }

    private Record lastDocumentOf(Actor actor, String objectKey) {
        // l'anteprima ha bisogno di dati veri: inventare un documento finto significa mantenere
        // un secondo generatore che diverge in un mese
        return recordRepository.findFirstByObjectType_KeyAndIsDeletedFalseOrderByCreatedAtDesc(objectKey)
                .map(r -> load(actor, r.getId()))
                .orElseThrow(() -> new BadRequestException("Create a " + objectKey + " first: the preview needs real data"));
    }

    // <numero>.pdf, o <titolo>.pdf ripulito. Non il titolo grezzo: contiene '/' e ':'
    private String filename(Record doc) {
        Object number = doc.getData() == null ? null : doc.getData().get("number");
        String base = number != null && !number.toString().isBlank()
                ? number.toString()
                : (doc.getTitle() == null || doc.getTitle().isBlank()
                    ? doc.getId().toString() : doc.getTitle());

        String clean = base.replaceAll("[^\\p{L}\\p{N}._-]+", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-|-$", "");
        if (clean.isEmpty()) clean = doc.getId().toString();
        if (clean.length() > 80) clean = clean.substring(0, 80);
        return clean + ".pdf";    }
}
