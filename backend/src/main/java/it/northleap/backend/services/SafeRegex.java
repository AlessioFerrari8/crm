package it.northleap.backend.services;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

// Guardia anti-ReDoS per le regex definite dagli admin (FieldDef.pattern): una regex patologica
// (es. "(a+)+$" su input lungo) manderebbe il thread in backtracking catastrofico. Java non ha
// timeout nativi sulle regex: si usa una CharSequence che controlla una deadline dentro charAt()
// - il matcher la consulta a ogni carattere, quindi il backtracking viene interrotto appena
// scade il tempo. Utility statica, stesso stile di SafeUrlValidator/HashUtil.
public final class SafeRegex {

    // limite prudente: nessuna regex di validazione legittima si avvicina a questa lunghezza
    public static final int MAX_PATTERN_LENGTH = 512;

    private static final long DEFAULT_TIMEOUT_MS = 100;

    private SafeRegex() {
    }

    // il pattern e' sintatticamente valido e di lunghezza accettabile? (da chiamare al salvataggio
    // del FieldDef, cosi' l'admin ha subito un errore chiaro invece di un fallimento a runtime)
    public static String validationError(String pattern) {
        if (pattern == null || pattern.isBlank()) {
            return null;
        }
        if (pattern.length() > MAX_PATTERN_LENGTH) {
            return "pattern troppo lungo (max " + MAX_PATTERN_LENGTH + " caratteri)";
        }
        try {
            Pattern.compile(pattern);
            return null;
        } catch (PatternSyntaxException e) {
            return "pattern regex non valido: " + e.getDescription();
        }
    }

    // find() con deadline: true/false come Matcher.find(); una regex che sfora il tempo viene
    // trattata come NON corrispondente (fail-closed: il valore viene rifiutato dalla validazione)
    public static boolean find(String pattern, String input) {
        return find(pattern, input, DEFAULT_TIMEOUT_MS);
    }

    static boolean find(String pattern, String input, long timeoutMs) {
        if (pattern.length() > MAX_PATTERN_LENGTH) {
            return false;
        }
        long deadline = System.nanoTime() + timeoutMs * 1_000_000;
        try {
            Matcher m = Pattern.compile(pattern).matcher(new DeadlineCharSequence(input, deadline));
            return m.find();
        } catch (PatternSyntaxException | RegexTimeoutException e) {
            return false;
        }
    }

    private static final class RegexTimeoutException extends RuntimeException {
    }

    // charAt() e' chiamata dal matcher per ogni carattere esaminato, anche durante il
    // backtracking: e' il punto di aggancio piu' fine disponibile per interrompere il match
    private record DeadlineCharSequence(CharSequence inner, long deadlineNanos) implements CharSequence {

        @Override
        public char charAt(int index) {
            if (System.nanoTime() > deadlineNanos) {
                throw new RegexTimeoutException();
            }
            return inner.charAt(index);
        }

        @Override
        public int length() {
            return inner.length();
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return new DeadlineCharSequence(inner.subSequence(start, end), deadlineNanos);
        }

        @Override
        public String toString() {
            return inner.toString();
        }
    }
}
