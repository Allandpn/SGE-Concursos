-- Sprint 18 — Concurso-alvo com histórico (ADR-042) e análise de edital por
-- IA (ADR-043). Fonte: docs/SPRINT-18-CONCURSOS.md §1; 01_DOMINIO §3.8, §3.9.

CREATE TABLE concurso (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nome           VARCHAR(250) NOT NULL,
    cargo          VARCHAR(250),
    banca          VARCHAR(150),
    data_prova     DATE,
    origem         VARCHAR(20) NOT NULL,
    -- Pasta do planejamento (SGE-Importacao/<slug>/): identidade de
    -- reimportação. NULL no concurso criado pela análise por IA.
    slug           VARCHAR(150),
    ativo          BOOLEAN NOT NULL DEFAULT false,
    ativado_em     TIMESTAMPTZ,
    desativado_em  TIMESTAMPTZ,
    criado_em      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_concurso_origem CHECK (origem IN ('ANALISE_IA', 'PLANEJAMENTO')),
    CONSTRAINT ux_concurso_slug UNIQUE (slug)
);

-- D-58 — no máximo um concurso-alvo por vez. Índice único PARCIAL sobre a
-- constante: só as linhas ativas entram, e todas teriam a mesma chave.
CREATE UNIQUE INDEX ux_concurso_d58_um_ativo ON concurso (ativo) WHERE ativo;

-- Cobertura do edital (01_DOMINIO §3.8): o que aquele edital disse de cada
-- assunto. A única relação n:n do domínio (§9).
CREATE TABLE concurso_assunto (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    concurso_id    BIGINT NOT NULL,
    assunto_id     BIGINT NOT NULL,
    disciplina_id  BIGINT NOT NULL,
    peso           VARCHAR(10) NOT NULL,
    ordem          INTEGER NOT NULL,
    CONSTRAINT fk_concurso_assunto_concurso FOREIGN KEY (concurso_id) REFERENCES concurso (id),
    CONSTRAINT fk_concurso_assunto_assunto FOREIGN KEY (assunto_id) REFERENCES assunto (id),
    CONSTRAINT fk_concurso_assunto_disciplina FOREIGN KEY (disciplina_id) REFERENCES disciplina (id),
    CONSTRAINT ck_concurso_assunto_peso CHECK (peso IN ('ALTO', 'MEDIO', 'BAIXO')),
    CONSTRAINT ux_concurso_assunto UNIQUE (concurso_id, assunto_id)
);

CREATE INDEX ix_concurso_assunto_assunto ON concurso_assunto (assunto_id);

-- Análise de edital (01_DOMINIO §3.9): processo, não estado de estudo. O PDF
-- não é guardado, só o texto extraído (ADR-043).
CREATE TABLE analise_edital (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nome_arquivo     VARCHAR(250) NOT NULL,
    texto            TEXT NOT NULL,
    status           VARCHAR(30) NOT NULL,
    etapa_falha      VARCHAR(30),
    erro             TEXT,
    modelo           VARCHAR(150),
    concurso_nome    VARCHAR(250),
    banca            VARCHAR(150),
    data_prova       DATE,
    cargos           TEXT,
    cargo_escolhido  VARCHAR(250),
    proposta         TEXT,
    concurso_id      BIGINT,
    criado_em        TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_analise_edital_concurso FOREIGN KEY (concurso_id) REFERENCES concurso (id),
    CONSTRAINT ck_analise_edital_status CHECK (status IN
        ('LENDO_EDITAL', 'ESCOLHER_CARGO', 'SEPARANDO_ASSUNTOS', 'PRONTA', 'CONFIRMADA', 'FALHOU')),
    CONSTRAINT ck_analise_edital_etapa_falha CHECK (etapa_falha IS NULL OR etapa_falha IN
        ('LENDO_EDITAL', 'SEPARANDO_ASSUNTOS'))
);
