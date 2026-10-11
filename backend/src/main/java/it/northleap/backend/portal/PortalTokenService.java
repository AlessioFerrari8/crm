package it.northleap.backend.portal;


import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.util.Date;

// segreto separato (JWT_PORTAL_SECRET, senza default): con lo stesso segreto degli interni, un token del portale
// sarebbe spendibile sulle API interne appena un filtro dimentica un controllo
@Service
public class PortalTokenService {

    public static final String COOKIE = "portal_access_token";
    private static final Duration TTL = Duration.ofHours(8);

    @Value("${app.portal.jwt-secret}")
    private String secret;

    @Value("${app.security.cookie-secure:false}")
    private boolean cookieSecure;

    public String issue(PortalUser u) {
        return Jwts.builder()
                .subject(u.getId().toString())
                .claim("typ", "portal")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + TTL.toMillis()))
                .signWith(key(), Jwts.SIG.HS256)
                .compact();
    }

    // firma, scadenza e tipo: il claim si verifica esplicitamente, costa una riga
    public Claims parse(String token) {
        Claims c = Jwts.parser().verifyWith(key()).build().parseSignedClaims(token).getPayload();
        if (!"portal".equals(c.get("typ", String.class))) throw new IllegalArgumentException("Not a portal token");
        return c;
    }

    // Path=/api/portal: il browser non manda nemmeno il cookie del portale alle API interne
    public ResponseCookie cookie(String token) {
        return ResponseCookie.from(COOKIE, token).httpOnly(true).secure(cookieSecure).sameSite("Lax")
                .path("/api/portal").maxAge(TTL).build();
    }

    public ResponseCookie clear() {
        return ResponseCookie.from(COOKIE, "").httpOnly(true).secure(cookieSecure).sameSite("Lax")
                .path("/api/portal").maxAge(Duration.ZERO).build();
    }

    private SecretKey key() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }
}
