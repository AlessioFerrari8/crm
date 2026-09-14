package it.northleap.backend.dtos;

import jakarta.validation.constraints.*;

import java.util.UUID;

// anteprima di un template non salvato: l'html arriva dall'editor
// recordId opzionale, se manca si usa l'ultimo documento di quel modulo
public record PdfPreviewDto(
    @NotBlank @Size(max = 200_000) String html,
    @NotBlank @Size(max = 64) String objectKey,
    @Size(max = 16) String pageSize,
    @Min(0) @Max(60) Integer marginMm,
    UUID recordId
) {
    public String pageSizeOrDefault() { return pageSize == null ? "A4" : pageSize; }
    public int marginOrDefault() { return marginMm == null ? 15 : marginMm; }
}
