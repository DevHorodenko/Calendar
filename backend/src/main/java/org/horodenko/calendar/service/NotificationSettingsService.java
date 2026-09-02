package org.horodenko.calendar.service;

import org.horodenko.calendar.domain.NotificationSettings;
import org.horodenko.calendar.notification.NotificationChannel;
import org.horodenko.calendar.notification.NotificationException;
import org.horodenko.calendar.notification.TelegramChannel;
import org.horodenko.calendar.notification.WindowsTrayChannel;
import org.horodenko.calendar.repository.NotificationSettingsRepository;
import org.horodenko.calendar.web.dto.ChannelTestResponse;
import org.horodenko.calendar.web.dto.NotificationSettingsRequest;
import org.horodenko.calendar.web.dto.NotificationSettingsResponse;
import org.horodenko.calendar.web.dto.TelegramChatResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Guarda por onde os avisos saem, e deixa o usuario conferir antes de confiar.
 *
 * <p>A linha existe desde a migration, entao aqui nunca se cria nem se apaga nada:
 * so se le e se atualiza.
 */
@Service
public class NotificationSettingsService {

    private final NotificationSettingsRepository repository;
    private final List<NotificationChannel> channels;
    private final TelegramChannel telegram;
    private final WindowsTrayChannel windows;

    public NotificationSettingsService(NotificationSettingsRepository repository,
                                       List<NotificationChannel> channels,
                                       TelegramChannel telegram,
                                       WindowsTrayChannel windows) {
        this.repository = repository;
        this.channels = channels;
        this.telegram = telegram;
        this.windows = windows;
    }

    @Transactional(readOnly = true)
    public NotificationSettingsResponse view() {
        return NotificationSettingsResponse.from(repository.current(), windows.isAvailable());
    }

    /**
     * Grava o que houver, mesmo incompleto.
     *
     * <p>Ficha pela metade e o estado normal de quem esta comecando: o token vem do
     * BotFather e o chat so aparece depois de falar com o bot, entao exigir tudo de uma
     * vez obrigaria a digitar de novo mais tarde. E nao protegeria nada -- quem decide se
     * da para enviar e {@link NotificationSettings#isTelegramUsable()}, conferido a cada
     * volta do disparador.
     */
    @Transactional
    public NotificationSettingsResponse save(NotificationSettingsRequest request) {
        NotificationSettings settings = repository.current();

        // Token em branco quer dizer "mantenha o que ja esta": a tela nunca recebe o
        // token de volta e portanto nao teria como reenviar o que existe.
        if (request.hasNewToken()) {
            settings.setTelegramBotToken(request.telegramBotToken().strip());
        }
        settings.setTelegramChatId(request.chatIdOrNull());
        settings.setTelegramEnabled(request.telegramEnabled());
        settings.setWindowsEnabled(request.windowsEnabled());

        return NotificationSettingsResponse.from(settings, windows.isAvailable());
    }

    /**
     * Manda uma mensagem de conferencia por cada canal ligado.
     *
     * <p>Devolve o resultado de cada um em vez de estourar no primeiro erro: com dois
     * canais, saber que o balao apareceu e o Telegram nao respondeu vale mais do que uma
     * falha generica.
     */
    public ChannelTestResponse sendTestMessage() {
        List<NotificationChannel> ready = channels.stream().filter(NotificationChannel::isReady).toList();
        if (ready.isEmpty()) {
            throw new IllegalArgumentException(
                    "Nenhum canal esta pronto. Ligue o Telegram e preencha token e conversa, "
                            + "ou ligue a notificacao do Windows no aplicativo instalado.");
        }

        List<ChannelTestResponse.Result> results = new ArrayList<>();
        for (NotificationChannel channel : ready) {
            try {
                channel.send("Calendario",
                        "Teste de conexao. Se voce recebeu isto, os lembretes vao chegar.");
                results.add(ChannelTestResponse.Result.sent(channel.label()));
            } catch (NotificationException | IllegalArgumentException failure) {
                results.add(ChannelTestResponse.Result.failed(channel.label(), failure.getMessage()));
            }
        }
        return new ChannelTestResponse(results);
    }

    /**
     * Descobre o chat a partir das mensagens que o bot recebeu, para o usuario nao ter
     * de caçar um numero que a interface do Telegram nao mostra em lugar nenhum.
     */
    @Transactional
    public TelegramChatResponse detectTelegramChat() {
        NotificationSettings settings = repository.current();
        if (!settings.hasTelegramToken()) {
            throw new IllegalArgumentException("Grave o token do bot antes de detectar a conversa");
        }
        return telegram.detectChat(settings.getTelegramBotToken())
                .map(chat -> {
                    // Achou: ja grava, porque e sempre isso que o usuario quer em seguida.
                    settings.setTelegramChatId(chat.id());
                    return new TelegramChatResponse(true, chat.id(), chat.name());
                })
                .orElseGet(TelegramChatResponse::missing);
    }
}
