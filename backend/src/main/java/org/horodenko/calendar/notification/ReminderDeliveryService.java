package org.horodenko.calendar.notification;

import org.horodenko.calendar.domain.EventReminder;
import org.horodenko.calendar.domain.ReminderDelivery;
import org.horodenko.calendar.repository.EventReminderRepository;
import org.horodenko.calendar.repository.ReminderDeliveryRepository;
import org.horodenko.calendar.web.dto.OccurrenceResponse;
import org.horodenko.calendar.web.dto.ReminderResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Manda um lembrete por um canal e anota o que aconteceu.
 *
 * <p>Cada envio vive na sua propria transacao para que uma falha nao arraste os outros
 * lembretes -- nem os outros canais -- da mesma volta do disparador. A falha e capturada
 * aqui dentro, e nao deixada subir: se a transacao voltasse atras, o registro da tentativa
 * sumiria junto e o mesmo envio seria refeito a cada meio minuto, para sempre.
 *
 * <p>A chamada de rede acontece dentro da transacao. Segurar a conexao do banco por alguns
 * segundos e aceitavel num banco de arquivo de um usuario so, e e o preco de gravar o
 * resultado no mesmo passo em que ele acontece.
 */
@Service
public class ReminderDeliveryService {

    private static final System.Logger log = System.getLogger(ReminderDeliveryService.class.getName());

    private final EventReminderRepository reminderRepository;
    private final ReminderDeliveryRepository deliveryRepository;
    private final ReminderMessageFormatter formatter;
    private final int maxAttempts;

    public ReminderDeliveryService(EventReminderRepository reminderRepository,
                                   ReminderDeliveryRepository deliveryRepository,
                                   ReminderMessageFormatter formatter,
                                   @Value("${calendar.reminders.max-attempts:3}") int maxAttempts) {
        this.reminderRepository = reminderRepository;
        this.deliveryRepository = deliveryRepository;
        this.formatter = formatter;
        this.maxAttempts = maxAttempts;
    }

    /**
     * Envia o aviso desta ocorrencia por um canal, se ele ainda nao saiu por ali.
     *
     * @return {@code true} quando houve uma tentativa de fato, para quem chamou saber
     *         que falou com o mundo
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean deliver(ReminderResponse reminder, OccurrenceResponse occurrence,
                           NotificationChannel channel) {
        Optional<ReminderDelivery> recorded = deliveryRepository.find(
                reminder.id(), occurrence.occurrenceStart(), channel.type());

        if (recorded.filter(this::isSettled).isPresent()) {
            return false;
        }

        ReminderDelivery delivery = recorded.orElse(null);
        if (delivery == null) {
            EventReminder entity = reminderRepository.findById(reminder.id()).orElse(null);
            if (entity == null) {
                // O lembrete foi apagado entre a leitura da agenda e agora.
                return false;
            }
            delivery = new ReminderDelivery(entity, occurrence.occurrenceStart(), channel.type());
        }

        String text = formatter.format(reminder.message(), occurrence, reminder.minutesBefore());
        try {
            channel.send(occurrence.title(), text);
            delivery.recordSuccess();
            log.log(System.Logger.Level.INFO, () -> "Lembrete enviado por " + channel.label()
                    + ": " + occurrence.title() + " (" + occurrence.startAt() + ")");
        } catch (NotificationException | IllegalArgumentException failure) {
            delivery.recordFailure(failure.getMessage());
            log.log(System.Logger.Level.WARNING, () -> "Falha ao enviar por " + channel.label()
                    + " o lembrete de " + occurrence.title() + ": " + failure.getMessage());
        }
        deliveryRepository.save(delivery);
        return true;
    }

    /**
     * Ja resolvido: ou saiu, ou falhou vezes demais.
     *
     * <p>O teto de tentativas evita insistir de meio em meio minuto num token errado ate
     * a hora do evento -- o erro fica gravado e o usuario ve na tela de ajustes.
     */
    private boolean isSettled(ReminderDelivery delivery) {
        return delivery.isSent() || delivery.getAttempts() >= maxAttempts;
    }

    /** Poda o historico de envios; ele so serve para nao repetir mensagem recente. */
    @Transactional
    public int purgeHistoryBefore(Instant before) {
        return deliveryRepository.deleteAttemptedBefore(before);
    }
}
