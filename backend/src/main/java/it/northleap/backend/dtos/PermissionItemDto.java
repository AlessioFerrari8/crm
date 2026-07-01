package it.northleap.backend.dtos;

import it.northleap.backend.entities.PermScope;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// una riga della matrice permessi. scope nullo -> default ALL lato service (come il riferimento)
@Getter
@Setter
@NoArgsConstructor
public class PermissionItemDto {
    @NotBlank
    private String resource;

    private boolean canRead;
    private boolean canWrite;
    private boolean canExecute;
    private PermScope scope;
}
