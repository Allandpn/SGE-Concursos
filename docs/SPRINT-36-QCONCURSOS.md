# Sprint 36 — Resolver no QConcursos

| | |
|---|---|
| Versão | 1.1.0 |
| Data | 2026-11-04 |
| Subordinado a | `01_DOMINIO` v1.40.0 §3.24, §8, D-150 a D-156, D-133; `02_JORNADAS` v1.30.0 J-17; `03_INVARIANTES` v1.28.0; ADR-058 |
| Origem | Pedido do usuário (2026-11-04), decidido em três rodadas; wireframe aprovado na conversa |
| Modo de trabalho | Implementação direta (`AGENTS.md`) |

## 0. Escopo

**Entra:** o atalho "Resolver no QConcursos" (endereço montado pelo SGE) em Hoje, no detalhe do assunto, no Diagnóstico e no Peso
pela banca; o catálogo de bancas, que aprende pelo endereço colado no concurso; a sugestão de quantas; a volta com o lote rápido
aberto em qualquer tela; "Anotar erros" com o número Q; questões antes de ler só nas métricas; o parâmetro "minutos por questão".

**Fica de fora:** a captura automática por userscript (Sprint 37); ler qualquer coisa do QConcursos; o script de manutenção do
catálogo no planejamento (o catálogo inicial vem na migração; o script entra com a Sprint 37).

## 1. Banco — `V31__qconcursos.sql`

| Peça | O quê |
|---|---|
| `qconcursos_banca` | `nome_norm` (PK, o nome sem acento, sem caixa e sem pontuação), `nome`, `numero` (> 0), `origem` (`CATALOGO` ou `ENDERECO`), `atualizado_em`. Semeada com as bancas conferidas: CESPE/CEBRASPE 2, FCC 1, CESGRANRIO 5, FGV 63, OBJETIVA 121, VUNESP 152, IADES 276, FUNDAÇÃO LA SALLE 367, INSTITUTO AOCP 379, INSTITUTO MAIS 493, IDIB 1019, IDCAP 1703, INSTITUTO AVALIA 3682, ISET 4024 (D-151) |
| `erro.questao_externa` | `VARCHAR(20)`, opcional, `^Q[0-9]{4,10}$` (CHECK) (D-156) |
| Parâmetro `minutos_por_questao` | 3 (D-152) |

## 2. Servidor

| Peça | O quê |
|---|---|
| `LinkQConcursos` (função pura) | `montar(filtro)`: base `https://www.qconcursos.com/questoes-de-concursos/questoes`, `discipline_ids[]`, `subject_ids[]`, `examining_board_ids[]`, `exclude_nullified=true`, `exclude_outdated=true`, `my_questions=not_resolved`, `modality_ids[]` (1 múltipla escolha, 2 certo/errado), `per_page`, `publication_year[]` do ano − N ao ano; colchetes codificados (`%5B%5D`), como o site gera. `porPagina(n)`: o menor de 5, 10, 15, 20 que caiba *n* (20 no máximo). `sugeridas(foco, minutos, minimo)` = máx(mínimo, ⌊foco ÷ minutos⌋). `numeroDaBanca(endereco)`: o único `examining_board_ids[]` de um endereço do QConcursos (aceita também só o número) |
| `QConcursosService.atalhos(assuntoIds)` | Para cada assunto: a análise mais recente que o tem (`edital_assunto.assunto_id`, maior id); os nós vigentes (`edital_parte_no.proposto = false`) das partes `ACEITA`/`DECIDIDA` e `aparece`; nó negativo → disciplina `−id`; nó positivo → assunto, mais a disciplina da raiz dele (`no_arvore.raiz_id`, na versão vigente). Sem nó: os ids de `edital_disciplina.qconcursos_ids` (valor absoluto), senão a raiz da árvore vigente cujo nome normalizado é o da disciplina do SGE; aviso `DISCIPLINA`. Sem nenhuma: sem atalho. Banca do alvo pelo catálogo (aviso `SEM_BANCA` se não achar). Formato por `ReguaService.formatoSugerido` (D-133). Devolve `{assuntoId, url, sugeridas, banca, formato, filtro: [nomes], avisos}` |
| `GET /api/qconcursos/atalhos?assuntoIds=1,2` | O de cima, em lote (Hoje e as listas) |
| `GET /api/concursos/{id}/banca-qconcursos` | `{banca, numero, origem}` (banca do concurso, senão da prova hospedada mais recente dele, como D-145) |
| `PUT /api/concursos/{id}/banca-qconcursos` `{endereco}` | Lê o número e grava `(nome_norm da banca → numero, ENDERECO)`; sem banca no concurso, 409 `SEM_BANCA`; endereço sem um único número, 422 `ENDERECO_SEM_BANCA` |
| D-155 | `SessaoRepository.assuntosComSessao` passa a contar só assunto com sessão que não seja `QUESTOES` ou com alguma revisão; `primeiraSessaoDosAtivos` e o ritmo do Diagnóstico, o mesmo. `RevisaoService.processarRecuperacao`: sem revisão pendente e assunto nunca lido, não cria revisão |
| D-156 | `ErroRequest.questaoExterna` (opcional, normalizada para maiúscula com o Q); `ErroService` recusa (409 `ERROS_ACIMA_DO_LOTE`) mais erros ligados a uma sessão `QUESTOES` que *total − corretas*; `ErroResponse` devolve o número |

## 3. Tela

