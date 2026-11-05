-- Sprint 42 (01_DOMINIO §3.26, D-160 a D-162; ADR-062): tirar sessões da escada.
-- A tabela passa a se chamar sessao_registro e "sessao" vira a visão das ativas: toda leitura existente (escada, turno, métricas,
-- diagnóstico, frente...) passa a ver só as sessões que valem. A visão é simples, portanto aceita INSERT e UPDATE (o registro e a
-- entidade Sessao continuam iguais). As chaves estrangeiras e as restrições seguem com a tabela.
ALTER TABLE sessao RENAME TO sessao_registro;

CREATE VIEW sessao AS SELECT * FROM sessao_registro WHERE ativo;

CREATE INDEX ix_sessao_registro_data ON sessao_registro (data DESC, id DESC);
