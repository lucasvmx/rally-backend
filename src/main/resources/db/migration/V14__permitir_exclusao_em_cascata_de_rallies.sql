ALTER TABLE relatorios
    DROP CONSTRAINT relatorios_missao_id_fkey,
    ADD CONSTRAINT fk_relatorio_missao
        FOREIGN KEY (missao_id) REFERENCES missoes(id) ON DELETE CASCADE;

ALTER TABLE arquivos_drive
    DROP CONSTRAINT arquivos_drive_relatorio_id_fkey,
    ADD CONSTRAINT fk_arquivo_drive_relatorio
        FOREIGN KEY (relatorio_id) REFERENCES relatorios(id) ON DELETE CASCADE;

ALTER TABLE respostas_formularios
    DROP CONSTRAINT respostas_formularios_relatorio_id_fkey,
    ADD CONSTRAINT fk_resposta_formulario_relatorio
        FOREIGN KEY (relatorio_id) REFERENCES relatorios(id) ON DELETE CASCADE;

ALTER TABLE respostas_formularios
    DROP CONSTRAINT respostas_formularios_formulario_id_fkey,
    ADD CONSTRAINT fk_resposta_formulario_formulario
        FOREIGN KEY (formulario_id) REFERENCES formularios_publicos(id) ON DELETE CASCADE;

CREATE INDEX idx_respostas_formularios_formulario_id ON respostas_formularios(formulario_id);

ALTER TABLE metas_nacionais
    DROP CONSTRAINT metas_nacionais_missao_id_fkey,
    ADD CONSTRAINT fk_meta_nacional_missao
        FOREIGN KEY (missao_id) REFERENCES missoes(id) ON DELETE CASCADE;

ALTER TABLE pessoas_pontuadas
    DROP CONSTRAINT fk_pessoa_relatorio_missao,
    ADD CONSTRAINT fk_pessoa_relatorio_missao
        FOREIGN KEY (relatorio_id, missao_id)
        REFERENCES relatorios(id, missao_id) ON DELETE CASCADE;

ALTER TABLE missoes
    DROP CONSTRAINT fk_missao_rally,
    ADD CONSTRAINT fk_missao_rally
        FOREIGN KEY (rally_id) REFERENCES rallies(id) ON DELETE CASCADE;

ALTER TABLE pontuacoes_manuais
    DROP CONSTRAINT pontuacoes_manuais_rally_id_fkey,
    ADD CONSTRAINT fk_pontuacao_manual_rally
        FOREIGN KEY (rally_id) REFERENCES rallies(id) ON DELETE CASCADE;

CREATE INDEX idx_arquivos_drive_relatorio_id ON arquivos_drive(relatorio_id);

ALTER TABLE formularios_publicos ADD COLUMN missao_id UUID;

UPDATE formularios_publicos
SET missao_id = COALESCE(
    NULLIF(definicao::jsonb ->> 'missao_id', '')::uuid,
    NULLIF(definicao::jsonb ->> 'missaoId', '')::uuid
)
WHERE COALESCE(definicao::jsonb ->> 'missao_id', definicao::jsonb ->> 'missaoId') IS NOT NULL;

ALTER TABLE formularios_publicos
    ADD CONSTRAINT fk_formulario_publico_missao
        FOREIGN KEY (missao_id) REFERENCES missoes(id) ON DELETE CASCADE;

CREATE INDEX idx_formularios_publicos_missao_id ON formularios_publicos(missao_id);
