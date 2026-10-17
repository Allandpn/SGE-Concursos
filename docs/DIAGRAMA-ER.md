# DIAGRAMA ER

Visualização do schema de banco em Mermaid. **Não define nada** — é espelho
do que já existe nas migrações; a fonte de verdade de coluna, restrição e
índice é sempre `src/main/resources/db/migration/`, documentada por
`docs/SPRINT-1-BANCO.md` e pelos documentos técnicos das sprints que
alteraram o schema depois (`SPRINT-4-ESCADA` — `versao`; `SPRINT-7-METRICAS`
— D-46; `SPRINT-8-SIMULADO` — `simulado`/`resultado_simulado`;
`SPRINT-10-SEGMENTO` — `segmento`, `assunto.chave_externa`,
`sessao.segmento_id`). Se este diagrama e o schema real divergirem, o
schema real vence, e este arquivo está desatualizado — não o contrário.

| Campo | Valor |
|---|---|
| Versão | 2.5.0 |
| Data | 2026-10-17 |
| Status | Vigente |
| Subordinado a | `docs/SPRINT-1-BANCO.md`, `docs/SPRINT-4-ESCADA.md` (ADR-032), `docs/SPRINT-7-METRICAS.md` (D-46), `docs/SPRINT-8-SIMULADO.md` (D-13), `docs/SPRINT-10-SEGMENTO.md` (D-50 a D-53), `docs/SPRINT-18-CONCURSOS.md` (D-58), `docs/SPRINT-19-MATERIAL-AUTOMATICO.md` (D-64, D-65); `src/main/resources/db/migration/V1`, `V3`, `V5`, `V7`, `V8`, `V9`, `V12`, `V13` |

---

## Diagrama

