-- Sprint 17, lacuna 5 — D-35: após N resultados PARCIAL seguidos no mesmo
-- assunto, o sistema sugere dividi-lo (01_DOMINIO §3.2.1, partida 4).
-- Fonte: docs/SPRINT-17-USO-REAL.md §6.1.

INSERT INTO parametro (chave, valor, descricao) VALUES
    ('parciais_seguidos_dividir', '4', 'Quantos resultados PARCIAL seguidos no mesmo assunto fazem o balanço semanal sugerir dividi-lo (01_DOMINIO §3.2.1, D-35)');
