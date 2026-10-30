# Sprint 29 — Pomodoro: o foco cronometrado

| | |
|---|---|
| Versão | 1.1.0 |
| Data | 2026-10-30 |
| Subordinado a | `00_PRODUTO` v1.11.0 §8.1, §8.5; `01_DOMINIO` v1.30.0 §3.15, D-98 a D-105; `02_JORNADAS` v1.19.0 J-8; `03_INVARIANTES` v1.18.0; ADR-050 |
| Origem | Pedido do usuário (2026-10-30), enquanto testa o fluxo de estudo; wireframe publicado na conversa |
| Modo de trabalho | Implementação direta (`AGENTS.md`) |

## 0. Escopo

**Entra:** a fase do Pomodoro (foco, pausa curta, pausa longa) com o relógio calculado do início gravado; a barra fixa e os botões
"Foco"; o tempo medido que pré-preenche o registro de `ESTUDO`, `QUESTOES` e `FLASHCARDS` e é consumido também por `RECUPERACAO`; os
parâmetros em Ajustes; o aviso de fim de fase por sinal na tela e, opcional, por Web Push; as três consultas de D-105.

**Fica de fora:** qualquer meta, ranking, sequência de dias ou lembrete (D-104, D-105); montar o turno em ciclos (`01_DOMINIO` §7 não
muda); tela nova (as oito telas ficam); histórico de focos como lista; edição de uma fase já encerrada além de apontar assunto;
o app Android nativo.

## 1. O desenho em uma página

```
Hoje/Assuntos "Foco" ─► POST /api/foco/iniciar {tentativaId, assuntoId?, segmentoId?}
        └─► fase_foco(início = agora, duração programada = parâmetro de hoje)      (uma em curso: D-98)
Barra fixa (casca) ─► GET /api/foco/atual ao abrir, ao voltar à aba e a cada 30 s; entre consultas, só desenha o ponteiro
Fim: o cliente chega ao vencimento ─► sinal na tela ─► GET /atual (o servidor encerra a vencida)
     ou o servidor chega primeiro (job a cada 15 s) ─► encerra a vencida ─► push só nas inscrições com "aviso de fase" ligado
Registrar (ESTUDO/QUESTOES/FLASHCARDS) ─► GET /api/foco/livres?assuntoId= ─► soma pré-preenche o tempo (confirmar ou corrigir)
        └─► POST /api/sessoes {…, fasesIds} ─► mesma transação: fase.sessao_id = sessão (D-101)
Recuperar ─► mesmo POST com fasesIds ─► consome os focos do assunto sem tocar o tempo da tela (D-101)
```

## 2. Banco — `V22__pomodoro.sql`

Uma tabela nova, quatro colunas de parâmetro e um marcador na inscrição de push.

**`fase_foco`**

| Coluna | Para quê |
|---|---|
| `id`, `tentativa_id` (UUID, único) | identidade; o início é idempotente como D-45 (tocar duas vezes em "Foco" não abre duas fases) |
| `especie` | `FOCO`, `PAUSA_CURTA` ou `PAUSA_LONGA` (`ck_fase_foco_especie`) |
| `assunto_id`, `segmento_id` | opcionais; FKs (D-18: nada é apagado) |
| `inicio` | o instante gravado de que o relógio nasce (ADR-050) |
| `duracao_programada_s` | cópia dos parâmetros no início: mudar Ajustes não altera fase passada nem em curso |
| `pausado_desde`, `pausado_acumulado_s` | pausa manual: ao retomar, soma o intervalo ao acumulado e zera `pausado_desde` |
| `fim`, `motivo_fim` | `ENCERRADA` (pessoa) ou `VENCIDA` (chegou à duração); `fim` nulo = em curso |
| `aviso_fim_em` | o push de fim já saiu (uma vez por fase) |
| `sessao_id` | a sessão que usou este foco, no máximo uma (D-101); só em `FOCO` |

Restrições, com o identificador da regra no nome (`AGENTS.md`):

| Nome | Garante |
|---|---|
| `ux_fase_foco_d98_uma_em_curso` — índice único parcial em `((true)) WHERE fim IS NULL` | no máximo uma fase em curso, em qualquer aparelho (D-98); mesmo desenho de D-05 |
| `ck_fase_foco_d99_fim_depois_do_inicio` | `fim >= inicio` |
| `ck_fase_foco_d101_sessao_so_em_foco` | `sessao_id` só quando `especie = 'FOCO'` |
| `fk_fase_foco_d101_sessao` | `sessao_id` referencia `sessao` |
| `ux_fase_foco_tentativa` | início idempotente |

