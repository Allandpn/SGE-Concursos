# Sprint 21 — Reta final

| | |
|---|---|
| Versão | 1.1.0 |
| Data | 2026-11-01 |
| Subordinado a | `01_DOMINIO` v1.37.0 §3.22, §7.4, D-140 a D-144; `02_JORNADAS` v1.26.0 J-14; `03_INVARIANTES` v1.25.0; ADR-056 |
| Origem | Pedido do usuário (2026-10-17 e 2026-11-01); wireframe aprovado na conversa |
| Modo de trabalho | Implementação direta (`AGENTS.md`) |

## 0. Escopo

**Entra:** a sugestão em Hoje e "Agora não"; entrar e sair em Concursos; a faixa da reta final; assunto novo só de peso alto; a
manutenção de médio e baixo fora da fila (e fora do represado); a antecipação dos de peso alto; o simulado da semana no segundo bloco
do fim de semana; o parâmetro das semanas.

**Fica de fora:** reta final sem data de prova; mexer em peso, ordem, escada ou teto; desfazer a antecipação ao sair.

## 1. Banco — `V28__reta_final.sql`

| Peça | O quê |
|---|---|
| `reta_final` | `id`, `concurso_id` (FK), `entrou_em`, `saiu_em` (nulo = aberta) |
| `ux_reta_final_d141_uma_aberta` | índice único parcial em `concurso_id` onde `saiu_em IS NULL` (D-141) |
| `concurso.reta_final_dispensada_em` | o "Agora não" (D-140) |
| `revisao.data_prevista_antes_reta_final` | a data anterior da manutenção antecipada (D-143); a entidade não a mapeia, só a escrita por SQL |
| Parâmetro `reta_final_semanas_aviso` | 8 |

## 2. Servidor

| Peça | O quê |
|---|---|
| `RetaFinalConsulta` | `ativa()`: reta final aberta do concurso-alvo, com `entrou_em` depois da última ativação dele (trocar de alvo encerra sem escrita) e hoje até a data da prova (D-141). Sem dependência do plano, para o `FrenteService` usá-la |
| `RetaFinalService` | `situacao()` (ativa e sugestão); `entrar(concursoId)`: recusa sem data futura (409 `SEM_DATA_DE_PROVA`) ou se não é o alvo (409 `NAO_E_O_ALVO`); fecha retas finais abertas que já não valem, insere a nova e antecipa; `sair`; `dispensar` |
| A sugestão (D-140) | alvo com data futura a até `reta_final_semanas_aviso` semanas, sem dispensa e sem reta final desde a última ativação |
| A antecipação (D-143) | revisões pendentes de assuntos consolidados de peso `ALTO` com `data_prevista` depois de *prova − 7*; ordenadas pela data; a *i*-ésima de *n* vai para `amanhã + ⌊i × dias ÷ n⌋`, com `dias` = de amanhã até *prova − 7* inclusive. Sem dia nesse intervalo (entrar na última semana), nada é antecipado. Atualiza `versao` (ADR-021) |
| `FrenteService` | `assuntosComManutencaoEscondida()`: na reta final, os assuntos consolidados de peso médio e baixo; `represado()` os desconta; `proximaSugestaoDeConteudo` só abre do backlog peso `ALTO` (D-142) |
| `TurnoService.plano` | tira da fila e das antecipáveis as revisões desses assuntos; devolve `retaFinal {concursoId, dataProva, dias}`, `manutencoesEsperando`, `simuladoDaSemana` (sábado ou domingo, sem simulado desde a segunda; D-144), e nesse caso `proximoBloco` nulo |
| Rotas | `GET /api/reta-final` → `{ativa, sugestao}`; `POST /api/concursos/{id}/reta-final` → `{antecipadas, ate}`; `POST /api/concursos/{id}/reta-final/sair` e `/dispensar` → 204 |

## 3. Tela

