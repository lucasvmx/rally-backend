CREATE TABLE usuarios_acesso (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    google_subject VARCHAR(255) UNIQUE,
    papel VARCHAR(40) NOT NULL,
    tribo_id UUID REFERENCES tribos(id),
    nacao_id UUID REFERENCES nacoes(id),
    criada_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizada_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_usuario_papel CHECK (papel IN (
        'ASSISTENTE', 'COORDENADOR_TRIBO', 'COORDENADOR_NACAO', 'ADMINISTRADOR_SISTEMA'
    )),
    CONSTRAINT ck_usuario_escopo CHECK (
        (papel = 'COORDENADOR_TRIBO' AND tribo_id IS NOT NULL AND nacao_id IS NULL)
        OR (papel = 'COORDENADOR_NACAO' AND tribo_id IS NULL AND nacao_id IS NOT NULL)
        OR (papel IN ('ASSISTENTE', 'ADMINISTRADOR_SISTEMA') AND tribo_id IS NULL AND nacao_id IS NULL)
    ),
    CONSTRAINT ck_usuario_email_normalizado CHECK (email = LOWER(email))
);

INSERT INTO usuarios_acesso (id, email, papel)
SELECT gen_random_uuid(), email, 'ASSISTENTE'
FROM contas_acesso
ON CONFLICT (email) DO NOTHING;

ALTER TABLE contas_acesso
    ADD CONSTRAINT fk_contas_acesso_usuario
    FOREIGN KEY (email) REFERENCES usuarios_acesso(email);
