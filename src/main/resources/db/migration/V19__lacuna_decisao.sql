-- Redesenho do fluxo (docs/REDESENHO-FLUXO.md §6, passo B): o que a pessoa decidiu de cada lacuna do material.
-- "Estudo por fora" e "ignorar" não mudam o que foi entregue: só dizem à tela que a lacuna já tem destino.
CREATE TABLE edital_lacuna_decisao (
    assunto_edital_id BIGINT      NOT NULL REFERENCES edital_assunto (id),
    parte             TEXT        NOT NULL,
    decisao           VARCHAR(10) NOT NULL CHECK (decisao IN ('POR_FORA', 'IGNORADA')),
    decidida_em       TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (assunto_edital_id, parte)
);
