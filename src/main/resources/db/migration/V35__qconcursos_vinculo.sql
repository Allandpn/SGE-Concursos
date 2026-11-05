-- =============================================================================
-- V35 — O assunto ligado à árvore do QConcursos e as questões por vez (Sprint 45,
-- docs/SPRINT-45-VINCULO-QCONCURSOS.md; 01_DOMINIO §3.24, D-173 e D-174).
--
-- O vínculo é escolhido pela pessoa e vale antes dos nós do edital (D-173). Guarda
-- só o id do nó (o mesmo da árvore: negativo para disciplina); nome e caminho vêm
-- da versão vigente. Até 3 por assunto, conferido no serviço.
-- =============================================================================

CREATE TABLE assunto_qconcursos_no (
    assunto_id  BIGINT NOT NULL,
    no_id       BIGINT NOT NULL,
    ordem       INT NOT NULL,
    ligado_em   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_assunto_qconcursos_no PRIMARY KEY (assunto_id, no_id),
    CONSTRAINT fk_assunto_qconcursos_no_assunto FOREIGN KEY (assunto_id) REFERENCES assunto (id),
    CONSTRAINT ck_assunto_qconcursos_no_d173_ordem CHECK (ordem BETWEEN 1 AND 3)
);

CREATE INDEX ix_assunto_qconcursos_no_no ON assunto_qconcursos_no (no_id);

-- D-174: quantas questões a página do QConcursos abre; 0 = pelo Pomodoro (D-152).
INSERT INTO parametro (chave, valor, descricao) VALUES
    ('questoes_por_vez', '0', 'Questões por vez no atalho do QConcursos: 5, 10, 15 ou 20; 0 = automático, pelo Pomodoro (01_DOMINIO §3.24, D-174)');
