package org.horodenko.calendar.notification;

import org.horodenko.calendar.domain.NotificationChannelType;

/**
 * Por onde um lembrete sai.
 *
 * <p>Cada canal le a propria configuracao e responde se tem como enviar agora, para o
 * disparador nao precisar conhecer o que cada um exige -- o Telegram quer token e chat,
 * a bandeja do Windows nao quer nada, mas so existe no aplicativo instalado.
 *
 * <p>Adicionar um canal e escrever mais uma implementacao: o Spring recolhe todas, e nem
 * o agendamento nem o registro de envio mudam.
 */
public interface NotificationChannel {

    NotificationChannelType type();

    /** Nome curto para a tela e para as mensagens de erro. */
    String label();

    /**
     * Se o canal esta ligado e tem tudo de que precisa. Falso aqui nao e erro: e um canal
     * que o usuario nao quis, ou que ainda nao terminou de configurar.
     */
    boolean isReady();

    /**
     * @param title  cabecalho curto, normalmente o titulo do evento
     * @param body   o texto do lembrete, ja montado
     * @throws NotificationException quando o aviso nao foi aceito
     */
    void send(String title, String body);
}
