# Sprint 22 — Peso pela banca

| | |
|---|---|
| Versão | 1.1.0 |
| Data | 2026-11-02 |
| Subordinado a | `01_DOMINIO` v1.38.0 §3.23, D-145 a D-147; `02_JORNADAS` v1.27.0 J-15; `03_INVARIANTES` v1.26.0; ADR-057 |
| Origem | Pedido do usuário (2026-11-02); wireframe aprovado na conversa |
| Modo de trabalho | Implementação direta (`AGENTS.md`) |

## 0. Escopo

**Entra:** a incidência medida nas provas hospedadas; a proposta de um degrau; aceitar (um, por disciplina, todos) e desfazer; o
ajuste vigente aplicado em ativar, reativar e reimportar; três parâmetros.

**Fica de fora:** incidência das apostilas ou do QConcursos; peso da disciplina; editar a banca do concurso.

## 1. Banco — `V29__peso_banca.sql`

| Peça | O quê |
|---|---|
| `peso_banca` | `id`, `concurso_id` (FK), `assunto_id` (FK), `peso` (`ALTO`/`MEDIO`/`BAIXO`), `aceito_em`, `desfeito_em` (nulo = vigente) |
| `ux_peso_banca_d147_um_vigente` | índice único parcial em (`concurso_id`, `assunto_id`) onde `desfeito_em IS NULL` (D-147) |
| Parâmetros | `banca_anos` (5), `banca_provas_minimas` (2), `banca_questoes_minimas` (30) |

## 2. Servidor

| Peça | O quê |
|---|---|
| `PesoBanca` (função pura) | `mesmaBanca(a, b)`: sem acento, sem caixa, só letras e dígitos, "cespe" vira "cebraspe". `propor(linhas)`: para cada assunto da disciplina, com *t* = ⌈2(n−1)/3⌉, sobe se pelo menos *t* outros têm estritamente menos questões, desce se pelo menos *t* outros têm estritamente mais; com n < 2, nada; alto não sobe, baixo não desce (D-146) |
| `PesoBancaService.situacao(concursoId)` | a banca (do concurso, senão da prova hospedada mais recente dele); as provas da mesma banca com `ano` ≥ ano atual − `banca_anos` ou sem ano; por disciplina da cobertura, as questões avaliáveis com assunto da cobertura dela; a amostra (provas distintas, questões) e, se suficiente, as propostas (só as que mudam o peso vigente); a lista dos ajustados vigentes com a proposta de hoje |
| `aceitar(concursoId, assuntoIds)` | recalcula, aceita só o que é proposta agora: fecha o ajuste vigente e, se o proposto não é o peso do edital, grava outro; se o concurso é o alvo, o peso vai ao assunto |
| `desfazer(concursoId, assuntoId)` | fecha o vigente; se alvo, o assunto volta ao peso da cobertura (o do edital) |
| `ConcursoService` | `reativar` e `ativarComCobertura` trocam o peso dos itens pelo ajuste vigente antes de `aplicarCobertura`; a cobertura gravada continua com o peso do edital (ADR-057) |
| Rotas | `GET /api/concursos/{id}/peso-banca`; `POST /api/concursos/{id}/peso-banca/aceitar` `{assuntoIds}` → a situação nova; `POST /api/concursos/{id}/peso-banca/{assuntoId}/desfazer` → a situação nova |

## 3. Tela

- **Concursos:** no alvo, "Peso pela banca" (com "· N propostas" quando houver); o painel com a amostra geral, as disciplinas
  (amostra ou "sem amostra suficiente"), as propostas com Aceitar, "aceitar todos desta disciplina", "Aceitar todos" no topo, o aviso
  de subir para alto, e "Ajustados pela banca" com Desfazer.
- **Ajustes:** os três parâmetros no grupo "Peso pela banca".
- `CASCA_VERSAO` sobe.

## 4. Testes

| Teste | Prova |
|---|---|
| `PesoBancaTest` (unitário) | `mesmaBanca` (acento, caixa, CESPE = Cebraspe); os terços com e sem empate; zero; alto não sobe, baixo não desce; n = 1 (D-146) |
| `PesoBancaApiTest` | só a mesma banca, só avaliável com assunto, só dentro dos anos (sem ano conta); a banca da prova quando o concurso não tem; abaixo do mínimo, sem proposta; aceitar aplica ao assunto; reimportar mantém; desfazer volta; prova nova não muda o peso; dois vigentes recusados pelo banco (D-145, D-147) |
| `ParametroListagemTest` | 30 parâmetros |
| `AcessoRotasTest` (já existe) | as rotas novas nascem fechadas |
| Chromium | abrir o painel, aceitar um, aceitar todos, desfazer |

## 5. Itens e ordem

| # | Item | Situação |
|---|---|---|
| 22.0 | Especificação e wireframe aprovados | feito |
| 22.1 | `V29`; `PesoBanca`, serviço, rotas; o ajuste em ativar, reativar e reimportar | feito |
| 22.2 | Tela em Concursos e Ajustes; `CASCA_VERSAO` | feito |
| 22.3 | Testes e Chromium | feito |
| 22.4 | Deploy pelo Guardian | — |

## 6. Decisões e alternativas

| Escolhido | Recusado | O que quebraria |
|---|---|---|
| Os terços por "mais que dois terços dos outros" (comparação estrita) | Cortar a lista ordenada em três pedaços iguais | Com empate na fronteira, o corte por posição sobe um assunto e não o outro com o mesmo número de questões |
| Banca da prova mais recente quando o concurso não tem | Pedir a banca numa tela nova | O concurso importado do planejamento não traz banca e não há onde editar; a prova hospedada já diz qual é |
| "Últimos N anos" como `ano` ≥ ano atual − N | Janela por data exata | A prova guarda só o ano |

## Changelog

| Versão | Data | Mudança |
|---|---|---|
| 1.1.0 | 2026-11-02 | Itens 22.1 a 22.3 feitos. `V29`; `PesoBanca` (função pura), `PesoBancaService` e as rotas; `ConcursoService` troca o peso do edital pelo ajuste vigente em reativar e em ativar com cobertura; painel em Concursos e o grupo em Ajustes; `CASCA_VERSAO` sobe. 6 testes novos (`PesoBancaTest`, `PesoBancaApiTest`). Ao escrever o código, D-145 foi precisado em `01_DOMINIO`: sem banca no concurso, vale a da prova hospedada mais recente dele. Chromium: duas provas da FGV, 32 questões; 3 propostas (Crase e Regência médio → alto, Pontuação alto → médio, Ortografia baixo fica); aceitar um e "aceitar todos" gravaram os pesos; desfazer Pontuação voltou a alto e a proposta reapareceu |
| 1.0.0 | 2026-11-02 | Criado, com o wireframe aprovado pelo usuário |
