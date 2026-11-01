-- =============================================================================
-- V23 — O acesso: login por passkey (Sprint 30, docs/SPRINT-30-LOGIN.md §2;
-- 01_DOMINIO §3.16, D-106 a D-113; ADR-051).
--
-- Um titular só (ADR-040): não há tabela de usuário. Nada se apaga (D-18):
-- passkey removido, sessão encerrada e código usado ou invalidado são marcas.
-- =============================================================================

CREATE TABLE passkey (
    id             BIGSERIAL PRIMARY KEY,
    credencial_id  BYTEA NOT NULL,
    chave_publica  BYTEA NOT NULL,
    contador       BIGINT NOT NULL,
    nome           VARCHAR(80) NOT NULL,
    criado_em      TIMESTAMPTZ NOT NULL DEFAULT now(),
    ultimo_uso_em  TIMESTAMPTZ,
    removido_em    TIMESTAMPTZ,
    CONSTRAINT uk_passkey_credencial UNIQUE (credencial_id),
    CONSTRAINT ck_passkey_d107_contador_nao_negativo CHECK (contador >= 0)
);

-- D-107: o contador de assinaturas nunca regride. Igualdade só passa em zero:
-- o passkey que não conta (sincronizado entre aparelhos) manda sempre 0. Só o
-- login escreve o contador, então toda escrita nele é uma assinatura nova.
CREATE FUNCTION passkey_d107_contador() RETURNS trigger AS $$
BEGIN
    IF NEW.contador < OLD.contador OR (NEW.contador = OLD.contador AND OLD.contador > 0) THEN
        RAISE EXCEPTION 'D-107: o contador do passkey % não avançou (% -> %)', OLD.id, OLD.contador, NEW.contador
            USING ERRCODE = 'check_violation', CONSTRAINT = 'trg_passkey_d107_contador';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_passkey_d107_contador
    BEFORE UPDATE OF contador ON passkey
    FOR EACH ROW EXECUTE FUNCTION passkey_d107_contador();

-- D-111: o último passkey ativo não se remove.
CREATE FUNCTION passkey_d111_ultimo() RETURNS trigger AS $$
BEGIN
    IF OLD.removido_em IS NULL AND NEW.removido_em IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM passkey p WHERE p.removido_em IS NULL AND p.id <> OLD.id) THEN
        RAISE EXCEPTION 'D-111: o passkey % é o último e não se remove', OLD.id
            USING ERRCODE = 'check_violation', CONSTRAINT = 'trg_passkey_d111_ultimo';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_passkey_d111_ultimo
    BEFORE UPDATE OF removido_em ON passkey
    FOR EACH ROW EXECUTE FUNCTION passkey_d111_ultimo();

-- D-109: o servidor guarda só o resumo (SHA-256) do valor do cookie.
CREATE TABLE sessao_acesso (
    id             BIGSERIAL PRIMARY KEY,
    resumo         BYTEA NOT NULL,
    passkey_id     BIGINT,
    aparelho       VARCHAR(200),
    criada_em      TIMESTAMPTZ NOT NULL,
    ultimo_uso_em  TIMESTAMPTZ NOT NULL,
    expira_em      TIMESTAMPTZ NOT NULL,
    encerrada_em   TIMESTAMPTZ,
    CONSTRAINT uk_sessao_acesso_d109_resumo UNIQUE (resumo),
    CONSTRAINT fk_sessao_acesso_passkey FOREIGN KEY (passkey_id) REFERENCES passkey (id)
);

-- D-110: códigos de uso único, guardados só como resumo.
CREATE TABLE codigo_recuperacao (
    id             BIGSERIAL PRIMARY KEY,
    resumo         BYTEA NOT NULL,
    criado_em      TIMESTAMPTZ NOT NULL,
    usado_em       TIMESTAMPTZ,
    invalidado_em  TIMESTAMPTZ,
    CONSTRAINT uk_codigo_recuperacao_d110_resumo UNIQUE (resumo)
);
