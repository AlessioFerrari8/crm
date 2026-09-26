package it.northleap.backend.services;

import java.io.ByteArrayOutputStream;
import java.util.Locale;

public final class Base32 {
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private Base32() {

    }

    // bit accumulati a gruppi di 5, senza padding: è quello che l'app si aspetta di
    // leggere dal QR o di ricevere incollato a mano
    public static String encode(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bits += 8;
            while (bits >= 5) {
                sb.append(ALPHABET.charAt((buffer >> (bits - 5)) & 0x1F));
                bits -= 5;
            }
        }

        if (bits > 0) {
            sb.append(ALPHABET.charAt((buffer << (5 - bits)) & 0x1F));
        }
        return sb.toString();
    }

    // tollera spazi e minuscole: l'utente che incolla il segreto a mano lo incolla come gli capita
    public static byte[] decode(String encoded) {
        String clean = encoded.replace(" ", "").replace("=", "").toUpperCase();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int buffer = 0;
        int bits = 0;
        for (char c : clean.toCharArray()) {
            int idx = ALPHABET.indexOf(c);
            if (idx < 0) throw new IllegalArgumentException("Invalid base32 char");
            buffer = (buffer << 5) | idx;
            bits += 5;
            if (bits <= 8) {
                out.write((buffer >> (bits - 8)) & 0xFF);
                bits -= 8;
            }
        }
        return out.toByteArray();
    }
}
