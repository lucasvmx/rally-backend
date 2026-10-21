ALTER TABLE usuarios_acesso
    ADD COLUMN ultimo_login_em TIMESTAMPTZ,
    ADD COLUMN versao_credenciais BIGINT NOT NULL DEFAULT 0;

-- V8 did not preserve legacy local registration timestamps. Keep Google-linked users
-- out of this backfill because a Google account can also have a local password.
UPDATE usuarios_acesso AS usuario
SET criada_em = conta.criada_em
FROM contas_acesso AS conta
WHERE conta.email = usuario.email
  AND usuario.tipo_conta = 'LOCAL'
  AND usuario.google_subject IS NULL;

ALTER TABLE contas_acesso
    ADD COLUMN troca_senha_obrigatoria BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE usuarios_acesso_revogados (
    email VARCHAR(255) PRIMARY KEY,
    google_subject VARCHAR(255),
    revogada_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_usuario_revogado_email_normalizado CHECK (email = LOWER(email))
);

CREATE INDEX idx_usuarios_acesso_revogados_google_subject
    ON usuarios_acesso_revogados (google_subject)
    WHERE google_subject IS NOT NULL;
