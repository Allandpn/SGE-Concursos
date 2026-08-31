-- Item 5.1 de PROGRESSO.md — chaves de parametro que a Sprint 5 consome.
-- Fonte: docs/SPRINT-5-FRENTE.md §0.

INSERT INTO parametro (chave, valor, descricao) VALUES
    ('teto_diario_recuperacoes', '8',   'Recuperações oferecidas por dia antes de rolar pro dia seguinte (01_DOMINIO §5.3/§12)'),
    ('teto_global_frente',       '100', 'Quantos assuntos podem estar na frente de estudo ao mesmo tempo (01_DOMINIO §6.7)'),
    ('teto_disciplina_frente',   '12',  'Quantos assuntos de uma mesma disciplina podem estar na frente ao mesmo tempo (01_DOMINIO §6.7)');
