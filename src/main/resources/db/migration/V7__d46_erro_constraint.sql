-- Item 7.1 de PROGRESSO.md — docs/SPRINT-7-METRICAS.md §2.
--
-- A tabela `erro` já existe desde V1 (docs/SPRINT-1-BANCO.md §2.5): a FK
-- para assunto foi criada como `fk_erro_assunto`, sem identificador de
-- regra, porque nenhuma regra D-xx cobria essa integridade referencial
-- ainda. D-46 fecha essa lacuna (01_DOMINIO.md v1.12.0) — esta migração só
-- renomeia a restrição para carregar o identificador, mesmo padrão de
-- fk_sessao_d01_assunto (D-01).
ALTER TABLE erro RENAME CONSTRAINT fk_erro_assunto TO fk_erro_d46_assunto;