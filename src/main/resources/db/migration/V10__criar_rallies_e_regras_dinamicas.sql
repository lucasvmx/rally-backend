CREATE TABLE rallies (
    id UUID PRIMARY KEY,
    nome VARCHAR(180) NOT NULL,
    data_inicio DATE NOT NULL,
    data_fim DATE NOT NULL,
    CONSTRAINT ck_rally_datas CHECK (data_inicio <= data_fim)
);

-- O esquema anterior não guardava o rally de cada missão. As missões existentes
-- ficam em um único rally legado para manter o placar histórico coerente.
INSERT INTO rallies (id, nome, data_inicio, data_fim)
SELECT '00000000-0000-4000-8000-000000000010'::UUID, 'Rally legado', MIN(data_inicio), MAX(data_fim)
FROM missoes
HAVING COUNT(*) > 0;

ALTER TABLE missoes ADD COLUMN rally_id UUID;
UPDATE missoes SET rally_id = '00000000-0000-4000-8000-000000000010'::UUID;
ALTER TABLE missoes ALTER COLUMN rally_id SET NOT NULL;
ALTER TABLE missoes ADD CONSTRAINT fk_missao_rally FOREIGN KEY (rally_id) REFERENCES rallies(id);
ALTER TABLE missoes ADD COLUMN regras_pontuacao TEXT NOT NULL DEFAULT '[]';
ALTER TABLE missoes ADD COLUMN campos_relatorio TEXT NOT NULL DEFAULT '[]';

ALTER TABLE relatorios ADD COLUMN dados TEXT NOT NULL DEFAULT '{}';
ALTER TABLE relatorios ADD COLUMN avisos TEXT NOT NULL DEFAULT '[]';
ALTER TABLE relatorios ADD CONSTRAINT uq_relatorio_id_missao UNIQUE (id, missao_id);

CREATE TABLE pessoas_pontuadas (
    id UUID PRIMARY KEY,
    missao_id UUID NOT NULL,
    relatorio_id UUID NOT NULL,
    nome VARCHAR(200) NOT NULL,
    nome_normalizado VARCHAR(200) NOT NULL,
    ocorrencia INTEGER NOT NULL CHECK (ocorrencia > 0),
    categoria VARCHAR(80) NOT NULL,
    dados TEXT NOT NULL DEFAULT '{}',
    CONSTRAINT uq_pessoa_missao_ocorrencia UNIQUE (missao_id, nome_normalizado, ocorrencia),
    CONSTRAINT fk_pessoa_relatorio_missao FOREIGN KEY (relatorio_id, missao_id)
        REFERENCES relatorios(id, missao_id)
);

-- Importa os nomes dos formulários antigos para impedir pontuação repetida
-- em uma missão após a atualização do backend.
WITH nomes AS (
    SELECT relatorio.missao_id, relatorio.id AS relatorio_id, relatorio.enviado_em,
           btrim(regexp_replace(btrim(linha.nome), '[[:space:]]+', ' ', 'g')) AS nome,
           'INTEGRANTE' AS categoria
    FROM respostas_formularios resposta
    JOIN relatorios relatorio ON relatorio.id = resposta.relatorio_id
    CROSS JOIN LATERAL regexp_split_to_table(
        COALESCE(resposta.respostas::jsonb ->> 'nomes_integrantes', ''), E'\\r?\\n'
    ) AS linha(nome)
    WHERE btrim(linha.nome) <> '' AND lower(btrim(linha.nome)) <> 'nenhum'
      AND length(btrim(linha.nome)) <= 200

    UNION ALL

    SELECT relatorio.missao_id, relatorio.id AS relatorio_id, relatorio.enviado_em,
           btrim(regexp_replace(btrim(linha.nome), '[[:space:]]+', ' ', 'g')) AS nome,
           'NOVO_AFASTADO' AS categoria
    FROM respostas_formularios resposta
    JOIN relatorios relatorio ON relatorio.id = resposta.relatorio_id
    CROSS JOIN LATERAL regexp_split_to_table(
        COALESCE(resposta.respostas::jsonb ->> 'nomes_novos_afastados', ''), E'\\r?\\n'
    ) AS linha(nome)
    WHERE btrim(linha.nome) <> '' AND lower(btrim(linha.nome)) <> 'nenhum'
      AND length(btrim(linha.nome)) <= 200
), normalizados AS (
    SELECT missao_id, relatorio_id, enviado_em, nome, lower(nome) AS nome_normalizado, categoria
    FROM nomes
), numerados AS (
    SELECT missao_id, relatorio_id, nome, nome_normalizado, categoria,
           row_number() OVER (
               PARTITION BY missao_id, nome_normalizado
               ORDER BY enviado_em, relatorio_id, categoria
           ) AS ocorrencia
    FROM normalizados
)
INSERT INTO pessoas_pontuadas (
    id, missao_id, relatorio_id, nome, nome_normalizado, ocorrencia, categoria, dados
)
SELECT gen_random_uuid(), missao_id, relatorio_id, nome, nome_normalizado, ocorrencia, categoria, '{}'
FROM numerados;

UPDATE relatorios relatorio
SET avisos = '[{"codigo":"NOME_REPETIDO_NA_MISSAO","mensagem":"Um nome repetido nesta missão foi considerado como pessoa diferente. Confira possíveis homônimos; a identificação usa somente o nome."}]'
WHERE EXISTS (
    SELECT 1
    FROM pessoas_pontuadas pessoa
    JOIN pessoas_pontuadas outra
      ON outra.missao_id = pessoa.missao_id
     AND outra.nome_normalizado = pessoa.nome_normalizado
     AND outra.id <> pessoa.id
    WHERE pessoa.relatorio_id = relatorio.id
);

CREATE INDEX idx_missoes_rally_id ON missoes(rally_id);
CREATE INDEX idx_pessoas_pontuadas_relatorio_id ON pessoas_pontuadas(relatorio_id);
