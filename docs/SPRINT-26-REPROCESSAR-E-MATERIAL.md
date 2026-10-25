# Sprint 26 — Reprocessar, decisões para todos os editais e material por casamento

| | |
|---|---|
| Versão | 1.0.0 |
| Data | 2026-10-25 |
| Subordinado a | `01_DOMINIO` v1.27.0 §3.10 e §3.12, D-86 a D-95; `03_INVARIANTES` v1.15.0; ADR-047 |
| Antecedente | `docs/SPRINT-25-CONFERENCIA-EDITAL.md` v1.5.0 (feita e em produção) |
| Contraparte | `docs/proposta-material-por-casamento.md` (0.4.0) e `integracao/` no repositório do planejamento |
| Modo de trabalho | Implementação direta (`CLAUDE.md`, 2026-10-24) |

## 0. Escopo

**Entra:** reprocessar um edital preservando o que a pessoa decidiu (D-86, D-89, D-95); decisão com escopo "todos os editais" e a
confirmação extra (D-87); o atalho "aceitar todos os mais amplos da disciplina"; o **material por casamento** depois da conclusão
(§3.10): o planejamento casa as partes com as aulas, entrega os segmentos e o PDF "Questões deste assunto" (D-94), e a tela mostra a
situação do material por assunto; o selo do aviso em Hoje (§9 da Sprint 25).

**Fica de fora:** trocar a **fonte** de uma parte e mostrar o que entra e sai do material ao trocar o nó (D-93, parte do material;
pede a entrega do planejamento por parte, a desenhar com o uso real); mapa de disciplinas editável e "duas execuções discordam".

## 1. O desenho em uma página

```
Conferência concluída ─► GET /internal/editais/material/pendentes   (assuntos com chave, partes e nós, sem material ainda)
   │ worker: casar → confirmar → manifestos → segunda chance → consulta   (preparar_edital.py, as etapas da Sprint 26)
   ├─ segmentos: importar_segmentos (CSV de sempre, chaveExterna do assunto) — o SGE nunca apaga segmento (D-18)
   ├─ lacunas por parte e PDF de consulta: POST /internal/editais/material   → edital_assunto.material_*
   └─ assunto sem fonte nenhuma: lacuna do assunto (D-65), como hoje

Reprocessar (botão na conferência) ─► análise volta a SEPARANDO_ASSUNTOS (nada apagado) ─► worker lê de novo ─► entrega
   └─ a entrega se funde na conferência existente, pela chave (D-89); decisão da pessoa nunca é tocada (D-86)
```

## 2. Banco — `V18__reprocessar_e_material.sql`

| Mudança | Para quê |
|---|---|
| `edital_parte.ia_mudou` (boolean, falso) | marca a parte que a pessoa já decidiu e que a IA, na última execução, propôs diferente: vira o contador "a IA mudou, a sua decisão foi mantida" (D-95) |
| `edital_assunto.material_situacao` (`COMPLETO`, `PARCIAL`, `SEM_MATERIAL`, `RECUSADO`; nulo = a preparar), `material_minutos`, `material_fontes`, `material_lacunas` (JSON, uma por parte, com motivo), `material_consulta` (caminho relativo em `/material/`), `material_consulta_paginas`, `material_em` | o que o planejamento entregou do material, para a tela e para o aviso; **derivado dos segmentos** quanto ao que já existe (D-16): isto é só o que os segmentos não dizem (lacunas por parte, consulta, tempo) |
| `analise_edital.reprocessar` (boolean, falso) | o worker lê "refazer do zero" no pedido pendente |
| `edital_decisao.escopo` já existe | `TODOS` passa a ser aceito de verdade |

## 3. Reprocessar (D-89, D-95)

- `POST /api/editais/analises/{id}/conferencia/reprocessar`: só em `EM_CONFERENCIA` ou `CONFIRMADA`; marca `reprocessar = true` e a
  análise vai a `SEPARANDO_ASSUNTOS`. Nenhuma linha é apagada. Em `CONFIRMADA`, os assuntos já criados ficam como estão.
- O worker, ao ver `reprocessar`, descarta a pasta de trabalho do edital (a leitura recomeça) e aplica as decisões ativas.
- **A entrega se funde** (substitui o `409 REPROCESSAR_NA_26` da Sprint 25), pela chave, em disciplina → assunto → parte:

| Caso | O que faz |
|---|---|
| chave nova | entra como nova (`PROPOSTA`), na fila |
| chave que existe, parte `PROPOSTA` | recebe a ligação nova (nós, natureza, origem) |
| chave que existe, parte decidida ou aceita | **não muda**; se os nós novos diferem dos atuais, `ia_mudou = true` e os nós novos vão para `proposto` |
| chave que existe, assunto `aceito` | não muda; erros e alertas novos só entram se o assunto não estava aceito |
| chave que sumiu (e a parte veio da entrega, `execucao_id` não nulo) | `aparece = false`, na fila como `NAO_APARECE_MAIS`; nunca removida |
| parte criada pela pessoa (editar, dividir, juntar: `execucao_id` nulo) | fica |
| trecho já resolvido, ou igual a um existente | não duplica |

- A tela mostra **só as pendências novas** (a fila já é derivada) e, no cabeçalho, "N partes: a IA mudou, a sua decisão foi mantida".

## 4. Escopo "todos os editais" (D-87)

