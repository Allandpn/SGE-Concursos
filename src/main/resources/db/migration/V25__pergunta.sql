-- =============================================================================
-- V25 — Perguntas de recuperação (Sprint 32, docs/SPRINT-32-PERGUNTAS.md §2;
-- 01_DOMINIO §3.18, D-122 a D-128; ADR-053).
--
-- Nada se apaga (D-18): pergunta ruim é marca (ruim_em). O que faltou numa
-- recuperação não é guardado como lista: sai de sessao_ponto (D-128, D-16).
-- =============================================================================

-- D-122: a fila de preparação, um registro por bloco.
CREATE TABLE pergunta_preparo (
    segmento_id    BIGINT PRIMARY KEY,
    situacao       VARCHAR(10) NOT NULL,
    tentativas     INT NOT NULL DEFAULT 0,
    pedido_em      TIMESTAMPTZ NOT NULL,
    atualizado_em  TIMESTAMPTZ NOT NULL,
    motivo         TEXT,
    CONSTRAINT fk_pergunta_preparo_segmento FOREIGN KEY (segmento_id) REFERENCES segmento (id),
    CONSTRAINT ck_pergunta_preparo_d122_situacao CHECK (situacao IN ('PENDENTE', 'PRONTA', 'SEM_TEXTO', 'FALHOU'))
);

CREATE TABLE pergunta (
    id              BIGSERIAL PRIMARY KEY,
    segmento_id     BIGINT NOT NULL,
    tipo            VARCHAR(10) NOT NULL,
    enunciado       TEXT NOT NULL,
    origem_marcada  BOOLEAN NOT NULL DEFAULT false,
    criada_em       TIMESTAMPTZ NOT NULL,
    ruim_em         TIMESTAMPTZ,
    CONSTRAINT fk_pergunta_segmento FOREIGN KEY (segmento_id) REFERENCES segmento (id),
    -- D-123: só os quatro tipos, e enunciado de verdade.
    CONSTRAINT ck_pergunta_d123_tipo CHECK (tipo IN ('EVOCACAO', 'COMPLETAR', 'PORQUE', 'COMPARACAO')),
    CONSTRAINT ck_pergunta_d123_enunciado CHECK (length(btrim(enunciado)) > 0)
);

CREATE INDEX ix_pergunta_segmento ON pergunta (segmento_id);

CREATE TABLE pergunta_ponto (
    id           BIGSERIAL PRIMARY KEY,
    pergunta_id  BIGINT NOT NULL,
    ordem        INT NOT NULL,
    texto        TEXT NOT NULL,
    principal    BOOLEAN NOT NULL,
    pagina       INT NOT NULL,
    CONSTRAINT fk_pergunta_ponto_pergunta FOREIGN KEY (pergunta_id) REFERENCES pergunta (id),
    -- D-123: todo ponto tem página e texto.
    CONSTRAINT ck_pergunta_ponto_d123_pagina CHECK (pagina >= 1),
    CONSTRAINT ck_pergunta_ponto_d123_texto CHECK (length(btrim(texto)) > 0)
);

CREATE INDEX ix_pergunta_ponto_pergunta ON pergunta_ponto (pergunta_id);

-- D-123: toda pergunta tem ao menos um ponto. Conferido no fim da transação, depois que os pontos entram.
CREATE FUNCTION pergunta_d123_tem_ponto() RETURNS trigger AS $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pergunta_ponto WHERE pergunta_id = NEW.id) THEN
        RAISE EXCEPTION 'D-123: a pergunta % não tem ponto no gabarito', NEW.id
            USING ERRCODE = 'check_violation', CONSTRAINT = 'trg_pergunta_d123_tem_ponto';
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_pergunta_d123_tem_ponto
    AFTER INSERT ON pergunta
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION pergunta_d123_tem_ponto();

-- As perguntas que uma recuperação usou (as "nunca vistas" saem daqui, D-124).
CREATE TABLE sessao_pergunta (
    sessao_id    BIGINT NOT NULL,
    pergunta_id  BIGINT NOT NULL,
    CONSTRAINT pk_sessao_pergunta PRIMARY KEY (sessao_id, pergunta_id),
    CONSTRAINT fk_sessao_pergunta_sessao FOREIGN KEY (sessao_id) REFERENCES sessao (id),
    CONSTRAINT fk_sessao_pergunta_pergunta FOREIGN KEY (pergunta_id) REFERENCES pergunta (id)
);

-- D-126: os pontos marcados na conferência, um fato da sessão.
CREATE TABLE sessao_ponto (
    sessao_id  BIGINT NOT NULL,
    ponto_id   BIGINT NOT NULL,
    veio       BOOLEAN NOT NULL,
    CONSTRAINT pk_sessao_ponto PRIMARY KEY (sessao_id, ponto_id),
    CONSTRAINT fk_sessao_ponto_sessao FOREIGN KEY (sessao_id) REFERENCES sessao (id),
    CONSTRAINT fk_sessao_ponto_ponto FOREIGN KEY (ponto_id) REFERENCES pergunta_ponto (id)
);
