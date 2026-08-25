package org.horodenko.calendar.recurrence;

import java.time.DayOfWeek;
import java.util.Locale;

/**
 * Um item de BYDAY: um dia da semana com um ordinal opcional.
 * Exemplos: {@code MO} (toda segunda), {@code 2FR} (segunda sexta do periodo),
 * {@code -1SU} (ultimo domingo do periodo).
 */
public record WeekdayNum(Integer ordinal, DayOfWeek day) {

    public static WeekdayNum parse(String token) {
        String value = token.trim().toUpperCase(Locale.ROOT);
        if (value.length() < 2) {
            throw new IllegalArgumentException("BYDAY invalido: " + token);
        }
        String code = value.substring(value.length() - 2);
        String prefix = value.substring(0, value.length() - 2);

        DayOfWeek day = switch (code) {
            case "MO" -> DayOfWeek.MONDAY;
            case "TU" -> DayOfWeek.TUESDAY;
            case "WE" -> DayOfWeek.WEDNESDAY;
            case "TH" -> DayOfWeek.THURSDAY;
            case "FR" -> DayOfWeek.FRIDAY;
            case "SA" -> DayOfWeek.SATURDAY;
            case "SU" -> DayOfWeek.SUNDAY;
            default -> throw new IllegalArgumentException("Dia da semana invalido em BYDAY: " + token);
        };

        Integer ordinal = null;
        if (!prefix.isEmpty()) {
            try {
                ordinal = Integer.valueOf(prefix);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Ordinal invalido em BYDAY: " + token, e);
            }
            if (ordinal == 0) {
                throw new IllegalArgumentException("Ordinal de BYDAY nao pode ser zero: " + token);
            }
        }
        return new WeekdayNum(ordinal, day);
    }

    public static String code(DayOfWeek day) {
        return switch (day) {
            case MONDAY -> "MO";
            case TUESDAY -> "TU";
            case WEDNESDAY -> "WE";
            case THURSDAY -> "TH";
            case FRIDAY -> "FR";
            case SATURDAY -> "SA";
            case SUNDAY -> "SU";
        };
    }

    @Override
    public String toString() {
        return (ordinal == null ? "" : String.valueOf(ordinal)) + code(day);
    }
}
