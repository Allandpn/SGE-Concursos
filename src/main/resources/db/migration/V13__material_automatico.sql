-- Sprint 19 — material preparado pelo sistema (ADR-044).
-- Fonte: docs/SPRINT-19-MATERIAL-AUTOMATICO.md §1; 01_DOMINIO §3.8–§3.10.

-- D-64: todo concurso tem identificador. O concurso lido por IA nascia sem
-- slug, e a importação do planejamento criava um segundo concurso.
UPDATE concurso SET slug = 'concurso-' || id WHERE slug IS NULL;
ALTER TABLE concurso ALTER COLUMN slug SET NOT NULL;

-- D-64 aplicada a quem já foi confirmado antes dela: assunto ativo sem chave,
-- coberto por um concurso da análise por IA, ganha uma.
UPDATE assunto a
   SET chave_externa = gen_random_uuid()::text
 WHERE a.chave_externa IS NULL
   AND a.ativo
   AND EXISTS (
       SELECT 1
         FROM concurso_assunto ca
         JOIN concurso c ON c.id = ca.concurso_id
        WHERE ca.assunto_id = a.id
          AND c.origem = 'ANALISE_IA');

-- Chave e modelo de IA cadastrados em Ajustes (ADR-044). Uma linha só;
-- coluna nula = vale a variável de ambiente.
CREATE TABLE configuracao_ia (
    id             SMALLINT PRIMARY KEY,
    chave          TEXT,
    modelo         VARCHAR(150),
    atualizado_em  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_configuracao_ia_linha_unica CHECK (id = 1)
);

-- D-65: o acervo não ensina este assunto. Fato registrado, não derivado;
-- segmento vence lacuna no estado do material, então nada é apagado.
CREATE TABLE lacuna_material (
    assunto_id     BIGINT PRIMARY KEY,
    motivo         TEXT NOT NULL,
    registrada_em  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_lacuna_material_assunto FOREIGN KEY (assunto_id) REFERENCES assunto (id),
    CONSTRAINT ck_lacuna_material_d65_motivo CHECK (length(btrim(motivo)) > 0)
);
