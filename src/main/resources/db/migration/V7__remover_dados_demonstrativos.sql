-- IDs fixos das migrations antigas tinham bits de versão/variante inválidos.
-- A detecção usa esses bits sem repetir valores de UUID no código.

ALTER TABLE tribos DROP CONSTRAINT tribos_nacao_id_fkey;
ALTER TABLE tribos
    ADD CONSTRAINT tribos_nacao_id_fkey
    FOREIGN KEY (nacao_id) REFERENCES nacoes(id) ON UPDATE CASCADE;

ALTER TABLE relatorios DROP CONSTRAINT relatorios_missao_id_fkey;
ALTER TABLE relatorios
    ADD CONSTRAINT relatorios_missao_id_fkey
    FOREIGN KEY (missao_id) REFERENCES missoes(id) ON UPDATE CASCADE;

ALTER TABLE relatorios DROP CONSTRAINT relatorios_tribo_id_fkey;
ALTER TABLE relatorios
    ADD CONSTRAINT relatorios_tribo_id_fkey
    FOREIGN KEY (tribo_id) REFERENCES tribos(id) ON UPDATE CASCADE;

ALTER TABLE metas_nacionais DROP CONSTRAINT metas_nacionais_missao_id_fkey;
ALTER TABLE metas_nacionais
    ADD CONSTRAINT metas_nacionais_missao_id_fkey
    FOREIGN KEY (missao_id) REFERENCES missoes(id) ON UPDATE CASCADE;

ALTER TABLE metas_nacionais DROP CONSTRAINT metas_nacionais_nacao_id_fkey;
ALTER TABLE metas_nacionais
    ADD CONSTRAINT metas_nacionais_nacao_id_fkey
    FOREIGN KEY (nacao_id) REFERENCES nacoes(id) ON UPDATE CASCADE;

DELETE FROM metas_nacionais
WHERE substring(id::text, 15, 4) = '0000'
  AND substring(id::text, 20, 4) = '0000';

DELETE FROM missoes missao
WHERE substring(missao.id::text, 15, 4) = '0000'
  AND substring(missao.id::text, 20, 4) = '0000'
  AND NOT EXISTS (
      SELECT 1 FROM relatorios relatorio WHERE relatorio.missao_id = missao.id
  )
  AND NOT EXISTS (
      SELECT 1 FROM metas_nacionais meta WHERE meta.missao_id = missao.id
  );

UPDATE nacoes
SET id = gen_random_uuid()
WHERE substring(id::text, 15, 4) = '0000'
  AND substring(id::text, 20, 4) = '0000';

UPDATE tribos
SET id = gen_random_uuid()
WHERE substring(id::text, 15, 4) = '0000'
  AND substring(id::text, 20, 4) = '0000';

UPDATE missoes
SET id = gen_random_uuid()
WHERE substring(id::text, 15, 4) = '0000'
  AND substring(id::text, 20, 4) = '0000';

UPDATE metas_nacionais
SET id = gen_random_uuid()
WHERE substring(id::text, 15, 4) = '0000'
  AND substring(id::text, 20, 4) = '0000';

ALTER TABLE tribos DROP CONSTRAINT tribos_nacao_id_fkey;
ALTER TABLE tribos
    ADD CONSTRAINT tribos_nacao_id_fkey
    FOREIGN KEY (nacao_id) REFERENCES nacoes(id);

ALTER TABLE relatorios DROP CONSTRAINT relatorios_missao_id_fkey;
ALTER TABLE relatorios
    ADD CONSTRAINT relatorios_missao_id_fkey
    FOREIGN KEY (missao_id) REFERENCES missoes(id);

ALTER TABLE relatorios DROP CONSTRAINT relatorios_tribo_id_fkey;
ALTER TABLE relatorios
    ADD CONSTRAINT relatorios_tribo_id_fkey
    FOREIGN KEY (tribo_id) REFERENCES tribos(id);

ALTER TABLE metas_nacionais DROP CONSTRAINT metas_nacionais_missao_id_fkey;
ALTER TABLE metas_nacionais
    ADD CONSTRAINT metas_nacionais_missao_id_fkey
    FOREIGN KEY (missao_id) REFERENCES missoes(id);

ALTER TABLE metas_nacionais DROP CONSTRAINT metas_nacionais_nacao_id_fkey;
ALTER TABLE metas_nacionais
    ADD CONSTRAINT metas_nacionais_nacao_id_fkey
    FOREIGN KEY (nacao_id) REFERENCES nacoes(id);
