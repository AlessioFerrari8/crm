package it.northleap.backend.dtos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class SetPermissionsDto {
    @NotNull
    @Valid
    private List<PermissionItemDto> permissions;
}
