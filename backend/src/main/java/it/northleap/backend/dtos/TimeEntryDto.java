package it.northleap.backend.dtos;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

// registrazione manuale o modifica. minutes vince su endedAt: "2 ore ieri" si scrive così
public record TimeEntryDto(
        @NotNull UUID recordId,
        @NotNull Instant startedAt,
        @Min(1) Integer minutes,
        Boolean billable,
        @Size(max = 500) String description) {
}
