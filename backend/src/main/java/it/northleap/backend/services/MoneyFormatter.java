package it.northleap.backend.services;

import it.northleap.backend.entities.UserPreference;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
public class MoneyFormatter {

    // "9.054,00 EUR" oppure "9.054,00", secondo symbolPlacement
    public String money(BigDecimal value, UserPreference p) {
        if (value == null) return "";
        String n = number(value, p, p.getCurrencyDecimals());
        String symbol = symbolOf(p.getCurrency());
        return p.getSymbolPlacement() == UserPreference.SymbolPlacement.BEFORE
                ? symbol + " " + n
                : n + " " + symbol;
    }

    // numero nudo, senza valuta: quantità, percentuali e aliquote
    public String number(BigDecimal value, UserPreference p, int decimals) {
        if (value == null) return "";
        DecimalFormatSymbols sym = new DecimalFormatSymbols(Locale.ROOT);
        sym.setGroupingSeparator(charOf(p.getGroupSeparator(), '.'));
        sym.setDecimalSeparator(charOf(p.getDecimalSeparator(), ','));

        StringBuilder pattern = new StringBuilder("#,##0");
        if (decimals > 0) {
            pattern.append('.');
            // truncateTrailingZeros: 10,00 diventa 10 ma 10,50 resta 10,5
            pattern.append(String.valueOf(p.isTruncateTrailingZeros() ? '#' : '0')
                    .repeat(decimals));
        }

        DecimalFormat df = new DecimalFormat(pattern.toString(), sym);
        df.setGroupingUsed(!p.getGroupSeparator().isEmpty());
        return df.format(value);
    }

    // percentuale di aliquota: 22 -> "22%", 4.5 -> "4,5%"
    public String percent(BigDecimal value, UserPreference p) {
        if (value == null) return "";
        return number(value.stripTrailingZeros(), p, 2) + "%";
    }

    public String date(Instant when, UserPreference p) {
        if (when == null) return "";
        return DateTimeFormatter.ofPattern(p.getDateFormat())
                .withZone(ZoneId.systemDefault())
                .format(when);
    }

    private char charOf(String s, char fallback) {
        return (s == null || s.isEmpty()) ? fallback : s.charAt(0);
    }

    private String symbolOf(String currency) {
        if (currency == null) return "";
        return switch (currency) {
            case "EUR" -> "€";
            case "USD" -> "$";
            case "GBP" -> "£";
            default -> currency;
        };
    }
}
