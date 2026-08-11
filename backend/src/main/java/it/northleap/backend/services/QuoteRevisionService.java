package it.northleap.backend.services;


import it.northleap.backend.exceptions.NotFoundException;
import it.northleap.backend.exceptions.RbacDeniedException;
import it.northleap.backend.repositories.RecordRepository;
import it.northleap.backend.security.Actor;
import it.northleap.backend.security.PermAction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import it.northleap.backend.entities.Record;


import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QuoteRevisionService {

    private final RecordRepository recordRepository;
    private final RbacService rbacService;

    @Transactional
    public Record revise(Actor actor, UUID quoteId) {
        Record parent = recordRepository.findById(quoteId)
                .orElseThrow(() -> new NotFoundException("Quote not found"));

        if (!rbacService.resolve(actor.roleIds(), parent.getObjectType().getKey(), PermAction.WRITE).allowed()) {
            throw new RbacDeniedException("Not allowed on quotes");
        }

        // un preventivo appena creato e mai compilato ha data null
        Map<String, Object> parentData = parent.getData() != null ? parent.getData() : Map.of();
        String parentNumber = String.valueOf(parentData.getOrDefault("number", ""));

        // quante revisioni dirette ha già questo preventivo: il suffisso è progressivo per
        // PADRE, non globale (2025-13_2, 2025-13_4, 2025-13_5)
        long existing = recordRepository.
                countRevisionsOf(parent.getObjectType().getId(), parentNumber + "_");

        Map<String, Object> data = new LinkedHashMap<>(parentData);
        // il "+2" non è un errore: la prima revisione di 2025-13 si chiama 2025-13_2, il
        // padre è implicitamente la 1
        data.put("number", parentNumber + "_" + (existing + 2));
        data.put("status", "created");
        data.put("previousVersion", parent.getId().toString());

        // save() diretto invece di recordsService.create(): la revisione nasce con
        // status "created" e il padre passa a "revised", due valori che oggi NON sono fra le
        // options del campo status di quote (draft/sent/accepted/rejected) - passare dal
        // validatore darebbe 400 finché quelle options non vengono allineate a mano
        // (UPDATE su field_def, vedi 1.4 della spec).
        // Il prezzo è noto: niente audit log, niente RecordCreatedEvent e soprattutto niente
        // riga in record_link per previousVersion, quindi la revisione non compare nel
        // pannello "collegati" del padre. Da rifare da RecordsService dopo l'allineamento.
        Record revision = new Record();
        revision.setObjectType(parent.getObjectType());
        revision.setOwnerId(actor.id());
        revision.setTitle(parent.getTitle() + " (rev)");
        revision.setStatus("created");
        revision.setData(data);
        recordRepository.save(revision);

        // TODO blocco 6 (righe documento): documentLineService.copyLines(parent.getId(), revision.getId());
        // una revisione senza le righe del padre è incompleta, ma DocumentLineService non
        // esiste ancora e il riferimento teneva giù tutta la build

        parent.setStatus("revised");
        Map<String, Object> newParentData = new LinkedHashMap<>(parentData);
        newParentData.put("status", "revised");
        parent.setData(newParentData);
        recordRepository.save(parent);

        return revision;

    }
}
