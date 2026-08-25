package org.horodenko.calendar.recurrence;

import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Expande uma serie recorrente nas datas concretas em que ela acontece.
 *
 * <p>A expansao sempre parte do inicio da serie, e nao do inicio da janela consultada,
 * porque COUNT conta ocorrencias desde a primeira. Um teto de periodos protege contra
 * regras que gerariam uma varredura sem fim.
 */
@Component
public class RecurrenceExpander {

    /** Teto de periodos percorridos: cobre ~55 anos de uma regra diaria. */
    private static final int MAX_PERIODS = 20_000;

    /**
     * Datas de inicio de cada ocorrencia da serie dentro de {@code [windowStart, windowEnd)}.
     *
     * @param seriesStart data e hora da primeira ocorrencia (o DTSTART da serie)
     * @param rule        regra de repeticao
     * @param windowStart inicio da janela consultada, inclusivo
     * @param windowEnd   fim da janela consultada, exclusivo
     */
    public List<LocalDateTime> expand(LocalDateTime seriesStart,
                                      RecurrenceRule rule,
                                      LocalDateTime windowStart,
                                      LocalDateTime windowEnd) {
        List<LocalDateTime> occurrences = new ArrayList<>();
        if (seriesStart == null || rule == null || !windowStart.isBefore(windowEnd)) {
            return occurrences;
        }

        LocalDate seriesDate = seriesStart.toLocalDate();
        LocalDate periodStart = firstPeriodStart(seriesDate, rule);
        int emitted = 0;

        for (int period = 0; period < MAX_PERIODS; period++) {
            if (periodStart.isAfter(windowEnd.toLocalDate())) {
                break;
            }

            for (LocalDate date : candidates(periodStart, seriesDate, rule)) {
                LocalDateTime occurrence = date.atTime(seriesStart.toLocalTime());

                if (occurrence.isBefore(seriesStart)) {
                    continue;
                }
                if (rule.until() != null && occurrence.isAfter(rule.until())) {
                    return occurrences;
                }
                if (rule.count() != null && emitted >= rule.count()) {
                    return occurrences;
                }
                emitted++;

                if (!occurrence.isBefore(windowStart) && occurrence.isBefore(windowEnd)) {
                    occurrences.add(occurrence);
                }
            }
            periodStart = nextPeriodStart(periodStart, rule);
        }
        return occurrences;
    }

    /** Verdadeiro se a serie tem uma ocorrencia comecando exatamente em {@code moment}. */
    public boolean occursAt(LocalDateTime seriesStart, RecurrenceRule rule, LocalDateTime moment) {
        return !expand(seriesStart, rule, moment, moment.plusNanos(1)).isEmpty();
    }

    // ---------------------------------------------------------------- periodos

    private LocalDate firstPeriodStart(LocalDate seriesDate, RecurrenceRule rule) {
        return switch (rule.freq()) {
            case DAILY -> seriesDate;
            case WEEKLY -> alignToWeekStart(seriesDate, rule.weekStart());
            case MONTHLY -> seriesDate.withDayOfMonth(1);
            case YEARLY -> seriesDate.withDayOfYear(1);
        };
    }

    private LocalDate nextPeriodStart(LocalDate periodStart, RecurrenceRule rule) {
        return switch (rule.freq()) {
            case DAILY -> periodStart.plusDays(rule.interval());
            case WEEKLY -> periodStart.plusWeeks(rule.interval());
            case MONTHLY -> periodStart.plusMonths(rule.interval());
            case YEARLY -> periodStart.plusYears(rule.interval());
        };
    }

    private LocalDate alignToWeekStart(LocalDate date, DayOfWeek weekStart) {
        int offset = Math.floorMod(date.getDayOfWeek().getValue() - weekStart.getValue(), 7);
        return date.minusDays(offset);
    }

    // ---------------------------------------------------------------- candidatos

    /** Datas geradas por um unico periodo, ja ordenadas e sem repeticoes. */
    private List<LocalDate> candidates(LocalDate periodStart, LocalDate seriesDate, RecurrenceRule rule) {
        Set<LocalDate> dates = new TreeSet<>();

        switch (rule.freq()) {
            case DAILY -> dates.add(periodStart);
            case WEEKLY -> dates.addAll(weeklyCandidates(periodStart, seriesDate, rule));
            case MONTHLY -> dates.addAll(monthCandidates(YearMonth.from(periodStart), seriesDate, rule));
            case YEARLY -> {
                for (int month : yearlyMonths(seriesDate, rule)) {
                    dates.addAll(monthCandidates(YearMonth.of(periodStart.getYear(), month), seriesDate, rule));
                }
            }
        }
        return dates.stream().filter(date -> matchesLimits(date, rule)).toList();
    }

    private List<LocalDate> weeklyCandidates(LocalDate weekStart, LocalDate seriesDate, RecurrenceRule rule) {
        Set<DayOfWeek> days = rule.byDay().isEmpty()
                ? EnumSet.of(seriesDate.getDayOfWeek())
                : daysOf(rule.byDay());

        List<LocalDate> dates = new ArrayList<>();
        for (int offset = 0; offset < 7; offset++) {
            LocalDate date = weekStart.plusDays(offset);
            if (days.contains(date.getDayOfWeek())) {
                dates.add(date);
            }
        }
        return dates;
    }

