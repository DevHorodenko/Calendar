package org.horodenko.calendar.notification;

import org.horodenko.calendar.web.dto.OccurrenceResponse;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Monta o texto do lembrete, seja qual for o canal por onde ele sai.
 *
 * <p>A mensagem e escrita pelo usuario e pode conter marcadores como <code>{titulo}</code>,
 * que sao trocados pelos dados da ocorrencia. Assim uma serie recorrente nao precisa de um
 * texto por vez: "Consulta {data} as {hora}" serve para todas.
 *
 * <p>Sem mensagem escrita, o texto padrao e montado a partir do proprio evento.
 */
@Component
public class ReminderMessageFormatter {

    /**
     * Teto do texto. Folgado para o Telegram, que aceita 4096, e apertado o bastante
     * para o balao do Windows, que corta o que passa de poucas centenas de caracteres.
     */
    public static final int MAX_LENGTH = 900;

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    public String format(String template, OccurrenceResponse occurrence, int minutesBefore) {
        Map<String, String> values = valuesFor(occurrence, minutesBefore);
        String text = (template == null || template.isBlank())
                ? defaultMessage(occurrence, values)
                : replace(template, values);
        return truncate(text.strip());
    }

    /** Amostra para a tela de ajustes, sem depender de haver um evento de verdade. */
    public String describeLead(int minutesBefore) {
        if (minutesBefore <= 0) {
            return "agora";
        }
        if (minutesBefore < 60) {
            return "em " + minutesBefore + plural(minutesBefore, " minuto", " minutos");
        }
        if (minutesBefore % (24 * 60) == 0) {
            int days = minutesBefore / (24 * 60);
            return "em " + days + plural(days, " dia", " dias");
        }
        if (minutesBefore % 60 == 0) {
            int hours = minutesBefore / 60;
            return "em " + hours + plural(hours, " hora", " horas");
        }
        int hours = minutesBefore / 60;
        int minutes = minutesBefore % 60;
        return "em " + hours + plural(hours, " hora", " horas") + " e " + minutes
                + plural(minutes, " minuto", " minutos");
    }

    private Map<String, String> valuesFor(OccurrenceResponse occurrence, int minutesBefore) {
        LocalDateTime start = occurrence.startAt();
        Map<String, String> values = new LinkedHashMap<>();
        values.put("{titulo}", occurrence.title() == null ? "" : occurrence.title());
        values.put("{data}", DATE.format(start));
        values.put("{hora}", occurrence.allDay() ? "dia inteiro" : TIME.format(start));
        values.put("{local}", occurrence.location() == null ? "" : occurrence.location());
        values.put("{descricao}", occurrence.description() == null ? "" : occurrence.description());
        values.put("{antecedencia}", describeLead(minutesBefore));
        return values;
    }

    private String defaultMessage(OccurrenceResponse occurrence, Map<String, String> values) {
        StringBuilder text = new StringBuilder("Lembrete: ")
                .append(values.get("{titulo}"));

        if (occurrence.allDay()) {
            text.append(" e ").append(values.get("{antecedencia}"))
                    .append(" (").append(values.get("{data}")).append(", dia inteiro)");
        } else {
            text.append(" comeca ").append(values.get("{antecedencia}"))
                    .append(" (").append(values.get("{data}"))
                    .append(" as ").append(values.get("{hora}")).append(")");
        }
        text.append('.');

        if (!values.get("{local}").isBlank()) {
            text.append('\n').append("Local: ").append(values.get("{local}")).append('.');
        }
        return text.toString();
    }

    private String replace(String template, Map<String, String> values) {
        String text = template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            text = text.replace(entry.getKey(), entry.getValue());
        }
        return text;
    }

    private String truncate(String text) {
        return text.length() <= MAX_LENGTH ? text : text.substring(0, MAX_LENGTH - 3) + "...";
    }

    private String plural(int amount, String singular, String plural) {
        return amount == 1 ? singular : plural;
    }
}