```mermaid
erDiagram
    disciplina ||--o{ assunto : "fk_assunto_disciplina"
    disciplina ||--o{ resultado_simulado : "fk_resultado_simulado_d13_disciplina"
    assunto ||--o{ sessao : "fk_sessao_d01_assunto"
    assunto ||--o{ revisao : "fk_revisao_assunto"
    assunto ||--o{ erro : "fk_erro_d46_assunto"
    sessao |o--o{ revisao : "fk_revisao_sessao_origem (opcional)"
    sessao |o--o{ revisao : "fk_revisao_sessao_cumpriu (D-06, opcional)"
    sessao |o--o{ erro : "fk_erro_sessao (opcional)"
    simulado ||--o{ resultado_simulado : "fk_resultado_simulado_simulado"
    assunto ||--o{ segmento : "fk_segmento_assunto (D-50: ordem única no assunto)"
    segmento |o--o{ sessao : "fk_sessao_d51_segmento_mesmo_assunto (composta, opcional, só ESTUDO)"
    concurso ||--o{ concurso_assunto : "fk_concurso_assunto_concurso (cobertura do edital)"
    assunto ||--o{ concurso_assunto : "fk_concurso_assunto_assunto"
    disciplina ||--o{ concurso_assunto : "fk_concurso_assunto_disciplina"
    concurso |o--o{ analise_edital : "fk_analise_edital_concurso (opcional, só depois de confirmar)"
    assunto ||--o| lacuna_material : "fk_lacuna_material_assunto (D-65, no máximo uma)"

    disciplina {
        bigint id PK
        text nome
        text peso "CHECK ALTO|MEDIO|BAIXO"
        boolean ativo
        timestamptz criado_em
        timestamptz atualizado_em
    }

    assunto {
        bigint id PK
        bigint disciplina_id FK
        text nome "único por disciplina, sem acento (J-1)"
        text peso "CHECK ALTO|MEDIO|BAIXO"
        smallint dificuldade_percebida "CHECK 1..5"
        integer ordem "obrigatório via CHECK, D-41"
        boolean ativo
        text chave_externa "opcional, único (D-52) — nullable, sem WHERE"
        timestamptz criado_em
        timestamptz atualizado_em
    }

    sessao {
        bigint id PK
        bigint assunto_id FK
        text tipo "ESTUDO|QUESTOES|FLASHCARDS|RECUPERACAO"
        date data
        integer tempo_minutos "CHECK maior que 0"
        text resultado "SUCESSO|PARCIAL|FALHA, nullable"
        integer questoes_corretas
        integer questoes_total
        text formato "MULTIPLA_ESCOLHA|CERTO_ERRADO"
        smallint previsao_percentual
        text previsao_reconstrucao "SUCESSO|PARCIAL|FALHA, nullable"
        uuid tentativa_id "único, D-45"
        boolean ativo
        bigint segmento_id FK "nullable, só ESTUDO (D-51, ck_sessao_d51_segmento_so_estudo)"
        timestamptz criado_em
        timestamptz atualizado_em
    }

    segmento {
        bigint id PK
        bigint assunto_id FK
        text chave_externa "obrigatório via CHECK + único (D-53) — identidade de reimportação"
        integer ordem "único entre os ativos do assunto (D-50, índice parcial desde V15) — posição, não identidade"
        text arquivo "/material/... servido pelo SGE (ADR-044) ou link legado"
        integer pagina_inicial
        integer pagina_final
        integer tempo_estimado_min
        timestamptz arquivado_em "D-66: refazer o material; nulo = ativo"
        timestamptz criado_em
        timestamptz atualizado_em
    }

    revisao {
        bigint id PK
        bigint assunto_id FK
        integer nivel "CHECK maior ou igual a 1"
        date data_prevista
        bigint sessao_origem_id FK "nullable"
        bigint sessao_cumpriu_id FK "nullable, exigido se CUMPRIDA (D-06)"
        text situacao "PENDENTE|CUMPRIDA|CANCELADA"
        bigint versao "controle de versão otimista, ADR-032"
        timestamptz criado_em
        timestamptz atualizado_em
    }

    erro {
        bigint id PK
        bigint assunto_id FK "D-46"
        bigint sessao_id FK "nullable, D-46"
        text descricao
        text causa "7 valores fechados"
        text confianca "BAIXA|MEDIA|ALTA"
        boolean resolvido
        timestamptz criado_em
        timestamptz atualizado_em
    }

    parametro {
        text chave PK
        text valor
        text descricao
        timestamptz atualizado_em
    }

    simulado {
        bigint id PK
        date data
        integer duracao_minutos "CHECK maior que 0"
        timestamptz criado_em
        timestamptz atualizado_em
    }

    resultado_simulado {
        bigint id PK
        bigint simulado_id FK
        bigint disciplina_id FK "D-13, sem assunto_id — é assim que D-13 se garante"
        text formato "MULTIPLA_ESCOLHA|CERTO_ERRADO"
        integer questoes_corretas
        integer questoes_total
        timestamptz criado_em
        timestamptz atualizado_em
    }

    concurso {
        bigint id PK
        varchar nome
        varchar cargo
        varchar banca
        date data_prova
        varchar origem "ANALISE_IA|PLANEJAMENTO"
        varchar slug UK "NOT NULL desde V13 (D-64)"
        boolean ativo "D-58: índice único parcial ux_concurso_d58_um_ativo"
        timestamptz ativado_em
        timestamptz desativado_em
        timestamptz criado_em
    }

    concurso_assunto {
        bigint id PK
        bigint concurso_id FK
        bigint assunto_id FK
        bigint disciplina_id FK
        varchar peso "ALTO|MEDIO|BAIXO"
        integer ordem
    }

    analise_edital {
        bigint id PK
        varchar nome_arquivo
        text texto "o PDF não é guardado (ADR-043)"
        varchar status
        varchar etapa_falha
        text erro
        varchar modelo
        varchar concurso_nome
        varchar banca
        date data_prova
        text cargos "JSON"
        varchar cargo_escolhido
        text proposta "JSON"
        bigint concurso_id FK
        timestamptz criado_em
        timestamptz atualizado_em
    }

    configuracao_ia {
        smallint id PK "CHECK id = 1 — uma linha só"
        text chave "OpenRouter; nunca devolvida pela API (ADR-044)"
        varchar modelo "OpenRouter"
        varchar provedor "CLAUDE|OPENROUTER (ADR-045)"
        text claude_token "assinatura; nunca devolvido"
        varchar claude_modelo "opus|sonnet|haiku"
        text claude_falha_motivo "o aviso de assinatura parada"
        timestamptz claude_falha_em
        timestamptz atualizado_em
    }

    lacuna_material {
        bigint assunto_id PK "e FK"
        text motivo "D-65, CHECK não vazio"
        timestamptz registrada_em
        timestamptz descartada_em "D-66: refazer o material; nula = vale"
    }
```

## Notas de leitura

- `parametro` e `configuracao_ia` não aparecem com nenhuma linha de
  relacionamento — são configuração pura (`01_DOMINIO` §3), sem FK para
  nenhuma outra tabela.
- `lacuna_material` é a única tabela da Sprint 19 que guarda um fato: o estado
  do material (pronto, sem material, a preparar) é derivado dos segmentos, e
  segmento vence lacuna (D-65). Nada a apagar quando o material chega.
- `revisao` tem duas FKs distintas para `sessao` (`sessao_origem_id` e
  `sessao_cumpriu_id`): a sessão que gerou a revisão e a sessão que a
  cumpriu não são a mesma pergunta. D-06 amarra a segunda: só pode ficar
  nula se `situacao != 'CUMPRIDA'`.
