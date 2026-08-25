package org.horodenko.calendar.service;

import java.util.UUID;

public class EventNotFoundException extends RuntimeException {

    public EventNotFoundException(UUID id) {
        super("Evento nao encontrado: " + id);
    }
}
