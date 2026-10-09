package it.northleap.backend.repositories;

import it.northleap.backend.entities.NpsResponse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NpsResponseRepository extends JpaRepository<NpsResponse, UUID> {
    Optional<NpsResponse> findByToken(String token);

    // cooldown: qualunque sondaggio, stessa persona
    boolean existsByRecordIdAndSentAtAfter(UUID recordId, Instant after);

    long countBySurvey_id(UUID surveyId);

    List<NpsResponse> findBySurvey_IdAndScoreIsNotNull(UUID surveyId);
}
