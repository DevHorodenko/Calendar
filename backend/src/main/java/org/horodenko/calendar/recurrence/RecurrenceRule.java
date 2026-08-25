package org.horodenko.calendar.recurrence;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Regra de recorrencia no formato RRULE da RFC 5545.
 *
 * <p>Suporta o subconjunto que um calendario pessoal precisa:
 * FREQ, INTERVAL, COUNT, UNTIL, BYDAY, BYMONTHDAY, BYMONTH e WKST.
 * Partes nao suportadas (BYSETPOS, BYWEEKNO, BYYEARDAY, BYHOUR...) sao rejeitadas
 * na leitura para nao gerarem expansoes silenciosamente erradas.
 */
public record RecurrenceRule(
        Frequency freq,
        int interval,
        Integer count,
        LocalDateTime until,
        List<WeekdayNum> byDay,
        List<Integer> byMonthDay,
        List<Integer> byMonth,
        DayOfWeek weekStart
) {

    private static final DateTimeFormatter UNTIL_UTC =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'", Locale.ROOT);
    private static final DateTimeFormatter UNTIL_LOCAL =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss", Locale.ROOT);
    private static final DateTimeFormatter UNTIL_DATE =
            DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT);

    public RecurrenceRule {
        if (freq == null) {
            throw new IllegalArgumentException("FREQ e obrigatorio em uma RRULE");
        }
        if (interval < 1) {
            throw new IllegalArgumentException("INTERVAL deve ser >= 1, recebido: " + interval);
        }
        if (count != null && until != null) {
            throw new IllegalArgumentException("COUNT e UNTIL nao podem ser usados juntos");
        }
        if (count != null && count < 1) {
            throw new IllegalArgumentException("COUNT deve ser >= 1, recebido: " + count);
        }
        byDay = byDay == null ? List.of() : List.copyOf(byDay);
        byMonthDay = byMonthDay == null ? List.of() : List.copyOf(byMonthDay);
        byMonth = byMonth == null ? List.of() : List.copyOf(byMonth);
        weekStart = weekStart == null ? DayOfWeek.MONDAY : weekStart;
    }

    /** Le uma RRULE como {@code FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE}. */
    public static RecurrenceRule parse(String rrule) {
        if (rrule == null || rrule.isBlank()) {
            throw new IllegalArgumentException("RRULE vazia");
        }
        String body = rrule.trim();
        if (body.toUpperCase(Locale.ROOT).startsWith("RRULE:")) {
            body = body.substring("RRULE:".length());
        }

        Frequency freq = null;
        int interval = 1;
        Integer count = null;
        LocalDateTime until = null;
        List<WeekdayNum> byDay = List.of();
        List<Integer> byMonthDay = List.of();
        List<Integer> byMonth = List.of();
        DayOfWeek weekStart = DayOfWeek.MONDAY;

        for (String part : body.split(";")) {
            if (part.isBlank()) {
                continue;
            }
            int eq = part.indexOf('=');
            if (eq < 0) {
                throw new IllegalArgumentException("Parte de RRULE sem sinal de igual: " + part);
            }
            String name = part.substring(0, eq).trim().toUpperCase(Locale.ROOT);
            String value = part.substring(eq + 1).trim();

            switch (name) {
                case "FREQ" -> freq = parseFrequency(value);
                case "INTERVAL" -> interval = parseInt(name, value);
                case "COUNT" -> count = parseInt(name, value);
                case "UNTIL" -> until = parseUntil(value);
                case "BYDAY" -> byDay = Arrays.stream(value.split(",")).map(WeekdayNum::parse).toList();
                case "BYMONTHDAY" -> byMonthDay = parseMonthDays(value);
                case "BYMONTH" -> byMonth = parseMonths(value);
                case "WKST" -> weekStart = WeekdayNum.parse(value).day();
                default -> throw new IllegalArgumentException("Parte de RRULE nao suportada: " + name);
            }
        }
        return new RecurrenceRule(freq, interval, count, until, byDay, byMonthDay, byMonth, weekStart);
    }

    private static Frequency parseFrequency(String value) {
        try {
            return Frequency.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "FREQ nao suportada: " + value + " (use DAILY, WEEKLY, MONTHLY ou YEARLY)", e);
        }
    }

    private static int parseInt(String name, String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " deve ser um numero: " + value, e);
        }
    }

    /**
     * UNTIL e aceito nos formatos {@code yyyyMMddTHHmmssZ}, {@code yyyyMMddTHHmmss} e
     * {@code yyyyMMdd}. Como os eventos sao guardados em hora local flutuante, o sufixo
     * Z e apenas tolerado, nao convertido.
     */
    private static LocalDateTime parseUntil(String value) {
        String raw = value.trim();
        try {
            if (raw.endsWith("Z")) {
                return LocalDateTime.parse(raw, UNTIL_UTC);
            }
            if (raw.contains("T")) {
                return LocalDateTime.parse(raw, UNTIL_LOCAL);
            }
            return LocalDate.parse(raw, UNTIL_DATE).atTime(23, 59, 59);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("UNTIL invalido: " + value, e);
        }
    }

    private static List<Integer> parseMonthDays(String value) {
        List<Integer> days = new ArrayList<>();
        for (String token : value.split(",")) {
            int day = parseInt("BYMONTHDAY", token.trim());
            if (day == 0 || day < -31 || day > 31) {
                throw new IllegalArgumentException("BYMONTHDAY fora do intervalo valido: " + token);
            }
            days.add(day);
        }
        return days;
    }

    private static List<Integer> parseMonths(String value) {
        List<Integer> months = new ArrayList<>();
        for (String token : value.split(",")) {
            int month = parseInt("BYMONTH", token.trim());
            if (month < 1 || month > 12) {
                throw new IllegalArgumentException("BYMONTH fora do intervalo valido: " + token);
            }
            months.add(month);
        }
        return months;
    }

    /** Reserializa a regra em texto RRULE canonico. */
    public String toRrule() {
        StringBuilder sb = new StringBuilder("FREQ=").append(freq);
        if (interval != 1) {
            sb.append(";INTERVAL=").append(interval);
        }
        if (count != null) {
            sb.append(";COUNT=").append(count);
        }
        if (until != null) {
            sb.append(";UNTIL=").append(until.format(UNTIL_LOCAL));
        }
        if (!byDay.isEmpty()) {
            sb.append(";BYDAY=").append(String.join(",", byDay.stream().map(WeekdayNum::toString).toList()));
        }
        if (!byMonthDay.isEmpty()) {
            sb.append(";BYMONTHDAY=").append(join(byMonthDay));
        }
        if (!byMonth.isEmpty()) {
            sb.append(";BYMONTH=").append(join(byMonth));
        }
        if (weekStart != DayOfWeek.MONDAY) {
            sb.append(";WKST=").append(WeekdayNum.code(weekStart));
        }
        return sb.toString();
    }

    private static String join(List<Integer> values) {
        return String.join(",", values.stream().map(String::valueOf).toList());
    }

    /** Copia a regra encerrando a serie em {@code newUntil} (usado ao editar "esta e as futuras"). */
    public RecurrenceRule truncatedAt(LocalDateTime newUntil) {
        return new RecurrenceRule(freq, interval, null, newUntil, byDay, byMonthDay, byMonth, weekStart);
    }
}
