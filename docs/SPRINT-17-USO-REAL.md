# SPRINT 17 — Uso real (começa pelos atritos da jornada)

| Campo | Valor |
|---|---|
| Versão | 1.1.0 |
| Data | 2026-10-14 |
| Status | Vigente — em andamento |
| Especificação conceitual | `01_DOMINIO.md` v1.21.0, `02_JORNADAS.md` v1.7.0, `03_INVARIANTES.md` v1.10.0 |

A Sprint 17 é colocar o sistema no Pi e usá-lo de verdade por 4–8 semanas. Antes
disso, uma análise da jornada do usuário (2026-10-14) achou atritos que
contaminariam os primeiros dados. O usuário pediu para resolvê-los um por vez,
antes do uso real. Cada um ganha uma seção aqui quando é resolvido.

| # | Atrito | Estado |
|---|---|---|
| 1 | Assunto com vários segmentos: escada começava no 1º segmento e os seguintes sumiam do plano | **resolvido** (§1) |
| 2 | Tempo de estudo medido como tempo de tela | **resolvido** (§2) |
| 3 | Conteúdo novo sugerido com revisões represadas | **resolvido** (§3) |
| 4 | Sem rodízio entre disciplinas na sugestão de conteúdo | **resolvido** (§4) |

Os atritos 2–4 foram decididos pelo usuário em 2026-10-14, entre opções
apresentadas, antes de qualquer documento ou código.

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

## 2. Tempo declarado (D-29, `01_DOMINIO` §7.7)

O defeito: Registrar conteúdo gravava o tempo entre abrir a tela e confirmar.
Como a leitura acontece **antes** de abrir o registro, uma hora de estudo virava
um minuto — e D-29 ("o sistema mede a real") ficava medindo o tempo de tela.
`02_JORNADAS` §5 sempre listou o tempo como campo de conteúdo; `04_FRONTEND`
§7A tinha divergido dela.

| Tipo | Tempo |
|---|---|
| `ESTUDO` | Campo visível, preenchido com `tempoEstimadoMin` do segmento escolhido ou, sem ele, com `duracao_bloco_min`. Trocar o segmento recalcula, até o usuário mexer no campo |
| `QUESTOES`, `FLASHCARDS` | Campo visível, vazio — lote de tamanho livre não tem estimativa honesta |
| `RECUPERACAO` | Continua o tempo de tela: a reconstrução acontece com a tela aberta |

Parâmetro novo `duracao_bloco_min` (partida **50**, `01_DOMINIO` §7.7: 45–55),
migração `V10__parametro_duracao_bloco.sql`. Fecha o teste de D-29 em
`03_INVARIANTES` ("alterar o parâmetro muda a duração sugerida sem reinício"),
que até aqui não tinha parâmetro a alterar. Ajustes ganha a seção "Turno".

Tempo vazio ou menor que 1 não sai da tela — mensagem "Informe o tempo em
minutos.", nada enviado.

## 3. Plano sob represamento (D-56, `01_DOMINIO` §5.3)

`FrenteService.proximaSugestaoDeConteudo` devolve vazio também quando o
alerta de represamento está ligado (`represado > teto diário`, mesma regra do
resumo — extraída para `FrenteService.alertaRepresamento()`). A continuação de
leitura (D-55) vem antes e não passa por ela, então segue sugerida.

`TurnoPlanoResponse` ganha `alertaRepresamento`. A tela Hoje mostra, com o
alerta ligado, uma nota no topo dizendo por que não há assunto novo — até
aqui o alerta só existia em `GET /api/frente/resumo`, e nenhuma tela o lia.

## 4. Rodízio entre disciplinas (D-57, `01_DOMINIO` §7.4)

Em vez de percorrer `findByAtivoTrue()` na ordem do banco (que nem era
garantida), as disciplinas ativas são ordenadas por **quantos assuntos têm na
frente**, crescente, desempate pelo `id`. A primeira com vaga (D-25) e backlog
dá o assunto, pela menor ordem (D-41). As contagens saem do mesmo mapa de
fases já calculado em lote (Sprint 16 §3) — nenhuma consulta nova.

## 5. Testes

| Teste | Prova |
|---|---|
| `SugestaoConteudoTest.represamento_naoSugereAssuntoNovo` | D-56 |
| `SugestaoConteudoTest.represamento_continuaLeituraEmCurso` | D-56 com D-55 |
| `SugestaoConteudoTest.semRepresamento_alertaDesligado` | o campo do plano |
| `SugestaoConteudoTest.assuntoNovoVemDaDisciplinaComMenosNaFrente` | D-57 |
| `SugestaoConteudoTest.empateNaFrente_ficaComAOrdemDasDisciplinas` | D-57, desempate |
| `SugestaoConteudoTest.duracaoDeBloco_ehParametroAlteravelSemReinicio` | D-29 |
| `ParametroListagemTest.listar_devolveAs19Chaves` | V10 |

`TurnoServiceTest.blocoDeConteudoSugeridoQuandoHaVaga` dependia do backlog que
outros testes deixam gravado; com D-56, as revisões vencidas desses mesmos
testes ligavam o alerta e cortavam a sugestão. Passou a montar o próprio
cenário (arquiva as disciplinas ativas dentro da transação).

Conferido num Chrome real contra Postgres descartável: D-57 sugerindo Crase
(Português, 0 na frente) e não DNS (Redes, 2 na frente); tempo vindo 50,
editado para 70 e gravado 70; Questões com tempo vazio; nota de represamento
em Hoje sem bloco de conteúdo; seção Turno em Ajustes.

---

## Changelog

| Versão | Data | Mudança |
|---|---|---|
| 1.1.0 | 2026-10-14 | Atritos 2, 3 e 4 resolvidos (§2–§5): tempo declarado com `duracao_bloco_min`, D-56, D-57 |
| 1.0.0 | 2026-10-14 | Criado com o atrito 1 resolvido |
