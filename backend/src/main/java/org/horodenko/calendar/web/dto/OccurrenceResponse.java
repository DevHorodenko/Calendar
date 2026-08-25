package org.horodenko.calendar.web.dto;

import java.time.LocalDateTime;
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
        String recurrenceRule
) {
}
