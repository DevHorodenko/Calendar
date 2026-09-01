package org.horodenko.calendar.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * A janela do calendario: descobrir se existe, traze-la para frente e abrir uma nova.
 *
 * <p>Fica fora do Spring de proposito -- o {@code main} precisa disso antes de haver
 * contexto, para lidar com uma segunda abertura do aplicativo.
 *
 * <p>A janela e do proprio navegador, sem abas nem barra de endereco. Se o site foi
 * instalado como aplicativo, abre por ele: so essa janela tem identidade propria no
 * Windows, e por isso so ela mostra o icone do calendario na barra de tarefas.
 */
public final class AppWindow {

    private static final Logger log = LoggerFactory.getLogger(AppWindow.class);

    /** Pasta de um aplicativo instalado pelo navegador: {@code _crx_<id>}. */
    private static final String INSTALLED_APP_PREFIX = "_crx_";

    private static final long SCRIPT_TIMEOUT_SECONDS = 15;

    /** Quanto esperar a janela surgir antes de considerar que a abertura falhou. */
    private static final int WINDOW_WAIT_ATTEMPTS = 6;

    private final Path dataDirectory;
    private final String title;
    private final String url;

    public AppWindow(Path dataDirectory, String title, String url) {
        this.dataDirectory = dataDirectory;
        this.title = title;
        this.url = url;
    }

    // -------------------------------------------------------------- existencia

