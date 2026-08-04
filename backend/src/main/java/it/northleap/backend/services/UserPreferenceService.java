package it.northleap.backend.services;

import it.northleap.backend.dtos.UserPreferenceDto;
import it.northleap.backend.entities.UserPreference;
import it.northleap.backend.exceptions.BadRequestException;
import it.northleap.backend.repositories.UserPreferenceRepository;
import it.northleap.backend.security.Actor;
import it.northleap.backend.security.ActorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;


// niente RBAC: ogni utente legge e scrive solo le proprie preferenze, l'id non
// arriva mai dalla richiesta, bensì dall'actor. Nessun permesso da definire,
// e nessun modo di leggere quelle di un altro
@Service
@RequiredArgsConstructor
public class UserPreferenceService {

    // inject
    private final UserPreferenceRepository userPreferenceRepository;

    // se l'utente non ha mai salvato nulla torna i default, senza scrivere righe
    public UserPreference get(Actor actor) {
        UUID userId = requireUser(actor);

        return userPreferenceRepository.findById(userId).orElseGet(() -> {
            UserPreference defaults = new UserPreference();
            defaults.setUserId(userId);
            return defaults;
        });
    }

    @Transactional
    public UserPreference save(Actor actor, UserPreferenceDto dto) {
        UUID userId = requireUser(actor);
        UserPreference pref = userPreferenceRepository.findById(userId)
                .orElseGet(UserPreference::new);
        pref.setUserId(userId);
        pref.setCurrency(dto.currency());
        pref.setGroupSeparator(dto.groupSeparator());
        pref.setDecimalSeparator(dto.decimalSeparator());
        pref.setSymbolPlacement(dto.symbolPlacement());
        pref.setCurrencyDecimals(dto.currencyDecimals());
        pref.setTruncateTrailingZeros(dto.truncateTrailingZeros());
        pref.setDateFormat(dto.dateFormat());
        if (dto.groupSeparator().equals(dto.decimalSeparator())) {
            throw new BadRequestException("Group and decimal separators must differ");
        }
        return userPreferenceRepository.save(pref);
    }

    // separatore dcimale e di gruppo uguali renderebbero i numeri illeggibili
    // (1.234.567 con decimale "." non è più parsabile a occhio)
    private UUID requireUser(Actor actor) {
        if (actor.type() != ActorType.USER) {
            throw new BadRequestException("This endpoint requires a JWT-authenticated user, not an API key");
        }

        return actor.id();
    }
}
