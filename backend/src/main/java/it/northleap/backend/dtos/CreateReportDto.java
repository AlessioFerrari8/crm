package it.northleap.backend.dtos;

import it.northleap.backend.entities.Report;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// `query` arriva come Map e non come ReportQuery: è quello che finisce nella colonna jsonb
// e riconvertirlo avanti e indietro non serve a nessuno. La validazione vera la fa ReportEngine
// quando lo esegue.
public record CreateReportDto(
        @NotBlank @Size(max = 200) String title,
        @NotNull Report.ReportRender render,
        @NotBlank String objectKey,
        @Size(max = 100) String folder,
        boolean personal, // true, solo mio, ownerId valorizzato
        @NotNull java.util.Map<String, Object> query
) {

}
