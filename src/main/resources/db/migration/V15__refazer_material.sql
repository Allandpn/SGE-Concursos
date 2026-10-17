-- Sprint 19 — refazer o material (D-66). Fonte: docs/SPRINT-19-MATERIAL-AUTOMATICO.md §3B.
-- Nada é apagado (D-18): segmento é arquivado, lacuna é descartada.

ALTER TABLE segmento ADD COLUMN arquivado_em TIMESTAMPTZ;
ALTER TABLE lacuna_material ADD COLUMN descartada_em TIMESTAMPTZ;

-- D-50 passa a valer entre os segmentos ativos: os segmentos novos de um material
-- refeito repetem as ordens dos arquivados.
ALTER TABLE segmento DROP CONSTRAINT ux_segmento_d50_ordem_por_assunto;
CREATE UNIQUE INDEX ux_segmento_d50_ordem_por_assunto ON segmento (assunto_id, ordem) WHERE arquivado_em IS NULL;
