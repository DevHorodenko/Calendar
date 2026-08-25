package org.horodenko.calendar.recurrence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecurrenceExpanderTest {

    private final RecurrenceExpander expander = new RecurrenceExpander();

    /** Janela larga o bastante para nao interferir no que a regra gera. */
    private static final LocalDateTime WINDOW_START = LocalDateTime.of(2020, 1, 1, 0, 0);
    private static final LocalDateTime WINDOW_END = LocalDateTime.of(2030, 1, 1, 0, 0);

    private List<LocalDate> datesOf(String start, String rrule) {
        return datesOf(start, rrule, WINDOW_START, WINDOW_END);
    }

    private List<LocalDate> datesOf(String start, String rrule, LocalDateTime from, LocalDateTime to) {
        return expander.expand(LocalDateTime.parse(start), RecurrenceRule.parse(rrule), from, to)
                .stream()
                .map(LocalDateTime::toLocalDate)
                .toList();
    }

    @Test
    @DisplayName("DAILY com COUNT para na quantidade pedida")
    void dailyWithCount() {
        assertThat(datesOf("2026-01-01T09:00", "FREQ=DAILY;COUNT=5"))
                .containsExactly(
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 1, 2),
                        LocalDate.of(2026, 1, 3),
                        LocalDate.of(2026, 1, 4),
                        LocalDate.of(2026, 1, 5));
    }

    @Test
    @DisplayName("DAILY com INTERVAL pula os dias intermediarios")
    void dailyWithInterval() {
        assertThat(datesOf("2026-01-01T09:00", "FREQ=DAILY;INTERVAL=3;COUNT=4"))
                .containsExactly(
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 1, 4),
                        LocalDate.of(2026, 1, 7),
                        LocalDate.of(2026, 1, 10));
    }

    @Test
    @DisplayName("A hora do inicio da serie se repete em todas as ocorrencias")
    void keepsTimeOfDay() {
        List<LocalDateTime> occurrences = expander.expand(
                LocalDateTime.of(2026, 1, 1, 14, 30),
                RecurrenceRule.parse("FREQ=DAILY;COUNT=3"),
                WINDOW_START, WINDOW_END);

        assertThat(occurrences).allSatisfy(occurrence ->
                assertThat(occurrence.toLocalTime()).isEqualTo(java.time.LocalTime.of(14, 30)));
    }

    @Test
    @DisplayName("WEEKLY com BYDAY gera cada dia marcado da semana")
    void weeklyByDay() {
        assertThat(datesOf("2026-03-02T10:00", "FREQ=WEEKLY;BYDAY=MO,WE,FR;COUNT=6"))
                .containsExactly(
                        LocalDate.of(2026, 3, 2),
                        LocalDate.of(2026, 3, 4),
                        LocalDate.of(2026, 3, 6),
                        LocalDate.of(2026, 3, 9),
                        LocalDate.of(2026, 3, 11),
                        LocalDate.of(2026, 3, 13));
    }

    @Test
    @DisplayName("WEEKLY sem BYDAY repete no dia da semana em que a serie comecou")
    void weeklyWithoutByDay() {
        assertThat(datesOf("2026-03-02T10:00", "FREQ=WEEKLY;COUNT=3"))
                .containsExactly(
                        LocalDate.of(2026, 3, 2),
                        LocalDate.of(2026, 3, 9),
                        LocalDate.of(2026, 3, 16));
    }

    @Test
    @DisplayName("WEEKLY com INTERVAL=2 pula uma semana inteira")
    void biweekly() {
        assertThat(datesOf("2026-03-02T10:00", "FREQ=WEEKLY;INTERVAL=2;BYDAY=MO;COUNT=3"))
                .containsExactly(
                        LocalDate.of(2026, 3, 2),
                        LocalDate.of(2026, 3, 16),
                        LocalDate.of(2026, 3, 30));
    }

    @Test
    @DisplayName("MONTHLY sem BYxxx repete no mesmo dia do mes")
    void monthlySameDayOfMonth() {
        assertThat(datesOf("2026-01-15T08:00", "FREQ=MONTHLY;COUNT=3"))
                .containsExactly(
                        LocalDate.of(2026, 1, 15),
                        LocalDate.of(2026, 2, 15),
                        LocalDate.of(2026, 3, 15));
    }

    @Test
    @DisplayName("MONTHLY no dia 31 pula os meses que nao tem dia 31")
    void monthlySkipsShortMonths() {
        assertThat(datesOf("2026-01-31T08:00", "FREQ=MONTHLY;COUNT=4"))
                .containsExactly(
                        LocalDate.of(2026, 1, 31),
                        LocalDate.of(2026, 3, 31),
                        LocalDate.of(2026, 5, 31),
                        LocalDate.of(2026, 7, 31));
    }

    @Test
    @DisplayName("MONTHLY com BYDAY=-1FR cai na ultima sexta de cada mes")
    void monthlyLastFriday() {
        assertThat(datesOf("2026-01-30T18:00", "FREQ=MONTHLY;BYDAY=-1FR;COUNT=4"))
                .containsExactly(
                        LocalDate.of(2026, 1, 30),
                        LocalDate.of(2026, 2, 27),
                        LocalDate.of(2026, 3, 27),
                        LocalDate.of(2026, 4, 24));
    }

    @Test
    @DisplayName("MONTHLY com BYDAY=2TU cai na segunda terca de cada mes")
    void monthlySecondTuesday() {
        assertThat(datesOf("2026-01-13T18:00", "FREQ=MONTHLY;BYDAY=2TU;COUNT=3"))
                .containsExactly(
                        LocalDate.of(2026, 1, 13),
                        LocalDate.of(2026, 2, 10),
                        LocalDate.of(2026, 3, 10));
    }

    @Test
    @DisplayName("MONTHLY com BYMONTHDAY=-1 cai no ultimo dia de cada mes")
    void monthlyLastDayOfMonth() {
        assertThat(datesOf("2026-01-31T23:00", "FREQ=MONTHLY;BYMONTHDAY=-1;COUNT=3"))
                .containsExactly(
                        LocalDate.of(2026, 1, 31),
                        LocalDate.of(2026, 2, 28),
                        LocalDate.of(2026, 3, 31));
    }

    @Test
    @DisplayName("YEARLY repete na mesma data todo ano")
    void yearly() {
        assertThat(datesOf("2026-06-15T12:00", "FREQ=YEARLY;COUNT=3"))
                .containsExactly(
                        LocalDate.of(2026, 6, 15),
                        LocalDate.of(2027, 6, 15),
                        LocalDate.of(2028, 6, 15));
    }

    @Test
    @DisplayName("YEARLY com BYMONTH gera uma ocorrencia em cada mes listado")
    void yearlyWithSeveralMonths() {
        assertThat(datesOf("2026-03-10T12:00", "FREQ=YEARLY;BYMONTH=3,9;BYMONTHDAY=10;COUNT=4"))
                .containsExactly(
                        LocalDate.of(2026, 3, 10),
                        LocalDate.of(2026, 9, 10),
                        LocalDate.of(2027, 3, 10),
                        LocalDate.of(2027, 9, 10));
    }

    @Test
    @DisplayName("UNTIL encerra a serie e a data limite ainda conta")
    void untilIsInclusive() {
        assertThat(datesOf("2026-01-01T09:00", "FREQ=DAILY;UNTIL=20260103T090000"))
                .containsExactly(
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 1, 2),
                        LocalDate.of(2026, 1, 3));
    }

    @Test
    @DisplayName("Uma janela estreita nao muda quais ocorrencias a regra tem")
    void windowDoesNotShiftCount() {
        // COUNT=10 a partir de 01/01: as ocorrencias 5 a 7 caem entre 05/01 e 08/01.
        List<LocalDate> dates = datesOf("2026-01-01T09:00", "FREQ=DAILY;COUNT=10",
                LocalDateTime.of(2026, 1, 5, 0, 0),
                LocalDateTime.of(2026, 1, 8, 0, 0));

        assertThat(dates).containsExactly(
                LocalDate.of(2026, 1, 5),
                LocalDate.of(2026, 1, 6),
                LocalDate.of(2026, 1, 7));
    }

    @Test
    @DisplayName("A janela nao devolve ocorrencias depois do fim da serie")
    void windowAfterSeriesEnd() {
        assertThat(datesOf("2026-01-01T09:00", "FREQ=DAILY;COUNT=3",
                LocalDateTime.of(2026, 2, 1, 0, 0),
                LocalDateTime.of(2026, 3, 1, 0, 0)))
                .isEmpty();
    }

    @Test
    @DisplayName("DAILY com BYDAY funciona como filtro de dias uteis")
    void dailyLimitedByWeekday() {
        assertThat(datesOf("2026-03-02T09:00", "FREQ=DAILY;BYDAY=MO,TU,WE,TH,FR;COUNT=6"))
                .containsExactly(
                        LocalDate.of(2026, 3, 2),
                        LocalDate.of(2026, 3, 3),
                        LocalDate.of(2026, 3, 4),
                        LocalDate.of(2026, 3, 5),
                        LocalDate.of(2026, 3, 6),
                        LocalDate.of(2026, 3, 9));
    }

    @Test
    @DisplayName("occursAt reconhece um horario que a regra gera e recusa os demais")
    void occursAt() {
        LocalDateTime start = LocalDateTime.of(2026, 3, 2, 10, 0);
        RecurrenceRule rule = RecurrenceRule.parse("FREQ=WEEKLY;BYDAY=MO");

        assertThat(expander.occursAt(start, rule, LocalDateTime.of(2026, 3, 9, 10, 0))).isTrue();
        assertThat(expander.occursAt(start, rule, LocalDateTime.of(2026, 3, 10, 10, 0))).isFalse();
        assertThat(expander.occursAt(start, rule, LocalDateTime.of(2026, 3, 9, 11, 0))).isFalse();
    }
}
