# Sprint 47 — Concursos em abas

| | |
|---|---|
| Versão | 1.1.0 |
| Data | 2026-11-06 |
| Subordinado a | `04_FRONTEND` v1.29.0 §8J |
| Origem | Revisão da estrutura pedida pelo usuário (árvore de telas), decidida numa rodada; wireframe aprovado na conversa |
| Modo de trabalho | Implementação direta (`AGENTS.md`) |

## 0. Escopo

**Entra:** o resumo do concurso-alvo; as quatro abas na rota; o diagnóstico e o peso pela banca dentro das abas; os outros editais
recolhidos no fim.

**Fica de fora:** regras, servidor e as telas próprias (conferência, novo edital, provas). O próximo item da revisão (os pontos de
entrada do QConcursos) vem numa sprint própria.

## 1. Tela

| Peça | O quê |
|---|---|
| `#concurso-resumo` | `resumoProva`, `resumoMaterial`, `pendencias` (partes sem nó + vínculos largos + nós que mudaram), `resumoReta` |
| `#concurso-abas` | `aba` (`visao`, `edital`, `banca`, `cuidado`), `mudarAba`; o router passa a aba de `#/concursos/{aba}` e, com a tela já aberta, só troca a aba |
| `#aba-visao` | material, conferência (Conferir), reta final (`#concurso-reta`: entrar/sair) |
| Diagnóstico | um bloco só (`#diagnostico-painel`): dentro da aba Edital quando é do alvo (`diagDoAlvo`), gaveta quando é de outro edital |
| `#aba-edital` | material por assunto (Ver por assunto) e preparar de novo |
| `#aba-banca` | `#banca-qconcursos`, `#peso-banca` (sem gaveta), a régua, as provas |
| `#aba-cuidado` | `#concurso-cuidado` |
| `#outros-editais` | `<details>` fechado, "Outros editais · N" |

`CASCA_VERSAO` → `casca-2026-11-06-1`.

## 2. Testes

| Teste | Prova |
|---|---|
| Chromium (`e2e-47`) | o resumo (70 dias, conferência, reta final); a aba Visão geral aberta e marcada; o resumo leva à aba Edital, com o diagnóstico dentro dela; Banca e prova com a banca e o peso pela banca; Cuidado; trocar de aba não recarrega; outros editais fechado, o diagnóstico de outro edital em gaveta que fecha; recarregar numa aba abre nela; tema escuro e computador |
| Regressão | 36 (banca e diagnóstico pelas abas), 43 (reiniciar pela aba Cuidado), 39, 44, 46 |

Sem mudança no servidor.

## 3. Itens e ordem

| # | Item | Situação |
|---|---|---|
| 47.0 | Especificação e wireframe aprovados | feito |
| 47.1 | O resumo, as abas na rota, o diagnóstico e o peso pela banca nas abas, os outros editais; `CASCA_VERSAO` | feito |
| 47.2 | Chromium e regressão | feito |
| 47.3 | Deploy pelo Guardian | — |

## Changelog

| Versão | Data | Mudança |
|---|---|---|
| 1.1.0 | 2026-11-06 | Itens 47.1 e 47.2 feitos. Chromium: 17 passos; regressão 36, 39, 43, 44 e 46 verde (36 e 43 entram pelas abas) |
| 1.0.0 | 2026-11-06 | Criado, com o wireframe aprovado pelo usuário |
