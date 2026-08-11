package it.northleap.backend.services;

import it.northleap.backend.dtos.DuplicateCandidateDto;
import it.northleap.backend.entities.ObjectType;
import it.northleap.backend.exceptions.NotFoundException;
import it.northleap.backend.exceptions.RbacDeniedException;
import it.northleap.backend.repositories.ObjectTypeRepository;
import it.northleap.backend.repositories.RecordRepository;
import it.northleap.backend.security.Actor;
import it.northleap.backend.security.PermAction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import it.northleap.backend.entities.Record;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class DuplicateFinderService {

    private final RecordRepository recordRepository;
    private final ObjectTypeRepository objectTypeRepository;
    private final RbacService rbacService;


    public List<DuplicateCandidateDto> find(Actor actor, String objectKey, double threshold) {
        if (!rbacService.resolve(actor.roleIds(), objectKey, PermAction.READ).allowed()) {
            throw new RbacDeniedException("Not allowed on " + objectKey);
        }


        ObjectType type = objectTypeRepository.findByKey(objectKey)
                .orElseThrow(() -> new NotFoundException("Object type not found"));

        // i titoli servono per la UI di confronto affiancato: una seconda lettura
        // in blocco invece di N query dentro il ciclo
        var pairs = recordRepository.findDuplicates(type.getId(), threshold);
        var ids = pairs.stream()
                .flatMap(p -> Stream.of(p.getLeftId(), p.getRightId())).distinct().toList();
        Map<UUID, String> titles = recordRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Record::getId, Record::getTitle));

        return pairs.stream()
                .map(p -> new DuplicateCandidateDto(
                        p.getLeftId(), titles.get(p.getLeftId()),
                        p.getRightId(), titles.get(p.getRightId()),
                        p.getScore()))
                .toList();

    }
}
