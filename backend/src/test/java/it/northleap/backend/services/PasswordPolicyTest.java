package it.northleap.backend.services;

import it.northleap.backend.exceptions.BadRequestException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordPolicyTest {

    @Test
    void strongPasswordPasses() {
        assertEquals(List.of(), PasswordPolicy.unmetRules("Str0ng!pass"));
        assertDoesNotThrow(() -> PasswordPolicy.assertValid("Str0ng!pass"));
    }

    @Test
    void weakPasswordListsAllMissingRules() {
        List<String> unmet = PasswordPolicy.unmetRules("abc");
        assertTrue(unmet.contains("almeno 8 caratteri"));
        assertTrue(unmet.contains("una lettera maiuscola"));
        assertTrue(unmet.contains("un numero"));
        assertTrue(unmet.contains("un carattere speciale"));
    }

    @Test
    void eachRuleIsCheckedIndependently() {
        assertEquals(List.of("una lettera maiuscola"), PasswordPolicy.unmetRules("str0ng!pass"));
        assertEquals(List.of("una lettera minuscola"), PasswordPolicy.unmetRules("STR0NG!PASS"));
        assertEquals(List.of("un numero"), PasswordPolicy.unmetRules("Strong!pass"));
        assertEquals(List.of("un carattere speciale"), PasswordPolicy.unmetRules("Str0ngpass"));
    }

    @Test
    void assertValidThrowsWithReadableMessage() {
        BadRequestException e = assertThrows(BadRequestException.class, () -> PasswordPolicy.assertValid("password"));
        assertTrue(e.getMessage().contains("Password troppo debole"));
    }

    @Test
    void nullIsRejected() {
        assertThrows(BadRequestException.class, () -> PasswordPolicy.assertValid(null));
    }
}
