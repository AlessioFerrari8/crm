package it.northleap.backend.repositories;

import it.northleap.backend.entities.NpsSurvey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NpsSurveyRepository extends JpaRepository<NpsSurvey, UUID> {
    List<NpsSurvey> findAllByOrderByCreatedAtDesc();
}
