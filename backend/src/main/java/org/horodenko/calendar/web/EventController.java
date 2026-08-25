package org.horodenko.calendar.web;

import jakarta.validation.Valid;
import org.horodenko.calendar.service.EventService;
import org.horodenko.calendar.web.dto.EditScope;
import org.horodenko.calendar.web.dto.EventRequest;
import org.horodenko.calendar.web.dto.EventSeriesResponse;
import org.horodenko.calendar.web.dto.OccurrenceResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    /**
     * Ocorrencias da janela pedida, ja expandidas. E o unico endpoint que as visoes de
     * ano, mes, semana e dia precisam: cada uma manda o seu proprio intervalo.
     */
    @GetMapping
    public List<OccurrenceResponse> occurrences(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return eventService.findOccurrences(from, to);
    }

    /** As series como estao guardadas, sem expandir. Util para uma tela de gerenciamento. */
    @GetMapping("/series")
    public List<EventSeriesResponse> series() {
        return eventService.findAllSeries();
    }

    @GetMapping("/series/{id}")
    public EventSeriesResponse series(@PathVariable UUID id) {
        return eventService.findSeries(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EventSeriesResponse create(@Valid @RequestBody EventRequest request) {
        return eventService.create(request);
    }

    /**
     * Edita um evento. Em series recorrentes, {@code scope} decide o alcance e
     * {@code occurrenceStart} diz de qual ocorrencia se esta falando.
     */
    @PutMapping("/{id}")
    public EventSeriesResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody EventRequest request,
            @RequestParam(defaultValue = "ALL") EditScope scope,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime occurrenceStart) {
        return eventService.update(id, request, scope, occurrenceStart);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "ALL") EditScope scope,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime occurrenceStart) {
        eventService.delete(id, scope, occurrenceStart);
        return ResponseEntity.noContent().build();
    }
}
