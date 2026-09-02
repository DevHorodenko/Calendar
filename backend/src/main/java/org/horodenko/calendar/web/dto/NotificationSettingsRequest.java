package org.horodenko.calendar.web.dto;

import jakarta.validation.constraints.Size;

/**
 * Ajustes de por onde os avisos saem.
 *
 * <p>{@code telegramBotToken} em branco quer dizer "mantenha o que ja esta gravado": a
 * tela nunca recebe o token de volta, entao ela nao teria como reenviar o que nao viu.
 */
public record NotificationSettingsRequest(

        boolean telegramEnabled,

        @Size(max = 128, message = "O token do bot deve ter no maximo 128 caracteres")
        String telegramBotToken,

        @Size(max = 64, message = "O chat deve ter no maximo 64 caracteres")
        String telegramChatId,

        boolean windowsEnabled
) {

    public boolean hasNewToken() {
        return telegramBotToken != null && !telegramBotToken.isBlank();
    }

    public String chatIdOrNull() {
        return (telegramChatId == null || telegramChatId.isBlank()) ? null : telegramChatId.strip();
    }
}
