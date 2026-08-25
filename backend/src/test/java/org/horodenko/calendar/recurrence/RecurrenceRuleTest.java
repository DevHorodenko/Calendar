package org.horodenko.calendar.recurrence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecurrenceRuleTest {

    @Test
    @DisplayName("Le todas as partes suportadas de uma RRULE")
    void parsesEverySupportedPart() {
        RecurrenceRule rule = RecurrenceRule.parse(
                "FREQ=MONTHLY;INTERVAL=2;COUNT=10;BYDAY=1MO,-1FR;BYMONTHDAY=1,15;BYMONTH=3,6;WKST=SU");

        assertThat(rule.freq()).isEqualTo(Frequency.MONTHLY);
        assertThat(rule.interval()).isEqualTo(2);
        assertThat(rule.count()).isEqualTo(10);
        assertThat(rule.until()).isNull();
        assertThat(rule.byDay()).containsExactly(
                new WeekdayNum(1, DayOfWeek.MONDAY),
                new WeekdayNum(-1, DayOfWeek.FRIDAY));
        assertThat(rule.byMonthDay()).containsExactly(1, 15);
        assertThat(rule.byMonth()).containsExactly(3, 6);
        assertThat(rule.weekStart()).isEqualTo(DayOfWeek.SUNDAY);
    }

    @Test
    @DisplayName("Aceita o prefixo RRULE:")
    void acceptsRrulePrefix() {
        assertThat(RecurrenceRule.parse("RRULE:FREQ=DAILY").freq()).isEqualTo(Frequency.DAILY);
    }

    @Test
    @DisplayName("UNTIL e aceito com hora, com sufixo Z e apenas como data")
    void parsesUntilInEveryForm() {
        assertThat(RecurrenceRule.parse("FREQ=DAILY;UNTIL=20261231T235900").until())
                .isEqualTo(LocalDateTime.of(2026, 12, 31, 23, 59));
        assertThat(RecurrenceRule.parse("FREQ=DAILY;UNTIL=20261231T235900Z").until())
                .isEqualTo(LocalDateTime.of(2026, 12, 31, 23, 59));
        assertThat(RecurrenceRule.parse("FREQ=DAILY;UNTIL=20261231").until())
                .isEqualTo(LocalDateTime.of(2026, 12, 31, 23, 59, 59));
    }

    @Test
    @DisplayName("Reserializar mantem a regra equivalente")
    void roundTrip() {
        String rrule = "FREQ=WEEKLY;INTERVAL=2;COUNT=10;BYDAY=MO,WE";
        assertThat(RecurrenceRule.parse(rrule).toRrule()).isEqualTo(rrule);
    }

    @Test
    @DisplayName("truncatedAt troca COUNT por UNTIL para encerrar a serie no corte")
    void truncatedAtReplacesCount() {
        RecurrenceRule truncated = RecurrenceRule.parse("FREQ=DAILY;COUNT=10")
                .truncatedAt(LocalDateTime.of(2026, 5, 1, 12, 0));

        assertThat(truncated.count()).isNull();
        assertThat(truncated.until()).isEqualTo(LocalDateTime.of(2026, 5, 1, 12, 0));
    }

    @Test
    @DisplayName("Recusa regras invalidas em vez de expandi-las errado")
    void rejectsInvalidRules() {
        assertThatThrownBy(() -> RecurrenceRule.parse("FREQ=HOURLY"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("FREQ nao suportada");

        assertThatThrownBy(() -> RecurrenceRule.parse("FREQ=DAILY;BYSETPOS=1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nao suportada");

        assertThatThrownBy(() -> RecurrenceRule.parse("FREQ=DAILY;COUNT=5;UNTIL=20261231"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nao podem ser usados juntos");

        assertThatThrownBy(() -> RecurrenceRule.parse("FREQ=DAILY;INTERVAL=0"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("INTERVAL");

        assertThatThrownBy(() -> RecurrenceRule.parse("FREQ=WEEKLY;BYDAY=0MO"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nao pode ser zero");

        assertThatThrownBy(() -> RecurrenceRule.parse("BYDAY=MO"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("FREQ e obrigatorio");
    }
}
