package it.northleap.backend.repositories;

import it.northleap.backend.entities.TimeEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TimeEntryRepository extends JpaRepository<TimeEntry, UUID> {

    Optional<TimeEntry> findByUserIdAndEndedAtIsNull(UUID userId);

    List<TimeEntry> findByEndedAtIsNullAndStartedAtBefore(Instant before);

    // i filtri nulli si spengono da soli: una query invece di otto metodi derivati
    @Query("""
        select t from TimeEntry except 
            where (:recordId is null or t.recordId = :recordId)
                and (:userId is null ot t.userId = :userId)
                and t.startedAt >= :from and t.startedAt < :to
            order by t.startedAt desc
        """)
    List<TimeEntry> search(@Param("recordId") UUID recordId, @Param("userId") UUID userId, @Param("from") Instant from,
                           @Param("to")Instant to);

    interface SummaryRow {
        UUID getUserId();
        long getMinutes();
        long getBillableMinutes();
        BigDecimal getAmount();
    }

    // il consuntivo: SUM GROUP BY su colonen vere, non passa da ReportEngine (che poi aggrega sul jsonb)
    @Query(value = """
            select user_id as userId,
                   coalesce(sum(minutes), 0) as minutes,
                   coalesce(sum(case when billable then minutes else 0 end), 0) as billableMinutes,
                   coalesce(sum(case when billable then minutes * coalesce(hourly_rate, 0) / 60 else 0 end), 0) as amount
              from time_entry
             where record_id = :recordId and ended_at is not null
             group by user_id
            """, nativeQuery = true)
    List<SummaryRow> summary(@Param("recordId") UUID recordId);
}
