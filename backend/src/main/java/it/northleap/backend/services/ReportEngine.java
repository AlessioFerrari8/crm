package it.northleap.backend.services;


// Aggregazione in SQL, non in memoria. La differenza con ChartService (che carica i record e fa
// i bucket con una HashMap) è tutta qui: un pivot su 200k record non si può fare lato Java

import it.northleap.backend.dtos.ReportQuery;
import it.northleap.backend.dtos.ReportResult;
import it.northleap.backend.entities.ObjectType;
import it.northleap.backend.exceptions.BadRequestException;
import it.northleap.backend.exceptions.NotFoundException;
import it.northleap.backend.repositories.ObjectTypeRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

// SICUREZZA: i nomi dei campi finiscono dentro l'SQL come chiavi jsonb, quindi non possono
// essere concatenati grezzi. Ogni identificatore passa da safeKey(), i valori sono sempre parametri
// blindati. È l'unico punto del CRM dove si costruisce SQL a stringhe
@Service
@RequiredArgsConstructor
public class ReportEngine {

    private final EntityManager entityManager;
    private final ObjectTypeRepository objectTypeRepository;
    private final RecordFilterCompiler filterCompiler;

    // solo lettere, cifre e underscore: qualsiasi altra cosa è un tentativo di injection
    // o un errore, in entrambi i casi va fermata qui
    private static String safeKey(String key) {
        if (key == null || !key.matches("^[A-Za-z0-9_]{1,64}$")) {
            throw new BadRequestException("Invalid field name: " + key);
        }
        return key;
    }

    // le colonne vere della tabella record; tutto il resto è dentro data
    private static final Set<String> REAL_COLUMNS =
            Set.of("status", "owner_id", "created_at", "updated_at", "title");

    // espressione SQL per un campo: colonna vera oppure estrazione dal jsonb
    private String expr(String field) {
        String key = safeKey(field);
        return REAL_COLUMNS.contains(key) ? "r." + key : "r.data->>'" + key + "'";
    }

    private String dimensionExpr(ReportQuery.Dimension dim) {
        if (dim.grain() == null || dim.grain() == ReportQuery.Grain.NONE) return expr(dim.field());
        return "date_trunc('" + dim.grain().name().toLowerCase() + "', " + timestamp(dim.field()) + ")";
    }

    private String metricExpr(ReportQuery.Metric metric) {
        // con secondField null AVG_DAYS_BETWEEN passa da safeKey() e sbatte in un 400
        if (metric.aggregate() == ReportQuery.Aggregate.AVG_DAYS_BETWEEN
                && metric.secondField() == null) {
            throw new BadRequestException("AVG_DAYS_BETWEEN needs a second field");
        }


        return switch (metric.aggregate()) {
            case COUNT -> "count(*)";
            case SUM -> "sum(" + numeric(metric.field()) + ")";
            case AVG -> "avg(" + numeric(metric.field()) + ")";
            case MIN -> "min(" + numeric(metric.field()) + ")";
            case MAX -> "max(" + numeric(metric.field()) + ")";
            case AVG_DAYS_BETWEEN -> "avg(extract(epoch from (" + timestamp(metric.secondField())
                    + " - " + timestamp(metric.field()) + ")) / 86400)";
        };
    }

