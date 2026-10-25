-- Sprint 25 — Conferência do edital (ADR-047). Fonte: docs/SPRINT-25-CONFERENCIA-EDITAL.md §2;
-- 01_DOMINIO §3.12, D-86 a D-95.
--
-- Nada que se deriva é guardado (D-16): a fila de revisão e as contagens são consultas. Só entra
-- o que o planejamento entregou e o que a pessoa decidiu.

-- ---------------------------------------------------------------- analise_edital
-- A proposta por IA do SGE deixou de existir (ADR-047): o planejamento entrega a leitura e a
-- pessoa a confere. PRONTA só tinha proposta, nunca cadastro (D-62), então nada se perde.
UPDATE analise_edital
   SET status = 'FALHOU',
       etapa_falha = 'SEPARANDO_ASSUNTOS',
       erro = 'Análise anterior à conferência do edital; envie o edital de novo.'
 WHERE status = 'PRONTA';

ALTER TABLE analise_edital DROP COLUMN proposta;
ALTER TABLE analise_edital ADD COLUMN pdf_arquivo VARCHAR(300);

ALTER TABLE analise_edital DROP CONSTRAINT ck_analise_edital_status;
ALTER TABLE analise_edital ADD CONSTRAINT ck_analise_edital_status CHECK (status IN
    ('LENDO_EDITAL', 'ESCOLHER_CARGO', 'SEPARANDO_ASSUNTOS', 'EM_CONFERENCIA', 'CONFIRMADA', 'FALHOU'));

ALTER TABLE analise_edital DROP CONSTRAINT ck_analise_edital_etapa_falha;
ALTER TABLE analise_edital ALTER COLUMN etapa_falha TYPE VARCHAR(30);
ALTER TABLE analise_edital ADD CONSTRAINT ck_analise_edital_etapa_falha CHECK (etapa_falha IS NULL OR etapa_falha IN
    ('LENDO_EDITAL', 'SEPARANDO_ASSUNTOS', 'EXTRACAO', 'ORGANIZACAO', 'QUEBRA', 'LIGACAO'));

-- ---------------------------------------------------------------- árvore do QConcursos (D-90)
CREATE TABLE arvore_versao (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    rotulo          VARCHAR(100) NOT NULL,
    fonte           VARCHAR(30) NOT NULL,
    data            DATE NOT NULL,
    quantidade_nos  INTEGER NOT NULL,
    vigente         BOOLEAN NOT NULL DEFAULT false,
    carregada_em    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_arvore_versao_rotulo UNIQUE (rotulo)
);

-- D-90 — uma versão vigente por vez. Índice único PARCIAL sobre a constante, como D-58.
CREATE UNIQUE INDEX ux_arvore_d90_uma_vigente ON arvore_versao (vigente) WHERE vigente;

CREATE TABLE no_arvore (
    versao_id  BIGINT NOT NULL,
    no_id      BIGINT NOT NULL,
    nome       VARCHAR(500) NOT NULL,
    pai_id     BIGINT,
    ordem      INTEGER NOT NULL,
    raiz_id    BIGINT NOT NULL,
    PRIMARY KEY (versao_id, no_id),
    CONSTRAINT fk_no_arvore_versao FOREIGN KEY (versao_id) REFERENCES arvore_versao (id)
);

CREATE INDEX ix_no_arvore_filhos ON no_arvore (versao_id, pai_id, ordem);

CREATE TABLE no_arvore_diferenca (
    versao_id        BIGINT NOT NULL,
    no_id            BIGINT NOT NULL,
    tipo             VARCHAR(10) NOT NULL,
    nome_anterior    VARCHAR(500),
    pai_anterior_id  BIGINT,
    PRIMARY KEY (versao_id, no_id, tipo),
    CONSTRAINT fk_no_arvore_diferenca_versao FOREIGN KEY (versao_id) REFERENCES arvore_versao (id),
    CONSTRAINT ck_no_arvore_diferenca_tipo CHECK (tipo IN ('NOVO', 'REMOVIDO', 'RENOMEADO', 'MOVIDO'))
);

-- ---------------------------------------------------------------- a leitura entregue (D-88, D-89)
CREATE TABLE edital_execucao (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    analise_id       BIGINT NOT NULL,
    modelo           VARCHAR(150),
    prompt_versao    VARCHAR(50),
    arvore_versao_id BIGINT NOT NULL,
    executada_em     DATE,
    recebida_em      TIMESTAMPTZ NOT NULL DEFAULT now(),
    hash             VARCHAR(64) NOT NULL,
    CONSTRAINT fk_edital_execucao_analise FOREIGN KEY (analise_id) REFERENCES analise_edital (id),
    CONSTRAINT fk_edital_execucao_arvore FOREIGN KEY (arvore_versao_id) REFERENCES arvore_versao (id),
    -- D-89 — a mesma entrega duas vezes é uma só.
    CONSTRAINT ux_edital_execucao_d89 UNIQUE (analise_id, hash)
);

