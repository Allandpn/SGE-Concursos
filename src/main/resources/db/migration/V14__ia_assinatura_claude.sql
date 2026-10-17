-- Sprint 19 — IA pela assinatura do Claude, OpenRouter por escolha do usuário
-- (ADR-045). Fonte: docs/SPRINT-19-MATERIAL-AUTOMATICO.md §3A.

ALTER TABLE configuracao_ia
    ADD COLUMN provedor            VARCHAR(20),
    ADD COLUMN claude_token        TEXT,
    ADD COLUMN claude_modelo       VARCHAR(20),
    ADD COLUMN claude_falha_motivo TEXT,
    ADD COLUMN claude_falha_em     TIMESTAMPTZ,
    ADD CONSTRAINT ck_configuracao_ia_provedor CHECK (provedor IS NULL OR provedor IN ('CLAUDE', 'OPENROUTER')),
    ADD CONSTRAINT ck_configuracao_ia_claude_modelo CHECK (claude_modelo IS NULL OR claude_modelo IN ('opus', 'sonnet', 'haiku'));
