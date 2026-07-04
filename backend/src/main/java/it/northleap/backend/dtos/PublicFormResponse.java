package it.northleap.backend.dtos;

import it.northleap.backend.entities.FieldDef;

import java.util.List;

// definizione del modulo pubblico: solo cio' che serve a renderizzarlo (niente id interni del
// tipo, niente campi nascosti/readonly/calcolati - il filtro sta in PublicFormService)
public record PublicFormResponse(
        String key,
        String label,
        String color,
        List<FieldDef> fields
) {
}
