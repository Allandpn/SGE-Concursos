# Sprint 43 — Reiniciar o progresso

| | |
|---|---|
| Versão | 1.1.0 |
| Data | 2026-11-05 |
| Subordinado a | `01_DOMINIO` v1.43.0 §3.27 (D-163 a D-165); `02_JORNADAS` v1.35.0 J-21; `04_FRONTEND` v1.23.0 §8E; ADR-062 |
| Origem | Pedido do usuário (2026-11-05), decidido em duas rodadas; wireframe aprovado na conversa |
| Modo de trabalho | Implementação direta (`AGENTS.md`) |

## 0. Escopo

**Entra:** reiniciar um concurso (a cobertura toda, divididos inclusive) ou tudo, pela desativação de sessões da Sprint 42; o
registro do reinício e o desfazer; a confirmação digitada; os botões no concurso e em Ajustes; a lista de reinícios.

**Fica de fora:** zerar simulados, provas, marcações, a última página, a reta final ou erros sem sessão (o usuário escolheu só
sessões e escada); apagar o cadastro.

## 1. Banco

`V33__reinicio.sql`: `reinicio` (o concurso, ou nulo para tudo; quantas sessões e assuntos; feito e desfeito em) e
`reinicio_sessao` (as sessões que ele tirou, com chave para `sessao_registro`). Sem ADR nova: o reinício é a desativação da
ADR-062, registrada.

## 2. Servidor

| Peça | O quê |
|---|---|
| `ReinicioService.previa` | D-165: as sessões ativas do escopo (tudo, ou dos assuntos de `concurso_assunto` do concurso), quantos assuntos e, num concurso, os assuntos dele que estão na cobertura de outros, com os nomes |
| `ReinicioService.reiniciar` | Exige `confirmacao = "REINICIAR"` (422); sem sessão no escopo, 409 `REINICIO_VAZIO`. Registra o reinício e as sessões, desativa-as e refaz a escada dos assuntos (`RefazerEscada`, D-161) |
| `ReinicioService.desfazer` | D-164: reativa as sessões do reinício que ainda estiverem tiradas, refaz a escada e marca `desfeito_em`; desfeito, 409 |
| Rotas | `GET /api/reinicios/previa?concursoId=`; `POST /api/reinicios` `{concursoId, confirmacao}`; `GET /api/reinicios`; `POST /api/reinicios/{id}/desfazer` |

## 3. Tela

`js/reinicio.js` (`Alpine.store('reinicio')`): a folha de confirmação (o que zera, os divididos com o outro concurso, o que fica, o
campo "Digite REINICIAR" e o botão vermelho que só libera com a palavra), o aviso com **Desfazer**. No concurso-alvo, a caixa
"Cuidado"; em cada edital de antes, "Reiniciar o progresso". Em Ajustes, no fim, **Reinícios** (data, escopo, sessões, desfazer ou
"desfeito em") e a zona de cuidado com **Reiniciar todo o progresso**. `CASCA_VERSAO` sobe.

## 4. Testes

| Teste | Prova |
|---|---|
| `ReinicioTest` | concurso: a prévia (sessões, assuntos, o dividido com o outro), a cobertura zerada com o dividido, o de fora fica, nada apagado; uma sessão feita depois entra junto ao desfazer, pela data; desfazer de novo recusado; a lista. Tudo; sem a palavra não reinicia; sem progresso recusa |
| Chromium | a confirmação no concurso-alvo (2 sessões em 2 assuntos, o dividido com o TJ), o botão preso até REINICIAR, o aviso, a cobertura zerada e o de fora intacto, desfazer pelo aviso (a escada de volta), Reinícios em Ajustes, reiniciar tudo e desfazer pela lista, tema escuro, computador |

## 5. Itens e ordem

| # | Item | Situação |
|---|---|---|
| 43.0 | Especificação e wireframe aprovados | feito |
| 43.1 | Migração, serviço e rotas | feito |
| 43.2 | A confirmação, os botões e os reinícios na tela; `CASCA_VERSAO` | feito |
| 43.3 | Testes e Chromium | feito |
| 43.4 | Deploy pelo Guardian | — |

## Changelog

| Versão | Data | Mudança |
|---|---|---|
| 1.1.0 | 2026-11-05 | Itens 43.1 a 43.3 feitos. `ReinicioTest` (4); suíte local 486 de 488 (os 2 de esquema pedem Docker; `EstruturaSchemaTest` com as tabelas novas). Chromium: 15 passos |
| 1.0.0 | 2026-11-05 | Criado, com o wireframe aprovado pelo usuário |
