package org.horodenko.calendar.web.dto;

import org.horodenko.calendar.domain.EventReminder;

import java.util.UUID;

/** Um aviso como esta guardado, para a tela devolver o que ja existia. */
public record ReminderResponse(
        UUID id,
        int minutesBefore,
        String message,
        boolean enabled
) {

    public static ReminderResponse from(EventReminder reminder) {
        return new ReminderResponse(
                reminder.getId(),
                reminder.getMinutesBefore(),
                reminder.getMessage(),
                reminder.isEnabled()
        );
    }
}
