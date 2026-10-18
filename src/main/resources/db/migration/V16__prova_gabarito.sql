-- Sprint 24 — Prova e gabarito (ADR-046). Fonte: docs/SPRINT-24-PROVA-GABARITO.md §2;
-- 01_DOMINIO §3.11, D-67 a D-85.
--
-- A prova é um processo com estados (como analise_edital); as questões, os
-- vereditos e a resolução vêm depois. O veredito é fato registrado e nunca
-- muda (D-73); a nota nunca é persistida aqui (D-16) — só o resultado_simulado
-- da Sprint 8 guarda o que M-1 precisa.

CREATE TABLE prova (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- D-77 — o alvo no momento do envio.
    concurso_id         BIGINT NOT NULL,
    nome                VARCHAR(250) NOT NULL,
    banca               VARCHAR(150),
    ano                 SMALLINT,
    tipo                VARCHAR(10),
    cargo_prova         VARCHAR(250),
    -- NULL até a leitura, que é quem descobre.
    formato             VARCHAR(20),
    status              VARCHAR(30) NOT NULL,
    etapa_falha         VARCHAR(30),
    erro                TEXT,
    hash_prova          VARCHAR(64) NOT NULL,
    gabarito_arquivo    VARCHAR(250),
    -- NULL = sem gabarito (D-81).
    gabarito_situacao   VARCHAR(20),
    gabarito_recorte    TEXT,
    questoes_esperadas  INTEGER,
    modelo              VARCHAR(150),
    criado_em           TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_prova_concurso FOREIGN KEY (concurso_id) REFERENCES concurso (id),
    CONSTRAINT ck_prova_formato CHECK (formato IS NULL OR formato IN ('MULTIPLA_ESCOLHA', 'CERTO_ERRADO')),
    CONSTRAINT ck_prova_status CHECK (status IN
        ('LENDO', 'AGUARDANDO_GABARITO', 'CONFERIR', 'JULGANDO', 'PRONTA', 'FALHOU')),
    CONSTRAINT ck_prova_etapa_falha CHECK (etapa_falha IS NULL OR etapa_falha IN ('LENDO', 'JULGANDO')),
    CONSTRAINT ck_prova_gabarito_situacao CHECK (gabarito_situacao IS NULL OR gabarito_situacao IN ('PRELIMINAR', 'DEFINITIVO')),
    CONSTRAINT ck_prova_formato_lido CHECK (status NOT IN ('CONFERIR', 'JULGANDO', 'PRONTA') OR formato IS NOT NULL),
    -- D-81 — sem gabarito, a prova não é julgada.
    CONSTRAINT ck_prova_d81_sem_gabarito_nao_julga CHECK (status NOT IN ('JULGANDO', 'PRONTA') OR gabarito_situacao IS NOT NULL),
    -- Idempotência do envio, como D-45: a mesma prova ao mesmo concurso é uma só.
    CONSTRAINT ux_prova_concurso_hash UNIQUE (concurso_id, hash_prova)
);

CREATE INDEX ix_prova_status ON prova (status);

CREATE TABLE questao (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    prova_id              BIGINT NOT NULL,
    numero                INTEGER NOT NULL,
    enunciado             TEXT,
    -- O texto/comando a que o item do CEBRASPE está ligado.
    texto_base            TEXT,
    cabecalho_disciplina  VARCHAR(250),
    -- A–E; certo/errado usa C e E.
    resposta_gabarito     VARCHAR(1),
    -- NULL = avaliável.
    motivo_nao_avaliavel  VARCHAR(20),
    -- NULL = ainda sem atribuição ou fora do edital (ver atribuicao).
    assunto_id            BIGINT,
    atribuicao            VARCHAR(20) NOT NULL DEFAULT 'PENDENTE',
    lida_por_ocr          BOOLEAN NOT NULL DEFAULT false,
    pagina_origem         INTEGER,
    criado_em             TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_questao_prova FOREIGN KEY (prova_id) REFERENCES prova (id),
    CONSTRAINT fk_questao_assunto FOREIGN KEY (assunto_id) REFERENCES assunto (id),
    CONSTRAINT ck_questao_numero CHECK (numero > 0),
    CONSTRAINT ck_questao_resposta_gabarito CHECK (resposta_gabarito IS NULL OR resposta_gabarito IN ('A', 'B', 'C', 'D', 'E')),
    -- D-70 e D-85 — os motivos pelos quais a questão sai do denominador.
    CONSTRAINT ck_questao_d70_motivo CHECK (motivo_nao_avaliavel IS NULL OR motivo_nao_avaliavel IN
        ('SEM_TEXTO', 'ANULADA', 'SEM_GABARITO', 'OCR_DUVIDOSO')),
    -- D-70 — questão avaliável sempre tem a resposta do gabarito.
    CONSTRAINT ck_questao_d70_avaliavel_tem_gabarito CHECK (motivo_nao_avaliavel IS NOT NULL OR resposta_gabarito IS NOT NULL),
    CONSTRAINT ck_questao_atribuicao CHECK (atribuicao IN ('PENDENTE', 'AUTOMATICA', 'REVISAR', 'CONFIRMADA', 'FORA_DO_EDITAL')),
    -- D-74 — "nenhum" é resposta válida e não aponta assunto.
    CONSTRAINT ck_questao_d74_fora_do_edital_sem_assunto CHECK (atribuicao <> 'FORA_DO_EDITAL' OR assunto_id IS NULL),
    CONSTRAINT ck_questao_atribuida_tem_assunto CHECK (atribuicao NOT IN ('AUTOMATICA', 'CONFIRMADA') OR assunto_id IS NOT NULL),
    CONSTRAINT ck_questao_enunciado_ou_sem_texto CHECK (enunciado IS NOT NULL OR motivo_nao_avaliavel = 'SEM_TEXTO'),
    CONSTRAINT ux_questao_prova_numero UNIQUE (prova_id, numero)
);

