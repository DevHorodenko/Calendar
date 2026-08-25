package org.horodenko.calendar.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * Dados enviados ao criar ou editar um evento.
 *
 * <p>{@code recurrenceRule} vazio significa evento unico. Ao editar com escopo
 * {@link EditScope#THIS}, a regra e ignorada: uma unica ocorrencia nao tem repeticao propria.
 */
public record EventRequest(

        @NotBlank(message = "O titulo e obrigatorio")
        @Size(max = 255, message = "O titulo deve ter no maximo 255 caracteres")
        String title,

        String description,

        @Size(max = 255, message = "O local deve ter no maximo 255 caracteres")
        String location,

        boolean allDay,

        @NotNull(message = "A data de inicio e obrigatoria")
        LocalDateTime startAt,

        @NotNull(message = "A data de fim e obrigatoria")
        LocalDateTime endAt,

        @Size(max = 500, message = "A regra de recorrencia deve ter no maximo 500 caracteres")
        String recurrenceRule,

        @Size(max = 32, message = "A cor deve ter no maximo 32 caracteres")
        String color
) {

    public boolean hasRecurrence() {
        return recurrenceRule != null && !recurrenceRule.isBlank();
    }

    /** Cor efetiva, caindo no padrao quando o cliente nao mandou nenhuma. */
    public String colorOrDefault() {
        return (color == null || color.isBlank()) ? "blue" : color;
    }
}
