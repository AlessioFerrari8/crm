package it.northleap.backend.dtos;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record PdfBatchDto(
        // il limite va messo subito: senza, il primo che seleziona "tutti" in
        // una lista da 5000 record fa fuori la memoria del container
        @NotEmpty @Size(max = 200) List<UUID> recordIds,
        @Size(max = 64) String template
) { }
