-- Sprint 43 (01_DOMINIO §3.27, D-163 a D-165): reiniciar o progresso de um concurso ou de tudo.
-- O reinício é a desativação de sessões da Sprint 42 (D-160, D-161), registrada para poder ser desfeita (D-164).
CREATE TABLE reinicio (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- NULL: tudo. Senão, a cobertura do concurso (concurso_assunto), divididos inclusive.
    concurso_id  BIGINT,
    sessoes      INTEGER NOT NULL,
    assuntos     INTEGER NOT NULL,
    feito_em     TIMESTAMPTZ NOT NULL DEFAULT now(),
    desfeito_em  TIMESTAMPTZ,
    CONSTRAINT fk_reinicio_concurso FOREIGN KEY (concurso_id) REFERENCES concurso (id),
    CONSTRAINT ck_reinicio_sessoes CHECK (sessoes > 0)
);

CREATE TABLE reinicio_sessao (
    reinicio_id  BIGINT NOT NULL,
    sessao_id    BIGINT NOT NULL,
    CONSTRAINT pk_reinicio_sessao PRIMARY KEY (reinicio_id, sessao_id),
    CONSTRAINT fk_reinicio_sessao_reinicio FOREIGN KEY (reinicio_id) REFERENCES reinicio (id),
    CONSTRAINT fk_reinicio_sessao_sessao FOREIGN KEY (sessao_id) REFERENCES sessao_registro (id)
);
