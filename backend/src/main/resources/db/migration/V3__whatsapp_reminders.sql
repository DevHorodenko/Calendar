-- Lembretes de WhatsApp.
--
-- O lembrete pertence a serie, e nao a uma ocorrencia: "avise 15 minutos antes"
-- vale para todas as vezes que o evento acontece. O que distingue uma ocorrencia
-- da outra na hora do envio e a linha de reminder_delivery, que guarda o horario
-- original do encaixe.

-- Credencial do CallMeBot. Linha unica: o calendario e de um usuario so, entao ha
-- um numero de destino e uma apikey. A chave fica no banco do proprio usuario, no
-- perfil dele, junto com os eventos -- nao ha servidor nem segundo leitor.
CREATE TABLE whatsapp_settings (
    id           integer      PRIMARY KEY,
    enabled      boolean      NOT NULL DEFAULT false,
    phone_number varchar(32),
    api_key      varchar(128),
    updated_at   timestamp with time zone NOT NULL,
    CONSTRAINT whatsapp_settings_single_row CHECK (id = 1)
);

INSERT INTO whatsapp_settings (id, enabled, phone_number, api_key, updated_at)
VALUES (1, false, NULL, NULL, CURRENT_TIMESTAMP);

-- Um evento pode ter mais de um aviso (um no dia anterior, outro 10 minutos antes),
-- desde que com antecedencias diferentes: dois lembretes no mesmo instante seriam
-- duas mensagens iguais seguidas.
CREATE TABLE event_reminder (
    id             uuid          PRIMARY KEY,
    event_id       uuid          NOT NULL REFERENCES event (id) ON DELETE CASCADE,
    minutes_before integer       NOT NULL,
    message        varchar(1000),
    enabled        boolean       NOT NULL DEFAULT true,
    -- Teto de 30 dias: acima disso a janela que o disparador varre a cada volta
    -- cresceria sem que o aviso ficasse mais util.
    CONSTRAINT event_reminder_lead_in_range CHECK (minutes_before >= 0 AND minutes_before <= 43200),
    CONSTRAINT event_reminder_unique_lead UNIQUE (event_id, minutes_before)
);

CREATE INDEX idx_event_reminder_event ON event_reminder (event_id);

-- O que ja foi enviado. Sem isso o disparador mandaria a mesma mensagem a cada
-- volta, porque a ocorrencia continua dentro da janela ate comecar.
CREATE TABLE reminder_delivery (
    id               uuid        PRIMARY KEY,
    reminder_id      uuid        NOT NULL REFERENCES event_reminder (id) ON DELETE CASCADE,
    occurrence_start timestamp   NOT NULL,
    status           varchar(16) NOT NULL,
    detail           varchar(1000),
    attempts         integer     NOT NULL DEFAULT 0,
    attempted_at     timestamp with time zone NOT NULL,
    CONSTRAINT reminder_delivery_status_valid CHECK (status IN ('SENT', 'FAILED')),
    CONSTRAINT reminder_delivery_unique_occurrence UNIQUE (reminder_id, occurrence_start)
);

-- A limpeza do historico varre por data.
CREATE INDEX idx_reminder_delivery_attempted ON reminder_delivery (attempted_at);
