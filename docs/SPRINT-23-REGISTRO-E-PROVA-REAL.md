# Sprint 23 — Registro de baixo atrito e prova real

| | |
|---|---|
| Versão | 1.1.0 |
| Data | 2026-11-01 |
| Subordinado a | `00_PRODUTO` v1.15.0 §7 (M-3); `01_DOMINIO` v1.35.0 §3.3, §3.20, §4.1, D-133 a D-136; `02_JORNADAS` v1.24.0 J-2, J-12; `03_INVARIANTES` v1.23.0; ADR-055 |
| Origem | Pedido do usuário (2026-11-01); wireframe aprovado na conversa |
| Modo de trabalho | Implementação direta (`AGENTS.md`) |

## 0. Escopo

**Entra:** "Fiz questões" na fila de Hoje com o lote rápido; o tipo `DISCURSIVA` (registro, resultado pela nota, revisão, M-3);
a régua da prova no concurso-alvo; a nota em pontos da prova resolvida e dos simulados, com a nota de corte.

**Fica de fora:** ler a régua do edital pela IA; nota de corte por disciplina; brancos no simulado; o app Android nativo (ADR-041).

## 1. Banco — `V27__discursiva_e_regua.sql`

- `sessao.nota_discursiva` (INT). `ck_sessao_tipo` passa a aceitar `DISCURSIVA`. `ck_sessao_d04a_previsao_por_tipo` ganha o ramo
  `DISCURSIVA` (previsão em três níveis, como a recuperação). `ck_sessao_d134_discursiva`: nota presente só e sempre na
  `DISCURSIVA`, entre 0 e 100. A contagem de questões já é recusada fora de `QUESTOES`/`FLASHCARDS` (`ck_sessao_questoes_por_tipo`).
- Parâmetros `discursiva_limiar_sucesso` (70) e `discursiva_limiar_parcial` (40).
- `concurso.errada_anula_certa` (BOOLEAN, padrão falso), `concurso.nota_corte` (NUMERIC, nulo); `concurso_pontuacao` (`concurso_id`,
  `disciplina_id`, `pontos` > 0), PK das duas. Disciplina sem linha vale 1 ponto.

## 2. Servidor

| Peça | O quê |
|---|---|
| `SessaoRequest` | ganha `notaDiscursiva` (mantém os construtores antigos) |
| `SessaoService.calcularResultado` | `DISCURSIVA`: nota obrigatória (0–100) e resultado pelos limiares (D-134) |
| `RevisaoService.processarSessao` | `DISCURSIVA` cumpre revisão pelo mesmo caminho da recuperação (D-07) |
| `MetricaService.m3` | a variante subjetiva inclui `DISCURSIVA`; a M-1 já só lê `QUESTOES` e simulados |
| `GET /api/assuntos/{id}/lote-sugerido` | `{formato}` para o lote rápido: o da última `QUESTOES` do assunto, senão o da prova mais recente do alvo, senão o da última `QUESTOES` de qualquer assunto, senão múltipla escolha (D-133) |
| `GET/PUT /api/concursos/{id}/regua` | `{erradaAnulaCerta, notaCorte, disciplinas: [{disciplinaId, nome, pontos}]}`; as disciplinas são as do concurso (D-135) |
| `GET /api/provas/{id}/pontos` | a nota em pontos da tentativa que conta (a primeira entregue), por disciplina e global, com corte e diferença; `null` sem tentativa (D-136) |
| `GET /api/simulados/pontos` | a nota em pontos de cada simulado (erradas = total − corretas) (D-136) |
| `Regua.pontuar` | função pura: por disciplina `certas × p − (anula ? erradas × p : 0)`, global = soma, nunca abaixo de zero |

Na prova, conta só questão avaliável com assunto (as fora do edital, anuladas e sem texto ficam fora, como na nota de acertos);
branco é questão avaliável sem alternativa marcada.

## 3. Tela

- **Hoje:** cada item da fila ganha "Fiz questões" ao lado de "Recuperar"; o painel do lote rápido traz assunto, formato
  (`lote-sugerido`) e tempo (focos livres, D-100, senão 2 min × feitas), faixas de previsão 50–90%, feitas e certas, e mostra o
  percentual e o resultado antes de gravar. "Mais campos" abre o Registrar completo.
- **Registrar:** a quarta pílula, **Discursiva**: previsão em três níveis, nota, tempo; mostra o resultado que a nota dá.
- **Concursos:** no alvo, "Pontuação da prova" (pontos por disciplina, errada anula certa, nota de corte).
- **Progresso:** os simulados com a nota em pontos e a barra com a marca do corte. **Relatório da prova:** o mesmo, na nota.
- **Ajustes:** os dois limiares da discursiva no grupo das recuperações.
- `CASCA_VERSAO` sobe.

## 4. Testes

| Teste | Prova |
|---|---|
| `ReguaTest` (unitário) | pontos por disciplina, errada anula certa, nunca abaixo de zero, sem régua = acertos |
| `DiscursivaTest` | as três faixas; nota fora de 0–100 recusada (serviço e banco); cumpre a revisão; entra na M-3 subjetiva e não na M-1 (D-134) |
| `ProvaResolucaoTest.d136_…` | régua salva e lida; pontos da prova resolvida e dos simulados; mudar a régua muda a conta (D-135, D-136) |
| `DiscursivaTest.d133_…` | o formato do lote vem da última `QUESTOES` do assunto (D-133). Os degraus seguintes (prova do alvo, histórico geral) dependem de dado global e ficaram sem teste isolado |
| `ParametroListagemTest` | a contagem de parâmetros sobe para 26 |
| Chromium | lote rápido em Hoje cumprindo a revisão; discursiva no Registrar; régua em Concursos; pontos no Progresso |

## 5. Itens e ordem

| # | Item | Situação |
|---|---|---|
| 23.0 | Especificação e wireframe aprovados | feito |
| 23.1 | `V27`; discursiva no serviço, revisão e M-3 | feito |
| 23.2 | Régua, pontos da prova e dos simulados; lote sugerido | feito |
| 23.3 | Telas (Hoje, Registrar, Concursos, Progresso, relatório, Ajustes); `CASCA_VERSAO` | feito |
| 23.4 | Testes e Chromium | feito |
| 23.5 | Deploy pelo Guardian | — |

## 6. Decisões e alternativas

| Escolhido | Recusado | O que quebraria |
|---|---|---|
| Previsão da discursiva em `previsao_reconstrucao` | Coluna nova de previsão | Mesma escala e mesma M-3 subjetiva; coluna nova duplicaria a regra D-04a |
| Formato do lote pela última sessão do assunto | Formato fixo no concurso | O concurso não guarda formato; a prova do alvo e o histórico já dizem |
| Brancos do simulado como erradas | Campo novo de brancos no simulado | Mudaria o registro do simulado (Sprint 8) por uma conta informativa; a limitação está escrita em §3.20 |

## Changelog

| Versão | Data | Mudança |
|---|---|---|
| 1.1.0 | 2026-11-01 | Itens 23.1 a 23.4 feitos. Os testes de API da régua entraram em `ProvaResolucaoTest` e o do lote sugerido em `DiscursivaTest` (§4). Chromium: lote rápido 13/20 com previsão 70% → PARCIAL, 40 min, revisão cumprida; discursiva nota 72 → SUCESSO; régua (2 pontos e 1 ponto, errada anula certa, corte 40) salva; Progresso "24 de 100 · corte 40 · faltaram 16" |
| 1.0.0 | 2026-11-01 | Criado, com o wireframe aprovado pelo usuário |