**Não existe coluna de tempo corrido, de "completo" nem de total do dia** (D-16, D-99, D-105): tudo isso é consulta.

**`parametro`** ganha quatro chaves (D-103), com o padrão 25 / 5 / 15 / 4: `pomodoro_foco_min`, `pomodoro_pausa_curta_min`,
`pomodoro_pausa_longa_min`, `pomodoro_focos_ate_longa`. **Sem faixa por chave**: a validação continua a única dos parâmetros (número
positivo, `docs/SPRINT-15-AJUSTES.md` §0, decisão do usuário). Valor fracionário é aceito e arredondado ao segundo; a duração nunca fica
abaixo de 1 s.

**`push_inscricao.avisa_fase`** — `BOOLEAN NOT NULL DEFAULT false`. A inscrição do ADR-048 é "avisos de processo neste aparelho"; o aviso
de fim de fase é uma escolha separada (D-104), então o envio de fase só vai às inscrições com este marcador.

## 3. Rotas (`/api/foco`)

| Rota | O que faz |
|---|---|
| `GET /atual` | a fase em curso com `decorrido_s`, `restante_s` e `pausada`, ou `204`. Antes de responder, encerra a fase vencida (`VENCIDA`, `fim` = vencimento) |
| `GET /proposta` | derivada: se a última fase encerrada foi foco, propõe pausa (longa quando os focos completos seguidos desde a última pausa longa, no dia, chegam a `pomodoro_focos_ate_longa`; senão curta); se foi pausa, propõe foco no mesmo assunto. Nada é criado (D-102) |
| `POST /iniciar` | `{tentativaId, especie, assuntoId?, segmentoId?}`. Encerra antes a fase vencida; se ainda há outra em curso responde `409` com ela (D-98). Repetir a `tentativaId` devolve a mesma fase |
| `POST /atual/pausar`, `/retomar`, `/encerrar` | congela, descongela, encerra. Encerrar antes do vencimento grava `ENCERRADA`; o tempo é o que correu |
| `PUT /{id}/assunto` | `{assuntoId?, segmentoId?}`; vale para foco em curso ou encerrado ainda sem `sessao_id` |
| `GET /livres?assuntoId=` | focos encerrados do assunto sem sessão: `{minutos, fasesIds}` (D-100) |
| `GET /resumo?de=&ate=` | foco por dia e focos completos × encerrados antes (D-105) |
| `GET /assuntos/{id}/segmentos` | estimado × real por segmento (D-105) |
| `PUT /api/push/inscricoes/fase` | `{endpoint, avisa}`: liga ou desliga o aviso de fim de fase naquele aparelho; `404` se o aparelho não está inscrito |
| `POST /api/push/teste-fase` | aviso de fim de fase de teste, só aos aparelhos que pediram |

`POST /api/sessoes` ganha `fasesIds` (opcional). Na mesma transação do registro: cada fase tem de ser `FOCO`, encerrada, do mesmo
assunto e sem `sessao_id`, senão `422`; marca `sessao_id`. O `tempoMinutos` continua o declarado (§7.7); em `RECUPERACAO` o `fasesIds` só
consome, e o tempo gravado continua o da tela (D-101).

## 4. Regras no código

| Regra | Onde mora | Como |
|---|---|---|
| D-98 | persistência | `ux_fase_foco_d98_uma_em_curso`; o serviço encerra a vencida antes de inserir, mas nunca verifica a unicidade |
| D-99 | `FocoCalculo` (função pura) | `corrido = min(fim ou agora − inicio − pausado, duracao_programada)`; só `FOCO`; vencida = duração programada |
| D-100 | `FocoService.livres` e a tela | a soma vira o valor inicial do campo; gravar usa o que a pessoa confirmou |
| D-101 | `fase_foco.sessao_id` (uma coluna) | a estrutura já impede dois usos; o serviço só valida assunto, espécie e encerramento |
| D-102 | `GET /proposta` | só deriva; nenhuma escrita |
| D-103 | `parametro` | Ajustes edita as quatro chaves |
| D-104 | `AvisoDeFase` | só inscrições com `avisa_fase`; só fase iniciada pela pessoa; uma vez (`aviso_fim_em`); texto "O foco terminou" |
| D-105 | `FocoService.resumo` e `porSegmento` | três consultas, sem coluna que as guarde |

