-- Colunas sem fuso não registram de qual zona vieram os valores históricos.
-- Os prazos e pontuações manuais foram produzidos como horário civil de São Paulo.
-- Os demais timestamps de sistema são interpretados como UTC, conforme a hipótese
-- do deploy Docker atual, que não define outro fuso para a JVM ou PostgreSQL.
-- Históricos de outros ambientes/coortes precisam ter sua zona de escrita confirmada.

ALTER TABLE tarefas_assincronas
    ALTER COLUMN criada_em DROP DEFAULT,
    ALTER COLUMN atualizada_em DROP DEFAULT;
ALTER TABLE auditoria
    ALTER COLUMN criada_em DROP DEFAULT;
ALTER TABLE pontuacoes_manuais
    ALTER COLUMN registrada_em DROP DEFAULT;
ALTER TABLE logos_nacoes
    ALTER COLUMN atualizada_em DROP DEFAULT;

ALTER TABLE missoes
    ALTER COLUMN prazo_envio TYPE TIMESTAMPTZ
        USING prazo_envio AT TIME ZONE 'America/Sao_Paulo';
ALTER TABLE relatorios
    ALTER COLUMN enviado_em TYPE TIMESTAMPTZ
        USING enviado_em AT TIME ZONE 'UTC';
ALTER TABLE arquivos_drive
    ALTER COLUMN enviado_em TYPE TIMESTAMPTZ
        USING enviado_em AT TIME ZONE 'UTC';
ALTER TABLE tarefas_assincronas
    ALTER COLUMN proxima_tentativa TYPE TIMESTAMPTZ
        USING proxima_tentativa AT TIME ZONE 'UTC',
    ALTER COLUMN criada_em TYPE TIMESTAMPTZ
        USING criada_em AT TIME ZONE 'UTC',
    ALTER COLUMN atualizada_em TYPE TIMESTAMPTZ
        USING atualizada_em AT TIME ZONE 'UTC';
ALTER TABLE auditoria
    ALTER COLUMN criada_em TYPE TIMESTAMPTZ
        USING criada_em AT TIME ZONE 'UTC';
ALTER TABLE pontuacoes_manuais
    ALTER COLUMN registrada_em TYPE TIMESTAMPTZ
        USING registrada_em AT TIME ZONE 'America/Sao_Paulo';
ALTER TABLE logos_nacoes
    ALTER COLUMN atualizada_em TYPE TIMESTAMPTZ
        USING atualizada_em AT TIME ZONE 'UTC';

ALTER TABLE tarefas_assincronas
    ALTER COLUMN criada_em SET DEFAULT CURRENT_TIMESTAMP,
    ALTER COLUMN atualizada_em SET DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE auditoria
    ALTER COLUMN criada_em SET DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE pontuacoes_manuais
    ALTER COLUMN registrada_em SET DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE logos_nacoes
    ALTER COLUMN atualizada_em SET DEFAULT CURRENT_TIMESTAMP;
