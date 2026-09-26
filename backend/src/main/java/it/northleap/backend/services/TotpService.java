package it.northleap.backend.services;

import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;

@Service
public class TotpService {

    private static final int DIGITS = 6;
    private static final int PERIOD = 30; // secondi
    private static final int WINDOW = 1; // +/- un passo
    private static final SecureRandom RANDOM  = new SecureRandom();

    // 20 byte sono 160 bit, la lunghezza che RFC 4226 raccomanda per HMAC-SHA1
    public byte[] newSecret() {
        byte[] s = new byte[20];
        RANDOM.nextBytes(s);
        return s;
    }

    public int codeAt(byte[] key, long counter) {
        try {
            byte[] data = ByteBuffer.allocate(8).putLong(counter).array();
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] h = mac.doFinal(data);
            // troncamento dinamico, RFC 4226
            int offset = h[h.length - 1] & 0x0F;
            int binary = ((h[offset] & 0x7f) << 24)
                    | ((h[offset + 1] & 0xff) << 16)
                    | ((h[offset + 2] & 0xff) << 8)
                    | (h[offset + 3] & 0xff);
            return binary % 1_000_000;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA1 unavailable", e);
        }
    }

    // ritorna il contatore accettato, o -1. Non un boolean: chi chiama deve poter scrivere
    // mfaLastCounter, che è l'anti-replay
    public long verify(byte[] key, String code, Long lastCounter) {
        if (code == null || !code.matches("\\d{6}")) return -1;
        long now = Instant.now().getEpochSecond() / PERIOD;
        int given = Integer.parseInt(code);
        for (long c = now - WINDOW; c <= now + WINDOW; c++) {
            if (lastCounter != null && c <= lastCounter) continue; // replay
            if (codeAt(key, c) == given) return c;
        }
        return -1;
    }
}