    public ReportResult run(ReportQuery query) {
        ObjectType obj = objectTypeRepository.findByKey(query.objectKey())
                .orElseThrow(() -> new NotFoundException("Object type not found: " + query.objectKey()));

        List<String> selects = new ArrayList<>();
        List<String> groups = new ArrayList<>();


        List<ReportQuery.Dimension> dims = new ArrayList<>();
        dims.addAll(query.rows() != null ? query.rows() : List.of());
        dims.addAll(query.columns() != null ? query.columns() : List.of());


        for (int i = 0; i < dims.size(); i++) {
            String e = dimensionExpr(dims.get(i));
            selects.add(e + " as dim" + i);
            groups.add(e);
        }
        List<ReportQuery.Metric> metrics = query.metrics() != null ? query.metrics() : List.of();

        if (metrics.isEmpty()) {
            throw new BadRequestException("A report needs at least one metric");
        }

        for (int i = 0; i < metrics.size(); i++) {
            selects.add(metricExpr(metrics.get(i)) + " as met" + i);
        }

        List<Object> params = new ArrayList<>();
        params.add(obj.getId());

        StringBuilder sql = new StringBuilder("select ")
                .append(String.join(", ", selects))
                .append(" from record r where r.object_type_id = ? and r.is_deleted = false");

        // Il filtro non si riscrive: è lo stesso FilterGroup del Filter Builder e lo compila la stessa
        // classe delle liste. Due dialetti di filtro nello stesso prodotto è la strada per averne due che
        // rispondono diverso.
        // Il compilatore emette colonne non qualificate (data ->>'x', status): qui la from ha una tabella sola
        // quindi si risolvono comunque su r
        String filterSql = filterCompiler.compileWhere(query.filter(), obj, params);
        if (filterSql != null && !filterSql.isBlank()) {
            sql.append(" and ").append(filterSql);
        }

        if (!groups.isEmpty()) {
            sql.append(" group by ").append(String.join(", ", groups));
        }
        sql.append(" order by ").append(orderByClause(query, dims.size(), metrics));
        // Un report che ne vuole di più è un export, non un report
        sql.append(" limit ").append(query.limit() != null ? Math.min(query.limit(), 5000) : 5000);

        Query q = entityManager.createNativeQuery(sql.toString());
        for (int i = 0; i < params.size(); i++) {
            q.setParameter(i + 1, params.get(i));
        }

        @SuppressWarnings("unchecked")
        List<Object[]> rows = q.getResultList();
        return ReportResult.of(dims.size(), metrics.size(), rows);
    }

    // order by per posizione (1-based sul select), mai per nome campo: così non c'è nessuna stringa
    // dell'utente dentro l'SQL e si può ordinare anche sulle metriche, che è quello che serve ai "TOP 10
    // mai fatturato"
    // formato accettato in orderBy: "dim0", "dim1", "met0", null = prima colonna
    private String orderByClause(ReportQuery query, int dimCount, List<ReportQuery.Metric> metrics) {
        String dir = query.orderDescOrDefault() ? "desc" : "asc";
        String key = query.orderBy();

        if (key == null || key.isBlank()) return "1 " + dir;
        try {
            if (key.startsWith("dim")) {
                int i = Integer.parseInt(key.substring(3));
                if (i >= 0 && i < dimCount) return (i + 1) + " " + dir;
            } else if (key.startsWith("met")) {
                int i = Integer.parseInt(key.substring(3));
                if (i >= 0 && i < metrics.size()) return (dimCount + i + 1) + " " + dir;
            }
        } catch (NumberFormatException ignored) {
            // cade nel default sotto: un orderBy scritto male non deve rompere il report
        }

        return "1 " + dir;
    }

    // helper

    // numero da un campo jsonb, che è sempre testo: nullif toglie di mezzo la stringa vuota (il
    // caso frequente: campo mai compilato), il regexp scarta tutto ciò che non è un numero. Senza questo, un
    // record sporco fa fallire il report di tutti.
    private String numeric(String field) {
        String e = expr(field);
        if (REAL_COLUMNS.contains(field)) return "(" + e + ")::numeric";
        return "(case when nullif(" + e + ", '') ~ '^-?[0-9]+([.,][0-9]+)?$' "
                + "then replace(nullif(" + e + ", ''), ',', '.')::numeric end)";
    }

    // stessa idea per le date: si castano solo i valori che sembrano una data
    private String timestamp(String field) {
        String e = expr(field);
        if (REAL_COLUMNS.contains(field)) return e;
        return "(case when nullif(" + e + ", '') ~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}' "
                + "then (nullif(" + e + ", ''))::timestamp end)";
    }


}