CREATE TABLE edital_disciplina (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    analise_id      BIGINT NOT NULL,
    ordem           INTEGER NOT NULL,
    nome            VARCHAR(250) NOT NULL,
    restricao       VARCHAR(250),
    comum           BOOLEAN NOT NULL DEFAULT false,
    questoes        INTEGER,
    -- Peso escolhido pela pessoa; nulo = o que a regra D-61 calcula das questões na conclusão.
    peso_pessoa     VARCHAR(10),
    qconcursos_ids  TEXT NOT NULL DEFAULT '[]',
    -- O texto da disciplina e as referências ("todo o conteúdo de X"): a conferência por regra, refeita quando a
    -- pessoa edita uma parte, precisa deles.
    texto           TEXT,
    referencias     TEXT NOT NULL DEFAULT '[]',
    tentativas      INTEGER NOT NULL DEFAULT 1,
    CONSTRAINT fk_edital_disciplina_analise FOREIGN KEY (analise_id) REFERENCES analise_edital (id),
    CONSTRAINT ux_edital_disciplina UNIQUE (analise_id, nome),
    CONSTRAINT ck_edital_disciplina_peso CHECK (peso_pessoa IS NULL OR peso_pessoa IN ('ALTO', 'MEDIO', 'BAIXO'))
);

CREATE TABLE edital_assunto (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    disciplina_id  BIGINT NOT NULL,
    chave          VARCHAR(16) NOT NULL,
    ordem          INTEGER NOT NULL,
    texto          TEXT NOT NULL,
    erros          TEXT NOT NULL DEFAULT '[]',
    alertas        TEXT NOT NULL DEFAULT '[]',
    aceito         BOOLEAN NOT NULL DEFAULT false,
    aparece        BOOLEAN NOT NULL DEFAULT true,
    assunto_id     BIGINT,
    CONSTRAINT fk_edital_assunto_disciplina FOREIGN KEY (disciplina_id) REFERENCES edital_disciplina (id),
    CONSTRAINT fk_edital_assunto_assunto FOREIGN KEY (assunto_id) REFERENCES assunto (id),
    CONSTRAINT ux_edital_assunto_d89 UNIQUE (disciplina_id, chave)
);

CREATE TABLE edital_parte (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    assunto_edital_id BIGINT NOT NULL,
    chave             VARCHAR(16) NOT NULL,
    ordem             INTEGER NOT NULL,
    texto             TEXT NOT NULL,
    contexto          TEXT,
    herdado           TEXT,
    natureza          VARCHAR(12),
    origem            VARCHAR(10) NOT NULL,
    estado            VARCHAR(12) NOT NULL DEFAULT 'PROPOSTA',
    arvore_versao_id  BIGINT NOT NULL,
    aparece           BOOLEAN NOT NULL DEFAULT true,
    no_mudou          BOOLEAN NOT NULL DEFAULT false,
    prop_natureza     VARCHAR(12),
    prop_origem       VARCHAR(10),
    execucao_id       BIGINT,
    comentario        TEXT,
    CONSTRAINT fk_edital_parte_assunto FOREIGN KEY (assunto_edital_id) REFERENCES edital_assunto (id),
    CONSTRAINT fk_edital_parte_arvore FOREIGN KEY (arvore_versao_id) REFERENCES arvore_versao (id),
    CONSTRAINT fk_edital_parte_execucao FOREIGN KEY (execucao_id) REFERENCES edital_execucao (id),
    CONSTRAINT ux_edital_parte_d89 UNIQUE (assunto_edital_id, chave),
    CONSTRAINT ck_edital_parte_natureza CHECK (natureza IS NULL OR natureza IN ('EXATO', 'MAIS_AMPLO', 'PARCIAL')),
    CONSTRAINT ck_edital_parte_prop_natureza CHECK (prop_natureza IS NULL OR prop_natureza IN ('EXATO', 'MAIS_AMPLO', 'PARCIAL')),
    CONSTRAINT ck_edital_parte_origem CHECK (origem IN ('IA', 'REFINO', 'DECISAO', 'PESSOA')),
    CONSTRAINT ck_edital_parte_estado CHECK (estado IN ('PROPOSTA', 'ACEITA', 'DECIDIDA', 'DESCARTADA'))
);

CREATE INDEX ix_edital_parte_assunto ON edital_parte (assunto_edital_id, ordem);

