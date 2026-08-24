package it.northleap.backend.services;


import it.northleap.backend.entities.ObjectType;
import it.northleap.backend.exceptions.BadRequestException;
import it.northleap.backend.exceptions.NotFoundException;
import it.northleap.backend.exceptions.RbacDeniedException;
import it.northleap.backend.repositories.ObjectTypeRepository;
import it.northleap.backend.repositories.RecordRepository;
import it.northleap.backend.security.Actor;
import it.northleap.backend.security.PermAction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import it.northleap.backend.entities.Record;
import it.northleap.backend.entities.ObjectType;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentConversionService {

    private final RecordRepository recordRepository;
    private final ObjectTypeRepository objectTypeRepository;
    private final DocumentLineService documentLineService;
    private final DocumentNumberService documentNumberService;
    private final RbacService rbacService;
    private final RecordQueryService recordQueryService;

    // i campi che si travasano dal preventivo all'ordine
    private static final List<String> CARRIED = List.of(
            "subject", "company", "contact", "paymentMethod", "billingAddress", "shippingAddress",
            "discountTotal", "shippingCost"
    );

    @Transactional
    public Record quoteToOrder(Actor actor, UUID quoteId) {
        Record quote = recordRepository.findById(quoteId)
                .orElseThrow(() -> new NotFoundException("Quote not found"));

        if (!"quote".equals(quote.getObjectType().getKey())) {
            throw new BadRequestException("Not a quote");
        }

        if (!rbacService.resolve(actor.roleIds(), "sales_order", PermAction.WRITE).allowed()) {
            throw new RbacDeniedException("Not allowed to create sales order");
        }

        ObjectType orderType = objectTypeRepository.findByKey("sales_order")
                .orElseThrow(() -> new NotFoundException("Object type not found: sales_order"));

        // idempotenza lato utente, il secondo click non deve creare un secondo ordine
        recordQueryService.findIdByFieldValue(orderType.getId(), "quote", quoteId.toString())
                .ifPresent(existing -> { throw new BadRequestException("Quote already converted"); });

        Map<String, Object> data = new LinkedHashMap<>();
        Map<String, Object> quoteData = quote.getData() != null ? quote.getData() : Map.of();

        CARRIED.forEach(key -> {
            if (quoteData.containsKey(key)) data.put(key, quoteData.get(key));
        });

        data.put("quote", quote.getId().toString()); // link della conversione
        data.put("status", "created");
        data.put("orderDate", LocalDate.now().toString());

        String number = documentNumberService.next(orderType);
        if (number != null) data.put("number", number);

        Record order = new Record();
        order.setObjectType(orderType);
        order.setOwnerId(actor.id());
        order.setTitle(String.valueOf(data.getOrDefault("subject", "Ordine")));
        order.setStatus("created");
        order.setData(data);
        recordRepository.save(order);

        // le righe si copiano identiche: è il senso della conversione
        documentLineService.copyLines(quote.getId(), order.getId());

        return order;
    }

}
