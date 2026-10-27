-- Sprint 27 — avisos de processo concluído por Web Push (ADR-048, D-96). Fonte: docs/SPRINT-27-AVISOS-DE-PROCESSO.md §2.

-- A chave VAPID: uma linha só; a privada nunca sai do servidor.
CREATE TABLE push_config (
    id              SMALLINT    PRIMARY KEY CHECK (id = 1),
    chave_publica   TEXT        NOT NULL,
    chave_privada   TEXT        NOT NULL,
    criada_em       TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Um aparelho inscrito.
CREATE TABLE push_inscricao (
    id               BIGSERIAL   PRIMARY KEY,
    endpoint         TEXT        NOT NULL UNIQUE,
    p256dh           TEXT        NOT NULL,
    auth             TEXT        NOT NULL,
    criada_em        TIMESTAMPTZ NOT NULL DEFAULT now(),
    ultimo_envio_em  TIMESTAMPTZ
);

-- A marca da rodada (D-96): o aviso já saiu para esta análise; refazer o material ou reprocessar a zera.
ALTER TABLE analise_edital
    ADD COLUMN aviso_conferencia_em TIMESTAMPTZ,
    ADD COLUMN aviso_material_em    TIMESTAMPTZ;
