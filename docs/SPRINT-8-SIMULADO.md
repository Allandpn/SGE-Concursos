# SPRINT 8 — SIMULADO

O documento de serviço da parte "Simulado" da Sprint 8 (`PROGRESSO.md` §1:
"Simulado por disciplina, backup testado, polimento"). Cobre só o Simulado —
backup testado e polimento ficam fora, sem especificação própria ainda,
decisão tomada com o usuário ao abrir esta sprint (§0).

**Nota de processo:** esta sprint foi aberta com a Sprint 7 ainda sem
execução de testes confirmada (`PROGRESSO.md` §8) — exceção consciente à
regra central do `CLAUDE.md` ("não implemente nada de sprint futura"),
decidida explicitamente pelo usuário, não um desvio silencioso.

| Campo | Valor |
|---|---|
| Versão | 1.0.0 |
| Data | 2026-08-31 |
| Status | Vigente |
| Subordinado a | `especificacao/00_PRODUTO.md` §6.7 (Simulado — a exceção honesta); `especificacao/01_DOMINIO.md` §3.6 (Simulado), §9 (Disciplina 1─N ResultadoSimulado), §10 D-13; `especificacao/03_INVARIANTES.md` (D-13: Invariante, garantida pela persistência); `docs/SPRINT-1-BANCO.md` §2.6 (desenho de duas tabelas, decidido, implementação adiada); `docs/SPRINT-7-METRICAS.md` (`MetricaService`/M-1 já existem, estendidos aqui, não recriados) |

---

## 0. Escopo e decisões tomadas com o usuário

Quatro pontos que nem `01_DOMINIO.md` nem nenhum outro documento fechava:

| Ponto | Decisão | Por quê |
|---|---|---|
| `formato` em `resultado_simulado` | Sim, um por resultado (`MULTIPLA_ESCOLHA`/`CERTO_ERRADO`) | Sem isso, D-36 (M-1 nunca soma formatos) fica violada assim que o simulado entrar em M-1 |
| Simulado em M-1 | **Mesmo agregado** de `QUESTOES`, por disciplina+formato — nunca série própria | `00_PRODUTO` §6.7: "é a entrada mais pura que M-1 pode ter" — ele *é* M-1, não uma quarta fonte ao lado dela |
| Escopo desta sprint | Só Simulado | Backup testado e polimento são preocupações diferentes (infra/UX), sem especificação própria — pendência documentada, não decidida agora |
| `duracaoMinutos` | Sim, mesmo padrão de `Sessao.tempoMinutos` | `00_PRODUTO` chama o simulado de "cronometrado"; registrar agora custa uma coluna e evita perder histórico |

Nenhuma regra `D-xx` nova: D-13 já cobre a integridade referencial de
`resultado_simulado → disciplina`. A FK `resultado_simulado → simulado` não
tem número, mesmo padrão não-numerado de `fk_revisao_sessao_origem`/
`fk_revisao_sessao_cumpriu` (é o evento pai, não uma regra de domínio).

Fora de escopo, por decisão da especificação (`01_DOMINIO §3.6`), não desta
sprint: simulado gerar revisão, consolidar, ou apontar para assunto — D-13
existe exatamente para isso nunca acontecer.

---

## 1. Duas tabelas, não uma

`docs/SPRINT-1-BANCO.md §2.6` já previa o desenho: o evento (`simulado`) e a
apuração por disciplina (`resultado_simulado`), 1─N.

### 1.1 `simulado`

| Coluna | Tipo | Regra |
|---|---|---|
| `id` | `BIGINT IDENTITY` | PK |
| `data` | `DATE NOT NULL` | — |
| `duracao_minutos` | `INTEGER NOT NULL` | "cronometrado" (`00_PRODUTO` §6.7), mesmo padrão de `Sessao.tempoMinutos` |
| `criado_em`, `atualizado_em` | `TIMESTAMPTZ` | Hibernate (ADR-016) |

### 1.2 `resultado_simulado`

