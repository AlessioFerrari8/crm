package it.northleap.backend.calendar;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CalendarAccountRepository extends JpaRepository<CalendarAccount, UUID> {
    List<CalendarAccount> findByActiveTrue();
    List<CalendarAccount> findByUserId(UUID userId);
    Optional<CalendarAccount> findByUserIdAndAccountEmail(UUID userId, String email);
}
