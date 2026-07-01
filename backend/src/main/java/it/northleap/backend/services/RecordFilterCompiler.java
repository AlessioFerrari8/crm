package it.northleap.backend.services;

import it.northleap.backend.dtos.FilterGroup;
import it.northleap.backend.dtos.SortSpec;
import it.northleap.backend.entities.FieldDef;
import it.northleap.backend.entities.FieldType;
import it.northleap.backend.entities.ObjectType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

// Porting di filter-builder.ts, adattato a Postgres: l'originale filtra il JSON via
// JSON_EXTRACT/path Prisma su MySQL (coercizione di tipo automatica). Postgres ->>'campo'
// ritorna sempre text, quindi confronti su numeri/date andrebbero in ordine lessicografico se
// non castati esplicitamente: qui si risale al FieldType del FieldDef e si applica ::numeric /
// ::timestamptz / ::boolean. Stesso set di operatori e stessa whitelist SAFE_KEY
// dell'originale (anti-injection nel path JSON), query sempre con parametri JDBC (mai
// concatenazione di valori in SQL — solo il nome campo, già validato, e il cast vengono
// interpolati nel testo). Estratto da RecordQueryService per essere testabile senza DB
// (compilazione SQL pura, l'esecuzione via EntityManager resta in RecordQueryService).
@Service
public class RecordFilterCompiler {

    private static final Pattern SAFE_KEY = Pattern.compile("^[a-zA-Z0-9_]{1,64}$");

    private static final Map<String, String> NATIVE_COLUMNS = Map.of(
            "title", "title",
            "status", "status",
            "ownerId", "owner_id",
            "createdAt", "created_at",
            "updatedAt", "updated_at"
    );

    public String compileWhere(FilterGroup filter, ObjectType obj, List<Object> params) {
        if (filter == null) {
            return null;
        }
        return compileGroup(filter, obj, params);
    }

    public String compileOrderBy(List<SortSpec> sorts, ObjectType obj) {
        if (sorts == null || sorts.isEmpty()) {
            return "updated_at DESC";
        }
        return sorts.stream()
                .map(s -> orderClause(s.getField(), "desc".equalsIgnoreCase(s.getDir()) ? "DESC" : "ASC", obj))
                .collect(Collectors.joining(", "));
    }

    // colonna nativa, oppure campo dinamico jsonb col cast del suo FieldType (stessa whitelist
    // SAFE_KEY + esistenza del FieldDef del WHERE: mai testo non validato nell'ORDER BY).
    // NULLS LAST sui dinamici: i record senza il campo (->> ritorna NULL) vanno in fondo,
    // altrimenti in DESC Postgres li metterebbe per primi. Migliora l'originale, che ordinava
    // solo le colonne native e faceva fallback silenzioso su updated_at per tutto il resto.
    private String orderClause(String field, String dir, ObjectType obj) {
        if (field == null || !SAFE_KEY.matcher(field).matches()) {
            return "updated_at " + dir;
        }
        String column = NATIVE_COLUMNS.get(field);
        if (column != null) {
            return column + " " + dir;
        }
        FieldDef fieldDef = obj != null ? findField(obj, field) : null;
        if (fieldDef == null) {
            return "updated_at " + dir;
        }
        return "(data->>'" + field + "')" + castFor(fieldDef.getType()) + " " + dir + " NULLS LAST";
    }

