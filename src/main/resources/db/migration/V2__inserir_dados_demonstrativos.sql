INSERT INTO nacoes (id, nome) VALUES
    ('00000000-0000-0000-0000-000000000001', 'Infalível'),
    ('00000000-0000-0000-0000-000000000002', 'Impacto'),
    ('00000000-0000-0000-0000-000000000003', 'Invencível'),
    ('00000000-0000-0000-0000-000000000004', 'Imbatível');

INSERT INTO tribos (id, nome, nacao_id) VALUES
    ('10000000-0000-0000-0000-000000000001', 'Benjamim', '00000000-0000-0000-0000-000000000001'),
    ('10000000-0000-0000-0000-000000000002', 'Aser', '00000000-0000-0000-0000-000000000002'),
    ('10000000-0000-0000-0000-000000000003', 'Rúben', '00000000-0000-0000-0000-000000000003'),
    ('10000000-0000-0000-0000-000000000004', 'Manassés', '00000000-0000-0000-0000-000000000003'),
    ('10000000-0000-0000-0000-000000000005', 'Efraim', '00000000-0000-0000-0000-000000000004'),
    ('10000000-0000-0000-0000-000000000006', 'Gade', '00000000-0000-0000-0000-000000000004'),
    ('10000000-0000-0000-0000-000000000007', 'Zebulom', '00000000-0000-0000-0000-000000000004');

INSERT INTO missoes (
    id, nome, descricao, data_inicio, data_fim, prazo_envio,
    pontos_por_pessoa, pontos_por_novo_afastado, bonificacao_a_cada_cinco_novos,
    meta_nacional, bonificacao_meta_nacional, exige_foto, tipo_pessoa, status, versao
) VALUES
    ('20000000-0000-0000-0000-000000000001', 'Algo a mais', 'Missão de segunda-feira.', '2026-09-14', '2026-09-14', '2026-09-14 23:00:00', 1500, 4000, 0, 0, 0, TRUE, 'integrante', 'PUBLICADA', 1),
    ('20000000-0000-0000-0000-000000000002', 'FJU nas reuniões principais', 'Reuniões de quarta a sexta às 20h.', '2026-09-16', '2026-09-18', '2026-09-18 23:00:00', 1000, 0, 0, 0, 0, TRUE, 'integrante', 'PUBLICADA', 1),
    ('20000000-0000-0000-0000-000000000003', 'FJU nas reuniões alternativas', 'Reuniões de quarta a sexta em horários alternativos.', '2026-09-16', '2026-09-18', '2026-09-18 23:00:00', 500, 0, 0, 0, 0, TRUE, 'integrante', 'PUBLICADA', 1),
    ('20000000-0000-0000-0000-000000000004', 'Hora dos solteiros', 'Missão de quinta-feira.', '2026-09-17', '2026-09-17', '2026-09-17 23:00:00', 2000, 0, 0, 0, 0, TRUE, 'integrante', 'PUBLICADA', 1),
    ('20000000-0000-0000-0000-000000000005', 'FJU no encontro com Deus', 'Reunião das 9h30.', '2026-09-20', '2026-09-20', '2026-09-20 20:00:00', 2000, 0, 0, 0, 0, TRUE, 'integrante', 'PUBLICADA', 1),
    ('20000000-0000-0000-0000-000000000006', 'FJU no horário alternativo', 'Horário alternativo do encontro com Deus.', '2026-09-20', '2026-09-20', '2026-09-20 20:00:00', 1000, 0, 0, 0, 0, TRUE, 'integrante', 'PUBLICADA', 1),
    ('20000000-0000-0000-0000-000000000007', 'Mega Help', 'Mega Help com bônus para novos ou afastados.', '2026-09-20', '2026-09-20', '2026-09-20 23:00:00', 4000, 5000, 25000, 0, 0, TRUE, 'integrante', 'PUBLICADA', 1),
    ('20000000-0000-0000-0000-000000000008', 'Participação no Mega Quiz', 'Cada nação deve levar cinco jovens novos ou afastados.', '2026-09-20', '2026-09-20', '2026-09-20 23:00:00', 0, 0, 0, 5, 40000, TRUE, 'novo_ou_afastado', 'PUBLICADA', 1);
