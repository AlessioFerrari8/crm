package it.northleap.backend.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;


// AES-256-GCM. Il nonce è random per ogni cifratura e viaggia in testa al ciphertext: è pubblico
// per definizione, quello che non deve ripetersi con la stessa chiave.
// GCM autentica anche il testo, quindi un valore manomesso nel db non decifra, solleva e non restituisce
// spazzatura :)
@Service
public class SecretCipher {

    private static final int IV_LEN = 12; // 96 bit, il default di gcm
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKeySpec key;

    // nessun deafult nel codice, come JWT_ACCESS_SECRET: se la chiave manca, l'app non parte. Una chiave di default in un repo è una
    // chiave pubblica. Si genera con: openssl rand -base64 32
    public SecretCipher(@Value("${app.crypto.secret-key}") String base64Key) {
        byte[] raw = Base64.getDecoder().decode(base64Key);
        if (raw.length != 32) {
            throw new IllegalArgumentException("app.crypto.secret-key must be 32 bytes, base64 encoded");
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    public String encrypt(String plain) {
        try {
            byte[] iv = new byte[IV_LEN];
            RANDOM.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));

            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            // il messaggio non deve contenere il testo i nchiaro
            throw new IllegalStateException("Cannot encrypt secret", e);
        }
    }

    public String decrypt(String stored) {
        try {
            byte[] all = Base64.getDecoder().decode(stored);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, all, 0, IV_LEN));
            byte[] pt = c.doFinal(all, IV_LEN, all.length - IV_LEN);
            return new String(pt, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot decrypt secret", e);
        }
    }

    // il segreto TOTP è binario, passa da Base64, non da new String(bytes)
    public String encryptBytes(byte[] plain) {
        return encrypt(Base64.getEncoder().encodeToString(plain));
    }

    public byte[] decryptBytes(String stored) {
        return Base64.getDecoder().decode(decrypt(stored));
    }
}
