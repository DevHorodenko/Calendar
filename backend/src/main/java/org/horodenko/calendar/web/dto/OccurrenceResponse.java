package org.horodenko.calendar.web.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Uma aparicao concreta de um evento no calendario.
 *
 * <p>Um evento unico vira uma ocorrencia; uma serie recorrente vira varias, todas
 * com o mesmo {@code seriesId}. O par {@code seriesId} + {@code occurrenceStart}
 * identifica a ocorrencia e e o que o cliente devolve ao editar ou apagar so uma delas.
 */
public record OccurrenceResponse(
        UUID seriesId,
        LocalDateTime occurrenceStart,
        LocalDateTime startAt,
        LocalDateTime endAt,
        boolean allDay,
        String title,
        String description,
        String location,
        String color,
        boolean recurring,
        boolean modified,
        String recurrenceRule,
        /** Avisos da serie a que esta ocorrencia pertence. */
        List<ReminderResponse> reminders
) {

    /**
     * A mesma ocorrencia com os avisos da serie anexados.
     *
     * <p>Os lembretes chegam depois porque sao buscados de uma vez so para todas as
     * series da janela: buscar por ocorrencia daria uma consulta por linha da tela.
     */
    public OccurrenceResponse withReminders(List<ReminderResponse> reminders) {
        return new OccurrenceResponse(seriesId, occurrenceStart, startAt, endAt, allDay, title,
                description, location, color, recurring, modified, recurrenceRule, reminders);
    }
}
