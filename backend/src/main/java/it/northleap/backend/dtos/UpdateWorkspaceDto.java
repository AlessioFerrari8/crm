package it.northleap.backend.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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

    // fattura elettronic: i pattern sono quelli dell'XSD, un valore sbagliato qui, è uno scarto domani
    @Pattern(regexp = "^$|RF(0[1-9]|1[0-9])") private String fiscalRegime;
    @Size(max = 60) private String street;
    @Pattern(regexp = "^$|[0-9]{5}") private String zipCode;
    @Size(max = 60) private String city;
    @Pattern(regexp = "^$|[A-Za-z]{2}") private String province;
    @Pattern(regexp = "^$|[A-Z]{2}") private String countryCode;

}
