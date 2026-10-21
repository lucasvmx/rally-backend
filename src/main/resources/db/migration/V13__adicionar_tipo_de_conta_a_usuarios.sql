ALTER TABLE usuarios_acesso
    ADD COLUMN tipo_conta VARCHAR(20) NOT NULL DEFAULT 'GOOGLE',
    ADD CONSTRAINT ck_usuario_tipo_conta CHECK (tipo_conta IN ('LOCAL', 'GOOGLE'));

UPDATE usuarios_acesso AS usuario
SET tipo_conta = 'LOCAL'
WHERE usuario.google_subject IS NULL
  AND EXISTS (
      SELECT 1
      FROM contas_acesso AS conta
      WHERE conta.email = usuario.email
  );
