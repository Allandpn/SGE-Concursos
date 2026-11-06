# Sprint 46 — Hoje reorganizado

| | |
|---|---|
| Versão | 1.1.0 |
| Data | 2026-11-05 |
| Subordinado a | `04_FRONTEND` v1.28.0 §8B (Hoje) e §8I; `02_JORNADAS` v1.39.0 |
| Origem | Pedido do usuário depois da árvore de telas (`docs/ESTRUTURA-TELAS.json`), decidido numa rodada; wireframe aprovado na conversa |
| Modo de trabalho | Implementação direta (`AGENTS.md`) |

## 0. Escopo

**Entra:** a nova ordem de Hoje: o progresso no cabeçalho; o Agora logo depois; os avisos numa faixa recolhida; o Depois numa lista
só, na ordem do plano; "Também hoje" no fim.

**Fica de fora:** qualquer mudança de regra, do plano ou do servidor. Nenhum bloco sai da tela; as próximas revisões de estrutura
(Concursos e os pontos de entrada do QConcursos) vêm em sprints próprias.

## 1. Tela

| Peça | O quê |
|---|---|
| `#hoje-cabecalho` | o dia, o alvo e a linha do progresso com barra (`#progresso-do-dia`) |
| `#proxima-acao` | o Agora, sem mudança, logo depois do cabeçalho |
| `#avisos-hoje` | faixa com a contagem e o primeiro aviso (`paginaHoje.avisos`); abre os blocos de sempre: IA parada (`avisoIa`), revisões acumuladas, a volta (J-4), a reta final ativa (`#faixa-reta-final`, com o que a entrada antecipou) ou a sugestão (`#sugestao-reta-final`) |
| `#depois-lista` | `li[data-tipo]`: reestudo, recuperacao, manutencao, conteudo, simulado (`#simulado-da-semana`); o título conta `totalDepois` |
| `#tambem-hoje` | `#caixa-captura` em destaque, o material preparando/pronto, `#quer-adiantar`, `#foco-sem-assunto` |

`js/pages.js`: `avisosAbertos`, `avisoIa`, `avisos`, `totalDepois`. `CASCA_VERSAO` → `casca-2026-11-05-10`.

## 2. Testes

| Teste | Prova |
|---|---|
| Chromium (`e2e-46`) | o progresso no cabeçalho; a ordem cabeçalho → Agora → Avisos → Depois → Também hoje; a faixa fechada com a contagem e o primeiro aviso, aberta com o aviso inteiro; Depois · 2 na ordem recuperação, conteúdo, com as ações; Foco sem assunto no fim; depois de recuperar, 1 de 3, Depois · 1 e a faixa some; a coluna dos 7 dias no computador; tema escuro |
| Regressão | os roteiros 36, 37, 39, 41, 44 e 45, com os seletores do Depois atualizados (`#depois-lista`) |

Sem mudança no servidor: a suíte não muda.

## 3. Itens e ordem

| # | Item | Situação |
|---|---|---|
| 46.0 | Especificação e wireframe aprovados | feito |
| 46.1 | A nova ordem de Hoje; `CASCA_VERSAO` | feito |
| 46.2 | Chromium e regressão | feito |
| 46.3 | Deploy pelo Guardian | `release/20` (`1632220`) |

## Changelog

| Versão | Data | Mudança |
|---|---|---|
| 1.1.0 | 2026-11-05 | Itens 46.1 e 46.2 feitos. Chromium: 16 passos e a regressão |
| 1.0.0 | 2026-11-05 | Criado, com o wireframe aprovado pelo usuário |
