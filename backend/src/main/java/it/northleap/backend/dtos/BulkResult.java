package it.northleap.backend.dtos;

// skipped = id ricevuti oltre il tetto di 500 e mai processati. Prima il troncamento era
// silenzioso: chi chiamava con 600 id ne vedeva aggiornati 500 e non aveva modo di accorgersene.
public record BulkResult(int affected, int skipped) {
}
