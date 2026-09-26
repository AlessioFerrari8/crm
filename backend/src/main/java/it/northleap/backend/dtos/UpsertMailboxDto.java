package it.northleap.backend.dtos;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SecondaryRow;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class UpsertMailboxDto {
    @NotBlank @Size(max = 120) private String label;
    @NotBlank private String host;
    @Min(1) @Max(65535) private int port;
    private boolean ssl = true;
    @NotBlank private String username;
    // in chiaro solo in ingresso, e solo alla scrittura: il service la cifra subito e non la rilegge mai
    // Null in PATCH = "lascia quella di prima"
    private String password;
    private String folder = "INBOX";
    private String targetObjectKey = "ticket";
    // senza, i ticket creati hanno ownerId null e con scope OWN non li vede nessuno: è la trappola
    // dei seeder, identica
    @NotNull private UUID defaultOwnerId;
    private boolean active = true;
}
