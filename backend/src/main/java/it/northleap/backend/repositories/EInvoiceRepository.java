package it.northleap.backend.repositories;

import it.northleap.backend.entities.EInvoice;
import it.northleap.backend.entities.EInvoiceStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EInvoiceRepository extends JpaRepository<EInvoice, UUID> {

    // la più recente per fattura: dopo uno scarto ne esistono due, e conta l'ultima
    Optional<EInvoice> findFirstByInvoice_IdOrderByCreatedAtDesc(UUID invoiceId);

    boolean existsByInvoice_IdAndStatusIn(UUID invoiceId, Collection<EInvoiceStatus> statuses);

    // EntityGraph: la pagina mostra il titolo della fattura, senza grafo sono N query
    @EntityGraph(attributePaths = "invoice")
    List<EInvoice> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = "invoice")
    List<EInvoice> findByStatusOrderByCreatedAtDesc(EInvoiceStatus status);
}
