-- Sprint 37 (01_DOMINIO §3.28, D-166 a D-171; ADR-063): a captura do QConcursos.

-- D-170: a chave de envio, uma por navegador; só o SHA-256 fica guardado.
CREATE TABLE captura_chave (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nome             VARCHAR(120) NOT NULL,
    resumo           CHAR(64) NOT NULL,
    criada_em        TIMESTAMPTZ NOT NULL DEFAULT now(),
    revogada_em      TIMESTAMPTZ,
    ultimo_envio_em  TIMESTAMPTZ,
    CONSTRAINT ux_captura_chave_resumo UNIQUE (resumo)
);

-- D-166: a questão capturada; só vira sessão quando registrada (lote ou caixa).
CREATE TABLE questao_capturada (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    codigo           VARCHAR(12) NOT NULL,
    acertou          BOOLEAN NOT NULL,
    resolvida_em     TIMESTAMPTZ NOT NULL,
    dia              DATE NOT NULL,
    disciplina_qc    VARCHAR(300),
    assuntos_qc      TEXT[] NOT NULL DEFAULT '{}',
    nos_qc           BIGINT[] NOT NULL DEFAULT '{}',
    banca            VARCHAR(200),
    ano              INTEGER,
    alternativa      VARCHAR(40),
    gabarito         VARCHAR(40),
    formato          VARCHAR(20),
    endereco         VARCHAR(2000),
    chave_id         BIGINT NOT NULL,
    assunto_id       BIGINT,
    situacao         VARCHAR(12) NOT NULL DEFAULT 'NA_CAIXA',
    sessao_id        BIGINT,
    criado_em        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_questao_capturada_codigo CHECK (codigo ~ '^Q[0-9]{4,10}$'),
    CONSTRAINT ck_questao_capturada_situacao CHECK (situacao IN ('NA_CAIXA', 'REGISTRADA', 'DESCARTADA')),
    CONSTRAINT ck_questao_capturada_formato CHECK (formato IS NULL OR formato IN ('MULTIPLA_ESCOLHA', 'CERTO_ERRADO')),
    CONSTRAINT ux_questao_capturada_d166_dia UNIQUE (codigo, dia),
    CONSTRAINT fk_questao_capturada_chave FOREIGN KEY (chave_id) REFERENCES captura_chave (id),
    CONSTRAINT fk_questao_capturada_assunto FOREIGN KEY (assunto_id) REFERENCES assunto (id),
    CONSTRAINT fk_questao_capturada_sessao FOREIGN KEY (sessao_id) REFERENCES sessao_registro (id)
);
CREATE INDEX ix_questao_capturada_caixa ON questao_capturada (situacao, assunto_id);

-- D-167: o par aprendido, pelo nome normalizado do assunto no QConcursos.
CREATE TABLE qconcursos_par (
    nome_norm    VARCHAR(300) PRIMARY KEY,
    nome         VARCHAR(300) NOT NULL,
    assunto_id   BIGINT NOT NULL,
    criado_em    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_qconcursos_par_assunto FOREIGN KEY (assunto_id) REFERENCES assunto (id)
);

-- D-171: o recorte de uma questão que o userscript não entendeu.
CREATE TABLE captura_diagnostico (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    chave_id    BIGINT NOT NULL,
    motivo      VARCHAR(300) NOT NULL,
    endereco    VARCHAR(2000),
    recorte     TEXT NOT NULL,
    criado_em   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_captura_diagnostico_recorte CHECK (length(recorte) <= 20000),
    CONSTRAINT fk_captura_diagnostico_chave FOREIGN KEY (chave_id) REFERENCES captura_chave (id)
);
