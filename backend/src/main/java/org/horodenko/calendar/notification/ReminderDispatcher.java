package org.horodenko.calendar.notification;

import org.horodenko.calendar.repository.EventReminderRepository;
import org.horodenko.calendar.service.EventService;
import org.horodenko.calendar.web.dto.OccurrenceResponse;
import org.horodenko.calendar.web.dto.ReminderResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Olha a agenda de tempos em tempos e manda os avisos que venceram.
 *
 * <p>Nao ha fila nem agendamento por evento: a cada volta o disparador pergunta quais
 * ocorrencias comecam dentro da maior antecedencia configurada e ve, entre elas, quais
 * ja passaram do horario de aviso. Assim uma serie recorrente nao precisa de nada
 * agendado de antemao, e mudar o horario de um evento nao deixa aviso orfao para tras --
 * a proxima volta ja pergunta de novo.
 *
 * <p>Cada lembrete vencido sai por todos os canais prontos, e cada canal guarda o proprio
 * registro de envio: o balao do Windows entregue nao apaga um Telegram que falhou.
 *
 * <p>Como o aplicativo mora na bandeja, o disparador so roda com ele aberto. Um aviso
 * que venceu com o computador desligado sai assim que o Calendario volta, contanto que
 * o evento ainda nao tenha comecado.
 */
@Component
@ConditionalOnProperty(name = "calendar.reminders.enabled", havingValue = "true", matchIfMissing = true)
public class ReminderDispatcher {

    private static final System.Logger log = System.getLogger(ReminderDispatcher.class.getName());

    /**
     * Atraso que ainda vale a pena avisar. Um lembrete que so venceu depois que o evento
     * comecou perdeu a graca; alguns minutos de folga cobrem a volta do relogio e o
     * aplicativo que acabou de subir.
     */
    private static final Duration LATE_TOLERANCE = Duration.ofMinutes(5);

    private final EventService eventService;
    private final EventReminderRepository reminderRepository;
    private final List<NotificationChannel> channels;
    private final ReminderDeliveryService deliveryService;
    private final Duration historyRetention;

    public ReminderDispatcher(EventService eventService,
                              EventReminderRepository reminderRepository,
                              List<NotificationChannel> channels,
                              ReminderDeliveryService deliveryService,
                              @Value("${calendar.reminders.history-days:60}") long historyDays) {
        this.eventService = eventService;
        this.reminderRepository = reminderRepository;
        this.channels = channels;
        this.deliveryService = deliveryService;
        this.historyRetention = Duration.ofDays(historyDays);
    }

    @Scheduled(fixedDelayString = "${calendar.reminders.poll-millis:30000}",
            initialDelayString = "${calendar.reminders.initial-delay-millis:20000}")
    public void dispatchDue() {
        try {
            List<NotificationChannel> ready = channels.stream()
                    .filter(NotificationChannel::isReady)
                    .toList();
            if (ready.isEmpty()) {
                // Nenhum canal configurado: nao ha o que tentar, e nem vale ler a agenda.
                return;
            }
            Integer largestLead = reminderRepository.findLargestEnabledLead();
            if (largestLead == null) {
                return;
            }
            send(ready, largestLead);
        } catch (RuntimeException e) {
            // O agendamento continua na proxima volta; o que nao pode e a excecao
            // escapar e derrubar a tarefa em silencio.
            log.log(System.Logger.Level.ERROR, "Falha ao varrer os lembretes vencidos.", e);
        }
    }

    private void send(List<NotificationChannel> ready, int largestLead) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime from = now.minus(LATE_TOLERANCE);
        // Um minuto a mais fecha o intervalo aberto: sem ele a ocorrencia que comeca
        // exatamente na ponta da janela ficaria de fora justo na volta em que venceu.
        LocalDateTime to = now.plusMinutes(largestLead + 1L);

        for (OccurrenceResponse occurrence : eventService.findOccurrences(from, to)) {
            if (occurrence.startAt().isBefore(from)) {
                // Evento longo que ja estava em andamento quando a janela abriu.
                continue;
            }
            for (ReminderResponse reminder : occurrence.reminders()) {
                if (!reminder.enabled()) {
                    continue;
                }
                LocalDateTime fireAt = occurrence.startAt().minusMinutes(reminder.minutesBefore());
                if (fireAt.isAfter(now)) {
                    continue;
                }
                for (NotificationChannel channel : ready) {
                    deliveryService.deliver(reminder, occurrence, channel);
                }
            }
        }
    }

    /**
     * Limpa o registro de envios antigos. Ele existe para impedir mensagem repetida, e
     * uma ocorrencia de dois meses atras nao tem como voltar a vencer.
     */
    @Scheduled(cron = "${calendar.reminders.purge-cron:0 40 3 * * *}")
    public void purgeOldDeliveries() {
        try {
            int removed = deliveryService.purgeHistoryBefore(
                    Instant.now().minus(historyRetention.toDays(), ChronoUnit.DAYS));
            if (removed > 0) {
                log.log(System.Logger.Level.INFO,
                        () -> "Historico de lembretes podado: " + removed + " registros.");
            }
        } catch (RuntimeException e) {
            log.log(System.Logger.Level.WARNING, "Falha ao podar o historico de lembretes.", e);
        }
    }
}
