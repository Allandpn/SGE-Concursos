-- Item 1.4 de PROGRESSO.md — função wrapper que o índice de J-1 (item 1.3)
-- vai usar. unaccent() é STABLE; índice por expressão exige IMMUTABLE
-- (ADR-021 abre exceção para função IMMUTABLE puramente utilitária usada
-- em índice por expressão). Infraestrutura da migração, não regra de
-- negócio — por isso sem d<NN> no nome (docs/SPRINT-1-BANCO.md §3.2).

CREATE EXTENSION IF NOT EXISTS unaccent;

CREATE OR REPLACE FUNCTION unaccent_imutavel(TEXT)
RETURNS TEXT
LANGUAGE sql
IMMUTABLE
PARALLEL SAFE
AS $$
    SELECT unaccent('unaccent', $1)
$$;