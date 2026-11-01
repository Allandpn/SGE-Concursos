-- =============================================================================
-- V27 — Discursiva e régua do edital (Sprint 23, docs/SPRINT-23-REGISTRO-E-PROVA-REAL.md §1;
-- 01_DOMINIO §3.3, §3.20, D-134 a D-136; ADR-055).
-- =============================================================================

-- D-134: o quinto tipo de sessão.
ALTER TABLE sessao ADD COLUMN nota_discursiva INTEGER;

ALTER TABLE sessao DROP CONSTRAINT ck_sessao_tipo;
ALTER TABLE sessao ADD CONSTRAINT ck_sessao_tipo
    CHECK (tipo IN ('ESTUDO', 'QUESTOES', 'FLASHCARDS', 'RECUPERACAO', 'DISCURSIVA'));

ALTER TABLE sessao DROP CONSTRAINT ck_sessao_d04a_previsao_por_tipo;
ALTER TABLE sessao ADD CONSTRAINT ck_sessao_d04a_previsao_por_tipo
    CHECK (
        (tipo = 'ESTUDO' AND previsao_percentual IS NULL AND previsao_reconstrucao IS NULL)
        OR (tipo IN ('QUESTOES', 'FLASHCARDS') AND previsao_reconstrucao IS NULL AND (resultado IS NULL OR previsao_percentual IS NOT NULL))
        OR (tipo IN ('RECUPERACAO', 'DISCURSIVA') AND previsao_percentual IS NULL AND (resultado IS NULL OR previsao_reconstrucao IS NOT NULL))
    );

-- D-134: a nota existe só na discursiva, sempre, entre 0 e 100.
ALTER TABLE sessao ADD CONSTRAINT ck_sessao_d134_discursiva
    CHECK ((tipo = 'DISCURSIVA') = (nota_discursiva IS NOT NULL) AND (nota_discursiva IS NULL OR nota_discursiva BETWEEN 0 AND 100));

INSERT INTO parametro (chave, valor, descricao) VALUES
    ('discursiva_limiar_sucesso', '70', 'Nota mínima (0–100) da discursiva para Reconstruí (01_DOMINIO §4.1, D-134)'),
    ('discursiva_limiar_parcial', '40', 'Nota mínima (0–100) da discursiva para Faltou um pedaço; abaixo, Não veio (D-134)');

-- D-135: a régua da prova no concurso. Disciplina sem linha em concurso_pontuacao vale 1 ponto.
ALTER TABLE concurso ADD COLUMN errada_anula_certa BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE concurso ADD COLUMN nota_corte NUMERIC(8,2);
ALTER TABLE concurso ADD CONSTRAINT ck_concurso_d135_nota_corte CHECK (nota_corte IS NULL OR nota_corte >= 0);

CREATE TABLE concurso_pontuacao (
    concurso_id    BIGINT NOT NULL,
    disciplina_id  BIGINT NOT NULL,
    pontos         NUMERIC(6,2) NOT NULL,
    CONSTRAINT pk_concurso_pontuacao PRIMARY KEY (concurso_id, disciplina_id),
    CONSTRAINT fk_concurso_pontuacao_concurso FOREIGN KEY (concurso_id) REFERENCES concurso (id),
    CONSTRAINT fk_concurso_pontuacao_disciplina FOREIGN KEY (disciplina_id) REFERENCES disciplina (id),
    CONSTRAINT ck_concurso_pontuacao_d135_pontos CHECK (pontos > 0)
);
