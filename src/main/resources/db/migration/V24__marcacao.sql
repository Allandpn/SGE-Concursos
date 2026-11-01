-- =============================================================================
-- V24 — O leitor: marcações e a última página (Sprint 31, docs/SPRINT-31-LEITOR.md §2;
-- 01_DOMINIO §3.17, D-115 a D-118 e D-121; ADR-052).
--
-- A marcação nunca toca o PDF (D-115). Nada se apaga (D-18): editar e remover são
-- versões novas. A versão vigente é derivada (D-16, D-117): a de maior hora de
-- edição no aparelho, empate pela que chegou depois. Não há coluna "vigente".
-- =============================================================================

CREATE TABLE marcacao (
    id           BIGSERIAL PRIMARY KEY,
    chave        UUID NOT NULL,
    segmento_id  BIGINT NOT NULL,
    pagina       INT NOT NULL,
    tipo         VARCHAR(12) NOT NULL,
    destaque_id  BIGINT,
    retangulos   JSONB,
    citacao      TEXT,
    posicao_x    NUMERIC(5,4),
    posicao_y    NUMERIC(5,4),
    criada_em    TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_marcacao_chave UNIQUE (chave),
    CONSTRAINT fk_marcacao_d115_segmento FOREIGN KEY (segmento_id) REFERENCES segmento (id),
    CONSTRAINT fk_marcacao_d115_destaque FOREIGN KEY (destaque_id) REFERENCES marcacao (id),
    CONSTRAINT ck_marcacao_d115_pagina CHECK (pagina >= 1),
    -- D-115: a forma de cada tipo (docs/SPRINT-31-LEITOR.md §2.1).
    CONSTRAINT ck_marcacao_d115_forma CHECK (
        (tipo = 'DESTAQUE' AND destaque_id IS NULL AND retangulos IS NOT NULL AND citacao IS NOT NULL
            AND posicao_x IS NULL AND posicao_y IS NULL)
     OR (tipo = 'NOTA_TRECHO' AND destaque_id IS NOT NULL AND retangulos IS NULL AND citacao IS NULL
            AND posicao_x IS NULL AND posicao_y IS NULL)
     OR (tipo = 'NOTA_PAGINA' AND destaque_id IS NULL AND retangulos IS NULL AND citacao IS NULL
            AND posicao_x BETWEEN 0 AND 1 AND posicao_y BETWEEN 0 AND 1)
    )
);

CREATE INDEX ix_marcacao_segmento ON marcacao (segmento_id, pagina);

-- D-115: a nota de trecho é de um destaque do mesmo bloco e da mesma página.
CREATE FUNCTION marcacao_d115_nota_de_destaque() RETURNS trigger AS $$
BEGIN
    IF NEW.tipo = 'NOTA_TRECHO' AND NOT EXISTS (
        SELECT 1 FROM marcacao d
        WHERE d.id = NEW.destaque_id AND d.tipo = 'DESTAQUE' AND d.segmento_id = NEW.segmento_id AND d.pagina = NEW.pagina) THEN
        RAISE EXCEPTION 'D-115: a nota de trecho precisa de um destaque do mesmo bloco e página'
            USING ERRCODE = 'check_violation', CONSTRAINT = 'trg_marcacao_d115_nota_de_destaque';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_marcacao_d115_nota_de_destaque
    BEFORE INSERT OR UPDATE ON marcacao
    FOR EACH ROW EXECUTE FUNCTION marcacao_d115_nota_de_destaque();

CREATE TABLE marcacao_versao (
    id           BIGSERIAL PRIMARY KEY,
    marcacao_id  BIGINT NOT NULL,
    mudanca_id   UUID NOT NULL,
    cor          VARCHAR(10),
    texto        TEXT,
    removida     BOOLEAN NOT NULL DEFAULT false,
    editada_em   TIMESTAMPTZ NOT NULL,
    recebida_em  TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_marcacao_versao_marcacao FOREIGN KEY (marcacao_id) REFERENCES marcacao (id),
    -- D-118: a mesma mudança, chegue quantas vezes chegar, grava uma vez.
    CONSTRAINT uk_marcacao_versao_d118_mudanca UNIQUE (mudanca_id),
    -- D-116: só as três cores de significado.
    CONSTRAINT ck_marcacao_versao_d116_cor CHECK (cor IS NULL OR cor IN ('IMPORTANTE', 'DUVIDA', 'DECORAR'))
);

CREATE INDEX ix_marcacao_versao_marcacao ON marcacao_versao (marcacao_id, editada_em DESC, id DESC);

-- D-116: destaque sempre tem cor; nota nunca tem.
CREATE FUNCTION marcacao_versao_d116_cor() RETURNS trigger AS $$
DECLARE
    t VARCHAR(12);
BEGIN
    SELECT tipo INTO t FROM marcacao WHERE id = NEW.marcacao_id;
    IF (t = 'DESTAQUE' AND NEW.cor IS NULL) OR (t <> 'DESTAQUE' AND NEW.cor IS NOT NULL) THEN
        RAISE EXCEPTION 'D-116: destaque tem uma das três cores, nota não tem cor'
            USING ERRCODE = 'check_violation', CONSTRAINT = 'trg_marcacao_versao_d116_cor';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_marcacao_versao_d116_cor
    BEFORE INSERT OR UPDATE ON marcacao_versao
    FOR EACH ROW EXECUTE FUNCTION marcacao_versao_d116_cor();

-- D-121: a última página aberta de cada bloco. Conveniência, não entra em métrica.
CREATE TABLE segmento_leitura (
    segmento_id    BIGINT PRIMARY KEY,
    pagina         INT NOT NULL,
    atualizada_em  TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_segmento_leitura_segmento FOREIGN KEY (segmento_id) REFERENCES segmento (id),
    CONSTRAINT ck_segmento_leitura_d121_pagina CHECK (pagina >= 1)
);
