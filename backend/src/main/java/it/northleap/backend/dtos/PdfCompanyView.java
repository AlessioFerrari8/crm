package it.northleap.backend.dtos;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PdfCompanyView {
    private final String name;
    private final String address;
    private final String vatNumber;
    private final String taxCode;
    private final String contactLine;
    private final String logoDataUri; // es. "data:image/png;base64"
    private final boolean hasLogo;
}