CREATE INDEX ix_questao_assunto ON questao (assunto_id);

CREATE TABLE alternativa (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    questao_id  BIGINT NOT NULL,
    letra       VARCHAR(1) NOT NULL,
    texto       TEXT NOT NULL,
    CONSTRAINT fk_alternativa_questao FOREIGN KEY (questao_id) REFERENCES questao (id),
    CONSTRAINT ck_alternativa_letra CHECK (letra IN ('A', 'B', 'C', 'D', 'E')),
    CONSTRAINT ux_alternativa_questao_letra UNIQUE (questao_id, letra)
);

-- Fato registrado, nunca alterado (D-73): reanalisar insere uma linha nova e o
-- vigente é o de maior id. Nada o marca como vigente (D-16).
CREATE TABLE veredito_questao (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    questao_id            BIGINT NOT NULL,
    veredito              VARCHAR(20) NOT NULL,
    -- A sustentação: um segmento do assunto e a página dentro dele.
    segmento_id           BIGINT,
    pagina_segmento       INTEGER,
    falta                 TEXT,
    -- A resposta do gabarito usada neste julgamento (D-84).
    gabarito_considerado  VARCHAR(1) NOT NULL,
    modelo                VARCHAR(150),
    criado_em             TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_veredito_questao_questao FOREIGN KEY (questao_id) REFERENCES questao (id),
    CONSTRAINT fk_veredito_questao_segmento FOREIGN KEY (segmento_id) REFERENCES segmento (id),
    CONSTRAINT ck_veredito_questao_veredito CHECK (veredito IN ('RESPONDIVEL', 'PARCIAL', 'NAO_RESPONDIVEL')),
    CONSTRAINT ck_veredito_questao_gabarito CHECK (gabarito_considerado IN ('A', 'B', 'C', 'D', 'E')),
    CONSTRAINT ck_veredito_questao_pagina CHECK (pagina_segmento IS NULL OR pagina_segmento > 0),
    -- D-68 — RESPONDIVEL exige a página que sustenta a alternativa do gabarito.
    CONSTRAINT ck_veredito_d68_sustentacao CHECK (veredito <> 'RESPONDIVEL' OR (segmento_id IS NOT NULL AND pagina_segmento IS NOT NULL))
);

CREATE INDEX ix_veredito_questao_vigente ON veredito_questao (questao_id, id DESC);

-- D-73 — o veredito não muda nem some: reanalisar cria outro.
CREATE FUNCTION veredito_questao_imutavel() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'tg_veredito_questao_d73_imutavel: veredito_questao não aceita % (D-73)', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_veredito_questao_d73_imutavel
    BEFORE UPDATE OR DELETE ON veredito_questao
    FOR EACH ROW EXECUTE FUNCTION veredito_questao_imutavel();

-- Resolução (Sprint 24, item 24.4). Sem coluna "acertou": a correção sai do
-- gabarito na leitura (D-80, D-16).
CREATE TABLE tentativa_prova (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    prova_id       BIGINT NOT NULL,
    -- Gerado no cliente ao abrir a tela (D-80).
    tentativa_id   UUID NOT NULL,
    iniciada_em    TIMESTAMPTZ NOT NULL DEFAULT now(),
    entregue_em    TIMESTAMPTZ,
    cronometrada   BOOLEAN NOT NULL DEFAULT false,
    limite_minutos INTEGER,
    -- Preenchido só na tentativa que conta para M-1 (D-78).
    simulado_id    BIGINT,
    criado_em      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_tentativa_prova_prova FOREIGN KEY (prova_id) REFERENCES prova (id),
    CONSTRAINT fk_tentativa_prova_simulado FOREIGN KEY (simulado_id) REFERENCES simulado (id),
    -- D-80 — a mesma tentativa conta uma vez.
    CONSTRAINT ux_tentativa_prova_d80_tentativa UNIQUE (tentativa_id),
    CONSTRAINT ck_tentativa_prova_limite CHECK (limite_minutos IS NULL OR limite_minutos > 0),
    CONSTRAINT ck_tentativa_prova_conta_entregue CHECK (simulado_id IS NULL OR entregue_em IS NOT NULL)
);

-- D-78 — só uma tentativa por prova alimenta M-1. Índice único PARCIAL: só as
-- linhas que contam entram.
CREATE UNIQUE INDEX ux_tentativa_prova_d78_uma_conta ON tentativa_prova (prova_id) WHERE simulado_id IS NOT NULL;

CREATE TABLE resposta_prova (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tentativa_id     BIGINT NOT NULL,
    questao_id       BIGINT NOT NULL,
    -- NULL = marcada para revisar, ainda sem resposta.
    alternativa      VARCHAR(1),
    marcada_revisar  BOOLEAN NOT NULL DEFAULT false,
    respondida_em    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_resposta_prova_tentativa FOREIGN KEY (tentativa_id) REFERENCES tentativa_prova (id),
    CONSTRAINT fk_resposta_prova_questao FOREIGN KEY (questao_id) REFERENCES questao (id),
    CONSTRAINT ck_resposta_prova_alternativa CHECK (alternativa IS NULL OR alternativa IN ('A', 'B', 'C', 'D', 'E')),
    CONSTRAINT ux_resposta_prova_tentativa_questao UNIQUE (tentativa_id, questao_id)
);
