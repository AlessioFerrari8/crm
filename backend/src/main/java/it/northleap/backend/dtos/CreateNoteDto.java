package it.northleap.backend.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CreateNoteDto {
    @NotBlank
    @Size(max = 4000)
    private String body;

    // visibile nel portale clienti
    private boolean customerVisible;
}
