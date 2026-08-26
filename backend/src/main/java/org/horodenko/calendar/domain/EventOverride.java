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

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A excecao de uma ocorrencia dentro de uma serie recorrente: aquela terca em que
 * a reuniao foi cancelada, ou aquela em que ela mudou de horario.
 *
 * <p>A ocorrencia e identificada por {@code occurrenceStart}, que guarda o horario
 * <em>original</em> gerado pela regra. Esse valor nao muda quando a ocorrencia e
 * remarcada, senao a excecao perderia a ocorrencia a que se refere.
 */
@Entity
@Table(name = "event_override")
public class EventOverride {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(name = "occurrence_start", nullable = false)
    private LocalDateTime occurrenceStart;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OverrideType type;

    private String title;

    @Column(length = 4000)
    private String description;

    private String location;

    @Column(name = "all_day")
    private Boolean allDay;

    @Column(name = "start_at")
    private LocalDateTime startAt;

    @Column(name = "end_at")
    private LocalDateTime endAt;

    private String color;

    protected EventOverride() {
        // exigido pelo JPA
    }

    private EventOverride(LocalDateTime occurrenceStart, OverrideType type) {
        this.occurrenceStart = occurrenceStart;
        this.type = type;
    }

    public static EventOverride cancellation(LocalDateTime occurrenceStart) {
        return new EventOverride(occurrenceStart, OverrideType.CANCELLED);
    }

    public static EventOverride modification(LocalDateTime occurrenceStart) {
        return new EventOverride(occurrenceStart, OverrideType.MODIFIED);
    }

    public boolean isCancelled() {
        return type == OverrideType.CANCELLED;
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

    public LocalDateTime getOccurrenceStart() {
        return occurrenceStart;
    }

    public OverrideType getType() {
        return type;
    }

    public void setType(OverrideType type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public Boolean getAllDay() {
        return allDay;
    }

    public void setAllDay(Boolean allDay) {
        this.allDay = allDay;
    }

    public LocalDateTime getStartAt() {
        return startAt;
    }

    public void setStartAt(LocalDateTime startAt) {
        this.startAt = startAt;
    }

    public LocalDateTime getEndAt() {
        return endAt;
    }

    public void setEndAt(LocalDateTime endAt) {
        this.endAt = endAt;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }
}
