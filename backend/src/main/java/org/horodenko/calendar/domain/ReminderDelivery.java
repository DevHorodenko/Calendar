package org.horodenko.calendar.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * O registro de que um lembrete ja foi tratado para uma ocorrencia.
 *
 * <p>E o que impede a repeticao: a ocorrencia continua dentro da janela do disparador
 * ate comecar, entao sem esta linha a mesma mensagem sairia a cada volta do relogio.
 * A ocorrencia e identificada pelo horario <em>original</em> do encaixe, o mesmo criterio
 * que {@link EventOverride} usa.
 *
 * <p>Ha uma linha por canal. Com o Telegram e o balao do Windows ligados ao mesmo tempo,
 * um pode falhar e o outro nao, e cada um precisa poder tentar de novo sozinho -- num
 * registro unico, o sucesso de um esconderia a falha do outro para sempre.
 */
@Entity
@Table(name = "reminder_delivery")
public class ReminderDelivery {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reminder_id", nullable = false)
    private EventReminder reminder;

    @Column(name = "occurrence_start", nullable = false)
    private LocalDateTime occurrenceStart;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationChannelType channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryStatus status;

    @Column(length = 1000)
    private String detail;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "attempted_at", nullable = false)
    private Instant attemptedAt;

    protected ReminderDelivery() {
        // exigido pelo JPA
    }

    public ReminderDelivery(EventReminder reminder, LocalDateTime occurrenceStart,
                            NotificationChannelType channel) {
        this.reminder = reminder;
        this.occurrenceStart = occurrenceStart;
        this.channel = channel;
        this.status = DeliveryStatus.FAILED;
        this.attempts = 0;
        this.attemptedAt = Instant.now();
    }

    public void recordSuccess() {
        this.status = DeliveryStatus.SENT;
        this.detail = null;
        this.attempts++;
        this.attemptedAt = Instant.now();
    }

    /** Guarda so o comeco da mensagem de erro: a coluna tem tamanho, e o log tem o resto. */
    public void recordFailure(String reason) {
        this.status = DeliveryStatus.FAILED;
        this.detail = reason == null ? null : reason.substring(0, Math.min(reason.length(), 1000));
        this.attempts++;
        this.attemptedAt = Instant.now();
    }

    public boolean isSent() {
        return status == DeliveryStatus.SENT;
    }

    public UUID getId() {
        return id;
    }

    public EventReminder getReminder() {
        return reminder;
    }

    public LocalDateTime getOccurrenceStart() {
        return occurrenceStart;
    }

    public NotificationChannelType getChannel() {
        return channel;
    }

    public DeliveryStatus getStatus() {
        return status;
    }

    public String getDetail() {
        return detail;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getAttemptedAt() {
        return attemptedAt;
    }
}