`PUT .../partes/{id}/nos`, `POST .../partes/{id}/aceitar` e `.../sem-no` ganham `escopo` (`EDITAL`, padrão, ou `TODOS`) e
`confirmarTodos`. Com `TODOS` sem `confirmarTodos = true`, a resposta é `409 CONFIRMACAO_TODOS` com **quantas partes de outros editais
mudariam**: as de chave igual (palavras da parte e do contexto) em outra análise, ainda não decididas pela pessoa, com nós diferentes.
Confirmado: a decisão grava com escopo `TODOS`; essas partes recebem a ligação e ficam `DECIDIDA`, origem `DECISAO` (nunca se toca
parte que a pessoa já decidiu, D-86); `/internal/editais/{id}/dados` passa a levar também as decisões `TODOS` (a mais recente por chave).
A lista de decisões mostra o escopo, e revogar uma decisão `TODOS` não desfaz as partes já ligadas.

## 5. Atalho "aceitar todos os mais amplos"

`POST .../disciplinas/{id}/aceitar-amplos`, como `aceitar-exatos`, para `MAIS_AMPLO` e `PARCIAL`. É uma escolha consciente de aceitar o
nó largo (o atalho para as questões do QConcursos continua valendo); registra uma decisão por parte, como o gesto de uma.

## 6. Material por casamento (§3.10)

**Rotas internas** (`/internal/editais`):

| Rota | O que faz |
|---|---|
| `GET /material/pendentes` | assuntos de conferências **concluídas** que ainda não têm situação de material, com `chave`, `chaveExterna`, disciplina, texto e as partes com os nós (por id); no máximo um edital por vez |
| `POST /{id}/material` | `{assuntos: [{chave, situacao, minutos, fontes, lacunas:[{parte, motivo}], consulta:{arquivo, paginas}}]}`: grava `material_*`; idempotente; recusa assunto de outra análise (`404`) e situação desconhecida (`422`) |

Os segmentos seguem o caminho de sempre (`POST /api/segmentos/importacoes/confirmar`, `chaveExterna` do assunto, `/material/blocos/...`):
nada novo no SGE para eles, e o SGE nunca apaga segmento. **Assunto que já tem segmento não recebe os novos**: o material novo fica só na
situação e nas lacunas (sugestão, D-93); a pessoa usa "Refazer o material" (D-66) se quiser trocar.
O PDF de consulta é servido em `/material/consulta/<chave>.pdf` (o volume `material` já é servido); não é segmento (D-94).

**Tela:** em Concursos → Ver por assunto, cada assunto mostra a situação (`completo`, `parcial`, `sem material`, `recusado`), o tempo estimado
e as lacunas por parte; no detalhe do assunto, "Questões deste assunto (consulta)" abre o PDF, fora do turno e da escada.

## 7. Planejamento (o worker)

`edital_worker.py` ganha o segundo ciclo: para o edital com material pendente, monta a pasta de trabalho a partir da conferência
(`partes.json` e `nos.json` com o que a pessoa **conferiu**, vindos de `/material/pendentes`, não os da IA), roda `casar`, `confirmar`,
`manifestos`, `segunda` e `consulta` (`preparar_edital.py`), corta os segmentos (`dias/montar_bloco.py`), importa os segmentos e entrega
a situação. As etapas de IA esperam a assinatura voltar (nunca viram veredito, D-75). Insumos: `catalogo.db` e `ligacao/fichas-nos.jsonl`
em `MATERIAL_DIR`, e o índice do acervo.

## 8. Selo em Hoje

O assunto do turno que ainda tem parte sem filtro de questões ou com vínculo largo mostra um selo discreto ("sem filtro de questões"),
que leva ao detalhe do assunto; vem de `GET /api/assuntos/{id}/partes-do-edital`, só para o assunto em foco. Nunca bloqueia nada.

## 9. Testes

| Garante | Teste |
|---|---|
| D-86 | reprocessar não muda parte decidida nem aceita; `ia_mudou` marca a diferença |
| D-89 | chave nova entra; chave que sumiu fica `aparece = false` e na fila; parte criada pela pessoa fica; a mesma entrega duas vezes é uma só |
| D-95 | a fila depois de reprocessar tem só o que é novo; o contador conta as decisões mantidas |
| D-87 | `TODOS` sem confirmar dá 409 com a contagem; confirmado liga as partes não decididas de outros editais e não toca as decididas; `/dados` leva a decisão |
| §5 | aceitar amplos aceita só `MAIS_AMPLO` e `PARCIAL` da disciplina |
| §6 | `/material/pendentes` só traz conferência concluída; `POST /material` grava, é idempotente e recusa assunto alheio |
| D-94 | o PDF de consulta não conta como segmento nem aparece no turno |

## 10. Itens e ordem

| # | Item | Depende de |
|---|---|---|
| 26.0 | Este documento | — |
| 26.1 | `V18` e reprocessar: botão, fusão da entrega, contador | 26.0 |
| 26.2 | Escopo "todos os editais" com a confirmação extra | 26.1 |
| 26.3 | Atalho "aceitar todos os mais amplos" | — |
| 26.4 | Rotas internas do material e `material_*` no SGE; telas (por assunto, consulta) | 26.0 |
| 26.5 | O ciclo do material no worker (planejamento) | 26.4 |
| 26.6 | Selo em Hoje | — |
| 26.7 | Pi: reprocessar o MPPR e preparar o material do concurso | todos |

## 11. Riscos

| Risco | Tratamento |
|---|---|
| A fusão tocar o que a pessoa decidiu | A regra é "decidida ou aceita nunca muda"; teste por caso |
| Cota de IA no material (centenas de chamadas por edital) | As etapas esperam a janela; o worker faz um edital por vez; já medido no MPPR |
| `catalogo.db` e `fichas-nos.jsonl` ficarem velhos | Estão em `MATERIAL_DIR`; refazer o catálogo é trabalho do acervo, fora desta sprint |

## Changelog

| Versão | Data | Mudança |
|---|---|---|
| 1.0.0 | 2026-10-25 | Primeira versão, contra `01_DOMINIO` v1.27.0 |