    @SuppressWarnings("unchecked")
    private String compileGroup(Object node, ObjectType obj, List<Object> params) {
        String combinator = extractCombinator(node);
        List<Object> conditions = (List<Object>) extractConditions(node);
        if (combinator == null || conditions == null || conditions.isEmpty()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (Object c : conditions) {
            String compiled = isGroup(c) ? compileGroup(c, obj, params) : compileSingleCondition(c, obj, params);
            if (compiled != null && !compiled.isBlank()) {
                parts.add(compiled);
            }
        }
        if (parts.isEmpty()) {
            return null;
        }
        String joiner = "or".equalsIgnoreCase(combinator) ? " OR " : " AND ";
        return "(" + String.join(joiner, parts) + ")";
    }

    private boolean isGroup(Object node) {
        if (node instanceof FilterGroup) {
            return true;
        }
        return node instanceof Map<?, ?> m && m.containsKey("combinator");
    }

    private String extractCombinator(Object node) {
        if (node instanceof FilterGroup g) {
            return g.getCombinator();
        }
        if (node instanceof Map<?, ?> m) {
            Object c = m.get("combinator");
            return c != null ? String.valueOf(c) : null;
        }
        return null;
    }

    private Object extractConditions(Object node) {
        if (node instanceof FilterGroup g) {
            return g.getConditions();
        }
        if (node instanceof Map<?, ?> m) {
            return m.get("conditions");
        }
        return null;
    }

    private String compileSingleCondition(Object node, ObjectType obj, List<Object> params) {
        if (!(node instanceof Map<?, ?> m)) {
            return null;
        }
        Object field = m.get("field");
        Object op = m.get("op");
        Object value = m.get("value");
        Object value2 = m.get("value2");
        if (!(field instanceof String f) || !(op instanceof String o)) {
            return null;
        }
        return compileCondition(f, o, value, value2, obj, params);
    }

    private String compileCondition(String field, String op, Object value, Object value2, ObjectType obj, List<Object> params) {
        if (!SAFE_KEY.matcher(field).matches()) {
            return null;
        }
        String nativeColumn = NATIVE_COLUMNS.get(field);
        String textExpr;
        String cast;
        if (nativeColumn != null) {
            textExpr = nativeColumn;
            cast = "";
        } else {
            FieldDef fieldDef = findField(obj, field);
            if (fieldDef == null) {
                return null;
            }
            textExpr = "(data->>'" + field + "')";
            cast = castFor(fieldDef.getType());
        }
        String typedExpr = textExpr + cast;

        return switch (op) {
            case "eq" -> {
                params.add(value);
                yield typedExpr + " = ?" + cast;
            }
            case "ne" -> {
                params.add(value);
                yield typedExpr + " <> ?" + cast;
            }
            case "contains" -> {
                params.add("%" + escapeLike(String.valueOf(value)) + "%");
                yield textExpr + " ILIKE ? ESCAPE '\\'";
            }
            case "startsWith" -> {
                params.add(escapeLike(String.valueOf(value)) + "%");
                yield textExpr + " ILIKE ? ESCAPE '\\'";
            }
            case "endsWith" -> {
                params.add("%" + escapeLike(String.valueOf(value)));
                yield textExpr + " ILIKE ? ESCAPE '\\'";
            }
            case "gt" -> {
                params.add(value);
                yield typedExpr + " > ?" + cast;
            }
            case "gte" -> {
                params.add(value);
                yield typedExpr + " >= ?" + cast;
            }
            case "lt" -> {
                params.add(value);
                yield typedExpr + " < ?" + cast;
            }
            case "lte" -> {
                params.add(value);
                yield typedExpr + " <= ?" + cast;
            }
            case "between" -> {
                params.add(value);
                params.add(value2);
                yield "(" + typedExpr + " >= ?" + cast + " AND " + typedExpr + " <= ?" + cast + ")";
            }
            case "in" -> buildInClause(typedExpr, cast, value, params, false);
            case "nin" -> buildInClause(typedExpr, cast, value, params, true);
            case "isEmpty" -> textExpr + " IS NULL";
            case "isNotEmpty" -> textExpr + " IS NOT NULL";
            default -> null;
        };
    }

    private String buildInClause(String typedExpr, String cast, Object value, List<Object> params, boolean negate) {
        List<?> values = (value instanceof List<?> l) ? l : List.of(value);
        if (values.isEmpty()) {
            return negate ? "TRUE" : "FALSE";
        }
        String placeholders = values.stream().map(v -> {
            params.add(v);
            return "?" + cast;
        }).collect(Collectors.joining(", "));
        String clause = typedExpr + " IN (" + placeholders + ")";
        return negate ? "NOT (" + clause + ")" : clause;
    }

    private String castFor(FieldType type) {
        return switch (type) {
            case NUMBER, INTEGER, BIGINT, DECIMAL, FLOAT, PERCENT, CURRENCY, RATING, DURATION, AUTONUMBER, TIMESTAMP ->
                    "::numeric";
            case DATE, DATETIME, TIME -> "::timestamptz";
            case BOOLEAN -> "::boolean";
            default -> "";
        };
    }

    private FieldDef findField(ObjectType obj, String key) {
        return obj.getFields().stream().filter(f -> f.getKey().equals(key)).findFirst().orElse(null);
    }

    private String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