-- Os nós da ligação: os vigentes (proposto = false) e os que a IA propôs e a pessoa não adotou
-- (proposto = true). Sem chave estrangeira para no_arvore: o nó vale para a versão da árvore da
-- parte (arvore_versao_id), e a versão muda por gesto da pessoa (D-90).
CREATE TABLE edital_parte_no (
    parte_id  BIGINT NOT NULL,
    proposto  BOOLEAN NOT NULL,
    ordem     SMALLINT NOT NULL,
    no_id     BIGINT NOT NULL,
    PRIMARY KEY (parte_id, proposto, ordem),
    CONSTRAINT fk_edital_parte_no_parte FOREIGN KEY (parte_id) REFERENCES edital_parte (id),
    CONSTRAINT ck_edital_parte_no_ate3 CHECK (ordem BETWEEN 1 AND 3)
);

CREATE TABLE edital_trecho (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    analise_id     BIGINT NOT NULL,
    disciplina_id  BIGINT,
    tipo           VARCHAR(12) NOT NULL,
    paginas        TEXT NOT NULL DEFAULT '[]',
    linhas         TEXT NOT NULL DEFAULT '[]',
    estado         VARCHAR(10) NOT NULL DEFAULT 'ABERTO',
    motivo         TEXT,
    CONSTRAINT fk_edital_trecho_analise FOREIGN KEY (analise_id) REFERENCES analise_edital (id),
    CONSTRAINT fk_edital_trecho_disciplina FOREIGN KEY (disciplina_id) REFERENCES edital_disciplina (id),
    CONSTRAINT ck_edital_trecho_tipo CHECK (tipo IN ('LINHA_FORA', 'SEM_ASSUNTO')),
    CONSTRAINT ck_edital_trecho_estado CHECK (estado IN ('ABERTO', 'DESCARTADO', 'INCLUIDO'))
);

-- ---------------------------------------------------------------- decisões e histórico (D-86, D-87)
CREATE TABLE edital_decisao (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- A parte e o contexto como a pessoa os viu (o planejamento os normaliza ao casar) e a chave de reaproveitamento
    -- normalizada (palavras ordenadas), sem a disciplina.
    parte_texto       TEXT NOT NULL,
    contexto_texto    TEXT NOT NULL DEFAULT '',
    parte_chave_norm  TEXT NOT NULL,
    contexto_norm     TEXT NOT NULL DEFAULT '',
    nos               TEXT NOT NULL DEFAULT '[]',
    natureza          VARCHAR(12),
    escopo            VARCHAR(8) NOT NULL DEFAULT 'EDITAL',
    analise_id        BIGINT NOT NULL,
    arvore_versao_id  BIGINT NOT NULL,
    ativa             BOOLEAN NOT NULL DEFAULT true,
    criada_em         TIMESTAMPTZ NOT NULL DEFAULT now(),
    revogada_em       TIMESTAMPTZ,
    comentario        TEXT,
    CONSTRAINT fk_edital_decisao_analise FOREIGN KEY (analise_id) REFERENCES analise_edital (id),
    CONSTRAINT fk_edital_decisao_arvore FOREIGN KEY (arvore_versao_id) REFERENCES arvore_versao (id),
    CONSTRAINT ck_edital_decisao_escopo CHECK (escopo IN ('EDITAL', 'TODOS')),
    CONSTRAINT ck_edital_decisao_natureza CHECK (natureza IS NULL OR natureza IN ('EXATO', 'MAIS_AMPLO', 'PARCIAL'))
);

-- Não único de propósito: duas decisões divergentes para a mesma chave são conflito para a pessoa
-- resolver (D-87), não erro de banco.
CREATE INDEX ix_edital_decisao_chave ON edital_decisao (parte_chave_norm, contexto_norm) WHERE ativa;

CREATE TABLE edital_evento (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    analise_id  BIGINT NOT NULL,
    parte_id    BIGINT,
    tipo        VARCHAR(30) NOT NULL,
    antes       TEXT,
    depois      TEXT,
    em          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_edital_evento_analise FOREIGN KEY (analise_id) REFERENCES analise_edital (id)
);

CREATE INDEX ix_edital_evento_analise ON edital_evento (analise_id, id);

-- D-86 — o histórico de cada gesto só cresce.
CREATE FUNCTION edital_evento_imutavel() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'tg_edital_evento_d86_imutavel: edital_evento não aceita % (D-86)', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_edital_evento_d86_imutavel
    BEFORE UPDATE OR DELETE ON edital_evento
    FOR EACH ROW EXECUTE FUNCTION edital_evento_imutavel();