| Coluna | Tipo | Regra |
|---|---|---|
| `id` | `BIGINT IDENTITY` | PK |
| `simulado_id` | `BIGINT NOT NULL` | FK → `simulado`, sem `D-xx` (evento pai) |
| `disciplina_id` | `BIGINT NOT NULL` | FK → `disciplina`, **D-13** |
| `formato` | `TEXT NOT NULL CHECK` | `MULTIPLA_ESCOLHA`/`CERTO_ERRADO`, §0 |
| `questoes_corretas` | `INTEGER NOT NULL` | ≥ 0 |
| `questoes_total` | `INTEGER NOT NULL` | > 0, ≥ corretas |
| `criado_em`, `atualizado_em` | `TIMESTAMPTZ` | Hibernate |

**Sem `assunto_id` — de propósito.** É assim que D-13 se garante: não existe
coluna para apontar errado (mesmo mecanismo estrutural de D-16, ausência
como garantia, `03_INVARIANTES §9`).

**Único por `(simulado_id, disciplina_id)`** — um simulado não tem duas
apurações da mesma disciplina. Higiene de dado, sem `D-xx` (mesma família de
`ck_sessao_questoes_corretas_limite`).

---

## 2. Migração `V8`

`simulado` + `resultado_simulado`, restrições de §1, índice
`ix_resultado_simulado_simulado` para a consulta de M-1.

---

## 3. `SimuladoService.registrar`

Tudo-ou-nada: um `Simulado` e a lista de `ResultadoSimulado`, uma transação
só — mesma disciplina de gravação de D-05/D-45 (tenta gravar, traduz erro do
banco, nunca pré-verifica). Lista de resultados vazia é
`ValidationException` (`RESULTADOS_OBRIGATORIOS`) — sem isso um simulado sem
nenhuma disciplina apurada não tem sentido nenhum, e não há restrição de
banco que cubra lista vazia.

| Restrição violada | `codigo` | Status |
|---|---|---|
| `fk_resultado_simulado_d13_disciplina` | `DISCIPLINA_INEXISTENTE` | 404 |
| `ux_resultado_simulado_simulado_disciplina` | `DISCIPLINA_DUPLICADA_NO_SIMULADO` | 409 |
| lista de resultados vazia (eager, sem restrição correspondente) | `RESULTADOS_OBRIGATORIOS` | 422 |

---

## 4. M-1 passa a somar `ResultadoSimulado`

`MetricaService.m1` hoje só lê `SessaoRepository.listarAcertoQuestoes`.
Passa a concatenar com uma consulta equivalente sobre `ResultadoSimulado`
(mesma projeção `AcertoLinha`, mesma janela de data — a de `Simulado.data`),
antes de agrupar por `(disciplinaId, formato)`. Simulado e `QUESTOES` do
mesmo formato/disciplina somam no mesmo `acertos`/`total` — §0.

Nenhuma outra métrica muda: M-2/M-3/M-4 são sobre `Sessao`/`Revisao`, e
simulado não tem nem uma nem outra (D-13 e a ausência de FK pra assunto
garantem isso estruturalmente).

---

## 5. Endpoints REST

| Método | Caminho | Retorno |
|---|---|---|
| `POST` | `/api/simulados` | `201`, `SimuladoResponse` |
| `GET` | `/api/simulados` | `200`, lista (histórico) |

---

## 6. Testes

- Erro de domínio: `DISCIPLINA_INEXISTENTE` (404), `DISCIPLINA_DUPLICADA_NO_SIMULADO` (409), `RESULTADOS_OBRIGATORIOS` (422).
- Estrutural: `resultado_simulado` não tem coluna `assunto_id` (D-13, mesmo molde de `EstruturaSchemaTest`).
- M-1: um simulado e uma sessão `QUESTOES` da mesma disciplina/formato somam no mesmo `acertos`/`total`.

---

## 7. Changelog

| Versão | Data | Mudança |
|---|---|---|
| 1.0.0 | 2026-08-31 | Criado. Sprint 8 aberta como exceção consciente à regra de não adiantar sprint futura (Sprint 7 ainda sem testes executados) — decisão do usuário, registrada aqui e em `PROGRESSO.md`. Escopo restrito a Simulado; backup testado e polimento ficam de fora. Quatro decisões de §0 fechadas com o usuário: `formato` por resultado, simulado soma no mesmo agregado de M-1, `duracaoMinutos` registrado |
