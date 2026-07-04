package it.northleap.backend.services;

import it.northleap.backend.exceptions.BadRequestException;

import java.util.ArrayList;
import java.util.List;

// Policy password unica per tutta l'app (onboarding, accept-invite, reset admin, cambio
// self-service): 8+ caratteri, almeno una maiuscola, una minuscola, un numero e un carattere
// speciale. Il frontend mostra la stessa checklist in tempo reale (ui-password-rules): qui la
// versione server-side, che resta l'unica garanzia vera. Utility statica, stile SafeRegex/HashUtil.
public final class PasswordPolicy {

    private PasswordPolicy() {
    }

    // regole non soddisfatte, in italiano (vuota = password valida)
    public static List<String> unmetRules(String password) {
        String p = password != null ? password : "";
        List<String> unmet = new ArrayList<>();
        if (p.length() < 8) unmet.add("almeno 8 caratteri");
        if (!p.chars().anyMatch(Character::isUpperCase)) unmet.add("una lettera maiuscola");
        if (!p.chars().anyMatch(Character::isLowerCase)) unmet.add("una lettera minuscola");
        if (!p.chars().anyMatch(Character::isDigit)) unmet.add("un numero");
        if (p.chars().allMatch(Character::isLetterOrDigit)) unmet.add("un carattere speciale");
        return unmet;
    }

    public static void assertValid(String password) {
        List<String> unmet = unmetRules(password);
        if (!unmet.isEmpty()) {
            throw new BadRequestException("Password troppo debole: manca " + String.join(", ", unmet));
        }
    }
}
