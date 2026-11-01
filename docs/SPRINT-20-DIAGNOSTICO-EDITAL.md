# Sprint 20 — Diagnóstico do edital

| | |
|---|---|
| Versão | 1.1.0 |
| Data | 2026-11-01 |
| Subordinado a | `01_DOMINIO` v1.36.0 §3.21, §8, D-137 a D-139; `02_JORNADAS` v1.25.0 J-13; `03_INVARIANTES` v1.24.0 |
| Origem | Pedido do usuário (2026-11-01); wireframe aprovado na conversa |
| Modo de trabalho | Implementação direta (`AGENTS.md`) |

## 0. Escopo

**Entra:** "Diagnóstico" em cada concurso; cobertura por fase com as faixas consolidado e encaminhado, em assuntos e em pontos;
barra por disciplina; o que cabe até a data pelo ritmo medido, com os que ficam de fora; data e ritmo simulados; arquivados e
fora do edital como informação.

**Fica de fora:** qualquer escrita; a origem do que já se sabe por edital anterior; o esquecimento dos consolidados até a data;
o modo reta final (Sprint 21).

## 1. Banco

Nada. Tudo é consulta (D-16).

## 2. Servidor

| Peça | O quê |
|---|---|
| `Diagnostico` (função pura) | `valores(disciplinas)`: valor de cada assunto = pontos × questões da disciplina ÷ assuntos dela; faltando o número de questões de alguma disciplina, nenhuma o usa (só os pontos por questão) (D-138). `cabe(nuncaVistos ordenados, hoje, data, ritmo)`: semanas = dias ÷ 7, cabem = ⌊semanas × ritmo⌋ (no máximo os nunca vistos), ficam de fora os últimos, todos vistos em hoje + ⌈nunca ÷ ritmo × 7⌉ dias (D-139) |
| `DiagnosticoService` | Lê a cobertura (`concurso_assunto`), a fase de cada assunto por `FrenteService.fasesDe`, separa `FRENTE` em **em estudo** (sem nenhuma linha em `revisao`) e **em escada**; pontos da régua (`concurso_pontuacao`, padrão 1); questões por disciplina da análise mais recente do concurso (`analise_edital` → `edital_disciplina`, casadas pelo nome sem acento e sem caixa); ritmo = assuntos com a primeira sessão ativa nos últimos 28 dias ÷ 4 |
| `GET /api/concursos/{id}/diagnostico?data=&ritmo=` | Resposta abaixo. `data` e `ritmo` são simulação e nunca gravados. A data digitada vale sempre que vier (precisa ser futura); sem ela, vale a data da prova se for futura. O ritmo digitado substitui o medido |

```
{concursoId, nome, dataProva, total,
 consolidado: {assuntos, pontosPct}, encaminhado: {assuntos, pontosPct},
 disciplinas: [{disciplinaId, nome, questoes, pontos, consolidado, escada, estudo, nunca, total}],
 arquivados, foraDoEdital,
 cabe: null | {data, simulada, dias, ritmo, ritmoMedido, nuncaVistos, cabem, todosVistosEm,
               ficamDeFora: [{assuntoId, nome, peso, minutos}], minutosDeFora}}
```

`cabe` é nulo sem data (nem do concurso no futuro, nem simulada). Com data e ritmo zero, `cabe.ritmo = 0`, `cabem = 0` e
`todosVistosEm` nulo: a tela pede o ritmo.

A ordem dos nunca vistos é a do edital: peso da cobertura (`ALTO`, `MEDIO`, `BAIXO`), depois `concurso_assunto.ordem`. Os que
cabem são os primeiros; os de fora, os últimos. Minutos de material = soma de `tempo_estimado_min` dos segmentos não arquivados.

## 3. Tela

- **Concursos:** "Diagnóstico" no alvo e em cada concurso do histórico; abre um painel (mesma tela) com as faixas, as barras por
  disciplina, "o que cabe" e os informativos. Campos "simular com a data" (sem data futura) e "ritmo" (sem ritmo medido, ou para
  mudar). Nada gravado; fechar descarta.
- `CASCA_VERSAO` sobe.

## 4. Testes

| Teste | Prova |
|---|---|
| `DiagnosticoTest` (unitário) | valor por assunto com e sem questões; cabem, ficam de fora e data de todos vistos; ritmo zero (D-138, D-139) |
| `DiagnosticoApiTest` | contagens por fase batem com a fase de cada assunto, para o alvo e para um não alvo; arquivados e fora do edital; ritmo medido; data simulada; nada gravado (D-137, D-139) |
| `AcessoRotasTest` (já existe) | a rota nova nasce fechada |
| Chromium | Concursos → Diagnóstico do alvo e de outro; simular data e ritmo |

## 5. Itens e ordem

| # | Item | Situação |
|---|---|---|
| 20.0 | Especificação e wireframe aprovados | feito |
| 20.1 | `Diagnostico`, serviço e rota | feito |
| 20.2 | Tela em Concursos; `CASCA_VERSAO` | feito |
| 20.3 | Testes e Chromium | feito |
| 20.4 | Deploy pelo Guardian | — |

## 6. Decisões e alternativas

| Escolhido | Recusado | O que quebraria |
|---|---|---|
| Ritmo pela primeira sessão de qualquer tipo | Só por sessão `ESTUDO` | Assunto começado por questões ou anexo não contaria como visto, e a fase o trata como visto |
| Questões da disciplina pela análise do edital, casadas pelo nome | Coluna nova de questões na cobertura | Mudaria o contrato da importação do planejamento; o concurso importado sem análise cai em "disciplinas iguais", como a regra diz |
| Semanas fracionárias (dias ÷ 7) | Semanas inteiras | Com 10 dias e ritmo 3,5, a semana inteira daria 3 ou 7; o fracionário dá 5, o que a pessoa espera |
| Painel no próprio Concursos | Tela nova | O wireframe aprovado abre a partir do cartão; uma tela a mais não serve a jornada nova nenhuma (`02_JORNADAS` §4) |

## Changelog

| Versão | Data | Mudança |
|---|---|---|
| 1.1.0 | 2026-11-01 | Itens 20.1 a 20.3 feitos. `Diagnostico` (função pura), `DiagnosticoService`, `GET /api/concursos/{id}/diagnostico`; painel em Concursos (alvo e editais de antes); `primary-300` na paleta para a faixa "em escada"; `CASCA_VERSAO` sobe. 7 testes novos (`DiagnosticoTest`, `DiagnosticoApiTest`). Chromium: abre em 0,6 s; ritmo medido 0,3/semana → cabem 3 de 4 até a prova; ritmo 1 e data em 14 dias → cabem 2, ficam de fora os dois de peso baixo pela ordem do edital; nada gravado. Ao escrever o código, D-138 foi precisado em `01_DOMINIO` (faltando o número de questões de uma disciplina, nenhuma o usa) |
| 1.0.0 | 2026-11-01 | Criado, com o wireframe aprovado pelo usuário |
