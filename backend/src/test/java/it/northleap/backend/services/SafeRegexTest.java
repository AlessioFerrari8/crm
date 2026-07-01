package it.northleap.backend.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafeRegexTest {

    @Test
    void matchingPatternFinds() {
        assertTrue(SafeRegex.find("^[A-Z]{2}\\d+$", "AB123"));
    }

    @Test
    void nonMatchingPatternDoesNotFind() {
        assertFalse(SafeRegex.find("^[A-Z]{2}\\d+$", "abc"));
    }

    @Test
    void invalidSyntaxIsFailClosed() {
        assertFalse(SafeRegex.find("([unclosed", "qualsiasi"));
    }

    @Test
    void redosPatternIsCutOffByDeadline() {
        // (a+)+$ su una lunga serie di 'a' terminata da 'X': backtracking esponenziale, senza
        // deadline girerebbe per minuti/ore. Deve invece tornare false entro il timeout.
        String evil = "(a+)+$";
        String input = "a".repeat(40) + "X";

        long start = System.nanoTime();
        boolean result = SafeRegex.find(evil, input, 100);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertFalse(result);
        // margine largo sul timeout da 100ms: sotto il secondo = la deadline ha interrotto il match
        assertTrue(elapsedMs < 1000, "il match doveva essere interrotto dalla deadline, ha impiegato " + elapsedMs + "ms");
    }

    @Test
    void tooLongPatternIsRejected() {
        String longPattern = "a".repeat(SafeRegex.MAX_PATTERN_LENGTH + 1);
        assertFalse(SafeRegex.find(longPattern, "aaa"));
        assertNotNull(SafeRegex.validationError(longPattern));
    }

    @Test
    void validationErrorOnBadSyntaxAndNullOnValid() {
        assertNotNull(SafeRegex.validationError("([unclosed"));
        assertNull(SafeRegex.validationError("^[a-z]+$"));
        assertNull(SafeRegex.validationError(null));
        assertNull(SafeRegex.validationError(""));
    }
}
