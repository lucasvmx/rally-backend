CREATE TABLE metas_nacionais (
    id UUID PRIMARY KEY,
    missao_id UUID NOT NULL REFERENCES missoes(id),
    nacao_id UUID NOT NULL REFERENCES nacoes(id),
    quantidade_necessaria INTEGER NOT NULL CHECK (quantidade_necessaria > 0),
    premio_em_talentos INTEGER NOT NULL CHECK (premio_em_talentos >= 0),
    UNIQUE (missao_id, nacao_id)
);
