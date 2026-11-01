ALTER TABLE pontuacoes_manuais
    ADD COLUMN missao_id UUID,
    ADD CONSTRAINT fk_pontuacao_manual_missao
        FOREIGN KEY (missao_id) REFERENCES missoes(id) ON DELETE SET NULL;

CREATE INDEX idx_pontuacoes_manuais_missao_id ON pontuacoes_manuais(missao_id);
