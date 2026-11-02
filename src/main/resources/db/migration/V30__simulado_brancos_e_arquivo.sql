-- Sprint 34 (docs/SPRINT-34-REGISTRO-SIMULADO.md §1; 01_DOMINIO §3.6).

-- D-148: em branco é opcional; com ele, certas + brancos nunca passam do total.
ALTER TABLE resultado_simulado ADD COLUMN questoes_em_branco INTEGER;
ALTER TABLE resultado_simulado ADD CONSTRAINT ck_resultado_simulado_d148_brancos
    CHECK (questoes_em_branco IS NULL OR (questoes_em_branco >= 0 AND questoes_corretas + questoes_em_branco <= questoes_total));

-- D-149: simulado errado é arquivado, nunca apagado nem editado.
ALTER TABLE simulado ADD COLUMN arquivado_em TIMESTAMPTZ;
