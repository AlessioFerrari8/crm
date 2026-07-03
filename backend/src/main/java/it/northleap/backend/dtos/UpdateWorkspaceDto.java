package it.northleap.backend.dtos;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UpdateWorkspaceDto {
    // nome azienda: obbligatorio e non vuoto (e' l'unico campo che la UI cambia per ora)
    @NotBlank
    private String name;

    // predisposti per dopo (logo/colore brand): se null, non vengono toccati dall'update
    private String brandColor;
    private String logoUrl;

    // link Cal.com per la pagina Calendario: null = non toccare, stringa vuota = rimuovere
    private String calcomUrl;
}
