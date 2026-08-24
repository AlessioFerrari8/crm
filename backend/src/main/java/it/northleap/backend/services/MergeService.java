package it.northleap.backend.services;

import it.northleap.backend.entities.RecordLink;
import it.northleap.backend.exceptions.BadRequestException;
import it.northleap.backend.exceptions.NotFoundException;
import it.northleap.backend.exceptions.RbacDeniedException;
import it.northleap.backend.repositories.RecordLinkRepository;
import it.northleap.backend.repositories.RecordNoteRepository;
import it.northleap.backend.repositories.RecordRepository;
import it.northleap.backend.repositories.RecordTagRepository;
import it.northleap.backend.security.Actor;
import it.northleap.backend.security.PermAction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import it.northleap.backend.entities.Record;

import java.util.*;

// Fusione di due record dello stesso tipo. Il perdente non viene cancellato, ma viene
// messo nel cestino (isDeleted): un merge sbagliato deve essere recuperabile, e il
// cestino esiste già per questo

@Service
@RequiredArgsConstructor
public class MergeService {

    // repositories
    private final RecordRepository recordRepository;
    private final RecordLinkRepository recordLinkRepository;
    private final RecordNoteRepository recordNoteRepository;
    private final RecordTagRepository recordTagRepository;
    private final RbacService rbacService;
    private final AuditService auditService;

    // winnerId sopravvive, loserId finisce nel cestino
    // fieldsFromLoser: le chiavi che l'utente ha scelto di rpendere dal perdente
    // nella UI di confronto affiancato (colonna sinistra / colonna destra)
    @Transactional
    public Record merge(Actor actor, UUID winnerId, UUID loserId, List<String> fieldsFromLoser) {
        if (winnerId.equals(loserId)) {
            throw new BadRequestException("Cannot merge a record with itself");
        }

        Record winner = load(actor, winnerId);
        Record loser = load(actor, loserId);

        if (!winner.getObjectType().getId().equals(loser.getObjectType().getId())) {
            throw new BadRequestException("Records of different types cannot be merged");
        }

        // 1. dati: parto dal vincitore e sovrascrivo solo i campi scelti
        Map<String, Object> data = new LinkedHashMap<>(
                winner.getData() != null ? winner.getData() : Map.of());
        Map<String, Object> loserData = loser.getData() != null ? loser.getData() : Map.of();

        for (String key : fieldsFromLoser) {
            if (loserData.containsKey(key)) {
                data.put(key, loserData.get(key));
            }
        }

        winner.setData(data);

        // 2. relazioni: tutto quello che puntava al perdente ora punta al vincitore
        relinkSources(loser, winner);
        relinkTargets(loser, winner);

        // 3. riferimenti dentro il jsonb: gli id del perdente citati dentro il data di altri
        // record (campi di tipo reference) devono puntare al vincitore, altrimenti restano
        // appesi a un record nel cestino
        recordRepository.findAllReferencing(loserId.toString()).forEach(other -> {
            if (other.getData() == null) return;
            Map<String, Object> d = new LinkedHashMap<>(other.getData());
            // il ramo sulla lista serve per le RELATION con config.multiple = true: lì il
            // jsonb contiene una lista di id, e senza questo caso resterebbero riferimenti
            // al perdente
            d.replaceAll((k, v) -> {
                if (loserId.toString().equals(v)) return winner.getId().toString();
                if (v instanceof List<?> list) {
                    return list.stream()
                            .map(x -> loserId.toString().equals(x) ? winner.getId().toString() : x)
                            .distinct() // il vincitore poteva già essere nella lista
                            .toList();
                }
                return v;
            });
            other.setData(d);
            recordRepository.save(other);
        });

        // 4. note e tag passano al vincitore: sono cronologia, non dati doppi
        recordNoteRepository.findByRecordIdOrderByCreatedAtDesc(loserId)
                // RecordNote.recordId è una soft FK (UUID), non una relazione: si riassegna l'id
                .forEach(note -> { note.setRecordId(winnerId); recordNoteRepository.save(note); });

        recordTagRepository.findByRecordId(loserId).forEach(rt -> {
            boolean already = recordTagRepository.findByRecordId(winnerId).stream()
                    .anyMatch(w -> w.getTag().getId().equals(rt.getTag().getId()));

            if (already) {
                recordTagRepository.delete(rt); // il vincitore ce l'ha già
            } else {
                rt.setRecord(winner);
                recordTagRepository.save(rt);
            }
        });

        // 5. perdente nel cestino
        loser.setDeleted(true);
        recordRepository.save(loser);
        recordRepository.save(winner);

        auditService.log(actor, "merge", winner.getObjectType().getKey(),
                winnerId.toString(), Map.of("loser", loserId.toString()), null);

        return winner;
    }

    // i link in cui il perdente è la sorgente passano al vincitore
    // Il duplicato va cercato fra i link uscenti del vincitore (stessa sorgente, stesso target, stessa relationKey)
    // è quello il vincolo UNIQUE che salterebbe. Cercare fra gli entranti (findByTarget_Id) confronta il vincitore
    // con sè stesso e non trova mai niente
    private void relinkSources(Record loser, Record winner) {
        List<RecordLink> winnerOut = recordLinkRepository.findBySource_Id(winner.getId());
        for (RecordLink link : recordLinkRepository.findBySource_Id(loser.getId())) {
            // un link perdente -> vincitore diventerebbe vincitore -> vincitore
            // un record collegato a sè stesso non ha senso, si butta
            if (link.getTarget().getId().equals(winner.getId())) {
                recordLinkRepository.delete(link);
                continue;
            }

            boolean duplicate = winnerOut.stream().anyMatch(w ->
                    w.getTarget().getId().equals(link.getTarget().getId())
                            && Objects.equals(w.getRelationKey(), link.getRelationKey()));

            if (duplicate) {
                recordLinkRepository.delete(link);
            } else {
                link.setSource(winner);
                recordLinkRepository.save(link);
            }
        }

    }

    // I link in cui il perdente è il bersaglio. Stessa logica a specchio.
    // findByTarget_Id e non la variante IsDeletedFalse: qui servono tutti i link del perdente,
    // anche quelli verso record già cestinati.
    private void relinkTargets(Record loser, Record winner) {
        List<RecordLink> winnerIn = recordLinkRepository.findByTarget_Id(winner.getId());
        for (RecordLink link : recordLinkRepository.findByTarget_Id(loser.getId())) {
            if (link.getSource().getId().equals(winner.getId())) {
                recordLinkRepository.delete(link);
                continue;
            }
            boolean duplicate = winnerIn.stream().anyMatch(w ->
                    w.getSource().getId().equals(link.getSource().getId())
                            && Objects.equals(w.getRelationKey(), link.getRelationKey()));
            if (duplicate) {
                recordLinkRepository.delete(link);
            } else {
                link.setTarget(winner);
                recordLinkRepository.save(link);
            }
        }
    }

    private Record load(Actor actor, UUID id) {
        Record record = recordRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Record not found"));

        if (!rbacService.resolve(actor.roleIds(), record.getObjectType().getKey(), PermAction.WRITE).allowed()) {
            throw new RbacDeniedException("Not allowed on " + record.getObjectType().getKey());
        }

        return record;
    }
}
