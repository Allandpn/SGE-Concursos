-- Sprint 26 — Reprocessar preservando a decisão (D-86, D-89, D-95) e material por casamento (§3.10, D-94).
-- Fonte: docs/SPRINT-26-REPROCESSAR-E-MATERIAL.md §2.

-- D-95: a parte que a pessoa já decidiu e que a IA, na última execução, propôs diferente.
ALTER TABLE edital_parte ADD COLUMN ia_mudou BOOLEAN NOT NULL DEFAULT false;

-- D-89: de qual execução veio o assunto; quem deixou de vir na última entrega é marcado "não aparece mais", nunca removido.
ALTER TABLE edital_assunto ADD COLUMN execucao_id BIGINT;
ALTER TABLE edital_assunto ADD CONSTRAINT fk_edital_assunto_execucao FOREIGN KEY (execucao_id) REFERENCES edital_execucao (id);
UPDATE edital_assunto a SET execucao_id = (
    SELECT max(e.id) FROM edital_execucao e JOIN edital_disciplina d ON d.analise_id = e.analise_id WHERE d.id = a.disciplina_id);

-- "Reprocessar" pedido pela pessoa: o worker lê a marca e refaz a leitura do zero; a entrega a apaga.
ALTER TABLE analise_edital ADD COLUMN reprocessar BOOLEAN NOT NULL DEFAULT false;

-- O que o planejamento entregou do material do assunto. A situação do que existe é derivada dos segmentos (D-16); aqui
-- ficam só as lacunas por parte, o tempo estimado e o PDF de consulta, que os segmentos não dizem.
ALTER TABLE edital_assunto
    ADD COLUMN material_situacao VARCHAR(12),
    ADD COLUMN material_minutos INTEGER,
    ADD COLUMN material_fontes INTEGER,
    ADD COLUMN material_lacunas TEXT NOT NULL DEFAULT '[]',
    ADD COLUMN material_consulta VARCHAR(300),
    ADD COLUMN material_consulta_paginas INTEGER,
    ADD COLUMN material_em TIMESTAMPTZ,
    ADD CONSTRAINT ck_edital_assunto_material_situacao CHECK (material_situacao IS NULL OR material_situacao IN
        ('COMPLETO', 'PARCIAL', 'SEM_MATERIAL', 'RECUSADO'));
