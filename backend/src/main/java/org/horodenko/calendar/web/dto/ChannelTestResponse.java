package org.horodenko.calendar.web.dto;

import java.util.List;

/**
 * O que aconteceu ao mandar a mensagem de conferencia.
 *
 * <p>Vem uma linha por canal em vez de um erro so: com dois canais ligados, um pode
 * entregar e o outro nao, e dizer apenas "falhou" esconderia metade da verdade.
 */
public record ChannelTestResponse(List<Result> results) {

    public record Result(String channel, boolean ok, String detail) {

        public static Result sent(String channel) {
            return new Result(channel, true, "Enviado.");
        }

        public static Result failed(String channel, String reason) {
            return new Result(channel, false, reason);
        }
    }
}
