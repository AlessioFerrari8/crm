package it.northleap.backend.services;

import it.northleap.backend.dtos.QueryRecordsDto;
import it.northleap.backend.entities.ObjectType;
import it.northleap.backend.entities.PermScope;
import it.northleap.backend.entities.Record;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// Esegue contro Postgres, via EntityManager, le query compilate da RecordFilterCompiler (la
// parte pura/testabile è lì). Qui restano solo l'assemblaggio delle condizioni su colonne native
// (status/q/scope) e l'esecuzione native query con binding parametrico.
@Service
@RequiredArgsConstructor
public class RecordQueryService {

    private final RecordFilterCompiler filterCompiler;

    @PersistenceContext
    private EntityManager entityManager;

    public record QueryResult(List<Record> items, long total, int page, int pageSize) {
    }

    public QueryResult query(ObjectType obj, QueryRecordsDto q, PermScope scope, UUID ownerScopeId) {
        int page = (q.getPage() != null && q.getPage() > 0) ? q.getPage() : 1;
        int pageSize = (q.getPageSize() != null) ? Math.min(q.getPageSize(), 200) : 50;

        StringBuilder where = new StringBuilder("object_type_id = ? AND is_deleted = false");
        List<Object> params = new ArrayList<>();
        params.add(obj.getId());

        if (q.getStatus() != null && !q.getStatus().isBlank()) {
            where.append(" AND status = ?");
            params.add(q.getStatus());
        }
        if (q.getQ() != null && !q.getQ().isBlank()) {
            where.append(" AND title ILIKE ? ESCAPE '\\'");
            params.add("%" + escapeLike(q.getQ()) + "%");
        }
        if (scope == PermScope.OWN && ownerScopeId != null) {
            where.append(" AND owner_id = ?");
            params.add(ownerScopeId);
        }
        String advanced = filterCompiler.compileWhere(q.getFilter(), obj, params);
        if (advanced != null && !advanced.isBlank()) {
            where.append(" AND ").append(advanced);
        }

        String orderBy = filterCompiler.compileOrderBy(q.getSort(), obj);
        String whereClause = where.toString();

        String selectSql = "SELECT * FROM record WHERE " + whereClause + " ORDER BY " + orderBy + " LIMIT ? OFFSET ?";
        List<Object> selectParams = new ArrayList<>(params);
        selectParams.add(pageSize);
        selectParams.add((long) (page - 1) * pageSize);

        Query selectQuery = entityManager.createNativeQuery(selectSql, Record.class);
        bindParams(selectQuery, selectParams);
        @SuppressWarnings("unchecked")
        List<Record> items = selectQuery.getResultList();

        String countSql = "SELECT COUNT(*) FROM record WHERE " + whereClause;
        Query countQuery = entityManager.createNativeQuery(countSql);
        bindParams(countQuery, params);
        long total = ((Number) countQuery.getSingleResult()).longValue();

        return new QueryResult(items, total, page, pageSize);
    }

    // esiste un altro record (non cancellato) dello stesso tipo con lo stesso valore per il
    // campo? Enforcement di FieldDef.isUnique (RecordsService). Confronto testuale su data->>key:
    // esatto per i tipi su cui "unico" ha senso (TEXT/EMAIL/VARCHAR/PHONE/...); anche la chiave
    // viaggia come parametro JDBC, zero testo non validato nella SQL.
    public boolean hasDuplicateValue(UUID objectTypeId, String fieldKey, String value, UUID excludeId) {
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) FROM record WHERE object_type_id = ? AND is_deleted = false AND data->>? = ?");
        List<Object> params = new ArrayList<>(List.of(objectTypeId, fieldKey, value));
        if (excludeId != null) {
            sql.append(" AND id <> ?");
            params.add(excludeId);
        }
        Query query = entityManager.createNativeQuery(sql.toString());
        bindParams(query, params);
        return ((Number) query.getSingleResult()).longValue() > 0;
    }

    // primo record (non cancellato) del tipo con quel valore testuale per il campo: usato dalle
    // integrazioni per l'upsert via ID esterno (es. Cal.com booking uid -> calendar_event.externalId)
    public Optional<UUID> findIdByFieldValue(UUID objectTypeId, String fieldKey, String value) {
        Query query = entityManager.createNativeQuery(
                "SELECT CAST(id AS text) FROM record WHERE object_type_id = ? AND is_deleted = false AND data->>? = ? LIMIT 1");
        bindParams(query, List.of(objectTypeId, fieldKey, value));
        @SuppressWarnings("unchecked")
        List<String> ids = query.getResultList();
        return ids.isEmpty() ? Optional.empty() : Optional.of(UUID.fromString(ids.get(0)));
    }

    private void bindParams(Query query, List<Object> params) {
        for (int i = 0; i < params.size(); i++) {
            query.setParameter(i + 1, params.get(i));
        }
    }

    private String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
