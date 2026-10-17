CREATE TABLE formularios_publicos (
 id UUID PRIMARY KEY,
 token VARCHAR(64) NOT NULL UNIQUE,
 definicao TEXT NOT NULL,
 ativo BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE TABLE respostas_formularios (
 relatorio_id UUID PRIMARY KEY REFERENCES relatorios(id),
 formulario_id UUID NOT NULL REFERENCES formularios_publicos(id),
 respostas TEXT NOT NULL,
 nome_arquivo VARCHAR(255),
 tipo_arquivo VARCHAR(120),
 arquivo BYTEA
);
