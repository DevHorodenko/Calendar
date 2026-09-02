package org.horodenko.calendar.notification;

import org.horodenko.calendar.domain.NotificationChannelType;
import org.horodenko.calendar.domain.NotificationSettings;
import org.horodenko.calendar.repository.NotificationSettingsRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

/**
 * Envio pela API de bots do Telegram.
 *
 * <p>Escolhido depois que o CallMeBot mostrou o seu limite: la cada bot tem um teto de
 * usuarios e, cheio, para de emitir chaves sem dizer nada. A API do Telegram e oficial,
 * gratuita, sem fila e sem teto -- e o bot e seu, nao de um servico compartilhado.
 *
 * <p>Aceita texto livre, sem template a aprovar, e a mesma mensagem chega ao mesmo tempo
 * no celular e em qualquer maquina com a conta aberta.
 *
 * <p>O Telegram responde 200 com {@code {"ok":false,...}} em varios erros, entao o codigo
 * de status sozinho nao basta: o veredito esta no campo {@code ok}.
 */
@Component
public class TelegramChannel implements NotificationChannel {

    private static final System.Logger log = System.getLogger(TelegramChannel.class.getName());

    private static final String API = "https://api.telegram.org/bot";

    private final NotificationSettingsRepository repository;
    private final ObjectMapper json;
    private final Duration timeout;

    public TelegramChannel(NotificationSettingsRepository repository,
                           ObjectMapper json,
                           @Value("${calendar.reminders.http-timeout-seconds:15}") long timeoutSeconds) {
        this.repository = repository;
        this.json = json;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
    }

    @Override
    public NotificationChannelType type() {
        return NotificationChannelType.TELEGRAM;
    }

    @Override
    public String label() {
        return "Telegram";
    }

    @Override
    public boolean isReady() {
        return repository.current().isTelegramUsable();
    }

    @Override
    public void send(String title, String body) {
        NotificationSettings settings = repository.current();
        sendWith(settings.getTelegramBotToken(), settings.getTelegramChatId(), body);
    }

    /**
     * Envio avulso com uma credencial escolhida, usado pelo teste da tela de ajustes --
     * que precisa poder disparar mesmo com o canal desligado.
     */
    public void sendWith(String botToken, String chatId, String text) {
        requireCredentials(botToken, chatId);

        String payload = json.writeValueAsString(new SendMessage(chatId, text));
        JsonNode response = call(botToken, "sendMessage", payload);

        if (!response.path("ok").asBoolean(false)) {
            throw new NotificationException("O Telegram recusou a mensagem: " + describe(response));
        }
        log.log(System.Logger.Level.DEBUG, "Lembrete entregue ao Telegram.");
    }

    /**
     * Descobre o chat a partir das mensagens que o bot recebeu.
     *
     * <p>O id do chat e um numero que nao aparece em lugar nenhum da interface do
     * Telegram; sem isto, configurar o aviso exigiria caçar esse numero num terceiro bot.
     * Aqui basta o usuario mandar qualquer coisa ao proprio bot e clicar em detectar.
     *
     * <p>Vazio quando ninguem falou com o bot ainda -- ou quando falou ha mais de 24
     * horas, que e o quanto o Telegram guarda as atualizacoes nao lidas.
     */
    public Optional<DetectedChat> detectChat(String botToken) {
        if (botToken == null || botToken.isBlank()) {
            throw new IllegalArgumentException("Informe o token do bot antes de detectar a conversa");
        }
        JsonNode response = call(botToken, "getUpdates?limit=100", null);
        if (!response.path("ok").asBoolean(false)) {
            throw new NotificationException("O Telegram recusou a consulta: " + describe(response));
        }

        JsonNode updates = response.path("result");
        // De tras para frente: a conversa que interessa e a ultima em que alguem falou.
        for (int i = updates.size() - 1; i >= 0; i--) {
            JsonNode chat = updates.get(i).path("message").path("chat");
            if (chat.isObject() && chat.hasNonNull("id")) {
                return Optional.of(new DetectedChat(chat.get("id").asString(), chatName(chat)));
            }
        }
        return Optional.empty();
    }

    private JsonNode call(String botToken, String method, String jsonBody) {
        URI uri = URI.create(API + encodePathToken(botToken) + "/" + method);

        try (HttpClient client = HttpClient.newBuilder().connectTimeout(timeout).build()) {
            HttpRequest.Builder request = HttpRequest.newBuilder(uri).timeout(timeout);
            if (jsonBody == null) {
                request.GET();
            } else {
                request.header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8));
            }
            HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());

            String payload = response.body();
            if (payload == null || payload.isBlank()) {
                throw new NotificationException(
                        "O Telegram respondeu " + response.statusCode() + " sem conteudo.");
            }
            // Erro de credencial chega como 401/404 com o motivo no corpo, entao o corpo
            // e lido antes de olhar o status: a descricao dele e melhor que o numero.
            return json.readTree(payload);
        } catch (IOException e) {
            throw new NotificationException("Nao foi possivel falar com o Telegram: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NotificationException("O envio do lembrete foi interrompido.", e);
        }
    }

    private void requireCredentials(String botToken, String chatId) {
        if (botToken == null || botToken.isBlank()) {
            throw new IllegalArgumentException("Informe o token do bot do Telegram");
        }
        if (chatId == null || chatId.isBlank()) {
            throw new IllegalArgumentException("Informe o chat do Telegram que vai receber os avisos");
        }
    }

    /** Traduz o erro do Telegram, que costuma ser claro, e cai no bruto quando nao e. */
    private String describe(JsonNode response) {
        String description = response.path("description").asString("");
        return description.isBlank() ? response.toString() : description;
    }

    private String chatName(JsonNode chat) {
        String first = chat.path("first_name").asString("");
        String last = chat.path("last_name").asString("");
        String full = (first + " " + last).strip();
        if (!full.isBlank()) {
            return full;
        }
        String title = chat.path("title").asString("");
        return title.isBlank() ? chat.path("username").asString("conversa") : title;
    }

    /**
     * O token vai no caminho da URL, e um token digitado errado pode trazer barra ou
     * espaco -- que virariam outra rota em vez de um erro claro do Telegram.
     */
    private String encodePathToken(String botToken) {
        return URLEncoder.encode(botToken.strip(), StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** Corpo do sendMessage. Sem parse_mode: o texto e do usuario e nao deve virar markup. */
    private record SendMessage(String chat_id, String text) {
    }

    /** A conversa encontrada, com um nome so para a tela confirmar que e a certa. */
    public record DetectedChat(String id, String name) {
    }
}
