package it.northleap.backend.services;


import it.northleap.backend.AbstractIntegrationTest;
import it.northleap.backend.dtos.FilterGroup;
import it.northleap.backend.dtos.ReportQuery;
import it.northleap.backend.dtos.ReportResult;
import it.northleap.backend.entities.*;
import it.northleap.backend.entities.Record;
import it.northleap.backend.repositories.ObjectTypeRepository;
import it.northleap.backend.repositories.RecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// con i mock qui non si prova niente. Il valore è proprio nell'SQL che gira.
// AbstractIntegrationTest porta su un postgres:16-apline usa e getta
class ReportEngineTest extends AbstractIntegrationTest {

    @Autowired private ReportEngine reportEngine;
    @Autowired private ObjectTypeRepository objectTypeRepository;
    @Autowired private RecordRepository recordRepository;

    private ObjectType deal;

    @BeforeEach
    void seed() {
        deal = new ObjectType();
        deal.setKey("test_deal");
        deal.setLabel("Deal");
        deal.setPluralLabel("Deal");

        FieldDef amount = new FieldDef();
        amount.setKey("amount");
        amount.setLabel("Importo");
        amount.setType(FieldType.CURRENCY);
        amount.setObjectType(deal);

        FieldDef stage = new FieldDef();
        stage.setKey("stage");
        stage.setLabel("Fase");
        stage.setType(FieldType.SELECT);
        stage.setObjectType(deal);

        deal.setFields(List.of(amount, stage));
        deal = objectTypeRepository.save(deal);

        save("won-1",  "won",  100);
        save("won-2",  "won",  250);
        save("lost-1", "lost", 999);
        save("lost-2", "lost", 999);
        save("open-1", "open",  10);
    }

    private void save(String title, String stage, int amount) {
        Record r = new Record();
        r.setObjectType(deal);
        r.setTitle(title);
        Map<String, Object> data = new HashMap<>();
        data.put("stage", stage);
        data.put("amount", amount);
        r.setData(data);
        recordRepository.save(r);
    }

    // il filtro è un FilterGroup vero, non una Map: è quello che arriva dal filter-builder del frontend
    // ed è il punto del test
    private FilterGroup stageIs(String value) {
        FilterGroup g = new FilterGroup();
        g.setCombinator("and");
        g.setConditions(List.of(Map.of("field", "stage", "op", "eq", "value", value)));

        return g;
    }

    private static double first(ReportResult r) {
        return r.metrics().get(0).get(0).doubleValue();
    }

    // senza il filtro compilato torna 5 e 1359, cioè due numeri credibili e sbagliati
    @Test
    void il_filtro_filtra_davvero() {
        ReportQuery q = new ReportQuery(
                "test_deal",
                stageIs("won"),
                List.of(), // rows
                List.of(), // columns
                List.of(new ReportQuery.Metric(ReportQuery.Aggregate.SUM, "amount", null, "Totale")),
                null, null, 100);

        ReportResult r = reportEngine.run(q);

        assertThat(r.metrics()).hasSize(1);
        assertThat(first(r)).isEqualTo(350.0); // solo i due "won"
    }

    @Test
    void il_raggruppamento_produce_una_riga_per_valore() {
        ReportQuery q = new ReportQuery(
                "test_deal", null,
                List.of(new ReportQuery.Dimension("stage", ReportQuery.Grain.NONE)),
                List.of(),
                List.of(new ReportQuery.Metric(ReportQuery.Aggregate.COUNT, null, null, "Quante")),
                null, null, 100);

        ReportResult r = reportEngine.run(q);

        assertThat(r.dimensions()).hasSize(3); // won, lost, open
    }

    @Test
    void senza_filtro_vede_tutto_il_modulo() {
        ReportQuery q = new ReportQuery(
                "test_deal", null, List.of(), List.of(),
                List.of(new ReportQuery.Metric(
                        ReportQuery.Aggregate.COUNT, null, null, "Quante")),
                null, null, 100);

        assertThat(first(reportEngine.run(q))).isEqualTo(5.0);
    }

    // orderDesc assente: il DTO lo dichiara Boolean apposta e
    // orderDescOrDefault() lo regge. Con un boolean primitivo Jackson 3
    // solleva e il report risponde 500.
    @Test
    void order_desc_assente_non_fa_esplodere_la_query() {
        ReportQuery q = new ReportQuery(
                "test_deal", null,
                List.of(new ReportQuery.Dimension("stage", ReportQuery.Grain.NONE)),
                List.of(),
                List.of(new ReportQuery.Metric(
                        ReportQuery.Aggregate.SUM, "amount", null, "Totale")),
                "met0", null, 100);

        assertThat(reportEngine.run(q).dimensions()).isNotEmpty();
    }

    // count non usa field, se un giorno metricExpr cambiasse e lo pretendesse, safeKey(null)
    // sbatterebbe in un 400 e questo test lo direbbe subito
    @Test
    void count_senza_campo_non_chiede_un_campo() {
        ReportQuery q = new ReportQuery(
                "test_deal", stageIs("lost"), List.of(), List.of(),
                List.of(new ReportQuery.Metric(
                        ReportQuery.Aggregate.COUNT, null, null, "Quante")),
                null, null, 100);

        assertThat(first(reportEngine.run(q))).isEqualTo(2.0);
    }
}