    /**
     * Candidatos dentro de um mes, usado por MONTHLY e por cada mes de YEARLY.
     * Quando BYMONTHDAY e BYDAY aparecem juntos, BYDAY funciona como filtro do primeiro.
     */
    private List<LocalDate> monthCandidates(YearMonth month, LocalDate seriesDate, RecurrenceRule rule) {
        if (!rule.byMonthDay().isEmpty()) {
            List<LocalDate> dates = new ArrayList<>();
            for (int day : rule.byMonthDay()) {
                LocalDate date = resolveMonthDay(month, day);
                if (date != null) {
                    dates.add(date);
                }
            }
            return dates;
        }
        if (!rule.byDay().isEmpty()) {
            return byDayInMonth(month, rule.byDay());
        }
        LocalDate date = resolveMonthDay(month, seriesDate.getDayOfMonth());
        return date == null ? List.of() : List.of(date);
    }

    private List<Integer> yearlyMonths(LocalDate seriesDate, RecurrenceRule rule) {
        return rule.byMonth().isEmpty() ? List.of(seriesDate.getMonthValue()) : rule.byMonth();
    }

    /**
     * Resolve um BYMONTHDAY dentro do mes. Valores negativos contam do fim
     * ({@code -1} e o ultimo dia). Retorna {@code null} quando o mes nao tem esse dia,
     * caso de {@code BYMONTHDAY=31} em fevereiro.
     */
    private LocalDate resolveMonthDay(YearMonth month, int day) {
        int length = month.lengthOfMonth();
        int resolved = day > 0 ? day : length + day + 1;
        return (resolved >= 1 && resolved <= length) ? month.atDay(resolved) : null;
    }

    /** Expande itens de BYDAY dentro de um mes, respeitando ordinais como {@code 2FR} ou {@code -1SU}. */
    private List<LocalDate> byDayInMonth(YearMonth month, List<WeekdayNum> byDay) {
        List<LocalDate> dates = new ArrayList<>();
        for (WeekdayNum weekday : byDay) {
            if (weekday.ordinal() == null) {
                dates.addAll(allDaysOfWeekIn(month, weekday.day()));
            } else {
                LocalDate date = nthDayOfWeekIn(month, weekday.day(), weekday.ordinal());
                if (date != null) {
                    dates.add(date);
                }
            }
        }
        return dates;
    }

    private List<LocalDate> allDaysOfWeekIn(YearMonth month, DayOfWeek day) {
        List<LocalDate> dates = new ArrayList<>();
        LocalDate date = firstDayOfWeekIn(month, day);
        while (YearMonth.from(date).equals(month)) {
            dates.add(date);
            date = date.plusWeeks(1);
        }
        return dates;
    }

    private LocalDate nthDayOfWeekIn(YearMonth month, DayOfWeek day, int ordinal) {
        LocalDate date = ordinal > 0
                ? firstDayOfWeekIn(month, day).plusWeeks(ordinal - 1L)
                : lastDayOfWeekIn(month, day).minusWeeks(-ordinal - 1L);
        return YearMonth.from(date).equals(month) ? date : null;
    }

    private LocalDate firstDayOfWeekIn(YearMonth month, DayOfWeek day) {
        LocalDate first = month.atDay(1);
        int offset = Math.floorMod(day.getValue() - first.getDayOfWeek().getValue(), 7);
        return first.plusDays(offset);
    }

    private LocalDate lastDayOfWeekIn(YearMonth month, DayOfWeek day) {
        LocalDate last = month.atEndOfMonth();
        int offset = Math.floorMod(last.getDayOfWeek().getValue() - day.getValue(), 7);
        return last.minusDays(offset);
    }

    // ---------------------------------------------------------------- filtros

    /**
     * Aplica as partes de BYxxx que funcionam como filtro em vez de gerador.
     * Para MONTHLY e YEARLY os BYxxx ja participaram da geracao, entao aqui
     * so restam os limites que valem para qualquer frequencia.
     */
    private boolean matchesLimits(LocalDate date, RecurrenceRule rule) {
        if (!rule.byMonth().isEmpty() && !rule.byMonth().contains(date.getMonthValue())) {
            return false;
        }
        boolean generatedByDay = rule.freq() == Frequency.MONTHLY || rule.freq() == Frequency.YEARLY;
        if (!generatedByDay && !rule.byDay().isEmpty() && rule.freq() == Frequency.DAILY
                && !daysOf(rule.byDay()).contains(date.getDayOfWeek())) {
            return false;
        }
        if (generatedByDay && !rule.byMonthDay().isEmpty() && !rule.byDay().isEmpty()
                && !daysOf(rule.byDay()).contains(date.getDayOfWeek())) {
            return false;
        }
        if (rule.freq() == Frequency.DAILY && !rule.byMonthDay().isEmpty()
                && !matchesAnyMonthDay(date, rule.byMonthDay())) {
            return false;
        }
        return true;
    }

    private boolean matchesAnyMonthDay(LocalDate date, List<Integer> byMonthDay) {
        int length = date.lengthOfMonth();
        for (int day : byMonthDay) {
            int resolved = day > 0 ? day : length + day + 1;
            if (resolved == date.getDayOfMonth()) {
                return true;
            }
        }
        return false;
    }

    private Set<DayOfWeek> daysOf(List<WeekdayNum> byDay) {
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        byDay.forEach(weekday -> days.add(weekday.day()));
        return days;
    }
}
