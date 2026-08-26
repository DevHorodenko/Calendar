package org.horodenko.calendar.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

/**
 * O que transforma o servidor em aplicativo de bandeja.
 *
 * <p>O processo fica residente segurando um icone na bandeja do sistema; a janela e
 * descartavel e pode ser aberta e fechada quantas vezes quiser sem encerrar nada.
 * Quem encerra e o "Sair" do menu.
 *
 * <p>Abrir e focar a janela e assunto de {@link AppWindow}; aqui fica so a bandeja.
 *
 * <p>Nada aqui e essencial: sem area de trabalho ou sem bandeja, o servidor continua
 * no ar e o endereco vai para o log.
 */
@Component
@Profile("desktop")
public class DesktopLauncher {

    private static final Logger log = LoggerFactory.getLogger(DesktopLauncher.class);

    /** Titulo da pagina, usado para achar a janela e o aplicativo instalado. */
    public static final String WINDOW_TITLE = "Calendario";

    private final ApplicationContext context;
    private final Path dataDirectory;
    private final boolean openOnStart;

    private AppWindow window;
    private TrayIcon trayIcon;

    /** Guardado porque o rotulo dele muda conforme o aplicativo esteja instalado ou nao. */
    private MenuItem installItem;

    /** Ultimo estado registrado, para o log so falar quando algo muda. */
    private Boolean lastInstalledState;

    public DesktopLauncher(ApplicationContext context,
                           @Value("${calendar.data-dir}") String dataDirectory,
                           @Value("${calendar.open-on-start:true}") boolean openOnStart) {
        this.context = context;
        this.dataDirectory = Path.of(dataDirectory);
        this.openOnStart = openOnStart;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        String url = "http://localhost:" + resolvePort();
        this.window = new AppWindow(dataDirectory, WINDOW_TITLE, url);
        log.info("Calendario disponivel em {}", url);

        installTrayIcon();

        // Iniciado junto com o Windows, o aplicativo sobe calado: fica so na bandeja
        // e espera ser chamado. Aberto a mao, mostra a janela na hora.
        if (openOnStart) {
            showWindow();
        } else {
            log.info("Iniciado em segundo plano; abra pelo icone da bandeja.");
        }
    }

    /** A porta e a fixa quando esta livre; so o servidor sabe qual acabou valendo. */
    private int resolvePort() {
        if (context instanceof WebServerApplicationContext webContext && webContext.getWebServer() != null) {
            return webContext.getWebServer().getPort();
        }
        return 8080;
    }

    /**
     * Sincronizado porque dois cliques seguidos na bandeja chegariam juntos e abririam
     * duas janelas.
     */
    private synchronized void showWindow() {
        window.show();
    }

    /**
     * Abrir ou focar chama o shell e espera pela resposta; feito na thread da bandeja,
     * isso congelaria o menu. Sai para uma thread propria.
     */
    private void showWindowAsync() {
        Thread.ofVirtual().name("abrir-janela").start(this::showWindow);
    }

    // ----------------------------------------------------------------- bandeja

    private void installTrayIcon() {
        if (!SystemTray.isSupported()) {
            log.info("Bandeja do sistema indisponivel; o aplicativo so pode ser encerrado pelo gerenciador de tarefas.");
            return;
        }
        try {
            PopupMenu menu = new PopupMenu();

            MenuItem open = new MenuItem("Abrir calendario");
            open.addActionListener(event -> showWindowAsync());
            menu.add(open);

            installItem = new MenuItem();
            installItem.addActionListener(event ->
                    Thread.ofVirtual().name("instalar-app").start(this::openInstallPage));
            menu.add(installItem);
            refreshInstallItem();

            menu.addSeparator();

            MenuItem quit = new MenuItem("Sair");
            quit.addActionListener(event -> shutdown());
            menu.add(quit);

            trayIcon = new TrayIcon(trayImage(), WINDOW_TITLE, menu);
            trayIcon.setImageAutoSize(true);
            // Clique duplo no icone abre, que e o que se espera de um app na bandeja.
            trayIcon.addActionListener(event -> showWindowAsync());
            // A instalacao acontece fora do aplicativo, entao o estado e reconferido a
            // cada abertura do menu -- e nao so na subida.
            trayIcon.addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent event) {
                    refreshInstallItem();
                }
            });

            SystemTray.getSystemTray().add(trayIcon);
        } catch (Exception e) {
            log.warn("Nao foi possivel criar o icone na bandeja", e);
        }
    }

    /**
     * Deixa o proprio menu dizer se o aplicativo ja esta instalado.
     *
     * <p>Antes essa informacao so aparecia num balao depois do clique, e balao o Windows
     * pode simplesmente nao mostrar. No rotulo, o estado se le de imediato.
     */
    private void refreshInstallItem() {
        if (installItem == null || window == null) {
            return;
        }
        boolean installed = window.findInstalledApp() != null;
        installItem.setLabel(installed ? "Instalado como aplicativo" : "Instalar como aplicativo");
        installItem.setEnabled(!installed);

        // Registrado so na mudanca: o menu e reconferido a cada clique direito.
        if (lastInstalledState == null || lastInstalledState != installed) {
            lastInstalledState = installed;
            log.info(installed
                    ? "Aplicativo instalado detectado; o menu mostra o estado e nao oferece instalar."
                    : "Aplicativo ainda nao instalado; o menu oferece instalar.");
        }
    }

    /**
     * Abre uma janela comum do navegador, de onde da para instalar o site como
     * aplicativo. A janela do proprio aplicativo nao tem menu, entao nao serve.
     */
    private void openInstallPage() {
        window.openPlainBrowser();
        notifyTray("Instalar o Calendario",
                "Na janela que abriu, clique no icone de instalar na barra de endereco "
                        + "(ou no menu ... > Aplicativos) e confirme o nome Calendario. "
                        + "Depois disso o icone do calendario aparece na barra de tarefas.");
    }

    private void notifyTray(String title, String message) {
        log.info("{}: {}", title, message);
        if (trayIcon != null) {
            trayIcon.displayMessage(title, message, TrayIcon.MessageType.INFO);
        }
    }

    private Image trayImage() throws IOException {
        try (InputStream stream = getClass().getResourceAsStream("/icon/one-ring.png")) {
            if (stream == null) {
                throw new IOException("Icone da bandeja nao encontrado no classpath");
            }
            return Toolkit.getDefaultToolkit().createImage(stream.readAllBytes());
        }
    }

    private void shutdown() {
        log.info("Encerrando o Calendario");
        // Fecha o contexto para o H2 gravar e liberar o arquivo antes de sair.
        int code = org.springframework.boot.SpringApplication.exit(context, () -> 0);
        System.exit(code);
    }
}
