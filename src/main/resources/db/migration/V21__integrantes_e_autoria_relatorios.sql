CREATE TABLE integrantes_tribo (
    id UUID PRIMARY KEY,
    tribo_id UUID NOT NULL REFERENCES tribos(id) ON DELETE CASCADE,
    nome VARCHAR(160) NOT NULL CHECK (length(trim(nome)) > 0),
    titulo VARCHAR(20) NOT NULL CHECK (titulo IN ('INTEGRANTE', 'CPO', 'COLABORADOR', 'OBREIRO')),
    papel VARCHAR(30) CHECK (papel IN ('COORDENADOR_TRIBO', 'AUXILIAR_TRIBO'))
);
CREATE INDEX idx_integrantes_tribo ON integrantes_tribo(tribo_id);
ALTER TABLE relatorios ADD COLUMN autor_id UUID REFERENCES usuarios_acesso(id) ON DELETE SET NULL;
CREATE INDEX idx_relatorios_autor ON relatorios(autor_id);
