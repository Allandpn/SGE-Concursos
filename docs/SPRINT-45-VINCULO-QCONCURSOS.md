# Sprint 45 — O assunto ligado ao QConcursos e as questões por vez

| | |
|---|---|
| Versão | 1.1.0 |
| Data | 2026-11-05 |
| Subordinado a | `01_DOMINIO` v1.46.0 §3.24 (D-173, D-174); `02_JORNADAS` v1.38.0 J-17; `04_FRONTEND` v1.27.0 §8H |
| Origem | Retorno do usuário no modo de teste (2026-11-05): "Juros simples: montante" sem atalho e filtros com milhares de questões. Decidido numa rodada; wireframe aprovado na conversa |
| Modo de trabalho | Implementação direta (`AGENTS.md`) |

## 0. Escopo

**Entra:** ligar o assunto a até 3 nós da árvore vigente do QConcursos, escolhidos pela busca; o vínculo antes do edital no atalho e
na captura; "Ligar ao QConcursos" onde não há atalho ou o atalho é pela disciplina inteira; o parâmetro "questões por vez".

**Fica de fora:** colar um endereço do QConcursos no assunto (escolhido: pela árvore); limitar o total do filtro (o QConcursos não
aceita; o teto é por vez).

## 1. Banco — `V35__qconcursos_vinculo.sql`

| Peça | O quê |
|---|---|
| `assunto_qconcursos_no` | `(assunto_id, no_id)` PK, `ordem` 1 a 3 (CHECK), `ligado_em`. Só o id do nó (negativo para disciplina); nome e caminho vêm da árvore vigente |
| Parâmetro `questoes_por_vez` | `0` (automático). Aceita só 0, 5, 10, 15, 20 (`ParametroService`, o único parâmetro com valores fixos) |

## 2. Servidor

| Peça | O quê |
|---|---|
| `VinculoQConcursosService` | `listar` (com `naArvore` falso para nó que saiu da árvore), `ligar` (troca o vínculo inteiro; 1 a 3 nós da vigente: `VINCULO_VAZIO`, `VINCULO_DEMAIS`, `NO_INEXISTENTE` 422; sem árvore, 404 `ARVORE_NAO_CARREGADA`), `tirar` |
| Rotas | `GET`, `PUT {noIds}` e `DELETE /api/assuntos/{id}/qconcursos-vinculo` |
| `QConcursosService.atalhos` | O vínculo (com nome e raiz na árvore vigente) vem antes dos nós do edital; nó fora da árvore dá o aviso `NO_FORA_DA_ARVORE`; o atalho ganha `vinculado`. `questoes_por_vez` > 0 manda nas sugeridas e no `per_page` |
| `CapturaService.acharAssunto` | Para cada nó candidato, o vínculo (um só assunto ativo) antes da conferência |

## 3. Tela

`js/qconcursos.js`: `Alpine.store('vinculoQc')` (a folha `#vinculo-qc`), `qc.semAtalho`, `qc.recarregar`, `qc.pedeVinculo`; o lote
da volta com "feitas" = questões por vez. `index.html`: "Ligar ao QConcursos" no Agora e na fila de Hoje; no detalhe do assunto,
"Ligado por você", trocar, tirar e o quadro sem atalho; Ajustes › Questões com os cinco botões. `CASCA_VERSAO` → `casca-2026-11-05-9`.

## 4. Testes

| Teste | Prova |
|---|---|
| `QConcursosApiTest` (+3) | o vínculo dá atalho a assunto sem nada e vence o edital; tirar volta ao edital; os limites (vazio, 4, nó inexistente, assunto inexistente); a árvore nova sem o nó: aviso e volta ao edital; questões por vez 15 → `per_page=15`, 7 recusado, 0 volta ao automático |
| `CapturaTest` (+1) | a questão com o nó ligado acha o assunto sem conferência |
| `ParametroListagemTest`, `EstruturaSchemaTest` | 32 parâmetros; a tabela nova |
| Chromium (`e2e-45`) | Hoje: sem atalho e pela disciplina inteira, os dois com "Ligar"; a folha com a busca, o caminho, o botão preso sem escolha; ligar e o atalho com o nó; Ajustes › 10; o detalhe com "Ligado por você" e 10 questões; tirar e ligar de novo pelo detalhe; a volta com 10 feitas; tema escuro |

## 5. Itens e ordem

| # | Item | Situação |
|---|---|---|
| 45.0 | Especificação e wireframe aprovados | feito |
| 45.1 | `V35`, o vínculo, o atalho e a captura | feito |
| 45.2 | A folha, os pontos de "Ligar", o detalhe, Ajustes, a volta; `CASCA_VERSAO` | feito |
| 45.3 | Testes e Chromium | feito |
| 45.4 | Deploy pelo Guardian | `release/19` (`e998239`) |

## Changelog

| Versão | Data | Mudança |
|---|---|---|
| 1.1.0 | 2026-11-05 | Itens 45.1 a 45.3 feitos. Testes novos: 3 em `QConcursosApiTest`, 1 em `CapturaTest`; suíte local 499 de 501 (os 2 de esquema pedem Docker). Chromium: 19 passos; regressão 36, 37, 39, 40 e 44 verde (o roteiro do 36 acompanha o "Filtro" no novo quadro) |
| 1.0.0 | 2026-11-05 | Criado, com o wireframe aprovado pelo usuário |
