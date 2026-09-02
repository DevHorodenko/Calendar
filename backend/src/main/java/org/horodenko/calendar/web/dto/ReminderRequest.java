package org.horodenko.calendar.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.horodenko.calendar.domain.EventReminder;

/**
 * Um aviso pedido para o evento: quanto tempo antes, e o que dizer.
 *
 * <p>{@code message} em branco significa deixar o texto padrao, montado a partir do evento.
 * {@code enabled} ausente vale como ligado: quem acabou de criar o lembrete o quer valendo.
 */
public record ReminderRequest(

        @NotNull(message = "Diga quanto tempo antes o aviso deve sair")
        @Min(value = 0, message = "A antecedencia nao pode ser negativa")
        @Max(value = EventReminder.MAX_MINUTES_BEFORE,
                message = "A antecedencia maxima e de 30 dias")
        Integer minutesBefore,

        @Size(max = 1000, message = "A mensagem deve ter no maximo 1000 caracteres")
        String message,

        Boolean enabled
) {

    public boolean enabledOrDefault() {
        return enabled == null || enabled;
    }

    /** Texto normalizado: em branco e o mesmo que nao ter mensagem propria. */
    public String messageOrNull() {
        return (message == null || message.isBlank()) ? null : message.strip();
    }
}
