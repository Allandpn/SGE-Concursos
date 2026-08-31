-- Item 3.1 de PROGRESSO.md — chaves de parametro que a Sprint 3 consome.
-- Fonte: docs/SPRINT-3-SESSAO.md §2 (valores de partida de 01_DOMINIO §4.2.1).
-- lote_minimo_questoes (01_DOMINIO §4.3) não entra aqui de propósito: sem
-- efeito até a escada existir (Sprint 4) — inserir agora seria configuração
-- que nenhum código lê.

INSERT INTO parametro (chave, valor, descricao) VALUES
    ('limiar_sucesso_multipla_escolha', '80', 'Percentual mínimo de acerto para SUCESSO em QUESTOES de múltipla escolha (01_DOMINIO §4.2.1)'),
    ('limiar_parcial_multipla_escolha', '60', 'Percentual mínimo de acerto para PARCIAL em QUESTOES de múltipla escolha (01_DOMINIO §4.2.1)'),
    ('limiar_sucesso_certo_errado',     '90', 'Percentual mínimo de acerto para SUCESSO em QUESTOES certo/errado (01_DOMINIO §4.2.1)'),
    ('limiar_parcial_certo_errado',     '75', 'Percentual mínimo de acerto para PARCIAL em QUESTOES certo/errado (01_DOMINIO §4.2.1)'),
    ('limiar_sucesso_flashcards',       '80', 'Percentual mínimo de acerto para SUCESSO em FLASHCARDS (01_DOMINIO §4.2, sem banca)'),
    ('limiar_parcial_flashcards',       '60', 'Percentual mínimo de acerto para PARCIAL em FLASHCARDS (01_DOMINIO §4.2, sem banca)');
