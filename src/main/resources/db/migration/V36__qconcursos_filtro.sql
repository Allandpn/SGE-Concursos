-- =============================================================================
-- V36 — O filtro do QConcursos: o padrão, a exceção do concurso e os filtros
-- aprendidos pelo endereço (Sprint 49, docs/SPRINT-49-FILTRO-QCONCURSOS.md;
-- 01_DOMINIO §3.24, D-175 a D-177).
--
-- Uma linha sem concurso é o padrão (completo); uma linha por concurso é a exceção,
-- e nela nulo quer dizer "segue o padrão" (D-175). Os filtros aprendidos (cargo,
-- áreas, instituição) só valem pelo número; o nome é da pessoa (D-176).
-- =============================================================================

CREATE TABLE qconcursos_filtro (
    id                    BIGSERIAL PRIMARY KEY,
    concurso_id           BIGINT,
    niveis                SMALLINT[],
    banca                 VARCHAR(10),
    anos                  SMALLINT,
    formato               VARCHAR(16),
    dificuldades          SMALLINT[],
    ordem                 VARCHAR(10),
    tirar_resolvidas      BOOLEAN,
    tirar_anuladas        BOOLEAN,
    tirar_desatualizadas  BOOLEAN,
    so_comentadas         BOOLEAN,
    -- Na exceção, falso: os aprendidos seguem os do padrão; no padrão, sempre verdadeiro.
    aprendidos_proprios   BOOLEAN NOT NULL DEFAULT FALSE,
    atualizado_em         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_qconcursos_filtro_concurso FOREIGN KEY (concurso_id) REFERENCES concurso (id),
    CONSTRAINT uq_qconcursos_filtro_d175_concurso UNIQUE (concurso_id),
    CONSTRAINT ck_qconcursos_filtro_d175_niveis CHECK (niveis <@ ARRAY[1, 2, 3]::SMALLINT[]),
    CONSTRAINT ck_qconcursos_filtro_d175_banca CHECK (banca IN ('CONCURSO', 'QUALQUER')),
    CONSTRAINT ck_qconcursos_filtro_d175_anos CHECK (anos IN (0, 3, 5, 10)),
    CONSTRAINT ck_qconcursos_filtro_d175_formato CHECK (formato IN ('BANCA', 'MULTIPLA_ESCOLHA', 'CERTO_ERRADO', 'QUALQUER')),
    CONSTRAINT ck_qconcursos_filtro_d175_dificuldades CHECK (dificuldades <@ ARRAY[1, 2, 3, 4, 5]::SMALLINT[]),
    CONSTRAINT ck_qconcursos_filtro_d175_ordem CHECK (ordem IN ('QC', 'RECENTES', 'DIFICEIS')),
    -- D-175: o padrão diz tudo; só a exceção pode deixar para o padrão.
    CONSTRAINT ck_qconcursos_filtro_d175_padrao_completo CHECK (concurso_id IS NOT NULL OR (
        niveis IS NOT NULL AND banca IS NOT NULL AND anos IS NOT NULL AND formato IS NOT NULL AND dificuldades IS NOT NULL
        AND ordem IS NOT NULL AND tirar_resolvidas IS NOT NULL AND tirar_anuladas IS NOT NULL
        AND tirar_desatualizadas IS NOT NULL AND so_comentadas IS NOT NULL AND aprendidos_proprios))
);

-- D-175: um padrão só.
CREATE UNIQUE INDEX uq_qconcursos_filtro_d175_um_padrao ON qconcursos_filtro ((concurso_id IS NULL)) WHERE concurso_id IS NULL;

-- D-176: cargo, área de formação, área de atuação e instituição, pelo número.
CREATE TABLE qconcursos_filtro_aprendido (
    filtro_id  BIGINT NOT NULL,
    tipo       VARCHAR(12) NOT NULL,
    numero     INTEGER NOT NULL,
    CONSTRAINT pk_qconcursos_filtro_aprendido PRIMARY KEY (filtro_id, tipo, numero),
    CONSTRAINT fk_qconcursos_filtro_aprendido_filtro FOREIGN KEY (filtro_id) REFERENCES qconcursos_filtro (id) ON DELETE CASCADE,
    CONSTRAINT ck_qconcursos_filtro_aprendido_d176_tipo CHECK (tipo IN ('CARGO', 'FORMACAO', 'ATUACAO', 'INSTITUICAO')),
    CONSTRAINT ck_qconcursos_filtro_aprendido_d176_numero CHECK (numero > 0)
);

-- D-176: o nome que a pessoa deu a cada número; vale nos próximos endereços.
CREATE TABLE qconcursos_rotulo (
    tipo           VARCHAR(12) NOT NULL,
    numero         INTEGER NOT NULL,
    nome           VARCHAR(120) NOT NULL,
    atualizado_em  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_qconcursos_rotulo PRIMARY KEY (tipo, numero),
    CONSTRAINT ck_qconcursos_rotulo_d176_tipo CHECK (tipo IN ('CARGO', 'FORMACAO', 'ATUACAO', 'INSTITUICAO')),
    CONSTRAINT ck_qconcursos_rotulo_d176_numero CHECK (numero > 0),
    CONSTRAINT ck_qconcursos_rotulo_d176_nome CHECK (length(trim(nome)) > 0)
);

-- D-175: o padrão de fábrica é o filtro de antes (D-150) mais o nível superior.
INSERT INTO qconcursos_filtro (concurso_id, niveis, banca, anos, formato, dificuldades, ordem, tirar_resolvidas, tirar_anuladas,
    tirar_desatualizadas, so_comentadas, aprendidos_proprios)
VALUES (NULL, ARRAY[3]::SMALLINT[], 'CONCURSO', 5, 'BANCA', ARRAY[]::SMALLINT[], 'QC', TRUE, TRUE, TRUE, FALSE, TRUE);

-- Os números com nome conferidos em endereços reais (catálogo de 2026-11-03 do planejamento).
INSERT INTO qconcursos_rotulo (tipo, numero, nome) VALUES
    ('ATUACAO', 1, 'Bancária / Administrativa'),
    ('ATUACAO', 6, 'Policial'),
    ('ATUACAO', 16, 'Vestibular'),
    ('ATUACAO', 26, 'Segurança Pública / Guarda Municipal'),
    ('FORMACAO', 26, 'Turismo / Hotelaria / Eventos'),
    ('CARGO', 43, 'Analista Judiciário - Área Judiciária'),
    ('CARGO', 948, 'Auditor'),
    ('INSTITUICAO', 29, 'CGU (Controladoria-Geral da União)'),
    ('INSTITUICAO', 3716, 'Prefeitura de Teresina'),
    ('INSTITUICAO', 5712, 'Casa da Moeda'),
    ('INSTITUICAO', 5727, 'Correios');
