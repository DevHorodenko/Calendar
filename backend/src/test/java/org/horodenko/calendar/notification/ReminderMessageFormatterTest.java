package org.horodenko.calendar.notification;

import org.horodenko.calendar.web.dto.OccurrenceResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReminderMessageFormatterTest {

    private final ReminderMessageFormatter formatter = new ReminderMessageFormatter();

    @Test
    @DisplayName("Troca todos os marcadores pelos dados da ocorrencia")
    void replacesEveryPlaceholder() {
        String text = formatter.format(
                "{titulo} em {data} as {hora}, {antecedencia}. Local: {local}. Obs: {descricao}",
                occurrence("Dentista", "Rua das Flores 10", "levar o cartao", false),
                30);

        assertThat(text).isEqualTo(
                "Dentista em 10/09/2026 as 14:30, em 30 minutos. "
                        + "Local: Rua das Flores 10. Obs: levar o cartao");
    }

    @Test
    @DisplayName("Marcador sem valor vira vazio, e nao o literal na mensagem")
    void emptyFieldsBecomeBlank() {
        String text = formatter.format("{titulo}|{local}|{descricao}",
                occurrence("Treino", null, null, false), 10);

        assertThat(text).isEqualTo("Treino||");
    }

    @Test
    @DisplayName("Sem mensagem propria, monta o texto padrao a partir do evento")
    void buildsDefaultMessage() {
        String text = formatter.format(null,
                occurrence("Consulta", "Clinica Sao Jorge", null, false), 15);

        assertThat(text).isEqualTo(
                "Lembrete: Consulta comeca em 15 minutos (10/09/2026 as 14:30).\n"
                        + "Local: Clinica Sao Jorge.");
    }

    @Test
    @DisplayName("Evento de dia inteiro nao anuncia horario")
    void allDayHasNoClockTime() {
        String text = formatter.format(null, occurrence("Feriado", null, null, true), 24 * 60);

        assertThat(text).isEqualTo("Lembrete: Feriado e em 1 dia (10/09/2026, dia inteiro).");
        assertThat(formatter.format("{hora}", occurrence("Feriado", null, null, true), 60))
                .isEqualTo("dia inteiro");
    }

    @Test
    @DisplayName("A antecedencia sai na maior unidade que couber inteira")
    void describesLeadInTheLargestWholeUnit() {
        assertThat(formatter.describeLead(0)).isEqualTo("agora");
        assertThat(formatter.describeLead(1)).isEqualTo("em 1 minuto");
        assertThat(formatter.describeLead(45)).isEqualTo("em 45 minutos");
        assertThat(formatter.describeLead(60)).isEqualTo("em 1 hora");
        assertThat(formatter.describeLead(120)).isEqualTo("em 2 horas");
        assertThat(formatter.describeLead(90)).isEqualTo("em 1 hora e 30 minutos");
        assertThat(formatter.describeLead(24 * 60)).isEqualTo("em 1 dia");
        assertThat(formatter.describeLead(3 * 24 * 60)).isEqualTo("em 3 dias");
    }

    @Test
    @DisplayName("Mensagem longa demais e cortada, para o provedor nao recusar a URL")
    void truncatesOverlongMessages() {
        String text = formatter.format("x".repeat(2000), occurrence("Qualquer", null, null, false), 5);

        assertThat(text).hasSize(ReminderMessageFormatter.MAX_LENGTH);
        assertThat(text).endsWith("...");
    }

    private OccurrenceResponse occurrence(String title, String location, String description, boolean allDay) {
        LocalDateTime start = LocalDateTime.of(2026, 9, 10, 14, 30);
        return new OccurrenceResponse(
                UUID.randomUUID(), start, start, start.plusHours(1), allDay,
                title, description, location, "#2b4c8c", false, false, null, List.of());
    }
}