Os testes do cálculo usam relógio injetável, como os de prazo (princípio 4 de `AGENTS.md`).

## 5. Aviso de fim de fase

`AvisoDeFase` roda a cada 15 s (`@Scheduled` com cron; a propriedade `estudos.foco.varredura-cron=-` o desliga, como nos testes, que chamam `varrer()` com o relógio na mão): encerra as fases vencidas (`VENCIDA`) e, para cada uma ainda sem `aviso_fim_em`, envia pelo
`PushService` do ADR-048 às inscrições com `avisa_fase`, **depois do commit** e fora da transação. Marca `aviso_fim_em` mesmo quando não
há inscrição, para não reavisar. Fase encerrada pela pessoa antes do vencimento nunca envia. A carga é `{titulo: "Pomodoro", corpo: "O foco
terminou" (ou "A pausa terminou"), url: "/"}`; sem nome de assunto, sem contagem, sem pergunta.

## 6. Tela

- **Casca (`index.html`, `router.js`)**: a barra do Pomodoro fica acima do conteúdo em todas as telas enquanto há fase em curso. Mostra
  o relógio, o assunto se houver e **Pausar** / **Encerrar**; na pausa de Pomodoro muda de cor e mostra **Pular**. Um estado compartilhado
  (`Alpine.store('foco')`) mantém o ponteiro; sincroniza com `GET /atual` ao abrir, ao voltar à aba e a cada 30 s. O bloco da barra usa
  `x-if`, não `x-show`, porque lê dado que pode faltar (`AGENTS.md`).
- **Hoje e detalhe do assunto**: botão **Foco** no bloco de conteúdo e por segmento; **Foco sem assunto** no rodapé de Hoje.
- **Fim de fase**: tela de proposta (botões da pausa, "mais um foco", "encerrar por hoje"), vinda de `GET /proposta`.
- **Registrar**: o campo de tempo abre com `GET /livres` quando há foco livre, com a marca "N focos · medido"; sem foco, vem a estimativa
  do segmento como hoje. **Recuperar** envia `fasesIds` sem mostrar nada novo.
- **Ajustes → Pomodoro**: quatro durações e o aviso com o app fechado (liga, desliga, teste). Som e vibração são conveniência do
  aparelho e ficam em `localStorage`, não em parâmetro.
- **Progresso** e **detalhe do assunto**: as consultas de D-105, pequenas e sem meta.
- `sw.js`: o `push` já existe (ADR-048); o texto novo não exige código novo. **`CASCA_VERSAO` sobe** (a barra mora na casca).

## 7. Testes

| Garante | Teste |
|---|---|
| D-98 | duas fases em curso: a segunda escrita falha na persistência (teste de banco, também com duas requisições simultâneas); iniciar com fase vencida a encerra e abre a nova; com fase viva responde `409` |
| D-98, idempotência | a mesma `tentativaId` devolve a mesma fase e não cria outra |
| D-99 | tabela de casos com data congelada: pausa manual congela; encerrada antes conta o corrido; vencida conta a duração programada mesmo se vista duas horas depois; pausa curta e longa valem zero |
| D-100 | registrar com focos livres traz a soma; corrigir o valor grava o corrigido; sem foco vem a estimativa |
| D-101 | uma fase usada por uma sessão não aparece em `livres`; usar de novo é `422`; em `RECUPERACAO` o tempo gravado é o da tela; `sessao_id` em pausa é recusado pelo banco |
| D-102 | encerrar uma fase não cria a seguinte; `GET /proposta` não escreve |
| D-103 | sem edição vale 25 / 5 / 15 / 4; mudar o parâmetro não altera fase em curso (a validação de valor é a única dos parâmetros) |
| D-104 | sem inscrição não envia; com `avisa_fase` envia uma vez ao vencer; encerrada pela pessoa não envia; inscrição só de processo não recebe; 404/410 remove |
| D-105 | as três consultas vêm dos dados gravados; estrutural: nenhuma coluna guarda tempo corrido, completo ou total |
| Estrutural | `fase_foco` não tem coluna derivada (D-16) |
| Tela | Chromium headless: iniciar em Hoje, barra em outra tela, pausar, encerrar, registrar com tempo vindo do foco, recarregar no meio sem perder o relógio |

## 8. Itens e ordem

