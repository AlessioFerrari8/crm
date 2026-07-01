package it.northleap.backend.services;

import it.northleap.backend.entities.FieldDef;
import it.northleap.backend.entities.FieldType;
import it.northleap.backend.exceptions.RecordValidationException;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// Porting di record-validator.ts: valida/normalizza il payload `data` di un Record contro i
// FieldDef del suo ObjectType. Lavora su Object/instanceof invece che su tipi forti perché la
// "forma" del JSON dipende dai FieldDef, che sono dinamici per definizione (vedi 03-MOTORE-DINAMICO.md).
@Service
public class RecordValidator {

    private static final List<String> TITLE_KEYS = List.of("title", "name", "subject", "number");

    public Map<String, Object> validate(List<FieldDef> fields, Map<String, Object> rawData) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (FieldDef f : fields) {
            Object v = rawData.get(f.getKey());
            if (isEmpty(v) && f.getDefaultValue() != null) {
                v = f.getDefaultValue();
            }
            if (isEmpty(v)) {
                if (f.isRequired()) {
                    throw new RecordValidationException("Campo obbligatorio mancante: " + f.getLabel());
                }
                continue;
            }
            out.put(f.getKey(), coerce(f, v));
        }
        return out;
    }

    public String deriveTitle(List<FieldDef> fields, Map<String, Object> data) {
        for (String key : TITLE_KEYS) {
            Object val = data.get(key);
            if (!isEmpty(val)) {
                return String.valueOf(val);
            }
        }
        for (FieldDef f : fields) {
            if ((f.getType() == FieldType.TEXT || f.getType() == FieldType.VARCHAR)) {
                Object val = data.get(f.getKey());
                if (!isEmpty(val)) {
                    return String.valueOf(val);
                }
            }
        }
        Object firstName = data.get("firstName");
        Object lastName = data.get("lastName");
        if (firstName != null || lastName != null) {
            String joined = Stream.of(firstName, lastName)
                    .filter(Objects::nonNull)
                    .map(String::valueOf)
                    .collect(Collectors.joining(" "));
            if (!joined.isBlank()) {
                return joined;
            }
        }
        return "Senza titolo";
    }

    public String deriveStatus(List<FieldDef> fields, Map<String, Object> data) {
        FieldDef statusField = fields.stream()
                .filter(f -> f.getType() == FieldType.STATUS)
                .findFirst()
                .orElseGet(() -> fields.stream().filter(f -> f.getKey().equals("status")).findFirst().orElse(null));
        if (statusField == null) {
            return null;
        }
        Object val = data.get(statusField.getKey());
        return val != null ? String.valueOf(val) : null;
    }

    private boolean isEmpty(Object v) {
        return v == null || (v instanceof String s && s.isEmpty());
    }

    private Object coerce(FieldDef f, Object v) {
        return switch (f.getType()) {
            case TEXT, VARCHAR, LONGTEXT, RICHTEXT, PHONE, ICON -> str(f, v);
            case COLOR -> coerceColor(f, v);
            case NUMBER, DECIMAL, FLOAT, CURRENCY, PERCENT, DURATION, RATING -> num(f, v);
            case INTEGER, BIGINT, AUTONUMBER, TIMESTAMP -> integerNum(f, v);
            case BOOLEAN -> coerceBoolean(v);
            case EMAIL -> coerceEmail(f, v);
            case URL, IMAGE -> coerceUrl(f, v);
            case SELECT, STATUS -> coerceSelect(f, v);
            case MULTISELECT, TAGS -> coerceList(v);
            case RELATION, LOOKUP -> coerceRelation(f, v);
            case DATE, DATETIME, TIME -> coerceDate(f, v);
            case GEO -> coerceGeo(f, v);
            case ADDRESS, JSON -> v;
            default -> v;
        };
    }

    private double parseNumber(FieldDef f, Object v) {
        double n;
        try {
            n = (v instanceof Number num) ? num.doubleValue() : Double.parseDouble(String.valueOf(v));
        } catch (NumberFormatException e) {
            throw new RecordValidationException(f.getLabel() + ": numero non valido");
        }
        if (Double.isNaN(n)) {
            throw new RecordValidationException(f.getLabel() + ": numero non valido");
        }
        return n;
    }

    private Double num(FieldDef f, Object v) {
        double n = parseNumber(f, v);
        if (f.getMin() != null && n < f.getMin()) {
            throw new RecordValidationException(f.getLabel() + ": minimo " + f.getMin());
        }
        if (f.getMax() != null && n > f.getMax()) {
            throw new RecordValidationException(f.getLabel() + ": massimo " + f.getMax());
        }
        return n;
    }

    private Long integerNum(FieldDef f, Object v) {
        double n = parseNumber(f, v);
        if (n != Math.rint(n)) {
            throw new RecordValidationException(f.getLabel() + ": deve essere intero");
        }
        long l = (long) n;
        if (f.getMin() != null && l < f.getMin()) {
            throw new RecordValidationException(f.getLabel() + ": minimo " + f.getMin());
        }
        if (f.getMax() != null && l > f.getMax()) {
            throw new RecordValidationException(f.getLabel() + ": massimo " + f.getMax());
        }
        return l;
    }

    private String str(FieldDef f, Object v) {
        String s = String.valueOf(v);
        if (f.getMin() != null && s.length() < f.getMin()) {
            throw new RecordValidationException(f.getLabel() + ": min " + f.getMin().intValue() + " caratteri");
        }
        if (f.getMax() != null && s.length() > f.getMax()) {
            throw new RecordValidationException(f.getLabel() + ": max " + f.getMax().intValue() + " caratteri");
        }
        // SafeRegex: match con deadline, una regex admin patologica (ReDoS) non blocca il thread
        if (f.getPattern() != null && !SafeRegex.find(f.getPattern(), s)) {
            throw new RecordValidationException(f.getLabel() + ": formato non valido");
        }
        return s;
    }

    private String coerceColor(FieldDef f, Object v) {
        String s = String.valueOf(v);
        if (!s.matches("^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$")) {
            throw new RecordValidationException(f.getLabel() + ": colore hex non valido");
        }
        return s;
    }

    private Boolean coerceBoolean(Object v) {
        if (v instanceof String s) {
            return s.equals("true") || s.equals("1");
        }
        if (v instanceof Boolean b) {
            return b;
        }
        if (v instanceof Number n) {
            return n.doubleValue() != 0;
        }
        return v != null;
    }

    private String coerceEmail(FieldDef f, Object v) {
        String s = String.valueOf(v);
        if (!s.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new RecordValidationException(f.getLabel() + ": email non valida");
        }
        return s.toLowerCase();
    }

    private String coerceUrl(FieldDef f, Object v) {
        String s = String.valueOf(v);
        try {
            URI uri = new URI(s);
            if (!uri.isAbsolute()) {
                throw new URISyntaxException(s, "manca lo schema");
            }
        } catch (URISyntaxException e) {
            throw new RecordValidationException(f.getLabel() + ": URL non valido");
        }
        return s;
    }

    private Object coerceSelect(FieldDef f, Object v) {
        List<Map<String, Object>> opts = f.getOptions();
        if (opts != null && !opts.isEmpty()) {
            boolean found = opts.stream()
                    .anyMatch(opt -> String.valueOf(opt.get("value")).equals(String.valueOf(v)));
            if (!found) {
                throw new RecordValidationException(f.getLabel() + ": valore non ammesso");
            }
        }
        return v;
    }

    private List<?> coerceList(Object v) {
        return (v instanceof List<?> list) ? list : List.of(v);
    }

    private Object coerceRelation(FieldDef f, Object v) {
        Map<String, Object> config = f.getConfig();
        boolean multiple = config != null && Boolean.TRUE.equals(config.get("multiple"));
        if (multiple) {
            return (v instanceof List<?> list) ? list : List.of(v);
        }
        if (v instanceof List<?> list) {
            return list.isEmpty() ? null : list.get(0);
        }
        return v;
    }

    private Object coerceDate(FieldDef f, Object v) {
        String s = String.valueOf(v);
        try {
            return switch (f.getType()) {
                case DATE -> LocalDate.parse(s.length() > 10 ? s.substring(0, 10) : s).toString();
                case TIME -> LocalTime.parse(s).toString();
                default -> parseDateTime(s);
            };
        } catch (DateTimeException e) {
            throw new RecordValidationException(f.getLabel() + ": data non valida");
        }
    }

    private String parseDateTime(String s) {
        try {
            return Instant.parse(s).toString();
        } catch (DateTimeParseException ignored) {
            // prova il formato successivo
        }
        try {
            return OffsetDateTime.parse(s).toInstant().toString();
        } catch (DateTimeParseException ignored) {
            // prova il formato successivo
        }
        try {
            return LocalDateTime.parse(s).atOffset(ZoneOffset.UTC).toInstant().toString();
        } catch (DateTimeParseException e) {
            throw new DateTimeException("data non parsabile: " + s);
        }
    }

    private Map<String, Object> coerceGeo(FieldDef f, Object v) {
        if (!(v instanceof Map<?, ?> m)) {
            throw new RecordValidationException(f.getLabel() + ": coordinate {lat,lng} richieste");
        }
        Object lat = m.get("lat");
        Object lng = m.get("lng");
        if (!(lat instanceof Number) || !(lng instanceof Number)) {
            throw new RecordValidationException(f.getLabel() + ": coordinate {lat,lng} richieste");
        }
        return Map.of("lat", ((Number) lat).doubleValue(), "lng", ((Number) lng).doubleValue());
    }
}
