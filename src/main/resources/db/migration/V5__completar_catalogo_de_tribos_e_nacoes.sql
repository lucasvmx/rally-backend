ALTER TABLE tribos
    ADD COLUMN IF NOT EXISTS ativa BOOLEAN NOT NULL DEFAULT FALSE;

INSERT INTO nacoes (id, nome) VALUES
    (gen_random_uuid(), 'Infalível'),
    (gen_random_uuid(), 'Imbatível'),
    (gen_random_uuid(), 'Impacto'),
    (gen_random_uuid(), 'Invencível')
ON CONFLICT (nome) DO NOTHING;

WITH tribos_catalogo (nome) AS (
    VALUES
        ('Rúben'),
        ('Simeão'),
        ('Judá'),
        ('Dã'),
        ('Naftali'),
        ('Gade'),
        ('Aser'),
        ('Issacar'),
        ('Zebulom'),
        ('Efraim'),
        ('Manassés'),
        ('Benjamim')
)
INSERT INTO tribos (id, nome, nacao_id)
SELECT gen_random_uuid(), tribos_catalogo.nome, nacoes.id
FROM tribos_catalogo
CROSS JOIN nacoes
ON CONFLICT (nome, nacao_id) DO NOTHING;
