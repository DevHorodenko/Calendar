package org.horodenko.calendar.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Uma serie de calendario.
 *
 * <p>Sem {@code recurrenceRule} a serie e um evento unico e acontece uma vez so.
 * Com a regra preenchida, {@code startAt} e {@code endAt} descrevem a primeira
 * ocorrencia, e a duracao entre eles se repete em todas as outras.
 *
 * <p>Os horarios sao locais e flutuantes: o evento acontece as 14h no fuso de quem
 * olha, sem conversao. Para um calendario pessoal isso evita que um horario mude
 * sozinho quando o horario de verao entra ou sai.
 */
@Entity
@Table(name = "event")
public class Event {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(length = 4000)
    private String description;

    private String location;

    @Column(name = "all_day", nullable = false)
    private boolean allDay;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    /** RRULE da RFC 5545 sem o prefixo, ou {@code null} para um evento unico. */
    @Column(name = "recurrence_rule")
    private String recurrenceRule;

    @Column(nullable = false)
    private String color;

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<EventOverride> overrides = new ArrayList<>();

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<EventReminder> reminders = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Event() {
        // exigido pelo JPA
    }

    public Event(String title, String description, String location, boolean allDay,
                 LocalDateTime startAt, LocalDateTime endAt, String recurrenceRule, String color) {
        this.title = title;
        this.description = description;
        this.location = location;
        this.allDay = allDay;
        this.startAt = startAt;
        this.endAt = endAt;
        this.recurrenceRule = recurrenceRule;
        this.color = color;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public boolean isRecurring() {
        return recurrenceRule != null && !recurrenceRule.isBlank();
    }

    /** Duracao de cada ocorrencia, replicada da primeira. */
    public java.time.Duration duration() {
        return java.time.Duration.between(startAt, endAt);
    }

    public void addOverride(EventOverride override) {
        overrides.add(override);
        override.setEvent(this);
    }

    public void removeOverride(EventOverride override) {
        overrides.remove(override);
        override.setEvent(null);
    }

    public void addReminder(EventReminder reminder) {
        reminders.add(reminder);
        reminder.setEvent(this);
    }

    public UUID getId() {
        return id;
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

    public boolean isAllDay() {
        return allDay;
    }

    public void setAllDay(boolean allDay) {
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

    public String getRecurrenceRule() {
        return recurrenceRule;
    }

    public void setRecurrenceRule(String recurrenceRule) {
        this.recurrenceRule = recurrenceRule;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public List<EventOverride> getOverrides() {
        return overrides;
    }

    public List<EventReminder> getReminders() {
        return reminders;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