- **Lote rápido global:** o painel sai de Hoje e vai para um componente na raiz (`Alpine.store('lote')`), aberto por "Fiz questões"
  e pela volta. Depois de Registrar, se houve erradas, "Anotar erros" (opcional) no mesmo painel. Registrar dispara
  `sge:sessao-registrada`; Hoje recarrega o plano ao ouvi-lo. O tempo sem foco usa `minutos_por_questao`.
- **Saída e volta (D-153):** `Alpine.store('qc')`: tocar no atalho (um `<a target="_blank" rel="noopener">`) guarda
  `{assuntoId, nome, saiuEm}` em `localStorage` (`qc.saida`). Em `visibilitychange` (visível) e ao carregar o app, com saída de até
  6 h e há pelo menos 1 min, abre o lote do assunto com o tempo = minutos desde a saída (focos livres mandam); mais de 6 h, descarta.
  "Não fiz" e Registrar apagam; ✕ só fecha.
- **Onde (D-154):** Hoje (fila e antecipáveis), com a linha de detalhe; detalhe do assunto, com a linha "Filtro"; Diagnóstico (ficam de
  fora) e Peso pela banca (propostas e ajustados), compactos.
- **Concursos:** "Banca no QConcursos" no alvo, com "trocar" (colar o endereço).
- **Erros:** o número Q vira link para a busca do QConcursos.
- **Ajustes:** grupo "Questões" (`minutos_por_questao`, `lote_minimo_questoes`).
- `CASCA_VERSAO` sobe.

## 4. Testes

| Teste | Prova |
|---|---|
| `LinkQConcursosTest` (unitário) | o endereço com assunto e disciplina, banca, anos, modalidade e tamanho da página; sem banca; `porPagina`; `sugeridas`; o número tirado de um endereço e recusado com zero ou dois (D-150 a D-152) |
| `QConcursosApiTest` | nós de assunto e de disciplina pela conferência; queda para a disciplina da análise e para a da árvore pelo nome, com aviso; sem atalho; banca pelo catálogo (CESPE = Cebraspe) e aprendida pelo endereço; sem alvo, sem banca (D-150, D-151) |
| `QuestoesAntesDeLerTest` | questões em assunto nunca lido: M-1 conta, revisão não nasce, fase backlog, diagnóstico nunca visto; depois do estudo, a escada abre (D-155) |
| `ErroQConcursosTest` | o número Q gravado e normalizado; formato inválido recusado; erros acima de total − corretas recusados (D-156) |
| `ParametroListagemTest`, `EstruturaSchemaTest` | 31 parâmetros; a tabela nova |
| Chromium | atalho em Hoje com o endereço certo; sair e voltar com o lote aberto e o tempo; "Não fiz"; registrar e anotar um erro; detalhe, Diagnóstico e Peso pela banca; banca aprendida por endereço |

## 5. Itens e ordem

| # | Item | Situação |
|---|---|---|
| 36.0 | Especificação e wireframe aprovados | feito |
| 36.1 | `V31`; `LinkQConcursos`, serviço, rotas | feito |
| 36.2 | D-155 e D-156 no servidor | feito |
| 36.3 | Telas (lote global, saída e volta, atalhos, Concursos, Erros, Ajustes); `CASCA_VERSAO` | feito |
| 36.4 | Testes e Chromium | feito |
| 36.5 | Deploy pelo Guardian | — |

## 6. Decisões e alternativas

| Escolhido | Recusado | O que quebraria |
|---|---|---|
| O endereço montado no servidor, em lote | Montar no navegador | A resolução dos nós lê a conferência e a árvore; o navegador precisaria de tudo isso |
| A banca num catálogo por nome, no banco | Coluna no concurso | Cada concurso da mesma banca pediria o endereço de novo |
| A saída no `localStorage` | No servidor | Só importa ao aparelho que saiu (ADR-058) |
| Lote rápido como componente global | Um painel por tela | A volta pode acontecer em qualquer tela; quatro cópias do mesmo painel divergiriam |

## Changelog

| Versão | Data | Mudança |
|---|---|---|
| 1.1.0 | 2026-11-04 | Itens 36.1 a 36.4 feitos. A resposta do atalho leva o nome curto da banca no catálogo (`BancaDoConcurso.nomeNoCatalogo`); a validação do endereço devolve 422, como as outras. `RevisaoEscadaTest.recuperacaoEspontanea…` passa a provar D-155 (questões sozinhas não abrem a escada; a recuperação livre abre). O painel do lote fica na camada de cima (`z-50`) para abrir sobre o Diagnóstico e o Peso pela banca, e abre e fecha por `visivel` (o objeto do lote só é trocado no próximo abrir). 9 testes novos (`LinkQConcursosTest`, `QConcursosApiTest`, `QuestoesAntesDeLerTest`, `ErroQConcursosTest`); suíte local 469 de 471 (os 2 de esquema pedem Docker). Chromium: 23 passos — o endereço da Crase pelo nó 28150, Improbidade pela disciplina da árvore com aviso, a aba nova, a volta com 24 min, 7 de 8 = 88% gravado, o erro Q2318890, "Não fiz", ✕ adia, 6 h esquece, o filtro no detalhe, FGV nº 63 pelo catálogo, IBFC aprendida pelo endereço (207), o atalho no que fica de fora do Diagnóstico, D-155 sem escada, Ajustes e o "Fiz questões" com 3 min por questão. Peso pela banca não foi conferido no Chromium (pede provas hospedadas) |
| 1.0.0 | 2026-11-04 | Criado, com o wireframe aprovado pelo usuário |
