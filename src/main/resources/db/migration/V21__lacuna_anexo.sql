-- Sprint 28 — PDF anexado a uma lacuna do material (D-97, ADR-049). Fonte: docs/SPRINT-28-PDF-ANEXADO-A-LACUNA.md §2.
CREATE TABLE lacuna_anexo (
    id                BIGSERIAL    PRIMARY KEY,
    assunto_edital_id BIGINT       NOT NULL REFERENCES edital_assunto (id),
    parte             TEXT         NOT NULL,
    nome_original     TEXT         NOT NULL,
    arquivo           TEXT         NOT NULL,
    paginas           INTEGER      NOT NULL CHECK (paginas > 0),
    estado            VARCHAR(10)  NOT NULL DEFAULT 'PENDENTE' CHECK (estado IN ('PENDENTE', 'PRONTO', 'FALHOU')),
    erro              TEXT,
    segmentos         INTEGER,
    minutos           INTEGER,
    criada_em         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    atualizada_em     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (assunto_edital_id, parte)
);
