package it.northleap.backend.calendar;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventMappingRepository extends JpaRepository<EventMapping, UUID> {
    Optional<EventMapping> findByAccountIdAndExternalEventId(UUID accountId, String externalEventId);
    List<EventMapping> findByAccountId(UUID accountId);
}
