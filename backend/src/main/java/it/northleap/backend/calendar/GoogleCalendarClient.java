package it.northleap.backend.calendar;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

// 4 chiamate a Google, niente SDK: token, refresh, calendario primario, eventi
@Component
public class GoogleCalendarClient {
    private static final String AUTH = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN = "https://oauth2.googleapis.com/token";
    private static final String API = "https://www.googleapis.com/calendar/v3";
    // solo lettura finché l'import è unidirezionale: chiedere la scrittura "per dopo" allunga la verifica di settimane
    private static final String SCOPE = "https://www.googleapis.com/auth/calendar.readonly";

    private final RestClient http = RestClient.create();

    @Value("${app.google.client-id:}")
    private String clientId;

    @Value("${app.google.client-secret:")
    private String clientSecret;

    // deve combaciare al carattere con quello registrato in console, schema e porta compresi
    @Value("${app.google.redirect-uri:}")
    private String redirectUri;

    public record Tokens(String accessToken, String refreshToken, Instant expiresAt) { }

    public record Page(List<Map<String, Object>> items, String nextPageToken, String nextSyncToken) { }

    // il syncToken è scaduto: non è un errore, è "rifai un giro completo"
    public static class SyncTokenExpired extends RuntimeException { }

    public boolean isConfigured() {
        return !clientId.isBlank() && !clientSecret.isBlank() && !redirectUri.isBlank();
    }

    // prompt=consent + access_type=offline dal primo giorno: Google dà il refresh token solo alla prima autorizzazione, e senza
    // prompt=consent chi ricollega resta senza
    public String authorizeUrl(String state) {
        return UriComponentsBuilder.fromUriString(AUTH)
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", SCOPE)
                .queryParam("prompt", "consent")
                .queryParam("state", state)
                .encode().build().toUriString();
    }

    public Tokens exchange(String code) {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("code", code);
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("redirect_uri", redirectUri);
        form.add("grant_type", "authorization_code");
        return tokens(form, null);
    }

    public Tokens refresh(String refreshToken) {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("refresh_token", refreshToken);
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("grant_type", "refresh_token");
        return tokens(form, refreshToken);
    }

    // l'id del calendario primario è l'email dell'account: niente scope openid in più
    @SuppressWarnings("unchecked")
    public String primaryCalendarId(String accessToken) {
        Map<String, Object> cal = http.get().uri(API + "/calendar/primary")
                .header("Authorization", "Bearer " + accessToken).retrieve().body(Map.class);
        return String.valueOf(cal.get("id"));
    }

    // singleEvents = true: le ricorrenze arrivano già espanse in istanze. Reimplementare le RRULE con eccezioni e
    // cancellazioni singole è un progetto a sè
    @SuppressWarnings("unchecked")
    public Page events(String accessToken, String calendarId, String syncToken, String pageToken) {
        var uri = UriComponentsBuilder.fromUriString(API + "/calendars/{cal}/events")
                .queryParam("singleEvents", "true")
                .queryParam("maxResults", "250");
        if (pageToken != null) uri.queryParam("pageToken", pageToken);
        if (syncToken != null) {
            uri.queryParam("syncToken", syncToken);
        } else {
            // primo giro: una finestra, non "tutto lo storico"
            uri.queryParam("timeMin", Instant.now().minus(30, ChronoUnit.DAYS).toString());
            uri.queryParam("timeMax", Instant.now().plus(180, ChronoUnit.DAYS).toString());
        }
        try {
            Map<String, Object> res = http.get().uri(uri.encode().buildAndExpand(calendarId).toUri())
                    .header("Authorization", "Bearer " + accessToken).retrieve().body(Map.class);
            return new Page((List<Map<String, Object>>) res.getOrDefault("items", List.of()), (String) res.get("nextPageToken"), (String) res.get("nextSyncToken"));
        } catch (HttpClientErrorException.Gone e) {
            throw new SyncTokenExpired();
        }
    }

    public void revoke(String token) {
        try {
            http.post().uri(UriComponentsBuilder.fromUriString("https://oauth2.googleapis.com/revoke")
                    .queryParam("token", token).build().toUri()).retrieve().toBodilessEntity();
        } catch (Exception ignored) {
            // già revocato dall'utente su google: va bene lo stesso
        }
    }

    @SuppressWarnings("unchecked")
    private Tokens tokens(LinkedMultiValueMap<String, String> form, String keepRefresh) {
        Map<String, Object> res = http.post().uri(TOKEN).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form).retrieve().body(Map.class);
        Object refresh = res.get("refresh_token");
        long expires = ((Number) res.getOrDefault("expires_in", 3600)).longValue();
        return new Tokens(String.valueOf(res.get("access_token")),
                refresh != null ? refresh.toString() : keepRefresh,
                // un minuto di margine: un token che scade durante il giro fa fallire metà pagine
                Instant.now().plusSeconds(expires - 60));
    }



}
