package org.horodenko.calendar.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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

        // A cor vem da roda do cliente, entao pode ser qualquer valor. Recusar aqui o
        // que nao for hex impede que texto arbitrario acabe dentro de uma regra CSS.
        @Pattern(regexp = "^$|^#[0-9a-fA-F]{6}$",
                message = "A cor deve estar no formato hexadecimal, como #8f2f1d")
        String color
) {

    /** Azur, o mesmo padrao que a migration gravou nas linhas antigas. */
    public static final String DEFAULT_COLOR = "#2b4c8c";

    public boolean hasRecurrence() {
        return recurrenceRule != null && !recurrenceRule.isBlank();
    }

    /** Cor efetiva em minusculas, caindo no padrao quando o cliente nao mandou nenhuma. */
    public String colorOrDefault() {
        return (color == null || color.isBlank()) ? DEFAULT_COLOR : color.toLowerCase(java.util.Locale.ROOT);
    }
}