| # | Item | Estado |
|---|---|---|
| 29.0 | Este documento, ADR-050 e a especificação | feito |
| 29.1 | `V22`, `FocoCalculo`, `FocoService`, rotas, `fasesIds` em sessão | feito |
| 29.2 | `AvisoDeFase`, `avisa_fase` e a rota de inscrição de fase | feito |
| 29.3 | Consultas de D-105 | feito |
| 29.4 | Barra, botões "Foco", proposta de fim de fase e `CASCA_VERSAO` | feito |
| 29.5 | Registrar e Recuperar com tempo medido; Ajustes → Pomodoro; Progresso e detalhe do assunto | feito |
| 29.6 | Suíte inteira verde e conferência no Chromium | feito — 371 de 373 verdes; os 2 restantes são os testes de schema, que exigem Docker (não há neste ambiente) e cuja lista branca recebeu `fase_foco`; Chromium conferido contra Postgres real |
| 29.7 | Deploy pelo Guardian: etiqueta `release/N` na `main`, com o OK do usuário | a fazer |

**Conferido no Chromium (Postgres 16 local, relógio real, foco de 9 s):** botão "Foco" no detalhe do assunto; barra com relógio e assunto; a barra sobrevive a recarregar; pausar e retomar; ao vencer, a proposta aparece e nada começa sozinho; Registrar abre com "Vem do Pomodoro" e o tempo preenchido; gravar prende o foco à sessão; segunda fase recusada com `409 FASE_EM_CURSO`; Ajustes mostra o grupo e o cartão do Pomodoro.

**O que não foi conferido:** o Web Push de fim de fase num navegador real (o transporte é falso nos testes, como no ADR-048), o som e a vibração, e os dois testes de schema contra Docker.

## 9. Decisões e alternativas

| Decisão | Alternativa recusada | O que quebraria com ela |
|---|---|---|
| Duração programada copiada para a fase | Ler o parâmetro na hora de calcular | Editar Ajustes no meio de um foco mudaria o vencimento e o tempo de fases passadas |
| Pausa manual acumulada em duas colunas | Tabela de intervalos de pausa | Mais escrita e junção para um dado que só se soma; nenhuma consulta pede o intervalo |
| Encerrar a vencida de forma preguiçosa (ao consultar) **e** por job | Só por job | Com o job parado a fase vencida bloquearia a seguinte e falsearia o tempo |
| `sessao_id` na fase | Tabela de ligação sessão × fase | A estrutura passaria a permitir uma fase em duas sessões, o que D-101 proíbe |
| Recuperação consome o foco sem usá-lo no tempo | Deixar o foco livre | O foco feito durante uma recuperação pré-preencheria depois uma sessão de conteúdo que nunca o usou |
| `avisa_fase` na inscrição | Reutilizar a inscrição de processo | Quem ligou o aviso de edital passaria a receber aviso de estudo sem pedir (D-96, D-104) |
| Som e vibração em `localStorage` | Parâmetros do banco | São escolha do aparelho; guardar no servidor não muda nada que outro aparelho precise saber |
| `GET /proposta` derivada | Guardar a próxima fase como fase `PROPOSTA` | Estado derivado persistido que diverge e abre brecha para a fase começar sozinha (D-102) |

## 10. Riscos

| Risco | Tratamento |
|---|---|
| Pausa manual esquecida bloqueia o próximo foco | a barra continua visível em qualquer tela; "Encerrar" é um toque; sem prazo automático para não inventar tempo |
| Relógio do aparelho errado | o servidor é o árbitro de início e fim; o cliente só desenha |
| Push atrasa ou é descartado | o sinal na tela existe; o aviso é opcional (D-104) |
| Foco medido inflado por distração | o sistema mede tempo com a fase em curso, não atenção (`01_DOMINIO` §3.15); é dito na especificação e não é "corrigido" |
| Duas abas ou dois aparelhos tocando ao mesmo tempo | índice único (D-98) e a mesma `tentativaId` |

## Changelog

| Versão | Data | Mudança |
|---|---|---|
| 1.1.0 | 2026-10-30 | Itens 29.0 a 29.6 feitos. A validação dos parâmetros segue a única existente (sem faixa por chave); a varredura do aviso tem cron desligável; `POST /api/push/teste-fase` entra; as consultas de D-105 moram em `FocoService`. Falta o 29.7 (deploy pelo Guardian) |
| 1.0.0 | 2026-10-30 | Primeira versão |
