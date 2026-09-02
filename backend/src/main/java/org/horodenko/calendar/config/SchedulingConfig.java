package org.horodenko.calendar.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Liga o relogio do aplicativo. Hoje quem depende dele e o disparador de lembretes,
 * que precisa acordar sozinho enquanto a janela esta fechada na bandeja.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
