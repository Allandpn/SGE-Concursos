# SPRINT 5 — FRENTE DE ESTUDO

O documento de serviço da Sprint 5: as consultas derivadas de frente/backlog/
consolidado (D-16, D-24), os tetos, a sugestão de vaga por consolidação e o
alerta de represamento. Nasce agora porque a Sprint 5 é "frente de estudo"
(`PROGRESSO.md` §1) — é também a sprint em que `ADR-033` ("visão simples,
nunca materializada") deixa de ser pendente de redação, porque é exatamente
agora que "a consulta da frente aparece".

| Campo | Valor |
|---|---|
| Versão | 1.0.0 |
| Data | 2026-08-30 |
| Status | Vigente |
| Subordinado a | `especificacao/00_PRODUTO.md` §6.5; `especificacao/01_DOMINIO.md` §6; `docs/00A_ADR.md` (ADR-033, escrita nesta sprint); `docs/SPRINT-4-ESCADA.md` (Revisao já existe, não muda) |

---

## 0. Escopo e parâmetros decididos com o usuário

**Nada aqui é entidade nova nem coluna nova.** Frente, backlog e consolidado
são conjuntos derivados de `Assunto` + `Sessao` + `Revisao` — `01_DOMINIO`
§6.1: "não é entidade nova e não guarda estado". Só leitura: nenhum endpoint
desta sprint grava nada.

Dois parâmetros que `01_DOMINIO` §12 listava como questão em aberto,
fechados agora:

| Chave | Valor | Por quê |
|---|---|---|
| `teto_diario_recuperacoes` | `8` | §12: "nunca aperta o bom, cobre o fraco" |
| `limiar_represamento` | derivado — sem chave própria | `represado > teto_diario_recuperacoes`, calculado, não parametrizado à parte |

Mais os dois tetos que `01_DOMINIO` §6.7 já tinha como decisão fechada, só
sem chave de `parametro` ainda:

| Chave | Valor |
|---|---|
| `teto_global_frente` | `100` |
| `teto_disciplina_frente` | `12` |

Fora de escopo, de propósito: a tela "Hoje" e o algoritmo de turno (Sprint 6
— "quanto cabe **hoje**" é pergunta diferente de "o que está sendo
aprendido **agora**"); erro influenciando ordem de fila (`01_DOMINIO` §12,
ainda em aberto); manutenção de assuntos irmãos agendada junta (idem).

---

## 1. `Clock` — primeira vez que o sistema precisa de "hoje"

Nenhuma sprint anterior precisou de `LocalDate.now()`: `Sessao.data` sempre
vem do cliente. Represamento é o primeiro cálculo que depende do dia atual.
Bean único (`EstudosApplication`), `Clock.systemDefaultZone()` — nunca
`LocalDate.now()` solto (`09_CODE_STYLE` §3.2).

---

## 2. `FrenteService` — as consultas

Reaproveita o cálculo de consolidação de `RevisaoService` (o mesmo "dois
últimos sucessos no nível-alvo" da Sprint 4) por um método novo,
`estaConsolidado(assuntoId, peso)`, em vez de duplicar a regra.

### 2.1 A fase de um assunto (D-16, D-24)

```
arquivado (assunto ou disciplina)     → fora de qualquer contagem
disciplina inativa                    → BACKLOG (D-24, mesmo com histórico)
sem nenhuma Sessao                    → BACKLOG (D-24)
tem Sessao e está consolidado         → CONSOLIDADO
tem Sessao e não está consolidado     → FRENTE
```

### 2.2 Resumo da frente

Contagens sobre os assuntos ativos de disciplinas ativas: quantos em
`FRENTE`, `BACKLOG`, `CONSOLIDADO`; `represado` = revisões `PENDENTE` com
`dataPrevista` antes de hoje; alerta dispara se `represado > teto diário`;
estimativa de dias para normalizar = `represado / teto diário` (divisão
inteira — é a mesma conta do exemplo de `02_JORNADAS.md` J-4: 89 represado ÷
8 = 11).

### 2.3 Próximo do backlog (vaga por consolidação)

Dada uma disciplina: o assunto de menor `ordem` (D-41) entre os que estão em
`BACKLOG` — só se a disciplina ainda não bateu o teto por disciplina (conta
quantos dela já estão em `FRENTE`). Sem vaga, devolve vazio — o chamador
decide o que fazer (`01_DOMINIO` §6.7 regra 6: recomendação, nunca catraca;
nenhum endpoint bloqueia nada).

---

## 3. Endpoints REST

Só leitura.

| Método | Caminho | Retorno |
|---|---|---|
| `GET` | `/api/assuntos/{id}/fase` | `200`, `{fase}` |
| `GET` | `/api/frente` | `200`, `FrenteResumoResponse` |
| `GET` | `/api/frente/proxima-vaga?disciplinaId=` | `200`, `AssuntoResponse` do sugerido, ou `204` sem corpo se não há vaga/backlog |

---

## 4. Testes

Mesmo padrão das sprints anteriores.

- Assunto sem sessão → `BACKLOG`; com sessão e não consolidado → `FRENTE`;
  consolidado (reaproveita o cenário de `RevisaoEscadaTest`) → `CONSOLIDADO`.
- Disciplina inativa → assuntos dela contam como `BACKLOG` mesmo com sessão.
- Resumo: contagens batem; represamento dispara só acima do teto diário;
  estimativa de dias é a divisão inteira.
- Próxima vaga: sugere o de menor ordem do backlog da disciplina; nenhuma
  sugestão quando a disciplina já está no teto, mesmo havendo backlog.

---

## 5. Changelog

| Versão | Data | Mudança |
|---|---|---|
| 1.0.0 | 2026-08-30 | Criado. Teto diário (8) e limiar de represamento (`> teto diário`) fechados com o usuário — eram questão em aberto de `01_DOMINIO.md` §12 |
