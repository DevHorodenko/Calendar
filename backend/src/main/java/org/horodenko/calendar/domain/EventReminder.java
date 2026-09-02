package org.horodenko.calendar.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Um aviso preso a uma serie: "mande esta mensagem tantos minutos antes".
 *
 * <p>A antecedencia e contada a partir do inicio da ocorrencia, e nao da serie, entao
 * um mesmo lembrete rende um envio por vez que o evento acontece. Ele pertence a serie
 * inteira: uma ocorrencia nao tem lembrete proprio.
 *
 * <p>{@code message} vazia significa usar o texto padrao montado a partir do evento.
 */
@Entity
@Table(name = "event_reminder")
public class EventReminder {

    /** Teto que a migration tambem cobra: 30 dias. */
    public static final int MAX_MINUTES_BEFORE = 30 * 24 * 60;

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(name = "minutes_before", nullable = false)
    private int minutesBefore;

    @Column(length = 1000)
    private String message;

    @Column(nullable = false)
    private boolean enabled;

    protected EventReminder() {
        // exigido pelo JPA
    }

    public EventReminder(int minutesBefore, String message, boolean enabled) {
        this.minutesBefore = minutesBefore;
        this.message = message;
        this.enabled = enabled;
    }

    public UUID getId() {
        return id;
    }

    public Event getEvent() {
        return event;
    }

    void setEvent(Event event) {
        this.event = event;
    }

    public int getMinutesBefore() {
        return minutesBefore;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
