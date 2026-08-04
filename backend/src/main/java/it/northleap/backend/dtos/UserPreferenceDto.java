package it.northleap.backend.dtos;


import it.northleap.backend.entities.UserPreference;
import jakarta.validation.constraints.*;

// i separatori sono vincolati a una lista chiusa: arrivano dal client e finiscono
// in una stringa di formattazione, un valore libero qui non serve a nessuno
public record UserPreferenceDto(
    @NotBlank @Pattern(regexp = "^[A-Z]{3}$", message = "currency must be an ISO 4217 code") String currency,
    @NotNull @Pattern(regexp = "^[.,' ]?$", message = "invalid group separator") String groupSeparator,
    @NotNull @Size(min = 1, max = 1) @Pattern(regexp = "^[.,]$", message = "invalid decimal separator") String decimalSeparator,
    @NotNull UserPreference.SymbolPlacement symbolPlacement,
    @Min(0) @Max(6) int currencyDecimals,
    boolean truncateTrailingZeros,
    @NotBlank String dateFormat
) {

}
