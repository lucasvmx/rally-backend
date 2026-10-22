ALTER TABLE respostas_formularios
    ALTER COLUMN formulario_id DROP NOT NULL,
    ADD COLUMN definicao TEXT;
