package org.horodenko.calendar.notification;

import org.horodenko.calendar.config.TrayNotifier;
import org.horodenko.calendar.domain.NotificationChannelType;
import org.horodenko.calendar.repository.NotificationSettingsRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * O balao nativo, pendurado no icone da bandeja.
 *
 * <p>Complementa o Telegram em vez de competir com ele: chega na area de trabalho mesmo
 * com o Telegram fechado, e nao depende de internet nem de credencial nenhuma. Em troca,
 * so existe onde ha bandeja -- ou seja, no aplicativo instalado, e nao em desenvolvimento.
 *
 * <p>Quando nao ha bandeja o canal se declara indisponivel, e o disparador simplesmente
 * o ignora. Nao ligar nada e melhor que uma falha registrada a cada lembrete.
 */
@Component
public class WindowsTrayChannel implements NotificationChannel {

    private final NotificationSettingsRepository repository;
    private final Optional<TrayNotifier> tray;

    public WindowsTrayChannel(NotificationSettingsRepository repository, Optional<TrayNotifier> tray) {
        this.repository = repository;
        this.tray = tray;
    }

    @Override
    public NotificationChannelType type() {
        return NotificationChannelType.WINDOWS;
    }

    @Override
    public String label() {
        return "Notificacao do Windows";
    }

    /** Se ha bandeja para receber o balao, independentemente de estar ligado nos ajustes. */
    public boolean isAvailable() {
        return tray.map(TrayNotifier::isAvailable).orElse(false);
    }

    @Override
    public boolean isReady() {
        return isAvailable() && repository.current().isWindowsEnabled();
    }

    @Override
    public void send(String title, String body) {
        TrayNotifier notifier = tray.filter(TrayNotifier::isAvailable).orElseThrow(() ->
                new NotificationException(
                        "Nao ha icone na bandeja para mostrar o aviso. O balao do Windows so "
                                + "funciona no aplicativo instalado."));
        notifier.show(title, body);
    }
}
