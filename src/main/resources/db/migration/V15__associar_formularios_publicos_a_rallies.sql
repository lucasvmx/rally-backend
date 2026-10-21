ALTER TABLE formularios_publicos ADD COLUMN rally_id UUID;

UPDATE formularios_publicos formulario
SET rally_id = missao.rally_id
FROM missoes missao
WHERE formulario.missao_id = missao.id;

-- Some older definitions may already store a rally directly instead of a
-- fixed mission. Keep that association so deleting the rally also removes
-- those links. Join rallies before casting so invalid or deleted IDs are
-- safely ignored.
UPDATE formularios_publicos formulario
SET rally_id = rally.id
FROM rallies rally
WHERE formulario.rally_id IS NULL
  AND COALESCE(
      NULLIF(formulario.definicao::jsonb ->> 'rally_id', ''),
      NULLIF(formulario.definicao::jsonb ->> 'rallyId', '')
  ) ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
  AND rally.id = COALESCE(
      NULLIF(formulario.definicao::jsonb ->> 'rally_id', ''),
      NULLIF(formulario.definicao::jsonb ->> 'rallyId', '')
  )::uuid;

ALTER TABLE formularios_publicos
    ADD CONSTRAINT fk_formulario_publico_rally
        FOREIGN KEY (rally_id) REFERENCES rallies(id) ON DELETE CASCADE;

CREATE INDEX idx_formularios_publicos_rally_id ON formularios_publicos(rally_id);
