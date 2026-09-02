package org.horodenko.calendar.notification;

/** O aviso nao chegou ao canal: rede fora, credencial recusada, bandeja indisponivel. */
public class NotificationException extends RuntimeException {

    public NotificationException(String message) {
        super(message);
    }

    public NotificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
