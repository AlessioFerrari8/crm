package it.northleap.backend.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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

    // brand: se null, non vengono toccati dall'update
    private String brandColor;
    private String logoUrl;

    // link Cal.com per la pagina Calendario: null = non toccare, stringa vuota = rimuovere
    private String calcomUrl;

    // anagrafica fiscale di chi emette i documenti
    @Size(max = 255) private String legalName;
    @Size(max = 20) private String vatNumber;
    @Size(max = 20) private String taxCode;
    @Size(max = 1000) private String address;
    @Size(max = 255) private String contactLine;

}
