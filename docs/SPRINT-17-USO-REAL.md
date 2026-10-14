# SPRINT 17 — Uso real (começa pelos atritos da jornada)

| Campo | Valor |
|---|---|
| Versão | 1.0.0 |
| Data | 2026-10-14 |
| Status | Vigente — em andamento |
| Especificação conceitual | `01_DOMINIO.md` v1.20.0, `03_INVARIANTES.md` v1.9.0 |

A Sprint 17 é colocar o sistema no Pi e usá-lo de verdade por 4–8 semanas. Antes
disso, uma análise da jornada do usuário (2026-10-14) achou atritos que
contaminariam os primeiros dados. O usuário pediu para resolvê-los um por vez,
antes do uso real. Cada um ganha uma seção aqui quando é resolvido.

| # | Atrito | Estado |
|---|---|---|
| 1 | Assunto com vários segmentos: escada começava no 1º segmento e os seguintes sumiam do plano | **resolvido** (§1) |
| 2 | Tempo de estudo medido como tempo de tela | aberto |
| 3 | Conteúdo novo sugerido com revisões represadas | aberto |
| 4 | Sem rodízio entre disciplinas na sugestão de conteúdo | aberto |

---

## 1. Leitura de assunto com segmentos (D-54, D-55)

### 1.1 Início da escada (D-54)

`RevisaoService.agendarPrimeira`, ao processar uma sessão `ESTUDO` sem revisão
pendente no assunto:

| Situação | Efeito |
|---|---|
| Assunto sem segmentos | Agenda nível 1 (como antes) |
| `ESTUDO` sem segmento | "Estudei o assunto inteiro": agenda nível 1 |
| `ESTUDO` com segmento, e depois dela todos os segmentos do assunto têm alguma sessão `ESTUDO` | Agenda nível 1 a partir da data desta sessão |
| `ESTUDO` com segmento, e ainda falta segmento | Não agenda nada — o assunto segue em estudo |

"Todos os segmentos estudados" é derivado: segmentos do assunto menos os ids
de segmento das sessões `ESTUDO` dele (`SessaoRepository.segmentosEstudados`).
Nenhuma coluna nova (D-16).

### 1.2 Continuação no plano do turno (D-55)

`FrenteService.proximaContinuacao`: assuntos ativos, de disciplina ativa, com
sessão e **sem nenhuma revisão** (em estudo, `01_DOMINIO` §8), ordenados pela
primeira sessão (o mais antigo primeiro). Para cada um, o próximo segmento é o
de menor ordem sem sessão `ESTUDO`. O primeiro encontrado vira o bloco do turno.

`TurnoPlanoResponse` ganha:

| Campo | Conteúdo |
|---|---|
| `blocoContinuacao` | `true` quando o bloco continua um assunto em estudo |
| `segmentoSugerido` | `id`, `ordem`, `total`, `arquivo`, `paginaInicial`, `paginaFinal`, `tempoEstimadoMin` do segmento a ler — `null` se o assunto não tem segmentos |

Sem continuação, o bloco é o de antes (D-20/D-25) e `segmentoSugerido` é o
primeiro segmento do assunto novo, se houver. Continuação não depende do teto
da frente.

### 1.3 Telas

- **Hoje:** o card mostra "Continuar" ou "Conteúdo novo", o bloco ("bloco 2 de
  5 · p. 31–60 · ~60 min") e um link "Abrir material" quando o arquivo é link.
- **Registrar conteúdo:** chegando de Hoje, o segmento sugerido já vem
  selecionado. As opções mostram "Bloco N · p. a–b", não o link. A opção sem
  segmento passa a se chamar "O assunto inteiro (começa as revisões)", com uma
  linha explicando que as revisões começam depois do último bloco.

### 1.4 Testes

| Teste | Prova |
|---|---|
| `LeituraSegmentosTest.estudarParteNaoAgendaRevisao_ultimoSegmentoAgenda` | D-54: 2 de 3 segmentos não agendam; o 3º agenda a partir da data dele |
| `LeituraSegmentosTest.estudoSemSegmentoAgenda` | D-54: assunto inteiro |
| `LeituraSegmentosTest.planoContinuaProximoSegmento` | D-55: sugere o segmento 2 do assunto em estudo antes de assunto novo |
| `LeituraSegmentosTest.continuacaoNaoDependeDoTetoDaFrente` | D-55 com frente no teto global |
| `LeituraSegmentosTest.assuntoNovoJaIndicaPrimeiroSegmento` | `segmentoSugerido` do assunto do backlog |

---

## Changelog

| Versão | Data | Mudança |
|---|---|---|
| 1.0.0 | 2026-10-14 | Criado com o atrito 1 resolvido |
