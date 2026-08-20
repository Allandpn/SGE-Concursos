-- Item 1.2 de PROGRESSO.md — cinco tabelas de domínio + parametro.
-- Fonte: docs/SPRINT-1-BANCO.md §2 e §4 (estrutura e índices de consulta).
--
-- O que NÃO está aqui, de propósito (item 1.3, docs/SPRINT-1-BANCO.md §3):
--   D-02, D-04a, D-06, D-36 — CHECKs compostos que amarram coluna a tipo
--   D-05, D-45              — índices únicos parciais (revisao, sessao)
--   J-1                     — índice único por expressão em assunto (precisa
--                              de unaccent_imutavel, item 1.4)
-- Essas cinco entram como ALTER TABLE / CREATE INDEX numa migração seguinte,
-- escrita por quem está aprendendo a regra por trás de cada uma.

CREATE TABLE disciplina (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nome           TEXT NOT NULL,
    peso           TEXT NOT NULL,
    ativo          BOOLEAN NOT NULL DEFAULT true,
    criado_em      TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_disciplina_peso CHECK (peso IN ('ALTO', 'MEDIO', 'BAIXO'))
);

CREATE TABLE assunto (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    disciplina_id         BIGINT NOT NULL,
    nome                  TEXT NOT NULL,
    peso                  TEXT NOT NULL,
    dificuldade_percebida SMALLINT NOT NULL,
    ordem                 INTEGER,
    ativo                 BOOLEAN NOT NULL DEFAULT true,
    criado_em             TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_assunto_disciplina FOREIGN KEY (disciplina_id) REFERENCES disciplina (id),
    CONSTRAINT ck_assunto_peso CHECK (peso IN ('ALTO', 'MEDIO', 'BAIXO')),
    CONSTRAINT ck_assunto_dificuldade_percebida CHECK (dificuldade_percebida BETWEEN 1 AND 5),
    -- D-41 — todo assunto tem ordem dentro da disciplina. CHECK, não NOT NULL
    -- nativo, para a regra ficar no nome da restrição (03_INVARIANTES §9.1).
    CONSTRAINT ck_assunto_d41_ordem_obrigatoria CHECK (ordem IS NOT NULL)
);

CREATE TABLE sessao (
    id                        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    assunto_id                BIGINT NOT NULL,
    tipo                      TEXT NOT NULL,
    data                      DATE NOT NULL,
    tempo_minutos             INTEGER NOT NULL,
    resultado                 TEXT,
    questoes_corretas         INTEGER,
    questoes_total            INTEGER,
    formato                   TEXT,
    previsao_percentual       SMALLINT,
    previsao_reconstrucao     BOOLEAN,
    tentativa_id              UUID NOT NULL,
    proxima_sessao_data       DATE,
    proxima_sessao_descricao  TEXT,
    ativo                     BOOLEAN NOT NULL DEFAULT true,
    criado_em                 TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em             TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- D-01 — sessão aponta para exatamente um assunto.
    CONSTRAINT fk_sessao_d01_assunto FOREIGN KEY (assunto_id) REFERENCES assunto (id),
    CONSTRAINT ck_sessao_tipo CHECK (tipo IN ('ESTUDO', 'QUESTOES', 'FLASHCARDS', 'RECUPERACAO')),
    CONSTRAINT ck_sessao_tempo_minutos CHECK (tempo_minutos > 0),
    CONSTRAINT ck_sessao_resultado CHECK (resultado IN ('SUCESSO', 'PARCIAL', 'FALHA')),
    CONSTRAINT ck_sessao_questoes_corretas CHECK (questoes_corretas >= 0),
    CONSTRAINT ck_sessao_questoes_total CHECK (questoes_total > 0),
    CONSTRAINT ck_sessao_formato CHECK (formato IN ('MULTIPLA_ESCOLHA', 'CERTO_ERRADO')),
    CONSTRAINT ck_sessao_previsao_percentual CHECK (previsao_percentual BETWEEN 0 AND 100),
    -- Higiene de dado, sem regra numerada (docs/SPRINT-1-BANCO.md §3.5).
    CONSTRAINT ck_sessao_questoes_corretas_limite CHECK (questoes_corretas <= questoes_total),
    CONSTRAINT ck_sessao_questoes_por_tipo CHECK (
        tipo IN ('QUESTOES', 'FLASHCARDS') OR (questoes_corretas IS NULL AND questoes_total IS NULL)
    )
);

CREATE TABLE revisao (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    assunto_id         BIGINT NOT NULL,
    nivel              INTEGER NOT NULL,
    data_prevista      DATE NOT NULL,
    sessao_origem_id   BIGINT,
    sessao_cumpriu_id  BIGINT,
    situacao           TEXT NOT NULL DEFAULT 'PENDENTE',
    criado_em          TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_revisao_assunto FOREIGN KEY (assunto_id) REFERENCES assunto (id),
    CONSTRAINT fk_revisao_sessao_origem FOREIGN KEY (sessao_origem_id) REFERENCES sessao (id),
    CONSTRAINT fk_revisao_sessao_cumpriu FOREIGN KEY (sessao_cumpriu_id) REFERENCES sessao (id),
    CONSTRAINT ck_revisao_nivel CHECK (nivel >= 1),
    CONSTRAINT ck_revisao_situacao CHECK (situacao IN ('PENDENTE', 'CUMPRIDA', 'CANCELADA'))
);

CREATE TABLE erro (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    assunto_id     BIGINT NOT NULL,
    sessao_id      BIGINT,
    descricao      TEXT NOT NULL,
    causa          TEXT NOT NULL,
    confianca      TEXT NOT NULL,
    resolvido      BOOLEAN NOT NULL DEFAULT false,
    criado_em      TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_erro_assunto FOREIGN KEY (assunto_id) REFERENCES assunto (id),
    CONSTRAINT fk_erro_sessao FOREIGN KEY (sessao_id) REFERENCES sessao (id),
    CONSTRAINT ck_erro_causa CHECK (causa IN (
        'FALTA_CONHECIMENTO', 'ESQUECIMENTO', 'INTERPRETACAO', 'DESATENCAO',
        'PEGADINHA', 'CHUTE', 'GESTAO_TEMPO'
    )),
    CONSTRAINT ck_erro_confianca CHECK (confianca IN ('BAIXA', 'MEDIA', 'ALTA'))
);

-- "Sétimo conceito": configuração, não domínio (01_DOMINIO §3). Nasce vazia —
-- cada sprint futura insere as chaves que consome, na sua própria migração.
CREATE TABLE parametro (
    chave          TEXT PRIMARY KEY,
    valor          TEXT NOT NULL,
    descricao      TEXT NOT NULL,
    atualizado_em  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Índices de padrão de consulta já nomeado na especificação (docs/SPRINT-1-BANCO.md §4).
-- Não incluem os que as restrições únicas do item 1.3 vão criar (D-05, D-45, J-1).
CREATE INDEX ix_assunto_disciplina ON assunto (disciplina_id);
CREATE INDEX ix_assunto_backlog ON assunto (disciplina_id, ativo, ordem);
CREATE INDEX ix_sessao_assunto_data ON sessao (assunto_id, data);
CREATE INDEX ix_revisao_assunto ON revisao (assunto_id);
CREATE INDEX ix_revisao_pendente_data ON revisao (situacao, data_prevista) WHERE situacao = 'PENDENTE';
CREATE INDEX ix_erro_assunto ON erro (assunto_id);