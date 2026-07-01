package it.northleap.backend.services;

import it.northleap.backend.dtos.FilterGroup;
import it.northleap.backend.dtos.SortSpec;
import it.northleap.backend.entities.FieldDef;
import it.northleap.backend.entities.FieldType;
import it.northleap.backend.entities.ObjectType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecordFilterCompilerTest {

    private RecordFilterCompiler compiler;
    private ObjectType obj;

    @BeforeEach
    void setUp() {
        compiler = new RecordFilterCompiler();
        obj = new ObjectType();
        obj.setFields(List.of(
                field("amount", FieldType.NUMBER),
                field("dueDate", FieldType.DATE),
                field("active", FieldType.BOOLEAN),
                field("name", FieldType.TEXT)
        ));
    }

    private FieldDef field(String key, FieldType type) {
        FieldDef f = new FieldDef();
        f.setKey(key);
        f.setType(type);
        return f;
    }

    private Map<String, Object> cond(String field, String op, Object value) {
        Map<String, Object> m = new java.util.HashMap<>();
        m.put("field", field);
        m.put("op", op);
        m.put("value", value);
        return m;
    }

    private Map<String, Object> condBetween(String field, Object value, Object value2) {
        Map<String, Object> m = cond(field, "between", value);
        m.put("value2", value2);
        return m;
    }

    private FilterGroup group(String combinator, Object... conditions) {
        FilterGroup g = new FilterGroup();
        g.setCombinator(combinator);
        g.setConditions(new ArrayList<>(List.of(conditions)));
        return g;
    }

    @Test
    void eqOnDynamicNumericFieldAppliesNumericCast() {
        List<Object> params = new ArrayList<>();
        String sql = compiler.compileWhere(group("and", cond("amount", "eq", 500)), obj, params);

        assertTrue(sql.contains("(data->>'amount')::numeric = ?::numeric"));
        assertEquals(List.of(500), params);
    }

    @Test
    void dateFieldUsesTimestamptzCast() {
        List<Object> params = new ArrayList<>();
        String sql = compiler.compileWhere(group("and", cond("dueDate", "gt", "2026-01-01")), obj, params);

        assertTrue(sql.contains("::timestamptz"));
    }

    @Test
    void booleanFieldUsesBooleanCast() {
        List<Object> params = new ArrayList<>();
        String sql = compiler.compileWhere(group("and", cond("active", "eq", true)), obj, params);

        assertTrue(sql.contains("::boolean"));
    }

    @Test
    void textFieldHasNoCast() {
        List<Object> params = new ArrayList<>();
        String sql = compiler.compileWhere(group("and", cond("name", "eq", "Acme")), obj, params);

        assertTrue(sql.contains("(data->>'name') = ?"));
        assertFalse(sql.contains("(data->>'name')::"));
    }

    @Test
    void nativeColumnConditionUsesColumnDirectlyNoCast() {
        List<Object> params = new ArrayList<>();
        String sql = compiler.compileWhere(group("and", cond("ownerId", "eq", "user-1")), obj, params);

        assertTrue(sql.contains("owner_id = ?"));
        assertFalse(sql.contains("data->>"));
    }

    @Test
    void allComparisonOperatorsProduceExpectedSql() {
        record Case(String op, String expectedFragment) {
        }
        List<Case> cases = List.of(
                new Case("ne", "<> ?"),
                new Case("gt", "> ?"),
                new Case("gte", ">= ?"),
                new Case("lt", "< ?"),
                new Case("lte", "<= ?")
        );
        for (Case c : cases) {
            List<Object> params = new ArrayList<>();
            String sql = compiler.compileWhere(group("and", cond("amount", c.op(), 10)), obj, params);
            assertTrue(sql.contains(c.expectedFragment()), "operator " + c.op() + " -> " + sql);
            assertEquals(List.of(10), params);
        }
    }

    @Test
    void betweenBindsBothValuesAndsTwoComparisons() {
        List<Object> params = new ArrayList<>();
        String sql = compiler.compileWhere(group("and", condBetween("amount", 10, 100)), obj, params);

        assertTrue(sql.contains(">= ?::numeric"));
        assertTrue(sql.contains("<= ?::numeric"));
        assertTrue(sql.contains(" AND "));
        assertEquals(List.of(10, 100), params);
    }

    @Test
    void inBindsEachValueAsPlaceholder() {
        List<Object> params = new ArrayList<>();
        String sql = compiler.compileWhere(group("and", cond("amount", "in", List.of(1, 2, 3))), obj, params);

        assertTrue(sql.contains("IN (?::numeric, ?::numeric, ?::numeric)"));
        assertEquals(List.of(1, 2, 3), params);
    }

    @Test
    void ninWrapsInNot() {
        List<Object> params = new ArrayList<>();
        String sql = compiler.compileWhere(group("and", cond("amount", "nin", List.of(1, 2))), obj, params);

        assertTrue(sql.contains("NOT ("));
        assertTrue(sql.contains("IN (?::numeric, ?::numeric)"));
    }

    @Test
    void containsStartsWithEndsWithUseEscapedIlike() {
        List<Object> params1 = new ArrayList<>();
        String containsSql = compiler.compileWhere(group("and", cond("name", "contains", "100%_off")), obj, params1);
        assertTrue(containsSql.contains("ILIKE ? ESCAPE '\\'"));
        assertEquals("%100\\%\\_off%", params1.get(0));

        List<Object> params2 = new ArrayList<>();
        String startsSql = compiler.compileWhere(group("and", cond("name", "startsWith", "Acme")), obj, params2);
        assertEquals("Acme%", params2.get(0));
        assertTrue(startsSql.contains("ILIKE"));

        List<Object> params3 = new ArrayList<>();
        String endsSql = compiler.compileWhere(group("and", cond("name", "endsWith", "Corp")), obj, params3);
        assertEquals("%Corp", params3.get(0));
    }

    @Test
    void isEmptyAndIsNotEmptyAddNoParams() {
        List<Object> params = new ArrayList<>();
        String sql = compiler.compileWhere(group("and", cond("name", "isEmpty", null)), obj, params);
        assertTrue(sql.contains("IS NULL"));
        assertTrue(params.isEmpty());

        List<Object> params2 = new ArrayList<>();
        String sql2 = compiler.compileWhere(group("and", cond("name", "isNotEmpty", null)), obj, params2);
        assertTrue(sql2.contains("IS NOT NULL"));
        assertTrue(params2.isEmpty());
    }

    @Test
    void nestedGroupsCombineWithCorrectJoinerAndParens() {
        Map<String, Object> nestedAnd = Map.of(
                "combinator", "and",
                "conditions", List.of(cond("amount", "gt", 100), cond("active", "eq", true))
        );
        List<Object> params = new ArrayList<>();
        String sql = compiler.compileWhere(
                group("or", cond("name", "eq", "Acme"), nestedAnd), obj, params);

        assertTrue(sql.contains(" OR "));
        assertTrue(sql.contains(" AND "));
        // il gruppo annidato deve essere tra parentesi
        assertTrue(sql.matches(".*\\(\\(data->>'amount'\\)::numeric > \\?::numeric AND \\(data->>'active'\\)::boolean = \\?::boolean\\).*"));
        assertEquals(List.of("Acme", 100, true), params);
    }

    @Test
    void unsafeFieldNameIsDropped() {
        List<Object> params = new ArrayList<>();
        String sql = compiler.compileWhere(group("and", cond("amount'; DROP TABLE record;--", "eq", 1)), obj, params);

        // la condizione viene scartata silenziosamente: nessuna clausola, nessun parametro
        assertNull(sql);
        assertTrue(params.isEmpty());
    }

    @Test
    void unsafeFieldNameWithSpaceIsDropped() {
        List<Object> params = new ArrayList<>();
        String sql = compiler.compileWhere(group("and", cond("amount field", "eq", 1)), obj, params);

        assertNull(sql);
    }

    @Test
    void unknownFieldNotInObjectTypeIsDropped() {
        List<Object> params = new ArrayList<>();
        String sql = compiler.compileWhere(group("and", cond("doesNotExist", "eq", 1)), obj, params);

        assertNull(sql);
        assertTrue(params.isEmpty());
    }

    @Test
    void nullFilterCompilesToNoClause() {
        List<Object> params = new ArrayList<>();
        String sql = compiler.compileWhere(null, obj, params);

        assertNull(sql);
    }

    @Test
    void orderByNativeFieldUsesColumnAndDirection() {
        SortSpec s = new SortSpec();
        s.setField("title");
        s.setDir("asc");

        String sql = compiler.compileOrderBy(List.of(s), obj);

        assertEquals("title ASC", sql);
    }

    @Test
    void orderByDynamicNumericFieldUsesJsonExprWithCastAndNullsLast() {
        SortSpec s = new SortSpec();
        s.setField("amount");
        s.setDir("desc");

        String sql = compiler.compileOrderBy(List.of(s), obj);

        assertEquals("(data->>'amount')::numeric DESC NULLS LAST", sql);
    }

    @Test
    void orderByDynamicTextFieldHasNoCast() {
        SortSpec s = new SortSpec();
        s.setField("name");
        s.setDir("asc");

        String sql = compiler.compileOrderBy(List.of(s), obj);

        assertEquals("(data->>'name') ASC NULLS LAST", sql);
    }

    @Test
    void orderByUnknownFieldFallsBackToUpdatedAt() {
        SortSpec s = new SortSpec();
        s.setField("nonEsiste");
        s.setDir("desc");

        String sql = compiler.compileOrderBy(List.of(s), obj);

        assertEquals("updated_at DESC", sql);
    }

    @Test
    void orderByInjectionAttemptFallsBackToUpdatedAt() {
        SortSpec s = new SortSpec();
        s.setField("amount; DROP TABLE record--");
        s.setDir("asc");

        String sql = compiler.compileOrderBy(List.of(s), obj);

        assertEquals("updated_at ASC", sql);
    }

    @Test
    void orderByEmptyListFallsBackToUpdatedAtDesc() {
        String sql = compiler.compileOrderBy(List.of(), obj);
        assertEquals("updated_at DESC", sql);

        String sqlNull = compiler.compileOrderBy(null, obj);
        assertEquals("updated_at DESC", sqlNull);
    }
}
