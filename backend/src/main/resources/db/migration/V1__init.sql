-- Series do calendario. Sem recurrence_rule a linha e um evento unico.
CREATE TABLE event (
    id              uuid          PRIMARY KEY,
    title           varchar(255)  NOT NULL,
    description     varchar(4000),
    location        varchar(255),
    all_day         boolean       NOT NULL DEFAULT false,
    start_at        timestamp     NOT NULL,
    end_at          timestamp     NOT NULL,
    recurrence_rule varchar(500),
    color           varchar(32)   NOT NULL DEFAULT 'blue',
    created_at      timestamp with time zone NOT NULL,
    updated_at      timestamp with time zone NOT NULL,
    CONSTRAINT event_ends_after_start CHECK (end_at >= start_at)
);

-- Consulta principal: tudo que comeca dentro da janela visivel.
CREATE INDEX idx_event_start_at ON event (start_at);

-- Excecoes de ocorrencias: uma ocorrencia cancelada ou alterada dentro de uma serie.
CREATE TABLE event_override (
    id               uuid         PRIMARY KEY,
    event_id         uuid         NOT NULL REFERENCES event (id) ON DELETE CASCADE,
    occurrence_start timestamp    NOT NULL,
    type             varchar(16)  NOT NULL,
    title            varchar(255),
    description      varchar(4000),
    location         varchar(255),
    all_day          boolean,
    start_at         timestamp,
    end_at           timestamp,
    color            varchar(32),
    CONSTRAINT event_override_type_valid CHECK (type IN ('CANCELLED', 'MODIFIED')),
    CONSTRAINT event_override_unique_occurrence UNIQUE (event_id, occurrence_start)
);

CREATE INDEX idx_event_override_event ON event_override (event_id);
