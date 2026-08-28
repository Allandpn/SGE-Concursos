-- Item 1.4 de PROGRESSO.md — função wrapper que o índice de J-1 (item 1.3)
-- vai usar. unaccent() é STABLE; índice por expressão exige IMMUTABLE
-- (ADR-021 abre exceção para função IMMUTABLE puramente utilitária usada
-- em índice por expressão). Infraestrutura da migração, não regra de
-- negócio — por isso sem d<NN> no nome (docs/SPRINT-1-BANCO.md §3.2).
--
-- Função e dicionário precisam vir qualificados com "public.": ao checar se
-- unaccent_imutavel é seguro para inlining num índice por expressão (CREATE
-- INDEX, aqui em J-1), o Postgres roda essa checagem com search_path
-- restrito a pg_catalog (mitigação contra sequestro de função por
-- search_path — mesma família de proteção usada em VACUUM/ANALYZE/REINDEX).
-- Sem qualificar, tanto unaccent(regdictionary, text) quanto o dicionário
-- "unaccent" ficam invisíveis nesse contexto, mesmo existindo em public e
-- funcionando normalmente fora de um índice.

CREATE EXTENSION IF NOT EXISTS unaccent;

CREATE OR REPLACE FUNCTION unaccent_imutavel(TEXT)
RETURNS TEXT
LANGUAGE sql
IMMUTABLE
PARALLEL SAFE
AS $$
    SELECT public.unaccent('public.unaccent'::regdictionary, $1)
$$;