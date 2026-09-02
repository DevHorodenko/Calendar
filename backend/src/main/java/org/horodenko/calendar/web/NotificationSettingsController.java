package org.horodenko.calendar.web;

import jakarta.validation.Valid;
import org.horodenko.calendar.service.NotificationSettingsService;
import org.horodenko.calendar.web.dto.ChannelTestResponse;
import org.horodenko.calendar.web.dto.NotificationSettingsRequest;
import org.horodenko.calendar.web.dto.NotificationSettingsResponse;
import org.horodenko.calendar.web.dto.TelegramChatResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings/notifications")
public class NotificationSettingsController {

    private final NotificationSettingsService settingsService;

    public NotificationSettingsController(NotificationSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    public NotificationSettingsResponse settings() {
        return settingsService.view();
    }

    @PutMapping
    public NotificationSettingsResponse save(@Valid @RequestBody NotificationSettingsRequest request) {
        return settingsService.save(request);
    }

    /** Manda uma mensagem de conferencia por cada canal ligado. */
    @PostMapping("/test")
    public ChannelTestResponse test() {
        return settingsService.sendTestMessage();
    }

    /** Le as mensagens que o bot recebeu e grava a conversa encontrada. */
    @PostMapping("/telegram/detect-chat")
    public TelegramChatResponse detectChat() {
        return settingsService.detectTelegramChat();
    }
}
