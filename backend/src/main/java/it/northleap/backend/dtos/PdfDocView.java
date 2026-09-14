package it.northleap.backend.dtos;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Map;

@Getter
@AllArgsConstructor
public class PdfDocView {
    private final String number; // "2026-3"
    private final String title;
    private final String typeLabel; // "Preventivo", da ObjectType.label
    private final String date; // già formattata
    private final String customerName; // relation risolta, non l'UUID
    private final String customerBlock; // indirizzo dell'intestatario, multiriga
    private final String notes;
    private final String paymentTerms;
    private final Map<String, Object> data; // jsonb grezzo
    private final Map<String, String> fields; // campo -> valore formattato
}
