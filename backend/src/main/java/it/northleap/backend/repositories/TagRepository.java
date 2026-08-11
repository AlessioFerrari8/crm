package it.northleap.backend.repositories;

import it.northleap.backend.dtos.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface TagRepository extends JpaRepository<Tag, UUID> {
    // i tag che un utente può vedere: i pubblici più i propri
    @Query("select t from Tag t where t.ownerId is null or t.ownerId = :userId order by t.name")
    List<Tag> findVisible(@Param("userId") UUID userId);

    boolean existsByNameAndOwnerIdIsNull(String name);

    boolean existsByNameAndOwnerId(String name, UUID ownerId);


}
