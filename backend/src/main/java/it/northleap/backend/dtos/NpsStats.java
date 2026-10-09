package it.northleap.backend.dtos;


// il numero SENZA il conteggio non significa niente: "NPS 42 su 118 risposte, 31% di risposta"
public record NpsStats(
        Integer nps,
        long sent,
        long answered,
        long promoters,
        long passives,
        long detractors,
        int responseRatePercent,
        boolean tooFewAnswers
) {
}
