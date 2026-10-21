CREATE TABLE logos_nacoes (
    nacao_id UUID PRIMARY KEY REFERENCES nacoes(id) ON DELETE CASCADE,
    tipo_arquivo VARCHAR(24) NOT NULL CHECK (tipo_arquivo IN ('image/png', 'image/jpeg')),
    arquivo BYTEA NOT NULL CHECK (octet_length(arquivo) <= 2097152),
    atualizada_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
