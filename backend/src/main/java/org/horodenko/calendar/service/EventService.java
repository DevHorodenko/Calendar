package org.horodenko.calendar.service;

import org.horodenko.calendar.domain.Event;
import org.horodenko.calendar.domain.EventOverride;
import org.horodenko.calendar.domain.OverrideType;
import org.horodenko.calendar.recurrence.RecurrenceExpander;
import org.horodenko.calendar.recurrence.RecurrenceRule;
import org.horodenko.calendar.repository.EventOverrideRepository;
import org.horodenko.calendar.repository.EventRepository;
import org.horodenko.calendar.web.dto.EditScope;
import org.horodenko.calendar.web.dto.EventRequest;
import org.horodenko.calendar.web.dto.EventSeriesResponse;
import org.horodenko.calendar.web.dto.OccurrenceResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Regras do calendario: guarda series e devolve as ocorrencias concretas que
 * o cliente desenha na tela.
 */
@Service
public class EventService {

    private final EventRepository eventRepository;
    private final EventOverrideRepository overrideRepository;
    private final RecurrenceExpander expander;
    private final long maxRangeDays;

    public EventService(EventRepository eventRepository,
                        EventOverrideRepository overrideRepository,
                        RecurrenceExpander expander,
                        @Value("${calendar.max-range-days:800}") long maxRangeDays) {
        this.eventRepository = eventRepository;
        this.overrideRepository = overrideRepository;
        this.expander = expander;
        this.maxRangeDays = maxRangeDays;
    }

    // ---------------------------------------------------------------- leitura

    /**
     * Todas as ocorrencias que aparecem entre {@code from} (inclusivo) e {@code to} (exclusivo),
     * ja com as excecoes de serie aplicadas e ordenadas por horario de inicio.
     */
    @Transactional(readOnly = true)
    public List<OccurrenceResponse> findOccurrences(LocalDateTime from, LocalDateTime to) {
        validateRange(from, to);

        List<OccurrenceResponse> occurrences = new ArrayList<>();
        Set<OccurrenceKey> emitted = new HashSet<>();

        for (Event event : eventRepository.findSingleEventsInWindow(from, to)) {
            occurrences.add(toOccurrence(event, event.getStartAt(), event.getStartAt(), event.getEndAt(), false));
            emitted.add(new OccurrenceKey(event.getId(), event.getStartAt()));
        }

        for (Event series : eventRepository.findRecurringSeriesStartedBefore(to)) {
            expandSeries(series, from, to, occurrences, emitted);
        }

        addOccurrencesRescheduledIntoWindow(from, to, occurrences, emitted);

        occurrences.sort(Comparator.comparing(OccurrenceResponse::startAt)
                .thenComparing(OccurrenceResponse::title, Comparator.nullsLast(String::compareTo)));
        return occurrences;
    }

    private void expandSeries(Event series, LocalDateTime from, LocalDateTime to,
                              List<OccurrenceResponse> target, Set<OccurrenceKey> emitted) {
        RecurrenceRule rule = RecurrenceRule.parse(series.getRecurrenceRule());
        Duration duration = series.duration();
        Map<LocalDateTime, EventOverride> overrides = overridesByOccurrence(series);

        // Uma ocorrencia que comecou antes da janela ainda pode invadi-la, entao a
        // expansao recua o tamanho de uma ocorrencia antes de olhar a janela.
        LocalDateTime expandFrom = from.minus(duration);

        for (LocalDateTime slot : expander.expand(series.getStartAt(), rule, expandFrom, to)) {
            EventOverride override = overrides.get(slot);
            if (override != null && override.isCancelled()) {
                continue;
            }

            LocalDateTime start = slot;
            LocalDateTime end = slot.plus(duration);
            if (override != null) {
                start = override.getStartAt() != null ? override.getStartAt() : start;
                end = override.getEndAt() != null ? override.getEndAt() : end;
            }
            if (!overlapsWindow(start, end, from, to)) {
                continue;
            }

            target.add(toOccurrence(series, slot, start, end, override));
            emitted.add(new OccurrenceKey(series.getId(), slot));
        }
    }

    /** Recupera ocorrencias que foram remarcadas de fora da janela para dentro dela. */
    private void addOccurrencesRescheduledIntoWindow(LocalDateTime from, LocalDateTime to,
                                                     List<OccurrenceResponse> target,
                                                     Set<OccurrenceKey> emitted) {
        for (EventOverride override : overrideRepository.findRescheduledIntoWindow(from, to)) {
            Event series = override.getEvent();
            OccurrenceKey key = new OccurrenceKey(series.getId(), override.getOccurrenceStart());
            if (emitted.contains(key)) {
                continue;
            }
            LocalDateTime start = override.getStartAt();
            LocalDateTime end = override.getEndAt() != null ? override.getEndAt() : start.plus(series.duration());
            if (!overlapsWindow(start, end, from, to)) {
                continue;
            }
            target.add(toOccurrence(series, override.getOccurrenceStart(), start, end, override));
            emitted.add(key);
        }
    }

