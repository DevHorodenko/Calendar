package org.horodenko.calendar.web.dto;

/**
 * A conversa detectada a partir do que o bot recebeu.
 *
 * <p>{@code found} falso nao e erro: e o caso normal de quem ainda nao mandou nada ao
 * proprio bot, e a tela responde pedindo exatamente isso.
 */
public record TelegramChatResponse(boolean found, String chatId, String name) {

    public static TelegramChatResponse missing() {
        return new TelegramChatResponse(false, null, null);
    }
}
