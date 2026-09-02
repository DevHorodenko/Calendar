-- O canal de aviso deixou de ser o WhatsApp.
--
-- O CallMeBot era o unico jeito de mandar texto livre para o WhatsApp sem conta
-- comercial, mas cada bot dele tem um teto de usuarios: cheio, ele para de criar
-- chaves e o cadastro simplesmente nao acontece -- sem erro, so silencio. Entraram
-- no lugar o Telegram, cuja API oficial e gratuita e sem fila, e o balao nativo do
-- Windows, que aparece mesmo com tudo fechado.

CREATE TABLE notification_settings (
    id                 integer      PRIMARY KEY,
    telegram_enabled   boolean      NOT NULL DEFAULT false,
    telegram_bot_token varchar(128),
    telegram_chat_id   varchar(64),
    -- Ligado por padrao: nao custa credencial nenhuma, entao quem instalar o
    -- aplicativo ja recebe o aviso sem configurar nada.
    windows_enabled    boolean      NOT NULL DEFAULT true,
    updated_at         timestamp with time zone NOT NULL,
    CONSTRAINT notification_settings_single_row CHECK (id = 1)
);

INSERT INTO notification_settings (id, telegram_enabled, windows_enabled, updated_at)
VALUES (1, false, true, CURRENT_TIMESTAMP);

DROP TABLE whatsapp_settings;

-- A entrega passa a ser contada por canal. Com os dois ligados, o Telegram pode falhar
-- e o balao nao, e cada um precisa poder tentar de novo por conta propria -- com uma
-- linha so, o sucesso de um esconderia a falha do outro para sempre.
--
-- O historico antigo aponta para um canal que nao existe mais, entao a tabela e refeita
-- em vez de convertida. No pior caso um aviso ainda pendente sai uma vez a mais.
DROP TABLE reminder_delivery;

CREATE TABLE reminder_delivery (
    id               uuid        PRIMARY KEY,
    reminder_id      uuid        NOT NULL REFERENCES event_reminder (id) ON DELETE CASCADE,
    occurrence_start timestamp   NOT NULL,
    channel          varchar(16) NOT NULL,
    status           varchar(16) NOT NULL,
    detail           varchar(1000),
    attempts         integer     NOT NULL DEFAULT 0,
    attempted_at     timestamp with time zone NOT NULL,
    CONSTRAINT reminder_delivery_status_valid CHECK (status IN ('SENT', 'FAILED')),
    CONSTRAINT reminder_delivery_channel_valid CHECK (channel IN ('TELEGRAM', 'WINDOWS')),
    CONSTRAINT reminder_delivery_unique_occurrence UNIQUE (reminder_id, occurrence_start, channel)
);

CREATE INDEX idx_reminder_delivery_attempted ON reminder_delivery (attempted_at);
