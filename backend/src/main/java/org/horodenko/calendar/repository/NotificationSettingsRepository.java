package org.horodenko.calendar.repository;

import org.horodenko.calendar.domain.NotificationSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationSettingsRepository extends JpaRepository<NotificationSettings, Integer> {

    /** A linha unica, que a migration garante existir. */
    default NotificationSettings current() {
        return findById(NotificationSettings.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException(
                        "A linha de ajustes de notificacao sumiu do banco; ela e criada pela migration V4."));
    }
}
