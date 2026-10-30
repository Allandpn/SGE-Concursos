-- Sprint 29 — Pomodoro: o foco cronometrado (01_DOMINIO §3.15, D-98 a D-105; ADR-050). Fonte: docs/SPRINT-29-POMODORO.md §2.

-- Uma fase do Pomodoro. O relógio nasce do início gravado; não há coluna de tempo corrido, de "completo" nem de total do dia
-- (D-16, D-99, D-105): tudo isso é consulta.
CREATE TABLE fase_foco (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tentativa_id          UUID        NOT NULL,
    especie               TEXT        NOT NULL,
    assunto_id            BIGINT,
    segmento_id           BIGINT,
    inicio                TIMESTAMPTZ NOT NULL,
    -- Cópia dos parâmetros no início: mudar Ajustes não altera fase passada nem em curso.
    duracao_programada_s  INTEGER     NOT NULL,
    pausado_desde         TIMESTAMPTZ,
    pausado_acumulado_s   INTEGER     NOT NULL DEFAULT 0,
    fim                   TIMESTAMPTZ,
    motivo_fim            TEXT,
    aviso_fim_em          TIMESTAMPTZ,
    sessao_id             BIGINT,
    criado_em             TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_fase_foco_tentativa UNIQUE (tentativa_id),
    CONSTRAINT ck_fase_foco_especie CHECK (especie IN ('FOCO', 'PAUSA_CURTA', 'PAUSA_LONGA')),
    CONSTRAINT ck_fase_foco_motivo_fim CHECK (motivo_fim IN ('ENCERRADA', 'VENCIDA')),
    CONSTRAINT ck_fase_foco_duracao CHECK (duracao_programada_s > 0 AND pausado_acumulado_s >= 0),
    -- D-99 — a fase termina depois de começar; fim e motivo andam juntos.
    CONSTRAINT ck_fase_foco_d99_fim_depois_do_inicio CHECK (fim IS NULL OR fim >= inicio),
    CONSTRAINT ck_fase_foco_d99_fim_com_motivo CHECK ((fim IS NULL) = (motivo_fim IS NULL)),
    -- D-101 — só o foco serve a uma sessão, e só depois de terminar.
    CONSTRAINT ck_fase_foco_d101_sessao_so_em_foco CHECK (sessao_id IS NULL OR (especie = 'FOCO' AND fim IS NOT NULL)),
    CONSTRAINT fk_fase_foco_assunto FOREIGN KEY (assunto_id) REFERENCES assunto (id),
    CONSTRAINT fk_fase_foco_segmento FOREIGN KEY (segmento_id) REFERENCES segmento (id),
    CONSTRAINT fk_fase_foco_d101_sessao FOREIGN KEY (sessao_id) REFERENCES sessao (id)
);

-- D-98 — no máximo uma fase em curso, em qualquer aparelho. Unicidade na persistência, nunca verificação prévia (como D-05).
CREATE UNIQUE INDEX ux_fase_foco_d98_uma_em_curso ON fase_foco ((true)) WHERE fim IS NULL;

CREATE INDEX ix_fase_foco_assunto ON fase_foco (assunto_id) WHERE assunto_id IS NOT NULL;
CREATE INDEX ix_fase_foco_sessao ON fase_foco (sessao_id) WHERE sessao_id IS NOT NULL;
CREATE INDEX ix_fase_foco_inicio ON fase_foco (inicio);

-- D-103 — durações e ciclos até a pausa longa. Convenção, não achado (01_DOMINIO §7.7).
INSERT INTO parametro (chave, valor, descricao) VALUES
    ('pomodoro_foco_min',          '25', 'Duração do foco do Pomodoro, em minutos (01_DOMINIO §3.15, D-103)'),
    ('pomodoro_pausa_curta_min',   '5',  'Duração da pausa curta do Pomodoro, em minutos (01_DOMINIO §3.15, D-103)'),
    ('pomodoro_pausa_longa_min',   '15', 'Duração da pausa longa do Pomodoro, em minutos (01_DOMINIO §3.15, D-103)'),
    ('pomodoro_focos_ate_longa',   '4',  'Quantos focos completos até a pausa longa do Pomodoro (01_DOMINIO §3.15, D-103)');

-- D-104 — o aviso de fim de fase é escolha separada do aviso de processo: a inscrição só o recebe se pedir.
ALTER TABLE push_inscricao ADD COLUMN avisa_fase BOOLEAN NOT NULL DEFAULT false;
