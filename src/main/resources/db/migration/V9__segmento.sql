-- Sprint 10 — Segmento (leitura fatiada de material) e chaveExterna de
-- Assunto. Fonte: docs/SPRINT-10-SEGMENTO.md §1/§2.

ALTER TABLE assunto
    ADD COLUMN chave_externa TEXT;

-- D-52 — chave externa, quando presente, é única entre todos os assuntos.
-- Índice único comum: múltiplos NULL não conflitam entre si no Postgres,
-- diferente de ux_assunto_d48_ordem_por_disciplina (que é PARCIAL por outro
-- motivo — filtrar só os ativos, não lidar com NULL).
CREATE UNIQUE INDEX ux_assunto_d52_chave_externa
    ON assunto (chave_externa);

CREATE TABLE segmento (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    assunto_id          BIGINT NOT NULL,
    chave_externa       TEXT,
    ordem               INTEGER NOT NULL,
    arquivo             TEXT NOT NULL,
    pagina_inicial      INTEGER,
    pagina_final        INTEGER,
    tempo_estimado_min  INTEGER,
    criado_em           TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_segmento_assunto FOREIGN KEY (assunto_id) REFERENCES assunto (id),
    -- D-53 — todo segmento tem chave externa. CHECK, não NOT NULL nativo,
    -- mesmo padrão de D-41 (V1__tabelas.sql, ck_assunto_d41_ordem_obrigatoria):
    -- a regra precisa do identificador no nome da restrição (03_INVARIANTES §9.1).
    CONSTRAINT ck_segmento_d53_chave_externa_obrigatoria CHECK (chave_externa IS NOT NULL),
    -- D-53 — e única entre todos os segmentos. Identidade de reimportação.
    CONSTRAINT ux_segmento_d53_chave_externa UNIQUE (chave_externa),
    -- D-50 — ordem única dentro do assunto. Posição, não identidade.
    CONSTRAINT ux_segmento_d50_ordem_por_assunto UNIQUE (assunto_id, ordem),
    -- Sem D-xx: alvo técnico da FK composta de D-51 (sessao, abaixo).
    CONSTRAINT ux_segmento_id_assunto UNIQUE (id, assunto_id)
);

CREATE INDEX ix_segmento_assunto ON segmento (assunto_id, ordem);

ALTER TABLE sessao
    ADD COLUMN segmento_id BIGINT;

-- D-51 (primeira metade) — segmento pertence ao mesmo assunto da sessão.
-- FK composta: aponta pra UNIQUE(id, assunto_id) de segmento, não só a PK —
-- é assim que o Postgres impõe uma invariante entre duas tabelas.
ALTER TABLE sessao
    ADD CONSTRAINT fk_sessao_d51_segmento_mesmo_assunto
    FOREIGN KEY (segmento_id, assunto_id) REFERENCES segmento (id, assunto_id);

-- D-51 (segunda metade) — só sessão ESTUDO referencia segmento.
ALTER TABLE sessao
    ADD CONSTRAINT ck_sessao_d51_segmento_so_estudo
    CHECK (tipo = 'ESTUDO' OR segmento_id IS NULL);