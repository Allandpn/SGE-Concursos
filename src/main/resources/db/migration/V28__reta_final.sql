-- Sprint 21 (docs/SPRINT-21-RETA-FINAL.md §1; 01_DOMINIO §3.22; ADR-056).

-- D-141: só o gesto é guardado; "ativa" é consulta. No máximo uma aberta por concurso.
CREATE TABLE reta_final (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    concurso_id  BIGINT NOT NULL,
    entrou_em    TIMESTAMPTZ NOT NULL DEFAULT now(),
    saiu_em      TIMESTAMPTZ,
    CONSTRAINT fk_reta_final_concurso FOREIGN KEY (concurso_id) REFERENCES concurso (id),
    CONSTRAINT ck_reta_final_saida_depois CHECK (saiu_em IS NULL OR saiu_em >= entrou_em)
);
CREATE UNIQUE INDEX ux_reta_final_d141_uma_aberta ON reta_final (concurso_id) WHERE saiu_em IS NULL;

-- D-140: "Agora não" cala a sugestão daquele concurso.
ALTER TABLE concurso ADD COLUMN reta_final_dispensada_em TIMESTAMPTZ;

-- D-143: a manutenção de peso alto antecipada guarda a data anterior.
ALTER TABLE revisao ADD COLUMN data_prevista_antes_reta_final DATE;

INSERT INTO parametro (chave, valor, descricao) VALUES
    ('reta_final_semanas_aviso', '8', 'Semanas antes da prova em que Hoje sugere entrar na reta final (01_DOMINIO §3.22, D-140)');
