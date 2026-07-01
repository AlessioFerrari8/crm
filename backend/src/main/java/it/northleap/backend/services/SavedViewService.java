package it.northleap.backend.services;

import it.northleap.backend.dtos.CreateSavedViewDto;
import it.northleap.backend.entities.SavedView;
import it.northleap.backend.exceptions.NotFoundException;
import it.northleap.backend.repositories.SavedViewRepository;
import it.northleap.backend.security.Actor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

// Viste salvate personali: ogni operazione è scoped su ownerId = attore (stesso pattern
// personale di NotificationService: nessuna @RequirePerm, l'isolamento sta nelle query).
@Service
@RequiredArgsConstructor
public class SavedViewService {

    private final SavedViewRepository savedViewRepository;

    public List<SavedView> list(Actor actor, String objectKey) {
        return savedViewRepository.findByOwnerIdAndObjectKeyOrderByCreatedAtAsc(actor.id(), objectKey);
    }

    public SavedView create(Actor actor, CreateSavedViewDto dto) {
        SavedView view = new SavedView();
        view.setObjectKey(dto.getObjectKey());
        view.setName(dto.getName());
        view.setOwnerId(actor.id());
        view.setConfig(dto.getConfig());
        return savedViewRepository.save(view);
    }

    public void remove(Actor actor, UUID id) {
        // findByIdAndOwnerId: una vista altrui risulta semplicemente inesistente (404, non 403 -
        // non riveliamo che l'id esiste)
        SavedView view = savedViewRepository.findByIdAndOwnerId(id, actor.id())
                .orElseThrow(() -> new NotFoundException("Vista non trovata"));
        savedViewRepository.delete(view);
    }
}
