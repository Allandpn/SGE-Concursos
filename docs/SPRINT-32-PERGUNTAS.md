# Sprint 32 — Perguntas de recuperação

| | |
|---|---|
| Versão | 1.1.0 |
| Data | 2026-11-01 |
| Subordinado a | `00_PRODUTO` v1.13.0 §5.2.1; `01_DOMINIO` v1.33.0 §3.18, §4.1, §5.1, D-122 a D-128; `02_JORNADAS` v1.22.0 §4.1; `03_INVARIANTES` v1.21.0; ADR-053 |
| Origem | Pedido do usuário (2026-11-01); wireframe aprovado na conversa (com a regra da sugestão e o estado das perguntas por bloco) |
| Modo de trabalho | Implementação direta (`AGENTS.md`) |

## 0. Escopo

**Entra:** a fila de preparação (bloco lido e blocos já lidos), a chamada da IA com o texto do bloco por página e as marcações, a
conferência da resposta da IA, a seleção de 3 a 5 perguntas, o gabarito só na etapa 2, os pontos marcados guardados com a sessão,
"pergunta ruim", o que faltou (na hora depois de PARCIAL, no reestudo depois de FALHA) e o estado das perguntas por bloco no assunto.

**Fica de fora:** editar pergunta; perguntas em `QUESTOES`/`FLASHCARDS`; o app Android nativo; refazer as perguntas de um bloco já
pronto (fica para quando houver pedido); intercalar assuntos numa mesma recuperação (a recuperação continua de um assunto, D-01).

## 1. O desenho em uma página

```
POST /api/sessoes (ESTUDO com segmento) ─► mesma transação: pergunta_preparo(segmento) PENDENTE
Varredura (a cada 5 min; "-" nos testes):
   1. completa a fila com até 5 blocos já lidos sem preparo, os de revisão pendente mais próxima primeiro (D-122)
   2. prepara até 2 pendentes, um por vez, fora de transação:
      PDF do bloco (MATERIAL_DIR) ─► texto por página ─► sem texto? SEM_TEXTO
      marcações vigentes do bloco (§3.17) ─► prompt ─► ServicoIa.completar ─► JSON
      IA fora (FalhaIaException) ─► fica PENDENTE (D-75) · JSON fora da forma ─► 2ª tentativa, depois FALHOU
      perguntas válidas ─► pergunta + pergunta_ponto (D-123) ─► PRONTA
Recuperar etapa 1: GET /api/assuntos/{id}/perguntas ─► 3 a 5 {id, tipo, enunciado} (D-124); vazio = livre
Recuperar etapa 2: GET /api/perguntas/gabarito?ids= ─► pontos (D-125) · POST/DELETE /api/perguntas/{id}/ruim (D-127)
Confirmar: POST /api/sessoes {…, conferencia: {perguntasIds, pontosQueVieram}} ─► sessao_pergunta + sessao_ponto (D-126)
Hoje: reestudo traz os pontos que não vieram na sessão FALHA de origem (D-128, derivado)
```

## 2. Banco — `V25__pergunta.sql`

| Tabela | Colunas | Restrições |
|---|---|---|
| `pergunta_preparo` | `segmento_id` (PK, FK), `situacao` (`PENDENTE`, `PRONTA`, `SEM_TEXTO`, `FALHOU`), `tentativas`, `pedido_em`, `atualizado_em`, `motivo` | `ck_pergunta_preparo_d122_situacao` |
| `pergunta` | `id`, `segmento_id` (FK), `tipo`, `enunciado`, `origem_marcada` (veio de trecho com dúvida ou decorar), `criada_em`, `ruim_em` | `ck_pergunta_d123_tipo` (`EVOCACAO`, `COMPLETAR`, `PORQUE`, `COMPARACAO`); `ck_pergunta_d123_enunciado` (não vazio); gatilho adiado `trg_pergunta_d123_tem_ponto` (sem ponto, o commit falha) |
| `pergunta_ponto` | `id`, `pergunta_id` (FK), `ordem`, `texto`, `principal`, `pagina` | `ck_pergunta_ponto_d123_pagina` (≥ 1), `principal NOT NULL`, texto não vazio |
| `sessao_pergunta` | `sessao_id`, `pergunta_id` | PK das duas; a pergunta que a recuperação usou (e as "nunca vistas" saem daqui) |
| `sessao_ponto` | `sessao_id`, `ponto_id`, `veio` | PK das duas; os pontos marcados (D-126) |

Nada se apaga (D-18): "pergunta ruim" é `ruim_em`; desfazer na mesma tela volta a nulo. A lista do que faltou não é guardada: sai de
`sessao_ponto` (D-128, D-16).

