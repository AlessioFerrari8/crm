package it.northleap.backend.config;

import it.northleap.backend.entities.ObjectType;
import it.northleap.backend.repositories.ObjectTypeRepository;
import it.northleap.backend.repositories.RecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import it.northleap.backend.entities.Record;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Order(5)
public class InventorySeeder implements ApplicationRunner {

    // inject
    private final ObjectTypeRepository objectTypeRepository;
    private final RecordRepository recordRepository;

    private record Row(String title, Map<String, Object> data) {};

    private static final List<Row> VAT_RATES = List.of(
            new Row("IVA 22%", Map.of("name", "IVA 22%", "rate", 22, "isDefault", true)),
            new Row("IVA 10%", Map.of("name", "IVA 10%", "rate", 10, "isDefault", false)),
            new Row("IVA 5%", Map.of("name", "IVA 5%", "rate", 5, "isDefault", false)),
            new Row("IVA 4%", Map.of("name", "IVA 4%", "rate", 4, "isDefault", false)),
            new Row("Esente art. 10", Map.of("name", "Esente art. 10", "rate", 0,
                    "sdiNature", "N4", "isDefault", false)),
            new Row("Non imponibile art. 8", Map.of("name", "Non imponibile art. 8", "rate", 0,
                    "sdiNature", "N3.1", "isDefault", false)),
            new Row("Reverse charge", Map.of("name", "Reverse charge", "rate", 0,
                    "sdiNature", "N6.9", "isDefault", false))
    );

    private static final List<Row> PAYMENT_METHODS = List.of(
            new Row("Rimessa diretta", Map.of("name", "Rimessa diretta", "days", 0, "sdiCode", "MP01")),
            new Row("Bonifico 30 gg", Map.of("name", "Bonifico 30 gg", "days", 30, "sdiCode", "MP05")),
            new Row("Bonifico 60 gg", Map.of("name", "Bonifico 60 gg", "days", 60, "sdiCode", "MP05")),
            new Row("Bonifico 30 gg fine mese", Map.of("name", "Bonifico 30 gg fine mese", "days", 30, "sdiCode", "MP05")),
            new Row("RiBa 60 gg", Map.of("name", "RiBa 60 gg", "days", 60, "sdiCode", "MP12")),
            new Row("Carta di credito", Map.of("name", "Carta di credito", "days", 0, "sdiCode", "MP08"))
    );

    @Override
    public void run(ApplicationArguments args) {
        seed("vat_rate", VAT_RATES);
        seed("payment_method", PAYMENT_METHODS);
    }

    // idempotente sul titolo: se c'è già non lo tocca, così un
    // riavvio non duplica le aliquote e non sovrascrive quelle mdoificate
    private void seed(String objKey, List<Row> rows) {
        ObjectType obj = objectTypeRepository.findByKey(objKey).orElse(null);
        if (obj == null) return;

        for (Row r : rows) {
            boolean exists = recordRepository
                    .existsByObjectTypeIdAndTitleAndIsDeletedFalse(obj.getId(), r.title());
            if (exists) continue;

            Record record = new Record();
            record.setObjectType(obj);
            record.setTitle(r.title());
            record.setData(new LinkedHashMap<>(r.data()));
            recordRepository.save(record);
        }
    }

}
