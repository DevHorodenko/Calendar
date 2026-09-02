package org.horodenko.calendar.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Como os avisos saem, em linha unica.
 *
 * <p>O calendario tem um dono, entao ha um destino por canal. A linha de id 1 e criada
 * pela migration e so e atualizada -- nunca inserida ou apagada.
 */
@Entity
@Table(name = "notification_settings")
public class NotificationSettings {

    /** A unica linha que a tabela aceita, como o CHECK da migration cobra. */
    public static final int SINGLETON_ID = 1;

    @Id
    private Integer id;

    @Column(name = "telegram_enabled", nullable = false)
    private boolean telegramEnabled;

    @Column(name = "telegram_bot_token", length = 128)
    private String telegramBotToken;

    @Column(name = "telegram_chat_id", length = 64)
    private String telegramChatId;

    @Column(name = "windows_enabled", nullable = false)
    private boolean windowsEnabled;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected NotificationSettings() {
        // exigido pelo JPA
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    /**
     * Ligado e com as duas metades da credencial. Sem o chat o bot nao sabe para quem
     * falar, e sem o token nem existe bot -- as duas faltas dao no mesmo silencio.
     */
    public boolean isTelegramUsable() {
        return telegramEnabled
                && telegramBotToken != null && !telegramBotToken.isBlank()
                && telegramChatId != null && !telegramChatId.isBlank();
    }

    public boolean hasTelegramToken() {
        return telegramBotToken != null && !telegramBotToken.isBlank();
    }

    public Integer getId() {
        return id;
    }

    public boolean isTelegramEnabled() {
        return telegramEnabled;
    }

    public void setTelegramEnabled(boolean telegramEnabled) {
        this.telegramEnabled = telegramEnabled;
    }

    public String getTelegramBotToken() {
        return telegramBotToken;
    }

    public void setTelegramBotToken(String telegramBotToken) {
        this.telegramBotToken = telegramBotToken;
    }

    public String getTelegramChatId() {
        return telegramChatId;
    }

    public void setTelegramChatId(String telegramChatId) {
        this.telegramChatId = telegramChatId;
    }

    public boolean isWindowsEnabled() {
        return windowsEnabled;
    }

    public void setWindowsEnabled(boolean windowsEnabled) {
        this.windowsEnabled = windowsEnabled;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
