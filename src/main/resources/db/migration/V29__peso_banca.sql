-- Sprint 22 (docs/SPRINT-22-PESO-PELA-BANCA.md §1; 01_DOMINIO §3.23; ADR-057).

-- D-147: o ajuste aceito pela pessoa; a cobertura continua com o peso do edital.
CREATE TABLE peso_banca (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    concurso_id  BIGINT NOT NULL,
    assunto_id   BIGINT NOT NULL,
    peso         VARCHAR(10) NOT NULL,
    aceito_em    TIMESTAMPTZ NOT NULL DEFAULT now(),
    desfeito_em  TIMESTAMPTZ,
    CONSTRAINT fk_peso_banca_concurso FOREIGN KEY (concurso_id) REFERENCES concurso (id),
    CONSTRAINT fk_peso_banca_assunto FOREIGN KEY (assunto_id) REFERENCES assunto (id),
    CONSTRAINT ck_peso_banca_peso CHECK (peso IN ('ALTO', 'MEDIO', 'BAIXO'))
);
CREATE UNIQUE INDEX ux_peso_banca_d147_um_vigente ON peso_banca (concurso_id, assunto_id) WHERE desfeito_em IS NULL;

-- D-145: a janela e o mínimo de amostra.
INSERT INTO parametro (chave, valor, descricao) VALUES
    ('banca_anos', '5', 'Provas da banca dos últimos N anos entram na incidência (01_DOMINIO §3.23, D-145)'),
    ('banca_provas_minimas', '2', 'Mínimo de provas da banca por disciplina para propor peso (D-145)'),
    ('banca_questoes_minimas', '30', 'Mínimo de questões da banca por disciplina para propor peso (D-145)');
