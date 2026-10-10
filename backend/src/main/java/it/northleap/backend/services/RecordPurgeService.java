package it.northleap.backend.services;


import it.northleap.backend.entities.Record;
import it.northleap.backend.exceptions.BadRequestException;
import it.northleap.backend.exceptions.NotFoundException;
import it.northleap.backend.repositories.CampaignRecipientRepository;
import it.northleap.backend.repositories.RecordRepository;
import it.northleap.backend.security.Actor;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.util.UUID;

// cancellazione definitiva dal cestino. L'ordine conta: document_line e record_tag hanno una FK vera verso record,
// e senza questo la DELETE fallisce con un errore di chiave esterna incomprensibile
@Service
@RequiredArgsConstructor
public class RecordPurgeService {

    private final RecordRepository recordRepository;
    private final RecordsService recordsService;
    private final CampaignRecipientRepository campaignRecipientRepository;
    private final FileStorageService fileStorageService;
    private final AuditService auditService;
    private final JdbcTemplate jdbc;

    @Transactional
    public void purge(Actor actor, String key, UUID id, String ip) {
        // stessi permessi e scope della cancellazione normale: passa da trash()
        Record r = recordsService.trash(actor, key).stream().filter(x -> x.getId().equals(id)).findFirst()
                .orElseThrow(() -> new NotFoundException("Record non nel cestino"));
        // un documento fiscale non si distrugge: la legge vuole che resti dieci anni
        Integer fiscal = jdbc.queryForObject("select count(*) from e_invoice where invoice_id = ?", Integer.class, id);
        if (fiscal != null && fiscal > 0) throw new BadRequestException("Fattura con fattura elettronica: non si elimina definitvamente");

        // i file prima delle righe: dopo, il percorso su disco non lo sa più nessuno
        for (var f : fileStorageService.listByRecord(id)) {
            try {
                Files.deleteIfExists(fileStorageService.resolveOnDisk(f));
            } catch (Exception ignored) {
                // un file già sparito dal disco non deve bloccare la purge
            }
        }

        jdbc.update("delete from file_object where record_id = ?", id);
        jdbc.update("delete from document_line where document_id = ?", id);
        jdbc.update("delete from record_tag where record_id = ?", id);
        jdbc.update("delete from record_link where source_id = ? or target_id = ?", id, id);
        jdbc.update("delete from record_note where record_id = ?", id);
        jdbc.update("delete from time_entry where record_id = ?", id);

        // GDPR 8.4: la riga della campagna resta per le statistiche, ma non dice più chi era
        campaignRecipientRepository.anonymize(id);
        recordRepository.delete(r);
        auditService.log(actor, "purge", key, id.toString(), null, ip);
    }
}
