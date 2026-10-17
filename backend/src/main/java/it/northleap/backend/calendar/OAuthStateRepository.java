package it.northleap.backend.calendar;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface OAuthStateRepository extends JpaRepository<OAuthState, String> {
    @Modifying
    @Query("delete from OAuthState s where s.expiresAt < :now")
    int purgeExpired(@Param("now")Instant now);
}