## 3. Rotas

| Rota | Entrada → saída | Regra |
|---|---|---|
| `GET /api/assuntos/{id}/perguntas` | → `[{id, tipo, enunciado}]` (3 a 5; vazio = recuperação livre) | D-124 |
| `GET /api/perguntas/gabarito?ids=1,2,3` | → `[{perguntaId, pontos: [{id, texto, principal, pagina, segmentoId, bloco}]}]` | D-125: só esta rota devolve pontos |
| `POST /api/perguntas/{id}/ruim` · `DELETE /api/perguntas/{id}/ruim` | → 204 | D-127 |
| `POST /api/sessoes` | ganha `conferencia: {perguntasIds, pontosQueVieram}` (só `RECUPERACAO`) | D-126; na mesma transação da sessão |
| `GET /api/turno/plano` | cada reestudo ganha `pontos: [{texto, segmentoId, bloco, pagina}]` | D-128 |
| `GET /api/assuntos/{id}/perguntas/estado` | → `[{segmentoId, situacao, prontas, ruins}]` | Tela 8 do wireframe |

**Seleção (D-124).** Candidatas: perguntas sem `ruim_em` dos blocos ativos do assunto. Ordem: nunca usadas (sem `sessao_pergunta`)
primeiro; depois as que, na última sessão em que apareceram, tiveram ponto principal que não veio; depois as demais pela mais antiga
uso. Dentro de cada faixa, as de pergunta tirada de trecho com *dúvida* ou *decorar* vêm antes (a IA marca isso em `origem_marcada`,
coluna booleana de `pergunta`), e o empate é sorteado. Quantas: 3 com um bloco lido, 4 com dois, 5 com três ou mais; menos se não
houver.

**Sugestão (D-126)** é calculada na tela, que tem as marcações em tempo real: principais que vieram / principais das perguntas não
tiradas. Todos → `SUCESSO`; ≥ metade → `PARCIAL`; menos → `FALHA`; sem ponto principal, nenhuma sugestão. O servidor só grava o
escolhido e os pontos. É a única regra só no frontend; o teste dela é de tela (§6).

## 4. A IA

- **Texto por página**: `PDFTextStripper` página a página, cada uma precedida de `[página N]` (N na numeração do PDF do bloco, a
  mesma do leitor). Menos de 300 caracteres úteis no bloco → `SEM_TEXTO`. Corta em 60 mil caracteres.
- **Marcações**: as vigentes do bloco, como `[cor] "citação" (p. N) — nota`.
- **Prompt** (`PromptsPerguntas`): 8 a 12 perguntas; os quatro tipos; nunca copiar o texto como resposta pronta na pergunta;
  completar com `____`; cada ponto curto, `principal` para a estrutura do assunto, `pagina` real; dúvida → porquê/comparação;
  decorar → completar; importante → evocação; resposta só JSON.
- **Conferência da resposta** (`RespostaPerguntas`): tipo nos quatro, enunciado não vazio, ao menos um ponto, ao menos um principal,
  página entre 1 e o total de páginas. Pergunta fora da forma é descartada; nenhuma válida conta como falha da resposta.
- **Falhas**: `FalhaIaException` (assinatura parada, sem rede) deixa `PENDENTE` sem contar tentativa (D-75). Resposta inválida conta
  tentativa; na segunda, `FALHOU` com o motivo. Arquivo ausente → `FALHOU`.

## 5. Tela

- **Recuperar, etapa 1**: ao abrir, `GET /assuntos/{id}/perguntas`. Com perguntas: cada uma com o tipo, o enunciado e um campo
  opcional (fica só na memória da tela); a frase "Feche o material e responda de memória". Os três botões de previsão continuam
  levando à etapa 2.
- **Etapa 2**: `GET /perguntas/gabarito`. Cada pergunta com "Você escreveu" (se escreveu), os pontos com caixa, ● no principal, "p. N",
  e "pergunta ruim" (com desfazer). A sugestão aparece sobre os três botões e o sugerido fica destacado; tocar em qualquer um grava.
- **Depois de gravar**: `PARCIAL` com ponto faltando mostra "O que faltou" com "ler p. N →" (`#/ler/{segmento}?p=N`) e
  "Próxima recuperação"; os outros casos voltam a Hoje como hoje. O leitor aceita `?p=N` para abrir na página.
- **Hoje**: o reestudo mostra os pontos que faltaram, cada um com "Bloco N · p. M →".
- **Assunto**: cada bloco mostra "Perguntas: N prontas · M tiradas", "preparando…", "sem texto" ou "depois da leitura".
- `CASCA_VERSAO` sobe.

