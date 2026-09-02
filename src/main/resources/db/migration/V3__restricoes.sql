-- Item 1.3 de PROGRESSO.md — as 9 restrições + J-1, sobre as tabelas que
-- V1__tabelas.sql já criou (D-01 e D-41 já foram embutidas lá, por decisão
-- do usuário; ficam de fora daqui).
--
-- Fonte: docs/SPRINT-1-BANCO.md §3 (tabela com regra → mecanismo → nome).
-- D-18 não entra aqui: é ausência verificada por teste estrutural (item 1.6),
-- não uma restrição de SQL.
--
-- Sete restrições para escrever, cada uma citada pelo identificador da regra
-- no nome (03_INVARIANTES §9.1):
--
--   D-02  | CHECK em sessao   | ck_sessao_d02_estudo_sem_resultado
--   D-04a | CHECK em sessao   | ck_sessao_d04a_previsao_por_tipo  (SQL já no §3.1)
--   D-05  | índice único PARCIAL — molde abaixo, já escrito
--   D-06  | CHECK em revisao  | ck_revisao_d06_cumprida_tem_sessao
--   D-36  | CHECK em sessao   | ck_sessao_d36_questoes_tem_formato
--   D-45  | índice único em sessao(tentativa_id)
--         | ux_sessao_d45_tentativa_unica
--   J-1   | índice único por expressão em
--         | assunto(disciplina_id, unaccent_imutavel(lower(nome)))
--         | ux_assunto_j1_nome_por_disciplina  (usa a função de V2)

-- D-05 — molde. A única das sete que é índice PARCIAL (o `WHERE` é o que
-- restringe a unicidade só às linhas pendentes; sem ele, um assunto jamais
-- poderia ter uma segunda revisão, nem depois de concluir a primeira).
-- 03_INVARIANTES §4.1: é a única com concorrência real, por isso vira
-- restrição de escrita, nunca checagem prévia no serviço — ver ADR-031.
CREATE UNIQUE INDEX ux_revisao_d05_pendente_por_assunto
    ON revisao (assunto_id)
    WHERE situacao = 'PENDENTE';

ALTER TABLE sessao
    ADD CONSTRAINT ck_sessao_d02_estudo_sem_resultado
    CHECK (tipo != 'ESTUDO' OR resultado IS NULL);


ALTER TABLE sessao
    ADD CONSTRAINT ck_sessao_d04a_previsao_por_tipo
        CHECK (
                (tipo = 'ESTUDO'AND previsao_percentual IS NULL AND previsao_reconstrucao IS NULL)
                OR (tipo IN ('QUESTOES','FLASHCARDS') AND previsao_reconstrucao IS NULL AND (resultado IS NULL OR previsao_percentual IS NOT NULL))
                OR (tipo = 'RECUPERACAO' AND previsao_percentual IS NULL AND (resultado IS NULL OR previsao_reconstrucao IS NOT NULL))
            );

ALTER TABLE revisao
    ADD CONSTRAINT ck_revisao_d06_cumprida_tem_sessao
        CHECK (situacao != 'CUMPRIDA' OR sessao_cumpriu_id IS NOT NULL);

ALTER TABLE sessao
    ADD CONSTRAINT ck_sessao_d36_questoes_tem_formato
        CHECK (tipo != 'QUESTOES' OR formato IS NOT NULL);

CREATE UNIQUE INDEX ux_sessao_d45_tentativa_unica
    ON sessao (tentativa_id);

CREATE UNIQUE INDEX ux_assunto_j1_nome_por_disciplina
    ON assunto (disciplina_id, unaccent_imutavel(lower(nome)));

CREATE UNIQUE INDEX ux_d47_nome_disciplina
    ON disciplina (unaccent_imutavel(lower(nome)));

-- D-48 — ordem é única só entre os assuntos ATIVOS da mesma disciplina
-- (índice PARCIAL, mesmo molde de D-05): ao contrário do nome (D-47, acima),
-- ordem não é identidade permanente, é disputa pela vaga — arquivar libera o
-- número. Achado em FrenteService.proximaVaga, que desempatava por ordem de
-- retorno do banco (sem ORDER BY, não determinística).
CREATE UNIQUE INDEX ux_assunto_d48_ordem_por_disciplina
    ON assunto (disciplina_id, ordem)
    WHERE ativo = true;







