-- Item 4.1 de PROGRESSO.md — coluna de versão em revisao (ADR-032) e os
-- parâmetros que a Sprint 4 consome (docs/SPRINT-4-ESCADA.md §1).

ALTER TABLE revisao ADD COLUMN versao BIGINT NOT NULL DEFAULT 0;

INSERT INTO parametro (chave, valor, descricao) VALUES
    ('intervalo_nivel_1', '1',  'Dias até a próxima revisão no nível 1 da escada (01_DOMINIO §5.1/§5.4)'),
    ('intervalo_nivel_2', '3',  'Dias até a próxima revisão no nível 2 da escada'),
    ('intervalo_nivel_3', '7',  'Dias até a próxima revisão no nível 3 da escada'),
    ('intervalo_nivel_4', '15', 'Dias até a próxima revisão no nível 4 da escada'),
    ('intervalo_nivel_5', '30', 'Dias até a próxima revisão no nível 5 da escada'),
    ('intervalo_nivel_6', '90', 'Dias até a próxima revisão no nível 6 da escada'),
    ('intervalo_manutencao_dias', '150', 'Dias entre revisões de manutenção, assunto consolidado (01_DOMINIO §5.5, "120-180", valor único determinístico)'),
    ('janela_tolerancia_percentual', '20', 'Percentual do intervalo do nível que pode ser antecipado sem perder o crédito da revisão (01_DOMINIO §5.4)');
