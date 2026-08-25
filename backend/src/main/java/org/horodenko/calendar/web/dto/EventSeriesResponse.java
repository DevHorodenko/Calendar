package org.horodenko.calendar.web.dto;

import org.horodenko.calendar.domain.Event;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/** A serie inteira, como ela esta guardada, sem expandir as ocorrencias. */
public record EventSeriesResponse(
        UUID id,
        String title,
        String description,
        String location,
        boolean allDay,
        LocalDateTime startAt,
        LocalDateTime endAt,
        String recurrenceRule,
        String color,
        boolean recurring,
        Instant createdAt,
        Instant updatedAt
) {

    public static EventSeriesResponse from(Event event) {
        return new EventSeriesResponse(
                event.getId(),
                event.getTitle(),
                event.getDescription(),
                event.getLocation(),
                event.isAllDay(),
                event.getStartAt(),
                event.getEndAt(),
                event.getRecurrenceRule(),
                event.getColor(),
                event.isRecurring(),
                event.getCreatedAt(),
                event.getUpdatedAt()
        );
    }
}
