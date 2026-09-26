package it.northleap.backend.services;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;


class TotpServiceTest {
    private final TotpService totp = new TotpService();

    // RFC 6238, appendice B: segreto ASCII "12345678901234567890",
    // HMAC-SHA1, passo 30s. Il contatore e' t / 30.
    private static final byte[] KEY =
            "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    @Test
    void i_vettori_ufficiali_rfc6238() {
        assertThat(totp.codeAt(KEY, 59L / 30)).isEqualTo(287082);        // 94287082
        assertThat(totp.codeAt(KEY, 1111111109L / 30)).isEqualTo(81804); // 07081804
        assertThat(totp.codeAt(KEY, 1111111111L / 30)).isEqualTo(50471); // 14050471 -> ultime sei
    }

    @Test
    void la_finestra_e_di_un_passo_soli() {
        long now = java.time.Instant.now().getEpochSecond() / 30;
        String ok = String.format("%06d", totp.codeAt(KEY, now - 1));
        String tooOld = String.format("%06d", totp.codeAt(KEY, now - 3));

        assertThat(totp.verify(KEY, ok, null)).isEqualTo(now - 1);
        assertThat(totp.verify(KEY, tooOld, null)).isEqualTo(-1);
    }

    @Test
    void lo_stesso_codice_non_si_rigioca() {
        long now = java.time.Instant.now().getEpochSecond() / 30;
        String code = String.format("%06d", totp.codeAt(KEY, now));

        assertThat(totp.verify(KEY, code, null)).isEqualTo(now);
        // secondo giro con lastCounter = now: rifiutato
        assertThat(totp.verify(KEY, code, now)).isEqualTo(-1);
    }

    @Test
    void un_codice_non_numerico_non_solleva() {
        assertThat(totp.verify(KEY, "abcdef", null)).isEqualTo(-1);
        assertThat(totp.verify(KEY, null, null)).isEqualTo(-1);
        assertThat(totp.verify(KEY, "12345", null)).isEqualTo(-1);
    }
}
