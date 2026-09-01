-- A cor do evento deixou de ser um conjunto fechado de nomes e passou a ser cor
-- livre, escolhida numa roda no cliente. As linhas gravadas com os nomes antigos
-- viram o hex equivalente; sem isso o front pediria uma cor que nao existe mais.
UPDATE event
SET color = CASE lower(color)
    WHEN 'blue'   THEN '#2b4c8c'
    WHEN 'green'  THEN '#2f5c33'
    WHEN 'amber'  THEN '#a3781d'
    WHEN 'red'    THEN '#9c2b21'
    WHEN 'violet' THEN '#6a3a6e'
    WHEN 'teal'   THEN '#2b6b6b'
    ELSE '#2b4c8c'
END
WHERE color IS NULL OR NOT REGEXP_LIKE(color, '^#[0-9a-fA-F]{6}$');

UPDATE event_override
SET color = CASE lower(color)
    WHEN 'blue'   THEN '#2b4c8c'
    WHEN 'green'  THEN '#2f5c33'
    WHEN 'amber'  THEN '#a3781d'
    WHEN 'red'    THEN '#9c2b21'
    WHEN 'violet' THEN '#6a3a6e'
    WHEN 'teal'   THEN '#2b6b6b'
    ELSE '#2b4c8c'
END
WHERE color IS NOT NULL AND NOT REGEXP_LIKE(color, '^#[0-9a-fA-F]{6}$');

ALTER TABLE event ALTER COLUMN color SET DEFAULT '#2b4c8c';

-- O formato passa a ser garantido pelo banco, e nao so pela validacao da API.
ALTER TABLE event
    ADD CONSTRAINT event_color_is_hex CHECK (REGEXP_LIKE(color, '^#[0-9a-fA-F]{6}$'));

ALTER TABLE event_override
    ADD CONSTRAINT event_override_color_is_hex
    CHECK (color IS NULL OR REGEXP_LIKE(color, '^#[0-9a-fA-F]{6}$'));
