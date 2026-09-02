package org.horodenko.calendar.web;

import org.horodenko.calendar.notification.NotificationException;
import org.horodenko.calendar.service.EventNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/** Traduz as falhas do dominio em respostas de erro previsiveis para o frontend. */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(EventNotFoundException.class)
    public ProblemDetail handleNotFound(EventNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    /** Regra de recorrencia invalida, intervalo absurdo, ocorrencia inexistente. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleInvalidArgument(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /**
     * O canal de aviso recusou ou nao respondeu. Nao e culpa do pedido, entao vai como
     * 502 -- e o motivo que ele deu vira o texto que a tela mostra.
     */
    @ExceptionHandler(NotificationException.class)
    public ProblemDetail handleNotificationFailure(NotificationException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Alguns campos do evento estao invalidos");
        problem.setProperty("fields", fields);
        return problem;
    }
}
