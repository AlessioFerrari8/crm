package it.northleap.backend.dtos;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
public class PublicFormSubmission {
    @NotNull
    private Map<String, Object> data;

    // honeypot anti-bot: campo invisibile nel form, un umano lo lascia vuoto. Se arriva pieno la
    // submission viene scartata in silenzio (finto successo, per non istruire il bot)
    private String website;
}
