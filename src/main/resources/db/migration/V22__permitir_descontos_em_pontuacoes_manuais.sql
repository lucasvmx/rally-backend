ALTER TABLE pontuacoes_manuais
    DROP CONSTRAINT pontuacoes_manuais_pontos_por_tribo_check,
    ADD CONSTRAINT ck_pontuacoes_manuais_pontos_por_tribo
        CHECK (pontos_por_tribo BETWEEN -1000000 AND 1000000 AND pontos_por_tribo <> 0);