- Nenhuma tabela tem coluna de fase (D-16). A fase do assunto é derivada em
  memória a partir de `revisao`/`sessao` — não existe aqui porque não deve
  existir. O mesmo vale para `simulado`/`resultado_simulado`: nenhuma FK
  para `assunto` — é assim que D-13 se garante, ausência como garantia
  (`03_INVARIANTES` §9), mesmo mecanismo de D-16.
- `resultado_simulado` é único por `(simulado_id, disciplina_id)` — um
  simulado não tem duas apurações da mesma disciplina. Higiene de dado, sem
  `D-xx` (a restrição não aparece no diagrama, que mostra só FK/PK).
- Todas as **9 tabelas** já existem no schema: as 5 de domínio original
  desde a Sprint 1, `simulado`/`resultado_simulado` desde a Sprint 8,
  `segmento` desde a Sprint 10. Ganhar entidade JPA é progresso de sprint
  separado: `disciplina`/`assunto` na Sprint 2; `sessao` na Sprint 3;
  `revisao` na Sprint 4; `erro` e `Simulado`/`ResultadoSimulado` na
  Sprint 7/8 (`docs/SPRINT-2-CADASTRO.md` §0) — `erro` e
  `simulado`/`resultado_simulado` tinham a **tabela** desde a Sprint 1, só
  ganharam entidade/service/controller bem depois.
- `fk_erro_assunto` virou `fk_erro_d46_assunto` na Sprint 7 (migração `V7`):
  a restrição já existia desde V1, sem identificador de regra — D-46
  formalizou o que faltava, sem mudar o schema em si.
- `segmento.chave_externa` e `assunto.chave_externa` são obrigatoriedade via
  `CHECK`, nunca `NOT NULL` nativo (mesmo padrão de D-41 em `assunto.ordem`)
  — é o que deixa o identificador da regra no nome da restrição
  (`03_INVARIANTES §9.1`); por isso o diagrama não marca essas colunas como
  `NOT NULL` mesmo quando obrigatórias.
- `fk_sessao_d51_segmento_mesmo_assunto` é **FK composta**
  `(segmento_id, assunto_id) → segmento (id, assunto_id)`, não uma FK
  simples — é o mecanismo que impõe "o segmento pertence ao mesmo assunto
  da sessão" sem checagem prévia no serviço. O Mermaid não representa FK
  composta como relação própria; a linha do diagrama mostra a cardinalidade,
  o nome completo do mecanismo está só no rótulo.

---

## Changelog

| Versão | Data | Mudança |
|---|---|---|
| 2.5.0 | 2026-10-17 | `V15`: `segmento.arquivado_em`, `lacuna_material.descartada_em` (D-66); D-50 vira índice único parcial sobre os segmentos ativos. `V14` só acrescentou colunas a `configuracao_ia` (provedor e assinatura do Claude, ADR-045) |
| 2.4.0 | 2026-10-17 | Sprint 18 (`V12`), que tinha ficado de fora: `concurso` (D-58), `concurso_assunto` (cobertura do edital, a única n:n) e `analise_edital`. Sprint 19 (`V13`): `concurso.slug` passa a `NOT NULL` (D-64), `configuracao_ia` e `lacuna_material` (D-65) |
| 2.3.0 | 2026-09-06 | Sprint 10 (`V9`): tabela `segmento` nova (`chave_externa` obrigatória e única — D-53 —, `ordem` única no assunto — D-50); `assunto.chave_externa` (opcional, único — D-52); `sessao.segmento_id` (nullable, só `ESTUDO` — D-51, via FK composta `fk_sessao_d51_segmento_mesmo_assunto`). Notas de leitura atualizadas para 9 tabelas |
| 2.2.0 | 2026-09-01 | `sessao.proxima_sessao_data`/`proxima_sessao_descricao` removidas (`docs/SPRINT-1-BANCO.md` v1.4.0) |
| 2.1.0 | 2026-08-31 | `sessao.previsao_reconstrucao`: `boolean` → `text` (V1, `docs/SPRINT-1-BANCO.md` v1.2.0) |
| 2.0.0 | 2026-08-31 | `simulado`/`resultado_simulado` adicionados (Sprint 8, V8); `revisao.versao` adicionado (ADR-032, V5, esquecido na v1.0.0); `fk_erro_assunto` renomeado para `fk_erro_d46_assunto` (V7); notas de leitura atualizadas para 8 tabelas |
| 1.0.0 | 2026-08-28 | Criado, a partir de `V1__tabelas.sql` e `V3__restricoes.sql` (schema fechado desde a Sprint 1) |
