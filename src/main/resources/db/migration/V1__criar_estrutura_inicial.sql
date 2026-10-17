CREATE TABLE nacoes (
    id UUID PRIMARY KEY,
    nome VARCHAR(120) NOT NULL UNIQUE
);

CREATE TABLE tribos (
    id UUID PRIMARY KEY,
    nome VARCHAR(120) NOT NULL,
    nacao_id UUID NOT NULL REFERENCES nacoes(id),
    CONSTRAINT uq_tribo_nome_por_nacao UNIQUE (nome, nacao_id)
);

CREATE TABLE missoes (
    id UUID PRIMARY KEY,
    nome VARCHAR(180) NOT NULL,
    descricao TEXT,
    data_inicio DATE NOT NULL,
    data_fim DATE NOT NULL,
    prazo_envio TIMESTAMP,
    pontos_por_pessoa INTEGER NOT NULL DEFAULT 0,
    pontos_por_novo_afastado INTEGER NOT NULL DEFAULT 0,
    bonificacao_a_cada_cinco_novos INTEGER NOT NULL DEFAULT 0,
    meta_nacional INTEGER NOT NULL DEFAULT 0,
    bonificacao_meta_nacional INTEGER NOT NULL DEFAULT 0,
    exige_foto BOOLEAN NOT NULL DEFAULT TRUE,
    tipo_pessoa VARCHAR(60) NOT NULL DEFAULT 'integrante',
    status VARCHAR(30) NOT NULL DEFAULT 'PUBLICADA',
    versao INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE relatorios (
    id UUID PRIMARY KEY,
    missao_id UUID NOT NULL REFERENCES missoes(id),
    tribo_id UUID NOT NULL REFERENCES tribos(id),
    quantidade_pessoas INTEGER NOT NULL DEFAULT 0 CHECK (quantidade_pessoas >= 0),
    quantidade_novos_afastados INTEGER NOT NULL DEFAULT 0 CHECK (quantidade_novos_afastados >= 0),
    observacoes TEXT,
    enviado_em TIMESTAMP NOT NULL,
    atrasado BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(30) NOT NULL DEFAULT 'ENVIADO',
    pontos_aprovados INTEGER NOT NULL DEFAULT 0,
    justificativa_revisao TEXT
);

CREATE TABLE arquivos_drive (
    id UUID PRIMARY KEY,
    relatorio_id UUID NOT NULL REFERENCES relatorios(id),
    identificador_drive VARCHAR(255) NOT NULL,
    nome_arquivo VARCHAR(255) NOT NULL,
    tipo_arquivo VARCHAR(120),
    tamanho_bytes BIGINT,
    hash_arquivo VARCHAR(128),
    enviado_em TIMESTAMP NOT NULL,
    UNIQUE (relatorio_id, identificador_drive)
);

CREATE TABLE tarefas_assincronas (
    id UUID PRIMARY KEY,
    tipo VARCHAR(80) NOT NULL,
    chave_idempotencia VARCHAR(180) NOT NULL UNIQUE,
    carga_util JSONB NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDENTE',
    tentativas INTEGER NOT NULL DEFAULT 0,
    proxima_tentativa TIMESTAMP,
    ultimo_erro TEXT,
    criada_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizada_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE auditoria (
    id UUID PRIMARY KEY,
    entidade VARCHAR(80) NOT NULL,
    entidade_id UUID,
    acao VARCHAR(80) NOT NULL,
    usuario_email VARCHAR(255),
    detalhes JSONB,
    criada_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_tribos_nacao_id ON tribos(nacao_id);
CREATE INDEX idx_relatorios_missao_id ON relatorios(missao_id);
CREATE INDEX idx_relatorios_tribo_id ON relatorios(tribo_id);
CREATE INDEX idx_relatorios_status ON relatorios(status);
CREATE INDEX idx_tarefas_status ON tarefas_assincronas(status, proxima_tentativa);
