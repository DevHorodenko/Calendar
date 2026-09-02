package org.horodenko.calendar.domain;

/**
 * Por onde um lembrete pode sair.
 *
 * <p>Os dois se complementam: o Telegram alcanca o celular e qualquer maquina em que a
 * conta esteja aberta; o balao do Windows aparece na propria area de trabalho, mesmo com
 * o Telegram fechado, mas so existe enquanto o Calendario estiver na bandeja.
 */
public enum NotificationChannelType {
    TELEGRAM,
    WINDOWS
}
