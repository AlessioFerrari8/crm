package it.northleap.backend.controllers;

    import it.northleap.backend.entities.Record;
    import it.northleap.backend.security.Actor;
    import it.northleap.backend.security.CurrentActor;
    import it.northleap.backend.services.DocumentConversionService;
    import lombok.RequiredArgsConstructor;
    import org.springframework.http.ResponseEntity;
    import org.springframework.web.bind.annotation.PathVariable;
    import org.springframework.web.bind.annotation.PostMapping;
    import org.springframework.web.bind.annotation.RequestMapping;
    import org.springframework.web.bind.annotation.RestController;

    import java.util.UUID;

// stessa collocazione di QuoteRevisionController: convertire è un azione su un record
@RestController
@RequestMapping("/api/records")
@RequiredArgsConstructor
public class DocumentConversionController {

    private final DocumentConversionService documentConversionService;

    @PostMapping("/quote/{id}/convert-to-order")
    public ResponseEntity<Record> convert(@CurrentActor Actor actor, @PathVariable UUID id) {
        return ResponseEntity.ok(documentConversionService.quoteToOrder(actor, id));
    }
}