# Sprint 40 — Interface: as demais telas

| | |
|---|---|
| Versão | 1.1.0 |
| Data | 2026-11-05 |
| Subordinado a | `docs/04_FRONTEND.md` v1.24.0 §8B ("As demais telas"); ADR-060 |
| Origem | Pedido do usuário (2026-11-04), a segunda das duas sprints de interface; decidida numa rodada (2026-11-05); wireframe aprovado |
| Modo de trabalho | Implementação direta (`AGENTS.md`) |

## 0. Escopo

**Entra:** Assuntos com busca, filtro de fase e uma consulta só; Progresso com os quatro cartões e as seções recolhíveis; a lista
geral de Erros (com "resolvido"); Ajustes em grupos; a porta no tema; os cartões e os toques de 44 px em Concursos, provas e
conferência.

**Fica de fora:** mudar os fluxos de Concursos, provas e conferência (o usuário escolheu só o padrão básico).

## 1. Servidor

| Peça | O quê |
|---|---|
| `PainelAssuntosService` · `GET /api/assuntos/painel` | As disciplinas ativas, cada uma com os assuntos ativos, a fase (`FrenteService.fasesDe`, em lote) e a data da revisão pendente. Troca as N+1 chamadas (`/assuntos?disciplinaId` e `/assuntos/{id}/fase` por assunto) |
| `ErrosGeraisService` · `GET /api/erros/todos` | Todos os erros dos assuntos e disciplinas ativos, do mais novo ao mais antigo (até 300), com `disciplinaId`, `causa` e `naoResolvidos`; o de sessão tirada da escada fica de fora (D-160) |
| `POST /api/erros/{id}/resolvido` `{resolvido}` | Marca ou desmarca. A coluna `erro.resolvido` existia desde a V1 sem caminho para mudá-la; sem isso, "só não resolvidos" não filtraria nada |

## 2. Tela

| Tela | O quê |
|---|---|
| Assuntos | Busca (sem acento), filtros Todos / Na fila / Estudando / Consolidado com a contagem; disciplinas recolhíveis com o resumo em palavras e o peso; cada assunto com a fase e a revisão em data humana ("revisão amanhã", "revisão atrasada há 3 dias"). Filtrando, as disciplinas abrem e as vazias somem |
| Progresso | Quatro cartões: acerto em questões (a janela global, o formato com mais questões), **retenção por assunto** (M-2 é por assunto e por tipo e não se soma — o cartão leva a Assuntos em vez de inventar um número), calibração (o desvio da objetiva, com a frase), em dia (M-4). Seções recolhíveis: Estudando (aberta), Acerto em questões, Calibração em detalhe, Em dia, Simulados, Foco |
| Erros | `#/erros` (`js/erros.js`), em Mais e no menu lateral: disciplina, causa, "Só não resolvidos"; cada erro com a causa, o assunto, a data humana e o número Q; "Resolvido" / "Desfazer"; tocar abre os erros do assunto |
| Ajustes | Grupos: Seus dados, Este aparelho, IA e integrações, Parâmetros (recolhido), e no fim Reinícios e a zona de cuidado; cada bloco abre no lugar (`<details>`). A tela vai de ~4.800 px para ~1.000 px de altura |
| A porta | `entrar.html` com `css/tema.css`, a escolha de Aparência e as cores por variável (claro e escuro) |
| Concursos, provas, conferência | Cartões com raio 12, botões de 44 px, datas humanas |

`CASCA_VERSAO` sobe (`js/erros.js` na casca).

## 3. Testes

| Teste | Prova |
|---|---|
| `TelasSprint40Test` | o painel com a fase e a revisão por disciplina; erros gerais com disciplina, causa, resolvido e não resolvidos; resolver um erro que não existe, 404 |
| Chromium (24 passos) | Assuntos: contagens, resumo, filtro Na fila, disciplina vazia some, busca sem acento, revisão atrasada em palavras, abrir o detalhe. Progresso: os cartões, Estudando aberto, Acerto abre. Erros: pelo Mais, causa, disciplina, resolvido, desfazer, abrir o assunto. Ajustes: grupos, Parâmetros recolhido, Aparência abre, altura. A porta no escuro. Computador: Erros no menu lateral |
| Regressão | os roteiros das Sprints 38 (17), 39 (30), 41 (14), 42 (15) e 43 (15) de novo, abrindo os grupos de Ajustes onde precisam |

## 4. Itens e ordem

| # | Item | Situação |
|---|---|---|
| 40.0 | Especificação e wireframe aprovados | feito |
| 40.1 | Servidor: o painel de Assuntos, os erros gerais e o resolvido | feito |
| 40.2 | As telas | feito |
| 40.3 | Testes e Chromium | feito |
| 40.4 | Deploy pelo Guardian | — |

## Changelog

| Versão | Data | Mudança |
|---|---|---|
| 1.1.0 | 2026-11-05 | Itens 40.1 a 40.3 feitos. `TelasSprint40Test` (2); suíte local 488 de 490 (os 2 de esquema pedem Docker). Chromium 24 passos e os roteiros antigos (91) de novo |
| 1.0.0 | 2026-11-05 | Criado, com o wireframe aprovado pelo usuário |
