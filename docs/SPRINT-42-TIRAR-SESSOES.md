# Sprint 42 — Tirar sessões da escada

| | |
|---|---|
| Versão | 1.1.0 |
| Data | 2026-11-05 |
| Subordinado a | `01_DOMINIO` v1.42.0 §3.26 (D-160 a D-162); `02_JORNADAS` v1.34.0 J-20; `04_FRONTEND` v1.22.0 §8D; ADR-062 |
| Origem | Pedido do usuário (2026-11-05), decidido numa rodada; wireframe aprovado na conversa |
| Modo de trabalho | Implementação direta (`AGENTS.md`) |

## 0. Escopo

**Entra:** a tabela `sessao_registro` e a visão `sessao` das ativas; refazer a escada de um assunto; a lista por dia, tirar e pôr de
volta; erros, perguntas e foco das tiradas fora das consultas; a tela em Ajustes.

**Fica de fora:** reiniciar o progresso de um concurso ou de tudo (Sprint 43).

## 1. Banco

`V32__sessao_desativada.sql`: `sessao` passa a se chamar `sessao_registro`; `CREATE VIEW sessao AS SELECT * FROM sessao_registro
WHERE ativo` (visão simples: aceita INSERT e UPDATE, a entidade `Sessao` não muda); índice por (data, id) para a lista. As chaves
estrangeiras e as restrições seguem com a tabela. Uma coluna nova em `sessao_registro` pede recriar a visão na mesma migração.

## 2. Servidor

| Peça | O quê |
|---|---|
| `RefazerEscada` | D-161: guarda a pendente; apaga as revisões do assunto; desliga as sessões ativas e as religa uma a uma, na ordem (data, id), passando cada uma por `RevisaoService.processarSessao` (cada passo vê só as anteriores, como no registro). A pendente refeita com a mesma origem e nível recupera a data e a `data_prevista_antes_reta_final`; assunto ou disciplina arquivados: cancela a pendente (D-17). Exige a transação de quem chama |
| `SessoesRegistradasService` | `listar(ate)`: 30 dias até `ate` (hoje, sem ela), ativas e tiradas, com assunto e disciplina, e se há anteriores. `tirar(ids)` e `porDeVolta(ids)`: mudam `ativo` só das que mudam, refazem a escada dos assuntos afetados e devolvem os nomes; até 200 por gesto; lista vazia, 422 |
| Rotas | `GET /api/sessoes/registradas?ate=`; `POST /api/sessoes/registradas/tirar` e `/por-de-volta` `{ids}` → `{sessoes, assuntos}` |
| D-160 nas ligadas | `ErroRepository.findByAssuntoId`: o erro com sessão só se a sessão está na visão (coluna crua `sessaoIdGravado`, porque o `left join fetch` não distingue sessão tirada de sessão nula); `PerguntaService.selecionar`: o último uso só de sessões ativas; `FocoService.resumo`: o foco ligado a sessão tirada sai |

O resto do sistema lê `sessao` e já não vê as tiradas. O modo de teste (ADR-061) copia `sessao_registro` como qualquer tabela.

## 3. Tela

Ajustes ganha o item **Sessões registradas** (`#/sessoes`, `js/sessoes.js`): grupos por dia ("hoje", "ontem", "terça, 03/11"), a
linha com caixa, assunto, tipo e minutos, resultado; as tiradas riscadas com "fora da escada" e **Pôr de volta**; o botão fixo
**Tirar N da escada** acima da barra inferior (no computador, ao lado do menu); a confirmação com os assuntos afetados; o aviso
"Escada refeita: …". `CASCA_VERSAO` sobe.

## 4. Testes

| Teste | Prova |
|---|---|
| `SessoesRegistradasTest` | tirar a recuperação volta ao degrau do estudo (mesma data), e pôr de volta refaz o avanço; tirar a do meio recalcula as seguintes; tirar a única sessão deixa o assunto sem revisão e sem sessão na visão, e a lista a mostra tirada; a pendente igual guarda a data antecipada; o erro da sessão tirada sai da lista; lista vazia recusada |
| Chromium | Ajustes › Sessões registradas, os dias, marcar, a confirmação com o assunto, o aviso, a riscada, a escada refeita (nível 2 → 1), Hoje com a recuperação de volta, pôr de volta (1 → 2) no tema escuro, o computador |

## 5. Itens e ordem

| # | Item | Situação |
|---|---|---|
| 42.0 | Especificação e wireframe aprovados | feito |
| 42.1 | Migração, refazer a escada, rotas e as consultas ligadas | feito |
| 42.2 | A tela em Ajustes; `CASCA_VERSAO` | feito |
| 42.3 | Testes e Chromium | feito |
| 42.4 | Deploy pelo Guardian | — |

## Changelog

| Versão | Data | Mudança |
|---|---|---|
| 1.1.0 | 2026-11-05 | Itens 42.1 a 42.3 feitos. `SessoesRegistradasTest` (6); suíte local 482 de 484 (os 2 de esquema pedem Docker; `EstruturaSchemaTest` atualizado para `sessao_registro`). Chromium: 15 passos; e o roteiro do modo de teste de novo (14), com a cópia passando por `sessao_registro` |
| 1.0.0 | 2026-11-05 | Criado, com o wireframe aprovado pelo usuário |
