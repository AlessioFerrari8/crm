package it.northleap.backend.dtos;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UpdateObjectTypeDto {
    private String label;
    private String pluralLabel;
    private String icon;
    private String color;
    private Boolean isEnabled;
    private Integer sortOrder;
}
