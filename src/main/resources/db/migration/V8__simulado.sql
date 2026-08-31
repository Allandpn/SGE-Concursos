-- Item 8.1 de PROGRESSO.md — docs/SPRINT-8-SIMULADO.md §1/§2.
-- Duas tabelas, já previstas desde docs/SPRINT-1-BANCO.md §2.6: o evento
-- (simulado) e a apuração por disciplina (resultado_simulado), 1-N.

CREATE TABLE simulado (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    data              DATE NOT NULL,
    duracao_minutos   INTEGER NOT NULL,
    criado_em         TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_simulado_duracao_minutos CHECK (duracao_minutos > 0)
);

CREATE TABLE resultado_simulado (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    simulado_id        BIGINT NOT NULL,
    disciplina_id      BIGINT NOT NULL,
    formato            TEXT NOT NULL,
    questoes_corretas  INTEGER NOT NULL,
    questoes_total     INTEGER NOT NULL,
    criado_em          TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em      TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Evento pai, sem regra D-xx (mesmo padrão de fk_revisao_sessao_origem).
    CONSTRAINT fk_resultado_simulado_simulado FOREIGN KEY (simulado_id) REFERENCES simulado (id),
    -- D-13 — resultado de simulado aponta para disciplina, nunca para assunto.
    -- Não existe coluna assunto_id: é assim que D-13 se garante (§1.2 do doc técnico).
    CONSTRAINT fk_resultado_simulado_d13_disciplina FOREIGN KEY (disciplina_id) REFERENCES disciplina (id),
    CONSTRAINT ck_resultado_simulado_formato CHECK (formato IN ('MULTIPLA_ESCOLHA', 'CERTO_ERRADO')),
    CONSTRAINT ck_resultado_simulado_questoes_corretas CHECK (questoes_corretas >= 0),
    CONSTRAINT ck_resultado_simulado_questoes_total CHECK (questoes_total > 0),
    -- Higiene de dado, sem regra numerada (mesma família de ck_sessao_questoes_corretas_limite).
    CONSTRAINT ck_resultado_simulado_corretas_limite CHECK (questoes_corretas <= questoes_total),
    CONSTRAINT ux_resultado_simulado_simulado_disciplina UNIQUE (simulado_id, disciplina_id)
);

CREATE INDEX ix_resultado_simulado_simulado ON resultado_simulado (simulado_id);