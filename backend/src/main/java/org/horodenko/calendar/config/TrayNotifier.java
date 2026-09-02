package org.horodenko.calendar.config;

/**
 * Quem consegue mostrar um balao na area de trabalho.
 *
 * <p>Existe para o canal de notificacao nao depender do {@link DesktopLauncher} inteiro,
 * que so existe no perfil {@code desktop}: em desenvolvimento nao ha bandeja, e o canal
 * simplesmente se declara indisponivel em vez de quebrar a subida.
 */
public interface TrayNotifier {

    /** Se ha mesmo um icone na bandeja para pendurar o balao. */
    boolean isAvailable();

    void show(String title, String message);
}