- **Hoje:** a sugestão (Entrar / Agora não), a faixa "Reta final · N dias · prova dd/mm", a linha de antecipadas logo depois de entrar,
  "N manutenções de peso médio e baixo esperam a prova", e o cartão "Simulado da semana" com "Registrar" no lugar do segundo bloco.
  O SPA não tinha tela de registro de simulado (a Sprint 8 só fez a API); o "Registrar" abre um formulário curto — data, duração,
  formato e certas/total por disciplina do concurso-alvo (as da régua) — que manda o `POST /api/simulados` de sempre.
- **Concursos:** no alvo, "Entrar na reta final" (com data futura) ou a marca "reta final" e "Sair da reta final".
- **Ajustes:** o parâmetro num grupo "Reta final".
- `CASCA_VERSAO` sobe.

## 4. Testes

| Teste | Prova |
|---|---|
| `RetaFinalTest` | entrar sem data futura ou fora do alvo é recusado; sugestão a até N semanas, some com "agora não" e depois de entrar; ativa e inativa (data passada, outro alvo, sair); uma aberta por concurso no banco (D-140, D-141) |
| `RetaFinalTest` (mesma classe) | assunto novo só de peso alto; continuação de qualquer peso; manutenção de médio e baixo fora da fila e de volta ao sair, com a data intacta; antecipação distribuída até prova − 7 com a data anterior; simulado no sábado sem simulado na semana e não em dia útil (relógio congelado) (D-142 a D-144) |
| `ParametroListagemTest` | 27 parâmetros |
| `AcessoRotasTest` (já existe) | as rotas novas nascem fechadas |
| Chromium | a sugestão, entrar, a faixa, sair |

## 5. Itens e ordem

| # | Item | Situação |
|---|---|---|
| 21.0 | Especificação e wireframe aprovados | feito |
| 21.1 | `V28`; consulta, serviço, rotas | feito |
| 21.2 | O plano na reta final (D-142 a D-144) | feito |
| 21.3 | Telas (Hoje, Concursos, Ajustes); `CASCA_VERSAO` | feito |
| 21.4 | Testes e Chromium | feito |
| 21.5 | Deploy pelo Guardian | — |

## 6. Decisões e alternativas

| Escolhido | Recusado | O que quebraria |
|---|---|---|
| "Trocou de alvo" derivado de `entrou_em` < `ativado_em` | Fechar a reta final dentro de `ConcursoService.ativar` | Acoplaria a ativação à reta final; a derivação não esquece nenhum caminho de troca |
| Descontar as escondidas do represado | Deixar o represado contar tudo | A manutenção escondida dispararia o alerta de represamento (D-56) e o plano pararia de abrir assunto novo por algo que a própria reta final escondeu |
| A sugestão some depois de qualquer entrada desde a última ativação | Voltar a sugerir depois de sair | Quem saiu decidiu; a sugestão viraria insistência |

## Changelog

| Versão | Data | Mudança |
|---|---|---|
| 1.1.0 | 2026-11-01 | Itens 21.1 a 21.4 feitos. `V28`; `RetaFinalConsulta` (a ativa, sem dependência do plano), `RetaFinalService` e as rotas; `FrenteService` com assunto novo só de peso alto e a manutenção escondida fora do represado; `TurnoService` com a fila filtrada e o simulado da semana; telas de Hoje (sugestão, faixa, antecipadas, manutenções esperando, simulado com o formulário de registro), Concursos (entrar, sair) e Ajustes; `CASCA_VERSAO` sobe. 6 testes novos em `RetaFinalTest` (sábado congelado). Chromium (domingo real): a sugestão a 6 semanas, Entrar trocou o bloco de Crase (médio) por Regência (alto), o simulado da semana apareceu e sumiu depois do registro, Sair em Concursos tirou a faixa e a sugestão não voltou |
| 1.0.0 | 2026-11-01 | Criado, com o wireframe aprovado pelo usuário |