    /**
     * Verdadeiro se ja existe uma janela do calendario, que e trazida para frente.
     *
     * <p>Quem responde e um script PowerShell, porque o Java nao enxerga janelas no
     * Windows. Ele enumera as janelas visiveis e exige <em>titulo exato</em> e
     * <em>processo dono sendo um navegador</em>.
     *
     * <p>Essa exigencia nao e preciosismo: o {@code AppActivate}, que seria o caminho
     * curto, casa por prefixo de titulo e da positivo para uma pasta do Explorer
     * chamada "Calendario" -- fazendo o aplicativo concluir que ja havia janela e
     * nunca abrir nenhuma.
     */
    public boolean showExisting() {
        if (!isWindows()) {
            return false;
        }
        Path script = extractFocusScript();
        if (script == null) {
            return false;
        }
        Integer exitCode = run(SCRIPT_TIMEOUT_SECONDS,
                powerShellPath(), "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                "-File", script.toString(), "-Title", title);
        return exitCode != null && exitCode == 0;
    }

    /**
     * O script vive no jar; para o PowerShell executa-lo, precisa existir em disco.
     * Reescrito a cada subida, para nao envelhecer junto com uma versao antiga.
     */
    private Path extractFocusScript() {
        Path destination = dataDirectory.resolve("focus-window.ps1");
        try (InputStream stream = AppWindow.class.getResourceAsStream("/scripts/focus-window.ps1")) {
            if (stream == null) {
                log.warn("Script de foco nao encontrado no jar");
                return null;
            }
            Files.createDirectories(dataDirectory);
            Files.copy(stream, destination, StandardCopyOption.REPLACE_EXISTING);
            return destination;
        } catch (IOException e) {
            log.warn("Nao foi possivel preparar o script de foco", e);
            return null;
        }
    }

    // ------------------------------------------------------------------ abrir

    /**
     * Mostra a janela: traz para frente a que existe, ou abre uma.
     *
     * @return o processo do navegador aberto, ou {@code null} se nenhuma foi aberta
     */
    public Process show() {
        if (showExisting()) {
            return null;
        }
        return open();
    }

    private Process open() {
        InstalledApp installed = findInstalledApp();
        if (installed != null) {
            log.info("Abrindo o aplicativo instalado (perfil {}, id {})", installed.profile(), installed.appId());
            Process process = start(installed.browser().executable(),
                    "--profile-directory=" + installed.profile(),
                    "--app-id=" + installed.appId());
            // Um id que o navegador nao reconhece nao da erro: ele simplesmente nao
            // abre nada. Sem esta conferencia, o clique ficaria sem efeito nenhum.
            if (process != null && waitForWindow()) {
                return process;
            }
            log.info("O aplicativo instalado nao abriu janela; caindo no modo --app=.");
        }

        Browser browser = firstAvailableBrowser();
        if (browser == null) {
            log.info("Nenhum navegador com modo aplicativo encontrado; abrindo o padrao do sistema.");
            openInDefaultBrowser();
            return null;
        }
        Process process = start(browser.executable(),
                "--app=" + url,
                // Perfil proprio: a janela ganha lugar separado na barra de tarefas,
                // em vez de se misturar as janelas de navegacao do usuario.
                "--user-data-dir=" + dataDirectory.resolve("janela"),
                "--window-size=1280,860",
                "--no-first-run",
                "--no-default-browser-check");
        if (process == null) {
            openInDefaultBrowser();
        }
        return process;
    }

    private boolean waitForWindow() {
        if (!isWindows()) {
            return true;
        }
        for (int attempt = 0; attempt < WINDOW_WAIT_ATTEMPTS; attempt++) {
            if (showExisting()) {
                return true;
            }
            try {
                TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private void openInDefaultBrowser() {
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            log.info("Abertura automatica indisponivel. Acesse {}", url);
            return;
        }
        try {
            Desktop.getDesktop().browse(URI.create(url));
        } catch (IOException | UnsupportedOperationException e) {
            log.warn("Nao foi possivel abrir o navegador. Acesse {}", url, e);
        }
    }

    // -------------------------------------------------------- aplicativo instalado

    /** Um navegador capaz de abrir janela sem abas, com a raiz dos perfis dele. */
    public record Browser(String executable, Path userDataRoot) {
        boolean isPresent() {
            return Files.isRegularFile(Path.of(executable));
        }
    }

    /** Um aplicativo ja instalado: em qual navegador, em qual perfil e sob qual id. */
    public record InstalledApp(Browser browser, String profile, String appId) {
    }

    private static List<Browser> browsers() {
        String localAppData = System.getenv("LOCALAPPDATA");
        Path base = Path.of(localAppData == null || localAppData.isBlank() ? "." : localAppData);
        return List.of(
                new Browser("C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe",
                        base.resolve("Microsoft/Edge/User Data")),
                new Browser("C:/Program Files/Microsoft/Edge/Application/msedge.exe",
                        base.resolve("Microsoft/Edge/User Data")),
                new Browser("C:/Program Files/Google/Chrome/Application/chrome.exe",
                        base.resolve("Google/Chrome/User Data")),
                new Browser("C:/Program Files (x86)/Google/Chrome/Application/chrome.exe",
                        base.resolve("Google/Chrome/User Data"))
        );
    }

    private Browser firstAvailableBrowser() {
        return browsers().stream().filter(Browser::isPresent).findFirst().orElse(null);
    }

    /**
     * Procura o aplicativo ja instalado pelo navegador.
     *
     * <p>A instalacao deixa {@code <perfil>/Web Applications/_crx__<id>/<Titulo>.ico}.
     * O id e gerado na instalacao e muda de maquina para maquina, entao nao da para
     * fixa-lo no codigo -- resta descobri-lo por esse rastro. Nao adianta procurar
     * atalho no Menu Iniciar: o Edge nao cria um.
     */
    public InstalledApp findInstalledApp() {
        for (Browser browser : browsers()) {
            if (!browser.isPresent() || !Files.isDirectory(browser.userDataRoot())) {
                continue;
            }
            for (Path profile : subdirectories(browser.userDataRoot())) {
                for (Path app : subdirectories(profile.resolve("Web Applications"))) {
                    String appId = appIdFrom(app.getFileName().toString());
                    if (appId != null && Files.isRegularFile(app.resolve(title + ".ico"))) {
                        return new InstalledApp(browser, profile.getFileName().toString(), appId);
                    }
                }
            }
        }
        return null;
    }

    /**
     * Extrai o id do nome da pasta.
     *
     * <p>A pasta vem como {@code _crx__<id>}, com uma barra baixa a mais entre o
     * prefixo e o id. Um id de aplicativo tem exatamente 32 letras de 'a' a 'p';
     * conferir isso evita passar lixo adiante -- um id invalido nao da erro, so
     * deixa de abrir a janela.
     */
    private static String appIdFrom(String folderName) {
        if (!folderName.startsWith(INSTALLED_APP_PREFIX)) {
            return null;
        }
        String candidate = folderName.substring(INSTALLED_APP_PREFIX.length()).replaceFirst("^_+", "");
        return candidate.matches("[a-p]{32}") ? candidate : null;
    }

    private static List<Path> subdirectories(Path directory) {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.filter(Files::isDirectory).toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    /** Abre o endereco numa janela comum do navegador, com barra de endereco e menu. */
    public void openPlainBrowser() {
        Browser browser = firstAvailableBrowser();
        if (browser == null) {
            openInDefaultBrowser();
        } else {
            start(browser.executable(), url);
        }
    }

    // ---------------------------------------------------------------- processos

    private static Process start(String... command) {
        try {
            return new ProcessBuilder(command).start();
        } catch (IOException e) {
            log.warn("Falha ao executar {}", command[0], e);
            return null;
        }
    }

    /** Executa e devolve o codigo de saida, ou {@code null} se nao deu para esperar. */
    private static Integer run(long timeoutSeconds, String... command) {
        Process process = null;
        try {
            process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return null;
            }
            return process.exitValue();
        } catch (IOException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (process != null) {
                process.destroyForcibly();
            }
            return null;
        }
    }

    private static String powerShellPath() {
        String systemRoot = System.getenv("SystemRoot");
        if (systemRoot != null && !systemRoot.isBlank()) {
            Path candidate = Path.of(systemRoot, "System32", "WindowsPowerShell", "v1.0", "powershell.exe");
            if (Files.isRegularFile(candidate)) {
                return candidate.toString();
            }
        }
        return "powershell";
    }

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
