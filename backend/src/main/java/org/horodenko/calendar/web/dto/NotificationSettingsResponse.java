package org.horodenko.calendar.web.dto;

import org.horodenko.calendar.domain.NotificationSettings;

import java.time.Instant;

/**
 * Os ajustes como a tela os ve.
 *
 * <p>O token nao volta: a tela so precisa saber se ha um gravado, para mostrar o campo
 * preenchido sem colocar o segredo na resposta.
 *
 * <p>{@code telegramEnabled} e {@code windowsEnabled} sao o que o usuario pediu;
 * {@code ready} e se algum canal tem como acontecer. Os dois divergem enquanto a ficha
 * esta pela metade, e e {@code ready} que a tela deve consultar antes de prometer que um
 * aviso vai sair.
 */
public record NotificationSettingsResponse(
        boolean telegramEnabled,
        boolean telegramTokenSet,
        String telegramChatId,
        boolean windowsEnabled,
        /** Se existe bandeja nesta instalacao. Falso em desenvolvimento, sem area de trabalho. */
        boolean windowsAvailable,
        boolean ready,
        Instant updatedAt
) {

    public static NotificationSettingsResponse from(NotificationSettings settings,
                                                    boolean windowsAvailable) {
        boolean windowsReady = windowsAvailable && settings.isWindowsEnabled();
        return new NotificationSettingsResponse(
                settings.isTelegramEnabled(),
                settings.hasTelegramToken(),
                settings.getTelegramChatId(),
                settings.isWindowsEnabled(),
                windowsAvailable,
                settings.isTelegramUsable() || windowsReady,
                settings.getUpdatedAt()
        );
    }
}
