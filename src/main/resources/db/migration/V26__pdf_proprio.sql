-- =============================================================================
-- V26 — PDF próprio (Sprint 33, docs/SPRINT-33-PDF-PROPRIO.md §2; 01_DOMINIO §3.19,
-- D-129 a D-132; ADR-054).
--
-- Nada se apaga (D-18, D-132): tirar o PDF marca retirado_em e arquiva os segmentos.
-- =============================================================================

CREATE TABLE pdf_proprio (
    id           BIGSERIAL PRIMARY KEY,
    assunto_id   BIGINT NOT NULL,
    nome         VARCHAR(255) NOT NULL,
    tipo         VARCHAR(10) NOT NULL,
    paginas      INT NOT NULL,
    sem_texto    BOOLEAN NOT NULL,
    criado_em    TIMESTAMPTZ NOT NULL,
    retirado_em  TIMESTAMPTZ,
    CONSTRAINT fk_pdf_proprio_assunto FOREIGN KEY (assunto_id) REFERENCES assunto (id),
    -- D-129: o tipo de leitura define o ritmo do corte.
    CONSTRAINT ck_pdf_proprio_d129_tipo CHECK (tipo IN ('APOSTILA', 'LEI_SECA')),
    CONSTRAINT ck_pdf_proprio_d129_paginas CHECK (paginas >= 1)
);

CREATE INDEX ix_pdf_proprio_assunto ON pdf_proprio (assunto_id);

-- D-132: o bloco de PDF próprio aponta para o PDF de onde veio.
ALTER TABLE segmento ADD COLUMN pdf_proprio_id BIGINT;
ALTER TABLE segmento ADD CONSTRAINT fk_segmento_d132_pdf_proprio FOREIGN KEY (pdf_proprio_id) REFERENCES pdf_proprio (id);
CREATE INDEX ix_segmento_pdf_proprio ON segmento (pdf_proprio_id) WHERE pdf_proprio_id IS NOT NULL;
