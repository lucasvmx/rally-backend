CREATE TABLE pontuacoes_manuais (
    id UUID PRIMARY KEY,
    rally_id UUID NOT NULL REFERENCES rallies(id),
    nacao_id UUID NOT NULL REFERENCES nacoes(id),
    tribo_chamada_id UUID NOT NULL REFERENCES tribos(id),
    atividade VARCHAR(180) NOT NULL CHECK (btrim(atividade) <> ''),
    pontos_por_tribo INTEGER NOT NULL DEFAULT 7000
        CHECK (pontos_por_tribo BETWEEN 1 AND 1000000),
    registrado_por VARCHAR(255) NOT NULL,
    registrada_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE pontuacoes_manuais_destinatarias (
    pontuacao_manual_id UUID NOT NULL REFERENCES pontuacoes_manuais(id) ON DELETE CASCADE,
    tribo_id UUID NOT NULL REFERENCES tribos(id),
    PRIMARY KEY (pontuacao_manual_id, tribo_id)
);

CREATE INDEX idx_pontuacoes_manuais_rally_id ON pontuacoes_manuais(rally_id);
CREATE INDEX idx_pontuacoes_manuais_destinatarias_tribo_id
    ON pontuacoes_manuais_destinatarias(tribo_id);