    @Transactional(readOnly = true)
    public List<EventSeriesResponse> findAllSeries() {
        return eventRepository.findAllSeries().stream().map(EventSeriesResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public EventSeriesResponse findSeries(UUID id) {
        return EventSeriesResponse.from(requireEvent(id));
    }

    // ---------------------------------------------------------------- escrita

    @Transactional
    public EventSeriesResponse create(EventRequest request) {
        validateRequest(request);
        Event event = new Event(
                request.title(),
                request.description(),
                request.location(),
                request.allDay(),
                request.startAt(),
                request.endAt(),
                request.hasRecurrence() ? normalizeRule(request.recurrenceRule()) : null,
                request.colorOrDefault()
        );
        return EventSeriesResponse.from(eventRepository.save(event));
    }

    /**
     * Edita um evento. Para series recorrentes o {@code scope} decide o que muda, e
     * {@code occurrenceStart} aponta qual ocorrencia serve de referencia.
     */
    @Transactional
    public EventSeriesResponse update(UUID id, EventRequest request, EditScope scope, LocalDateTime occurrenceStart) {
        validateRequest(request);
        Event event = requireEvent(id);

        if (!event.isRecurring() || scope == EditScope.ALL) {
            return EventSeriesResponse.from(updateWholeSeries(event, request));
        }
        requireOccurrence(event, occurrenceStart);

        return switch (scope) {
            case THIS -> EventSeriesResponse.from(updateSingleOccurrence(event, request, occurrenceStart));
            case THIS_AND_FUTURE -> EventSeriesResponse.from(splitSeries(event, request, occurrenceStart));
            case ALL -> throw new IllegalStateException("ALL ja foi tratado acima");
        };
    }

    private Event updateWholeSeries(Event event, EventRequest request) {
        String newRule = request.hasRecurrence() ? normalizeRule(request.recurrenceRule()) : null;

        // Mudar o inicio ou a regra move todos os horarios da serie; as excecoes
        // antigas apontariam para ocorrencias que nao existem mais.
        boolean timelineChanged = !Objects.equals(event.getRecurrenceRule(), newRule)
                || !Objects.equals(event.getStartAt(), request.startAt());
        if (timelineChanged) {
            event.getOverrides().clear();
        }

        event.setTitle(request.title());
        event.setDescription(request.description());
        event.setLocation(request.location());
        event.setAllDay(request.allDay());
        event.setStartAt(request.startAt());
        event.setEndAt(request.endAt());
        event.setRecurrenceRule(newRule);
        event.setColor(request.colorOrDefault());
        return event;
    }

    private Event updateSingleOccurrence(Event event, EventRequest request, LocalDateTime occurrenceStart) {
        EventOverride override = findOverride(event, occurrenceStart);
        if (override == null) {
            override = EventOverride.modification(occurrenceStart);
            event.addOverride(override);
        }
        override.setType(OverrideType.MODIFIED);
        override.setTitle(request.title());
        override.setDescription(request.description());
        override.setLocation(request.location());
        override.setAllDay(request.allDay());
        override.setStartAt(request.startAt());
        override.setEndAt(request.endAt());
        override.setColor(request.colorOrDefault());
        return event;
    }

    /**
     * Encerra a serie original na vespera da ocorrencia escolhida e cria uma serie
     * nova a partir dela com os dados enviados. As excecoes que caiam depois do corte
     * sao descartadas: elas pertenciam a horarios que a serie nova nao tem mais.
     */
    private Event splitSeries(Event event, EventRequest request, LocalDateTime occurrenceStart) {
        if (!occurrenceStart.isAfter(event.getStartAt())) {
            return updateWholeSeries(event, request);
        }

        RecurrenceRule original = RecurrenceRule.parse(event.getRecurrenceRule());
        event.setRecurrenceRule(original.truncatedAt(occurrenceStart.minusSeconds(1)).toRrule());
        event.getOverrides().removeIf(override -> !override.getOccurrenceStart().isBefore(occurrenceStart));

        String newRule = request.hasRecurrence()
                ? normalizeRule(request.recurrenceRule())
                : event.getRecurrenceRule();

        Event tail = new Event(
                request.title(),
                request.description(),
                request.location(),
                request.allDay(),
                request.startAt(),
                request.endAt(),
                newRule,
                request.colorOrDefault()
        );
        return eventRepository.save(tail);
    }

    @Transactional
    public void delete(UUID id, EditScope scope, LocalDateTime occurrenceStart) {
        Event event = requireEvent(id);

        if (!event.isRecurring() || scope == EditScope.ALL) {
            eventRepository.delete(event);
            return;
        }
        requireOccurrence(event, occurrenceStart);

        switch (scope) {
            case THIS -> cancelOccurrence(event, occurrenceStart);
            case THIS_AND_FUTURE -> truncateSeries(event, occurrenceStart);
            case ALL -> eventRepository.delete(event);
        }
    }

    private void cancelOccurrence(Event event, LocalDateTime occurrenceStart) {
        EventOverride override = findOverride(event, occurrenceStart);
        if (override == null) {
            event.addOverride(EventOverride.cancellation(occurrenceStart));
        } else {
            override.setType(OverrideType.CANCELLED);
        }
    }

    private void truncateSeries(Event event, LocalDateTime occurrenceStart) {
        if (!occurrenceStart.isAfter(event.getStartAt())) {
            eventRepository.delete(event);
            return;
        }
        RecurrenceRule rule = RecurrenceRule.parse(event.getRecurrenceRule());
        event.setRecurrenceRule(rule.truncatedAt(occurrenceStart.minusSeconds(1)).toRrule());
        event.getOverrides().removeIf(override -> !override.getOccurrenceStart().isBefore(occurrenceStart));
    }

    // ---------------------------------------------------------------- apoio

    private Event requireEvent(UUID id) {
        return eventRepository.findByIdWithOverrides(id).orElseThrow(() -> new EventNotFoundException(id));
    }

    /** Garante que occurrenceStart aponta mesmo para uma ocorrencia gerada pela regra. */
    private void requireOccurrence(Event event, LocalDateTime occurrenceStart) {
        if (occurrenceStart == null) {
            throw new IllegalArgumentException(
                    "occurrenceStart e obrigatorio para editar ou apagar parte de uma serie recorrente");
        }
        RecurrenceRule rule = RecurrenceRule.parse(event.getRecurrenceRule());
        if (!expander.occursAt(event.getStartAt(), rule, occurrenceStart)) {
            throw new IllegalArgumentException(
                    "A serie nao tem nenhuma ocorrencia comecando em " + occurrenceStart);
        }
    }

    private EventOverride findOverride(Event event, LocalDateTime occurrenceStart) {
        return event.getOverrides().stream()
                .filter(override -> override.getOccurrenceStart().equals(occurrenceStart))
                .findFirst()
                .orElse(null);
    }

    private Map<LocalDateTime, EventOverride> overridesByOccurrence(Event event) {
        Map<LocalDateTime, EventOverride> byOccurrence = new HashMap<>();
        for (EventOverride override : event.getOverrides()) {
            byOccurrence.put(override.getOccurrenceStart(), override);
        }
        return byOccurrence;
    }

    private boolean overlapsWindow(LocalDateTime start, LocalDateTime end,
                                   LocalDateTime windowStart, LocalDateTime windowEnd) {
        if (!start.isBefore(windowEnd)) {
            return false;
        }
        return end.isAfter(windowStart) || end.equals(start) && !start.isBefore(windowStart);
    }

    private OccurrenceResponse toOccurrence(Event event, LocalDateTime slot, LocalDateTime start,
                                            LocalDateTime end, EventOverride override) {
        if (override == null) {
            return toOccurrence(event, slot, start, end, false);
        }
        return new OccurrenceResponse(
                event.getId(),
                slot,
                start,
                end,
                override.getAllDay() != null ? override.getAllDay() : event.isAllDay(),
                override.getTitle() != null ? override.getTitle() : event.getTitle(),
                override.getDescription() != null ? override.getDescription() : event.getDescription(),
                override.getLocation() != null ? override.getLocation() : event.getLocation(),
                override.getColor() != null ? override.getColor() : event.getColor(),
                true,
                true,
                event.getRecurrenceRule()
        );
    }

    private OccurrenceResponse toOccurrence(Event event, LocalDateTime slot, LocalDateTime start,
                                            LocalDateTime end, boolean modified) {
        return new OccurrenceResponse(
                event.getId(),
                slot,
                start,
                end,
                event.isAllDay(),
                event.getTitle(),
                event.getDescription(),
                event.getLocation(),
                event.getColor(),
                event.isRecurring(),
                modified,
                event.getRecurrenceRule()
        );
    }

    private void validateRange(LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("Os parametros from e to sao obrigatorios");
        }
        if (!from.isBefore(to)) {
            throw new IllegalArgumentException("O parametro from deve ser anterior a to");
        }
        long days = ChronoUnit.DAYS.between(from, to);
        if (days > maxRangeDays) {
            throw new IllegalArgumentException(
                    "Intervalo grande demais: " + days + " dias (o maximo e " + maxRangeDays + ")");
        }
    }

    private void validateRequest(EventRequest request) {
        if (request.endAt().isBefore(request.startAt())) {
            throw new IllegalArgumentException("A data de fim nao pode ser anterior a de inicio");
        }
        if (request.hasRecurrence()) {
            // Erro de regra vira 400 aqui, e nao uma serie invalida que so quebra na leitura.
            RecurrenceRule.parse(request.recurrenceRule());
        }
    }

    private String normalizeRule(String rrule) {
        return RecurrenceRule.parse(rrule).toRrule();
    }

    /** Identidade de uma ocorrencia: a serie mais o horario original do encaixe. */
    private record OccurrenceKey(UUID seriesId, LocalDateTime occurrenceStart) {
    }
}
