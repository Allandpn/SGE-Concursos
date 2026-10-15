-- Sprint 17, atrito 2 — D-29: duração de bloco é parâmetro, não constante.
-- Pré-preenche o tempo de Registrar conteúdo quando o segmento não traz
-- estimativa própria (01_DOMINIO §7.7). Fonte: docs/SPRINT-17-USO-REAL.md §2.

INSERT INTO parametro (chave, valor, descricao) VALUES
    ('duracao_bloco_min', '50', 'Duração sugerida de um bloco de conteúdo, em minutos — só pré-preenche o tempo do registro, que o usuário confirma ou corrige (01_DOMINIO §7.7, D-29)');