## 6. Testes

| Teste | Prova |
|---|---|
| `PerguntaRestricoesTest` | Tipo fora dos quatro, enunciado vazio, ponto sem página, pergunta sem ponto (no commit) — D-123 |
| `PreparoPerguntasTest` (IA falsa, PDF gerado no teste) | ESTUDO enfileira; varredura prepara e grava; IA fora fica pendente e retoma; resposta inválida duas vezes → FALHOU; PDF sem texto → SEM_TEXTO; blocos já lidos entram pela revisão mais próxima — D-122 |
| `SelecaoPerguntasTest` | 3/4/5 conforme os blocos lidos; nunca vistas primeiro; com ponto principal faltando depois; ruim não volta; sem pergunta, vazio — D-124, D-127 |
| `ConferenciaTest` | Etapa 1 sem pontos; gabarito só pela rota própria; sessão com conferência grava os pontos; reestudo traz os que faltaram depois de FALHA; PARCIAL não cria reestudo — D-125, D-126, D-128 |
| Chromium | Etapa 1 sem gabarito no DOM; marcar pontos muda a sugestão nas três faixas; pergunta ruim; PARCIAL mostra o que faltou e abre o leitor na página; reestudo em Hoje |

## 7. Itens e ordem

| # | Item | Situação |
|---|---|---|
| 32.0 | Especificação e wireframe aprovados | feito |
| 32.1 | `V25__pergunta.sql` | feito |
| 32.2 | Preparação: fila, texto por página, prompt, conferência da resposta, varredura | feito |
| 32.3 | Seleção, gabarito, ruim, conferência na sessão, reestudo com pontos, estado por bloco | feito |
| 32.4 | Telas: Recuperar (etapas 1 e 2, o que faltou), leitor `?p=`, Hoje, assunto; `CASCA_VERSAO` | feito |
| 32.5 | Testes e Chromium | feito |
| 32.6 | Deploy pelo Guardian | — |

## 8. Decisões e alternativas

| Escolhido | Recusado | O que quebraria |
|---|---|---|
| Fila no banco com varredura | Gerar dentro da transação da sessão | A chamada da IA leva dezenas de segundos; a gravação da sessão esperaria, e IA fora faria o registro falhar |
| Texto com `[página N]` | Texto corrido | O ponto não teria página confiável para o leitor abrir |
| Sugestão só na tela | Calculada também no servidor | Duas cópias da mesma regra (o erro que `AGENTS.md` já registrou com `RegrasEdital`); o servidor não usa a sugestão para nada |
| Gabarito em rota própria | Junto com as perguntas, escondido na etapa 1 | Escondido ainda está no DOM; "não existe na etapa 1" (§4.1) vira "não está visível" |
| Descartar só a pergunta fora da forma | Recusar a resposta inteira | Uma pergunta ruim da IA jogaria fora nove boas |

## 9. Riscos

- **Qualidade da IA**: pergunta errada ou página errada. Mitigação: página em cada ponto, "pergunta ruim", conferência da forma.
- **Custo**: uma chamada por bloco lido; a varredura prepara no máximo 2 por vez a cada 5 minutos.
- **Sem Docker nesta sessão**: os testes de schema não rodam aqui; a lista de tabelas é atualizada à mão.

## Changelog

| Versão | Data | Mudança |
|---|---|---|
| 1.1.0 | 2026-11-01 | Itens 32.1 a 32.5 feitos. `V25`; `TextoDoBloco`, `PromptsPerguntas`, `RespostaPerguntas`, `PreparoPerguntas` (fila e varredura), `PerguntaService` e rotas; a sessão ganha `conferencia` e enfileira o bloco lido; o reestudo do plano ganha os pontos; Recuperar com perguntas (etapas 1, 2 e 3), leitor com `?p=N`, Hoje e assunto. 12 testes novos (`PerguntaTest`, com IA falsa e PDF gerado no teste). Chromium: etapa 1 sem gabarito no DOM; a sugestão passa pelas três faixas conforme as marcações; "pergunta ruim" e desfazer, com a sugestão recalculada; PARCIAL grava, mostra o que faltou e abre o leitor na página; FALHA leva os pontos ao reestudo de Hoje; o assunto mostra "Perguntas: 2 prontas". Achado: `:disabled` do Alpine com valor `undefined` deixa o atributo; usar `!!` (registrado em `04_FRONTEND` §7L). A geração com a IA de verdade não foi exercida nesta sessão (sem IA configurada aqui): conferir no Pi depois do deploy |
| 1.0.0 | 2026-11-01 | Criado, com o wireframe aprovado pelo usuário |
