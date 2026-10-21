ALTER TABLE respostas_formularios
    ADD COLUMN arquivo_anotado BYTEA,
    ADD COLUMN contagem_rostos BIGINT CHECK (contagem_rostos >= 0);
