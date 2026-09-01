package org.horodenko.calendar;

import org.horodenko.calendar.config.AppWindow;
import org.horodenko.calendar.config.DesktopLauncher;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

@SpringBootApplication
public class CalendarApplication {

    private static final System.Logger log = System.getLogger(CalendarApplication.class.getName());

    /**
     * Porta do aplicativo instalado. Precisa ser sempre a mesma: o aplicativo
     * instalado pelo Edge guarda a URL de partida, e uma porta sorteada a cada
     * abertura deixaria esse atalho apontando para o vazio.
     */
    private static final int PREFERRED_PORT = 17325;

    /**
     * A pasta de dados e a porta sao resolvidas antes do Spring subir, porque a URL do
     * banco em application.yml ja precisa da primeira. Em desenvolvimento, sem o perfil
     * desktop, o banco cai em ./data e a porta continua sendo a do application.yml.
     */
    public static void main(String[] args) {
        boolean desktop = isDesktopRun(args);

        if (System.getProperty("calendar.data-dir") == null) {
            System.setProperty("calendar.data-dir", desktop ? userDataDirectory() : "./data");
        }

        if (desktop && System.getProperty("server.port") == null && !claimPort()) {
            // Nao conseguiu a porta e ela e de outro Calendario: em vez de subir um
            // segundo servidor, mostra a janela do que ja esta no ar e sai.
            return;
        }

        SpringApplication.run(CalendarApplication.class, args);
    }

    /**
     * Reserva a porta do aplicativo, ou decide o que fazer quando ela esta ocupada.
     *
     * @return {@code false} quando quem ocupa a porta e outro Calendario, caso em que
     *         nao ha o que iniciar
     */
    private static boolean claimPort() {
        if (isPortFree(PREFERRED_PORT)) {
            System.setProperty("server.port", String.valueOf(PREFERRED_PORT));
            return true;
        }

        if (isCalendarRunningOn(PREFERRED_PORT)) {
            log.log(System.Logger.Level.INFO,
                    "O Calendario ja esta em execucao; mostrando a janela dele.");
            Path dataDirectory = Path.of(System.getProperty("calendar.data-dir"));
            new AppWindow(dataDirectory, DesktopLauncher.WINDOW_TITLE,
                    "http://localhost:" + PREFERRED_PORT).show();
            return false;
        }

        // A porta e de outro programa. Cedendo, o aplicativo continua utilizavel pela
        // bandeja; o que se perde e o atalho do aplicativo instalado, que aponta para
        // a porta fixa.
        log.log(System.Logger.Level.WARNING,
                "A porta " + PREFERRED_PORT + " esta ocupada por outro programa; usando uma porta sorteada. "
                        + "O atalho do aplicativo instalado nao vai funcionar nesta sessao.");
        System.setProperty("server.port", "0");
        return true;
    }

    private static boolean isPortFree(int port) {
        try (ServerSocket probe = new ServerSocket(port, 1, InetAddress.getLoopbackAddress())) {
            return probe.getLocalPort() == port;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Confere se quem responde na porta e mesmo um Calendario, e nao um programa
     * qualquer que por acaso escutava ali. O manifesto serve de assinatura.
     */
    private static boolean isCalendarRunningOn(int port) {
        try (HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build()) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + "/manifest.webmanifest"))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200
                    && response.body().contains("\"name\": \"" + DesktopLauncher.WINDOW_TITLE + "\"");
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * O perfil pode chegar de tres formas, e o aplicativo empacotado usa justamente
     * a menos obvia: o jpackage passa {@code -Dspring.profiles.active}, que nao
     * aparece em {@code args}.
     */
    private static boolean isDesktopRun(String[] args) {
        if (containsDesktop(System.getProperty("spring.profiles.active"))
                || containsDesktop(System.getenv("SPRING_PROFILES_ACTIVE"))) {
            return true;
        }
        for (String arg : args) {
            if (arg.contains("spring.profiles.active") && containsDesktop(arg)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsDesktop(String value) {
        return value != null && value.contains("desktop");
    }

    /**
     * Onde o banco e o log do aplicativo instalado moram: %LOCALAPPDATA%\Calendario
     * no Windows, ~/.local/share/Calendario nos demais. Fora de Program Files, para
     * gravar sem precisar de permissao de administrador.
     *
     * <p>O caminho sai com barras normais: a URL do H2 trata a barra invertida do
     * Windows como escape.
     */
    private static String userDataDirectory() {
        String localAppData = System.getenv("LOCALAPPDATA");
        Path base = (localAppData == null || localAppData.isBlank())
                ? Path.of(System.getProperty("user.home"), ".local", "share")
                : Path.of(localAppData);

        Path directory = base.resolve("Calendario");
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            // Sem a pasta escolhida, o diretorio de trabalho ainda permite abrir o app.
            return ".";
        }
        return directory.toString().replace('\\', '/');
    }
}
