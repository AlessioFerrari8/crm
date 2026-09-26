package it.northleap.backend.services;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageConfig;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import it.northleap.backend.dtos.MfaSetupResponse;
import it.northleap.backend.entities.User;
import it.northleap.backend.exceptions.BadRequestException;
import it.northleap.backend.exceptions.NotFoundException;
import it.northleap.backend.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MfaService {
    private final UserRepository userRepository;
    private final TotpService totpService;
    private final SecretCipher secretCipher;
    private final PasswordEncoder passwordEncoder;

    private static final int RECOVERY_CODES = 10;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ISSUER = "NorthLeap";

    // passo 1: genera e salva il segreto, ma mfaEnabled resta false. Attivare qui
    // chiuderebbe fuori chi ha inquadrato male il QR
    @Transactional
    public MfaSetupResponse setup(UUID userId) {
        User user = user(userId);
        if (user.isMfaEnabled()) {
            throw new BadRequestException("MFA is already enabled");
        }
        byte[] secret = totpService.newSecret();
        user.setMfaSecret(secretCipher.encryptBytes(secret));
        user.setMfaLastCounter(null);
        userRepository.save(user);

        String base32 = Base32.encode(secret);
        return new MfaSetupResponse(base32, qrDataUri(otpauthUri(user, base32)));
    }

    // solo un codice valido accende mfaEnabled. I dieci codici di recupero si vedono qui e mai più
    // in chiaro una volta, in hash sul db
    @Transactional
    public List<String> activate(UUID userId, String code) {
        User user = user(userId);
        if (user.getMfaSecret() == null) {
            throw new BadRequestException("Start the MFA setup first");
        }
        long counter = totpService.verify(
                secretCipher.decryptBytes(user.getMfaSecret()), code, user.getMfaLastCounter());

        if (counter < 0) {
            throw new BadRequestException("Invalid code");
        }

        List<String> plain = new ArrayList<>();
        List<String> hashed = new ArrayList<>();

        for (int i = 0; i < RECOVERY_CODES; i++) {
            byte[] raw = new byte[5]; // 10 caratteri esadecimali
            RANDOM.nextBytes(raw);
            String c = HexFormat.of().formatHex(raw);
            plain.add(c);
            hashed.add(HashUtil.sha256Hex(c));
        }

        user.setMfaEnabled(true);
        user.setMfaLastCounter(counter);
        user.setMfaRecoveryCodes(hashed);
        userRepository.save(user);
        return plain;
    }

    // la password corrente è obbligatoria, un cookie rubato non deve poter spegnere il secondo fattore
    @Transactional
    public void disable(UUID userId, String currentPassword) {
        User user = user(userId);
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BadRequestException("Invalid credentials");
        }
        user.setMfaEnabled(false);
        user.setMfaSecret(null);
        user.setMfaRecoveryCodes(null);
        user.setMfaLastCounter(null);
        userRepository.save(user);
    }

    // il login o il codice dell'app o uno dei dieci di recupero, monouso
    @Transactional
    public void assertSecondFactor(User user, String code, String recoveryCode) {
        if (recoveryCode != null && !recoveryCode.isBlank()) {
            String hash = HashUtil.sha256Hex(recoveryCode.trim().toLowerCase());
            List<String> left = user.getMfaRecoveryCodes();
            if (left == null || !left.remove(hash)) {
                throw new BadRequestException("Invalid recovery code");
            }
            user.setMfaRecoveryCodes(new ArrayList<>(left)); // jsonb nuovo
            userRepository.save(user);
            return;
        }

        long counter = totpService.verify(
                secretCipher.decryptBytes(user.getMfaSecret()), code,
                user.getMfaLastCounter());
        if (counter < 0) {
            throw new BadRequestException("Invalid code");
        }
        // anti-replay: da qui in poi quel contatore è bruciato. Senza, un codice letto sopra la spalla
        // resta valido per i suoi 30 secondi
        user.setMfaLastCounter(counter);
        userRepository.save(user);
    }

    private User user(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> new NotFoundException("User not found"));
    }

    // il label ripete l'issuer apposta: le app vecchie leggono il label, quelle nuove il parametro
    // issuer, e senza uno dei due l'utente si trova una voce chiamata "mario@rossi.it" senza sapere
    // di quale servizio sia
    private String otpauthUri(User user, String base32) {
        String label = URLEncoder.encode(ISSUER + ":" + user.getEmail(), StandardCharsets.UTF_8);

        return "otpauth://totp/" + label + "?secret=" + base32 + "&issuer=" + ISSUER + "&digits=6&period=30";
    }

    private String qrDataUri(String uri) {
        try {
            BitMatrix matrix = new MultiFormatWriter()
                    .encode(uri, BarcodeFormat.QR_CODE, 240, 240);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException("Cannot render the MFA QR code", e);
        }
    }
}
