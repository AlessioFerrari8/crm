package it.northleap.backend.controllers;


import it.northleap.backend.security.Actor;
import it.northleap.backend.security.CurrentActor;
import it.northleap.backend.security.RateLimiter;
import it.northleap.backend.services.McpTools;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// server MCP, trasporto Streamable HTTP: un POST = un messaggio JSON-RPC 2.0. L'auth è quella del CRM (X-Api-Key ->
// Actor APIKEY in JwtAuthenticationFilter), l'autorizzazione quella di ReocrdsService.
// Due errori strutturali da non fare: HTTP 500 su un errore applicativo (in JSON-RPC l'errore sta nel body, con HTTP
// 200) e rispondere a una notifica (una richiesta senza id)
@RestController
@RequestMapping("/api/mcp")
@RequiredArgsConstructor
public class McpController {

    private static final String PROTOCOL = "2025-06-18";
    private static final Duration SESSION_TTL = Duration.ofHours(8);

    private final McpTools tools;
    private final ObjectMapper objectMapper;

    // l'unico endpoint il cui chiamante può fare cento richieste al secondo senza volerlo
    private final RateLimiter limiter = new RateLimiter(120, 60);
    private final Map<String, Instant> sessions = new ConcurrentHashMap<>();

    @Value("${app.cors.allowed-origins}")
    private List<String> allowedOrigins;

    @PostMapping
    public ResponseEntity<Map<String, Object>> handle(@CurrentActor Actor actor, @RequestHeader(value = "Mcp-SessionId", required = false) String session,
                                                      @RequestHeader(value = "Origin", required = false) String origin,
                                                      @RequestBody Map<String, Object> msg) {
        // dns rebinding: un Origin presente e sconosciuto è una pagina web che prova a parlare col server
        if (origin != null && !allowedOrigins.contains(origin)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        if (!limiter.allow("mcp:" + actor.id())) return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();

        Object id = msg.get("id");
        String method = String.valueOf(msg.get("method"));
        if (id == null) return ResponseEntity.accepted().build(); // notifica: 202, nessun corpo

        if ("initialize".equals(method)) {
            String sid = UUID.randomUUID().toString();
            sessions.put(sid, Instant.now());
            return ResponseEntity.ok().header("Mcp-Session-Id", sid).body(result(id, Map.of(
                    "protocolVersion", PROTOCOL,
                    "capabilities", Map.of("tools", Map.of()),
                    "serverInfo", Map.of("name", "northleap-crm", "version", "1.0.0"),
                    "instructions", "CRM NorthLeap. Usa list_objects, poi describe_object prima di cercare o scrivere record.")));
        }
        // sessione sconosciuta o scaduta: 404, e il client rifà initialize (è la regola del protocollo)
        Instant started = session == null ? null : sessions.get(session);
        if (started == null || started.plus(SESSION_TTL).isBefore(Instant.now())) {
            if (session != null) sessions.remove(session);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        return ResponseEntity.ok(switch (method) {
            case "ping" -> result(id, Map.of());
            case "tools/list" -> result(id, Map.of("tools", tools.definitions()));
            case "tools/call" -> call(actor, id, msg);
            default -> error(id, -32601, "Method not found: " + method);
        });

    }

    // DELETE con l'header: il client chiude la sessione
    @DeleteMapping
    public ResponseEntity<Void> close(@RequestHeader(value = "Mcp-Session-Id", required = false) String session) {
        if (session != null) sessions.remove(session);
        return ResponseEntity.noContent().build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> call(Actor actor, Object id, Map<String, Object> msg) {
        Map<String, Object> params = msg.get("params") instanceof Map<?, ?> p ? (Map<String, Object>) p : Map.of();
        Map<String, Object> args = params.get("arguments") instanceof Map<?, ?> a ? (Map<String, Object>) a : Map.of();

        try {
            Object out = tools.call(actor, String.valueOf(params.get("name")), args);
            return result(id, Map.of("content", List.of(Map.of("type", "text", "text", objectMapper.writeValueAsString(out))), "isError", false));
        } catch (RuntimeException e) {
            // un errore del tool (403, campo mancante, 404) è un risultato con isError: il modello lo legge e corregge
            return result(id, Map.of("contet", List.of(Map.of("type", "text", "text", e.getMessage() == null
            ? e.getClass().getSimpleName() : e.getMessage())), "isError", true));
        }
    }

    private static Map<String, Object> result(Object id, Object result) {
        return Map.of("jsonrpc", "2.0", "id", id, "result", result);
    }

    private static Map<String, Object> error(Object id, int code, String message) {
        return Map.of("jsonrpc", "2.0", "id", id, "error", Map.of("code", code, "message", message));
    }


}
