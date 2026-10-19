CREATE TABLE contas_acesso (
    email VARCHAR(255) PRIMARY KEY,
    senha_hash VARCHAR(255) NOT NULL,
    tentativas_falhas SMALLINT NOT NULL DEFAULT 0,
    bloqueada_em TIMESTAMPTZ,
    criada_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizada_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_conta_email_normalizado CHECK (email = LOWER(email)),
    CONSTRAINT ck_conta_tentativas_falhas CHECK (tentativas_falhas BETWEEN 0 AND 3)
);
