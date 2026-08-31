# PROGRESSO

Onde o projeto está. **Primeiro arquivo a ler** em qualquer sessão, depois do
`CLAUDE.md`.

Duas partes: o **mapa** das sprints, que é estável, e a **sprint corrente**
detalhada, que é a única com itens abertos. Sprint futura tem uma linha e nada
mais — detalhá-la agora seria inventar precisão que ainda não existe.

---

## 1. O mapa

| # | Sprint | Entrega | Estado |
|---|---|---|---|
| 1 | **Ambiente e schema** | Docker, Postgres, migração, as 9 restrições, testes de invariante | **feito** |
| 2 | Cadastro e importação | Disciplina, assunto, importação e exportação CSV (J-1) | **feito** |
| 3 | Registrar sessão | O evento central: os 4 tipos, resultado, previsão, idempotência | **feito** |
| 4 | Escada e revisão | Agendamento, cumprimento, roteamento por resultado, janela | **feito** |
| 5 | Frente de estudo | Backlog, tetos, vaga por consolidação, alerta de represamento | **feito** |
| 6 | Plano de turno | A tela Hoje: fila de recuperação + blocos, e o "puxar mais" | **feito** |
| 7 | Métricas e erros | M-1 a M-4 com as regras de `n`, banco de erros como camada explicativa | não começou |
| 8 | Simulado e fechamento | Simulado por disciplina, backup testado, polimento | não começou |

> **O sistema fica utilizável ao fim da Sprint 4.** Cadastrar, estudar,
> registrar e revisar já fecham o ciclo. As sprints 5 a 8 melhoram o que já
> funciona — nenhuma delas é pré-requisito para começar a usar.

O mapa **pode mudar**. Mudar uma linha aqui é grátis; é justamente por isso que
ele existe em vez do plano detalhado das oito.

---

## 2. Sprint 1 · Ambiente e schema

**Documento técnico:** `docs/SPRINT-1-BANCO.md` v1.1.0

| | Item | Quem escreve | Estado |
|---|---|---|---|
| 1.0 | Documento de banco, revisado | — | **feito** |
| 1.1 | **ADR-031** — unicidade por tentativa e por revisão pendente | Claude | **feito** |
| 1.2 | Migração `V1` — as 5 tabelas + `parametro` | Claude | **feito** |
| 1.3 | Migração `V1` — **as 9 restrições + J-1** | **você** | **feito** |
| 1.4 | Função `unaccent_imutavel` (exige J-1) | Claude | **feito** |
| 1.5 | Testes de invariante — um por restrição | **você** | **feito** |
| 1.6 | Teste estrutural de ausência (D-16, D-28) | Claude, você revisa | **feito** |
| 1.7 | Docker + Postgres de pé, migração aplicando | Claude | **feito** |

**A ordem importa em dois pontos:** 1.4 antes de 1.3 (a restrição J-1 usa a
função), e 1.2 antes de 1.3 (não há onde pendurar restrição sem tabela). O resto
é sequência natural.

### Definition of Done

A sprint fecha quando **todos** passarem:

- [x] `docker compose up` sobe Postgres e a migração aplica sem erro
- [x] As 9 restrições existem, com o identificador da regra no nome
- [x] Cada uma tem teste que prova que a escrita inválida **é recusada pela
      restrição certa**, conferindo o nome — não só que falhou
- [x] O teste estrutural falha se alguém criar coluna de fase em `assunto` ou
      tabela de turno
- [x] `parametro` existe e está **vazia** — as chaves nascem com as sprints que
      as consomem
- [x] ADR-031 escrita e revisada
- [x] Nenhuma entidade JPA, repositório, service, controller ou endpoint existe

O último item é o mais fácil de violar sem perceber.

---

## 3. Sprint 2 · Cadastro e importação

**Documento técnico:** `docs/SPRINT-2-CADASTRO.md` v1.0.0 — rascunho, escrito
por Claude após três decisões de escopo discutidas com o usuário (só API
nesta sprint; importação em dois passos sem estado de servidor; arquivar
existe com D-17 documentada como pendência da Sprint 4). Aguardando revisão.

**Proposta de itens** (ainda não confirmada — ordem de dependência, não de
prioridade):

| | Item | Quem escreve | Estado |
|---|---|---|---|
| 2.0 | Documento técnico, revisado | — | **feito** |
| 2.1 | Entidades JPA `Disciplina`/`Assunto` | **você** | **feito** |
| 2.2 | Repositórios Spring Data | **você** | **feito** |
| 2.3 | `DisciplinaService`/`AssuntoService` — CRUD, arquivar, tradução de exceção por nome de restrição (§3.1) | Claude fez o molde de um erro, usuário replicou para os outros dois; resto do CRUD escrito por Claude a pedido do usuário (pressa) | **feito** |
| 2.4 | Controllers — endpoints de disciplina/assunto (§4, sem importação) | Claude, a pedido do usuário (pressa) | **feito** |
| 2.5 | Importação CSV — `validar` | Claude, a pedido do usuário (pressa) — sprint previa "você revisa" | **feito** |
| 2.6 | Importação CSV — `confirmar`, com a revalidação de §5.2 | Claude, a pedido do usuário (pressa) — sprint previa "você, seguindo o molde de 2.5" | **feito** |
| 2.7 | Exportação CSV | Claude, a pedido do usuário (pressa) | **feito** |
| 2.8 | Testes — erros de domínio (status + `codigo`), tudo-ou-nada da importação, exportação exclui arquivado | Claude, a pedido do usuário (pressa) — sprint previa "você" | **feito** |
| 2.9 | Teste estrutural D-16, caminho de código (§8, molde `EstruturaSchemaTest`) | Claude, a pedido do usuário (pressa) — sprint previa "você revisa" | **feito** |

### Definition of Done

- [x] `docs/SPRINT-2-CADASTRO.md` revisado e aceito
- [x] CRUD de disciplina e assunto funcionando, com arquivar
- [x] Importação CSV: tudo-ou-nada, resumo antes de gravar, nunca apaga
      (`02_JORNADAS` §J-1, regras 1–3)
- [x] `validar` nunca grava; `confirmar` revalida antes de gravar (§5.2)
- [x] Exportação no mesmo formato de entrada, com `id`, sem assunto arquivado
- [x] Cada erro de domínio da tabela de §3.1 tem teste que confere status
      HTTP **e** `codigo`
- [x] D-16 tem teste também no caminho de código (nenhum atributo mapeado
      fora da lista fechada de `Assunto`)
- [x] D-17 continua **não** implementada, e isso está registrado em
      `docs/SPRINT-2-CADASTRO.md` §7, não escondido
- [x] Nenhuma entidade/service/endpoint de `Sessão`, `Revisão` ou `Erro` existe

Todos os itens da Definition of Done batem. Falta só a revisão e o commit
final do usuário — mesma ressalva que fechou a Sprint 1 (item 1.7, changelog
1.7.0).

---

## 4. Sprint 3 · Registrar sessão

**Documento técnico:** `docs/SPRINT-3-SESSAO.md` v1.0.0 — escrito por Claude
após uma decisão de escopo discutida com o usuário: sessão registrada nesta
sprint não toca em `Revisao` (pendência documentada até a Sprint 4, mesmo
tratamento que D-17 recebeu na Sprint 2); lote mínimo (§4.3 de `01_DOMINIO`)
também sem efeito até a escada existir.

| | Item | Quem escreve | Estado |
|---|---|---|---|
| 3.0 | Documento técnico | — | **feito** |
| 3.1 | Migração `V4` — chaves de `parametro` desta sprint (§2 do doc técnico) | Claude, a pedido do usuário (pressa) | **feito** |
| 3.2 | Entidade JPA `Sessao` + enums `TipoSessao`/`ResultadoSessao`/`FormatoBanca` | Claude, a pedido do usuário (pressa) | **feito** |
| 3.3 | Repositórios (`SessaoRepository`, `ParametroRepository`) | Claude, a pedido do usuário (pressa) | **feito** |
| 3.4 | `SessaoService.registrar` — cálculo de resultado, tradução de exceção, D-45 como sucesso silencioso (§3 do doc técnico) | Claude, a pedido do usuário (pressa) | **feito** |
| 3.5 | Controller — endpoints de §4 do doc técnico | Claude, a pedido do usuário (pressa) | **feito** |
| 3.6 | Testes (§6 do doc técnico) | Claude, a pedido do usuário (pressa) | **feito** |

### Definition of Done

- [x] `docs/SPRINT-3-SESSAO.md` revisado e aceito
- [x] `POST /api/sessoes` grava `Sessao` para os 4 tipos, cada um com as
      colunas certas preenchidas/nulas (D-02, D-04a, D-36)
- [x] `QUESTOES`/`FLASHCARDS`: resultado calculado pelo serviço a partir de
      `parametro`, ignorando o que o cliente mandar nesse campo
- [x] `RECUPERACAO`: resultado é o que o cliente declarou
- [x] D-45: reenviar o mesmo `tentativaId` devolve sucesso com o mesmo `id`,
      nunca erro, e grava só uma vez
- [x] Cada erro de domínio da tabela de §3.1 do doc técnico tem teste que
      confere status HTTP **e** `codigo`
- [x] Nenhuma linha em `revisao` é criada ou atualizada por esta sprint
- [x] Nenhuma entidade/service/endpoint de `Erro`, `Simulado` ou da escada
      (roteamento por resultado, agendamento) existe

Todos os itens da Definition of Done batem. Falta só a revisão e o commit
final do usuário — mesma ressalva das Sprints 1 e 2.

---

## 5. Sprint 4 · Escada e revisão

**Documento técnico:** `docs/SPRINT-4-ESCADA.md` v1.0.0 — escrito por Claude
após duas decisões de escopo discutidas com o usuário antes de implementar: a
progressão de intervalos da escada (`01_DOMINIO` não lista os 6 valores
numericamente, só ancora nível 1=1 e nível 6=90) e o desenho completo do
roteamento (D-07/D-10/D-11/D-17).

| | Item | Quem escreve | Estado |
|---|---|---|---|
| 4.0 | Documento técnico | — | **feito** |
| 4.1 | Migração `V5` — coluna `versao` em `revisao` (ADR-032) + parâmetros da escada (§1 do doc técnico) | Claude, a pedido do usuário (pressa) | **feito** |
| 4.2 | `ADR-032` escrita (estava "pendente de redação" desde a Sprint 1) | Claude, a pedido do usuário (pressa) | **feito** |
| 4.3 | Entidade JPA `Revisao` + enum `SituacaoRevisao` | Claude, a pedido do usuário (pressa) | **feito** |
| 4.4 | `RevisaoService` — agendamento, cumprimento, roteamento D-07/D-10/D-11, janela de tolerância (§3 do doc técnico) | Claude, a pedido do usuário (pressa) | **feito** |
| 4.5 | D-17 — `AssuntoService`/`DisciplinaService.arquivar` cancelam revisão pendente (§3.4 do doc técnico) | Claude, a pedido do usuário (pressa) | **feito** |
| 4.6 | Controller (só leitura) + testes (§4/§5 do doc técnico) | Claude, a pedido do usuário (pressa) | **feito** |

### Definition of Done

- [x] `docs/SPRINT-4-ESCADA.md` revisado e aceito
- [x] `ADR-032` escrita e vigente
- [x] `ESTUDO` agenda a primeira pendente (nível 1); `ESTUDO` de novo com
      pendente aberta não duplica
- [x] `SUCESSO` sobe nível e agenda a próxima com o intervalo certo; `PARCIAL`
      repete; `FALHA` regride (piso 1) — D-07
- [x] Recuperação espontânea (sem pendente) roteia a partir do nível 1
      implícito, sem `sessaoCumpriu`
- [x] Fora da janela de tolerância: sessão grava, revisão pendente não muda
- [x] Consolidação (D-10): nível-alvo + dois `SUCESSO` seguidos **no
      nível-alvo** agenda no intervalo de manutenção
- [x] `FALHA` consolidado (D-11) volta ao nível-alvo, não abaixo
- [x] D-17: arquivar assunto/disciplina cancela a(s) revisão(ões) pendente(s)
- [x] Nenhuma entidade/service/endpoint de `Erro`, `Simulado` ou frente de
      estudo (Sprint 5) existe

Todos os itens da Definition of Done batem. Falta só a revisão e o commit
final do usuário — mesma ressalva das sprints anteriores.

---

## 6. Sprint 5 · Frente de estudo

**Documento técnico:** `docs/SPRINT-5-FRENTE.md` v1.0.0 — escrito por Claude
após fechar com o usuário dois parâmetros que `01_DOMINIO.md` §12 listava
como questão em aberto (teto diário de recuperações, limiar de
represamento). É também a sprint que escreve `ADR-033` ("pendente de
redação" desde a especificação original) — a decisão final ficou mais simples
que a prevista: nem visão de banco entrou, só consulta Spring Data + Java.

| | Item | Quem escreve | Estado |
|---|---|---|---|
| 5.0 | Documento técnico | — | **feito** |
| 5.1 | Migração `V6` — parâmetros de teto (§0 do doc técnico) | Claude, a pedido do usuário (pressa) | **feito** |
| 5.2 | `ADR-033` escrita (estava "pendente de redação" desde o início da especificação) | Claude, a pedido do usuário (pressa) | **feito** |
| 5.3 | `Clock` bean — primeiro "hoje" do sistema (§1 do doc técnico) | Claude, a pedido do usuário (pressa) | **feito** |
| 5.4 | `FrenteService` — fase derivada, resumo, próxima vaga (§2 do doc técnico) | Claude, a pedido do usuário (pressa) | **feito** |
| 5.5 | Controller (só leitura) + testes (§3/§4 do doc técnico) | Claude, a pedido do usuário (pressa) | **feito** |

### Definition of Done

- [x] `docs/SPRINT-5-FRENTE.md` revisado e aceito
- [x] `ADR-033` escrita e vigente
- [x] Nenhuma coluna, tabela ou entidade nova — fase/frente/backlog/consolidado
      são 100% derivados (D-16, D-24)
- [x] Assunto sem sessão → `BACKLOG`; com sessão e não consolidado → `FRENTE`;
      consolidado → `CONSOLIDADO`
- [x] Disciplina inativa → assuntos dela contam como `BACKLOG` mesmo com
      histórico (D-24)
- [x] Resumo da frente: contagens corretas, represamento só dispara acima do
      teto diário
- [x] Próxima vaga: sugere o de menor ordem do backlog da disciplina (D-41),
      respeitando o teto por disciplina; nunca bloqueia (§6.7 regra 6)
- [x] Nenhum endpoint desta sprint grava nada

Todos os itens da Definition of Done batem. Falta só a revisão e o commit
final do usuário — mesma ressalva das sprints anteriores.

---

## 7. Sprint 6 · Plano de turno

**Documento técnico:** `docs/SPRINT-6-TURNO.md` v1.0.0 — escrito por Claude
após confirmar o desenho com o usuário: sem endpoint dedicado para "puxar
mais" (emerge de recalcular o plano depois de registrar sessão) e sem regra
de "1 bloco em dia de semana, 2 no fim de semana" (`01_DOMINIO` §7.3 é
ilustração de orçamento de tempo, não regra de domínio).

| | Item | Quem escreve | Estado |
|---|---|---|---|
| 6.0 | Documento técnico | — | **feito** |
| 6.1 | `FrenteService.proximaSugestaoDeConteudo` — primeira disciplina ativa com vaga e backlog (§1 do doc técnico) | Claude, a pedido do usuário (pressa) | **feito** |
| 6.2 | `RevisaoRepository` — fila de vencidas por atraso (§2 do doc técnico) | Claude, a pedido do usuário (pressa) | **feito** |
| 6.3 | `TurnoService.plano` — fila + bloco de conteúdo (§2 do doc técnico) | Claude, a pedido do usuário (pressa) | **feito** |
| 6.4 | Controller (só leitura) + testes (§3/§4 do doc técnico) | Claude, a pedido do usuário (pressa) | **feito** |

### Definition of Done

- [x] `docs/SPRINT-6-TURNO.md` revisado e aceito
- [x] Nenhuma entidade, coluna ou estado novo — plano é derivado e efêmero
      (`01_DOMINIO` §7.4)
- [x] Fila de recuperação: mais atrasada primeiro, cortada no teto diário
- [x] Sem revisão vencida → fila vazia
- [x] Bloco de conteúdo sugerido só havendo vaga (frente global **e**
      disciplina); vazio quando a frente está no teto global
- [x] "Puxar mais" funciona registrando sessão e chamando o plano de novo —
      sem endpoint dedicado
- [x] Nenhum endpoint desta sprint grava nada

Todos os itens da Definition of Done batem. Falta só a revisão e o commit
final do usuário — mesma ressalva das sprints anteriores.

---

## 8. Como este arquivo se mantém honesto

1. **Item só vira "feito" quando o teste dele passa** — não quando o arquivo
   existe.
2. **Nada de sprint futura**, nem código nem documento. Se aparecer, é violação
   de escopo, não adiantamento.
3. **Sprint futura não ganha detalhe** antes de começar. O documento técnico
   dela nasce contra a especificação vigente **naquele momento**, não contra a
   de hoje.
4. Ao concluir qualquer item, **atualize a tabela da sprint corrente na mesma
   sessão**. Progresso lembrado é progresso perdido.

---

## 9. Changelog

| Versão | Data | Mudança |
|---|---|---|
| 1.14.0 | 2026-08-30 | **Sprint 6 completa** — itens 6.1–6.4 feitos, 54/54 testes verdes (50 herdados + 4 novos de `TurnoServiceTest`). `Parametro` ganhou `@Setter` em `valor` (só nesse campo) — precisava pra um teste baixar o teto global sem criar 100 assuntos de verdade. Verificado manualmente contra Postgres real: plano vazio sem nada cadastrado, fila com item vencido, bloco de conteúdo sugerido, e o "puxar mais" emergente confirmado na prática — registrar a sessão do assunto sugerido e pedir o plano de novo já tira ele da sugestão, sem endpoint nenhum dedicado a isso. Nenhum bug de código nesta sprint. Um teste inicialmente frágil: `blocoDeConteudoSugeridoQuandoHaVaga` assumia que o assunto do próprio teste seria o único candidato de `FrenteService.proximaSugestaoDeConteudo()`, e quebrava rodando a suíte inteira — outros testes não-transacionais (`ImportacaoAssuntoTest`, por gravarem de propósito para provar commit real) deixam disciplina/assunto de verdade no banco pelo resto da execução, e um deles virou candidato antes do meu. Corrigido comparando contra o resultado ao vivo de `FrenteService.proximaSugestaoDeConteudo()` em vez de um id fixo — o que importa testar ali é a fiação `TurnoService` → `FrenteService`, não qual candidato específico vence (isso já é determinístico em `FrenteServiceTest`) |
| 1.13.0 | 2026-08-30 | **Sprint 5 completa** — itens 5.1–5.5 feitos, 50/50 testes verdes (42 herdados + 8 novos de `FrenteServiceTest`). `ADR-033` escrita. Migração `V6` com os 3 parâmetros de teto. Primeiro `Clock` bean do sistema — represamento é o primeiro cálculo que depende de "hoje"; teste usa `Clock` fixo (`03_INVARIANTES` §10, data congelada). Verificado manualmente contra Postgres real: fase de assunto (backlog/frente/consolidado), D-24 (disciplina inativa vira backlog mesmo com histórico), resumo da frente, próxima vaga por ordem. Nenhum bug de código encontrado nesta sprint — só um erro de configuração de teste (`BeanDefinitionOverrideException`: dois `@Bean Clock` com o mesmo nome `clock()`, mesmo um deles `@Primary`, colidem por nome antes do Spring sequer chegar a resolver por tipo; corrigido renomeando o bean de teste para `clockFixo()`). Achado no caminho, fora do escopo desta sprint: `01_DOMINIO.md` §12 já citava valores prévios para os intervalos da escada (`1,7,15,30,60,90`, Sprint 4 decidiu `1,3,7,15,30,90`) que a pesquisa da Sprint 4 não tinha visto — usuário confirmou manter o que já estava implementado; `01_DOMINIO.md` corrigido (v1.11.2) fechando as três questões em aberto (intervalos, teto diário, limiar de represamento) antes desta sprint prosseguir |
| 1.12.0 | 2026-08-30 | **Sprint 4 completa** — itens 4.1–4.6 feitos, 42/42 testes verdes (32 herdados + 10 novos de `RevisaoEscadaTest`). `ADR-032` escrita (saiu de "pendente de redação"). Migração `V5` com a coluna `versao` de `revisao` e os 8 parâmetros da escada (6 intervalos de nível + manutenção + janela de tolerância). Verificado manualmente contra Postgres real, escada inteira de ponta a ponta: `ESTUDO` agenda, `SUCESSO`/`PARCIAL`/`FALHA` roteiam certo, primeira chegada no nível-alvo não consolida sozinha, dois `SUCESSO` seguidos *no nível-alvo* consolidam (intervalo de manutenção, +150 dias), D-11 (falha consolidado volta ao nível-alvo, não abaixo), janela de tolerância (1 dia fora recusa, dentro cumpre), D-17 (arquivar assunto/disciplina cancela pendente). Achado antes de rodar qualquer código, só de traçar a mão o cenário de consolidação: a checagem de "dois últimos sucesso" não filtrava por nível — comparar contra o resultado do degrau anterior, não contra outra tentativa no próprio nível-alvo, teria consolidado um nível cedo demais. Três defeitos reais encontrados rodando contra Postgres, todos na mesma família do bug de D-45 da Sprint 3 (sessão do Hibernate inutilizável depois de um flush que falha) e do bug do item 2.3 (`save()` não força escrita em entidade gerenciada): (1) `agendarPrimeira` capturava a violação de D-05 e continuava na mesma transação que também grava a `Sessao` — diferente do D-45, aqui não dá pra isolar numa transação nova (a `Revisao` referencia a `Sessao` ainda não commitada), corrigido com checagem prévia só neste ponto, exceção documentada no doc técnico §3.1; (2) `processarRecuperacao` usava `save()` em vez de `saveAndFlush()` pra marcar a pendente `CUMPRIDA`, então o `INSERT` da próxima pendente rodava antes do `UPDATE` ir pro banco e colidia com a própria linha que estava sendo liberada; (3) os `@Modifying` de cancelamento (D-17) não tinham `clearAutomatically`/`flushAutomatically` — um teste automatizado (não o manual, que usa uma transação por requisição HTTP) pegou isso: `findById` depois de arquivar devolvia a `Revisao` ainda `PENDENTE`, cache de primeiro nível do Hibernate não invalidado por um update em massa; faltar `flushAutomatically` também teria descartado o `setAtivo(false)` pendente do próprio `arquivar` |
| 1.11.0 | 2026-08-30 | **Sprint 3 completa** — itens 3.1–3.6 feitos, 32/32 testes verdes (22 herdados + 10 novos: 6 de erro de domínio, 3 de cálculo de resultado + FLASHCARDS/RECUPERACAO, 1 de D-45). Migração `V4__parametros_sessao.sql` com os 6 limiares de §4.2.1 (`lote_minimo_questoes` deliberadamente fora — sem efeito até a escada existir). Verificado manualmente contra Postgres real, mesmo esquema de container descartável das sprints anteriores: os 4 tipos de sessão, cálculo de resultado nos dois formatos de banca e em FLASHCARDS, D-45 (reenvio devolve os mesmos dados, `tempoMinutos` diferente do reenvio é ignorado — prova que voltou o registro original, não gravou de novo), e os 6 erros de domínio. Dois defeitos reais encontrados e corrigidos no caminho: (1) D-45 devolvia **500**, não sucesso — depois que `save()` falha por violação de restrição, a sessão do Hibernate fica inutilizável para qualquer operação seguinte (`AssertionFailure: has a null identifier`); tentar `findByTentativaId` na mesma transação quebrava. Corrigido separando `gravar`/`buscarPorTentativa` em métodos `@Transactional` distintos, chamados via `self` (injeção `@Lazy` do próprio bean) — sem isso `this.gravar(...)` pula o proxy do Spring e os dois `@Transactional` não valem nada (autoinvocação, um dos "suspeitos de sempre" da mentoria, `.claude/agents/mentor.md`). (2) `ESTUDO` com `resultado` no corpo era silenciosamente ignorado em vez de recusado — `ESTUDO_SEM_RESULTADO` documentado em `docs/SPRINT-3-SESSAO.md` §3.1 nunca disparava. Corrigido com validação eager antes de tentar gravar. Achado durante a implementação: duas validações (`QUESTOES_OBRIGATORIAS`, `RESULTADO_OBRIGATORIO`) não têm restrição de banco correspondente — `ck_sessao_questoes_por_tipo` só exige os campos **nulos** fora de QUESTOES/FLASHCARDS, nunca exige presença dentro; registradas no doc técnico como validação eager, não tradução de exceção |
| 1.10.0 | 2026-08-30 | Sprint 3 aberta. `docs/SPRINT-3-SESSAO.md` v1.0.0 escrito por Claude, depois de uma decisão de escopo discutida com o usuário: sessão registrada nesta sprint não toca em `Revisao` (pendência documentada até a Sprint 4, mesmo tratamento que D-17 recebeu na Sprint 2) — a alternativa (antecipar o vínculo básico com uma revisão pendente) foi recusada para não antecipar fatia da Sprint 4. `PROGRESSO.md` §4 criado com 6 itens (3.0–3.6) e Definition of Done |
| 1.9.8 | 2026-08-30 | Itens 2.8–2.9 (testes automatizados) escritos, todos verdes: 19/19 testes (9 herdados da Sprint 1 + 10 novos). `AssuntoEstruturaTest` (D-16, caminho de código — item 2.9): reflexão pura sobre `Assunto.class.getDeclaredFields()` contra lista branca fechada, sem banco. `IntegracaoTestBase` nova (`shared`), generaliza `RestricaoTestBase` da Sprint 1 para `@SpringBootTest` + `@AutoConfigureMockMvc` real (docs/SPRINT-2-CADASTRO.md §8): `AssuntoErroDominioTest` (um teste por erro de §3.1, confere status HTTP e `codigo`), `ImportacaoAssuntoTest` (validar não grava, confirmar com linha recusada não grava nada do arquivo, confirmar sem erro grava de verdade), `ExportacaoAssuntoTest` (arquivado não aparece). Duas descobertas de infraestrutura no caminho, nenhuma delas escondida: (1) Spring Boot 4.1 tirou `@AutoConfigureMockMvc` de `spring-boot-test-autoconfigure` (que ficou só com jdbc/json) — precisa do novo `spring-boot-starter-webmvc-test`, pacote da anotação também mudou para `org.springframework.boot.webmvc.test.autoconfigure`; (2) `@Container` do Testcontainers reinicia o contêiner a cada classe de teste — quebrava o padrão de contêiner único entre classes que `RestricaoTestBase` já usava (sem `@Container`, só `.start()` manual, Ryuk limpa no fim da JVM); `IntegracaoTestBase` corrigido para o mesmo padrão. Também tirado `@Transactional` da base: um teste que precisa provar commit/rollback real (a suíte de importação) não consegue enxergar isso rodando dentro da própria transação do teste — cada subclasse decide se quer o `@Transactional` de limpeza automática (`AssuntoErroDominioTest`, que só confere HTTP/JSON) ou não (`ImportacaoAssuntoTest`/`ExportacaoAssuntoTest`, que precisam do commit de verdade; usam nome de fixture único por teste em vez disso). **Definition of Done da Sprint 2 completa** — falta só revisão e commit final do usuário (mesma ressalva do fechamento da Sprint 1, changelog 1.7.0) |
| 1.9.7 | 2026-08-30 | Itens 2.5–2.7 (importação/exportação CSV) **código escrito e verificado manualmente**, ainda sem teste automatizado (2.8). Adicionada dependência `commons-csv` (1.12.0) ao `pom.xml` — decisão confirmada com o usuário antes de mexer no build. `ImportacaoAssuntoService.validar`/`confirmar` reaproveitam `DisciplinaService`/`AssuntoService` em vez de duplicar criação/tradução de erro; `validar` sempre força rollback (`TransactionAspectSupport...setRollbackOnly()`), `confirmar` só grava se `recusadas` vier vazio — mesmo se o motivo não for "id inexistente" (regra 1, tudo ou nada, `SPRINT-2-CADASTRO.md` §5.1). Duas lacunas reais entre `02_JORNADAS.md` (CSV é por linha de assunto) e o schema (`Disciplina.peso` e `Assunto.dificuldadePercebida` são `NOT NULL`) corrigidas na especificação primeiro (`02_JORNADAS.md` v1.2.2, defaults `MEDIO`/`3`) — decisão confirmada com o usuário, não inventada em silêncio. Testado manualmente contra Postgres real (container descartável, mesmo esquema das rodadas anteriores): `validar` resolve e não grava; `confirmar` grava; atualização por `id` conhecido; arquivo com `peso` inválido recusa **tudo**, nada persiste; arquivo com `id` inexistente aborta na hora com uma só linha recusada; duas linhas duplicadas (só caixa difere) no mesmo arquivo — a primeira "grava" dentro da transação, a segunda bate na constraint, e o rollback desfaz as duas; assunto arquivado sai da exportação. `mvn compile` limpo. Faltam: 2.8 (testes automatizados), 2.9 (D-16 caminho de código) |
| 1.9.6 | 2026-08-30 | CRUD de disciplina/assunto (2.3+2.4) **verificado manualmente** contra Postgres real (container `postgres:17-alpine` descartável, só para este teste, não o `docker-compose.yml` de produção — esse não muda, tem restrição de portas de propósito). `curl` cobriu: criar disciplina (201 + `Location` + `X-Request-Id`), criar assunto (201, `disciplinaId` correto via proxy LAZY sem query extra), os três erros de domínio de §3.1 com status e `codigo` certos (`NOME_DUPLICADO` 409 inclusive testando só acento/caixa diferente — confirma `unaccent_imutavel` funcionando pelo caminho JPA, não só pelo `INSERT` cru da Sprint 1; `DISCIPLINA_INEXISTENTE` 404; `ORDEM_OBRIGATORIA` 422 com `campo`), `PATCH` parcial, `arquivar` (sem `save()` explícito — dirty checking do Hibernate flushou sozinho no commit da transação) e o efeito dele na listagem (assunto arquivado some de `GET /api/assuntos?disciplinaId=`), `arquivar` de disciplina inexistente devolvendo 404. Todos os `ProblemDetail` bateram o formato de ADR-026, com `requestId` igual entre header e corpo. Ambiente de teste desmontado ao final (container e processo `mvn spring-boot:run` derrubados). Isto satisfaz o critério "CRUD de disciplina e assunto funcionando, com arquivar" da Definition of Done — marcado abaixo. Os itens 2.3/2.4 continuam "em andamento", não "feito": este arquivo (§4.1) só promove um item quando ele tem teste automatizado, e isso é o item 2.8 |
| 1.9.5 | 2026-08-30 | Item 2.4 **código escrito, ainda sem teste**: `DisciplinaController`/`AssuntoController` (todos os endpoints não-importação de §4), `DisciplinaResponse`/`AssuntoResponse` (records) e `DisciplinaMapper`/`AssuntoMapper` (entidade nunca sai do Service, `09_CODE_STYLE` checklist). `mvn compile` limpo. Escrito por Claude a pedido do usuário, mesma nota de transparência do item 2.3. Não testado em runtime: Docker Desktop não está rodando nesta máquina, sem Postgres para validar `curl`/Postman de verdade — só compilação foi verificada. `AssuntoMapper` lê `assunto.getDisciplina().getId()` sem `join fetch`: seguro porque o identificador de uma associação `LAZY` não inicializa o proxy do Hibernate (otimização específica para o getter de `@Id`), então não é violação do "suspeito de sempre" de LAZY sem fetch — só dispararia se algum campo além do id da disciplina fosse lido. Faltam: 2.5–2.7 (importação/exportação CSV), 2.8–2.9 (testes) |
| 1.9.4 | 2026-08-30 | Item 2.3 **código escrito, ainda sem teste** (não pode virar "feito" até então — regra §4.1 deste arquivo). `shared.exception` criado: `DominioException` (base), `NotFoundException`/`ConflictException`/`ValidationException`/`BusinessRuleException`, `GlobalExceptionHandler` (ADR-026, um `@RestControllerAdvice` só); `shared.web.RequestIdFilter` (`03C_LOGGING.md` §2, `OncePerRequestFilter` de maior precedência). `AssuntoRequest`/`DisciplinaRequest` (records, §4) criados agora porque o service precisava do tipo de parâmetro — antecipam só o *nome* que §4 já definia, não o endpoint. Molde de `traduzirViolacaoDeIntegridade` (NOME_DUPLICADO) escrito por Claude; usuário replicou os outros dois casos da tabela (DISCIPLINA_INEXISTENTE, ORDEM_OBRIGATORIA) em duas rodadas de revisão — primeira usou `ConflictException` nos três casos (status HTTP errado para 2 deles), corrigido para `NotFoundException`/`ValidationException`. Resto do CRUD (`buscar`, `listar`, `atualizar`, `arquivar` dos dois services) escrito por Claude, a pedido explícito do usuário para acelerar — não seguiu o "você escreve" da tabela acima, registrado aqui por transparência. Achado durante a escrita: `atualizar` usa `saveAndFlush`, não `save` — a entidade já é gerenciada (veio de `buscar` na mesma transação), então `save()` sozinho não força escrita a tempo do `catch` capturar a violação. Também usado código `ASSUNTO_INEXISTENTE`, não coberto pela tabela de erros de §3.1 do documento técnico (que só cobre tradução de restrição em escrita) — extensão razoável para 404 de leitura/atualização por id, não uma regra nova. `mvn compile` limpo. Faltam: 2.4 (controllers), 2.5–2.9 (importação, exportação, testes) |
| 1.9.3 | 2026-08-30 | Item 2.2 **concluído**. `DisciplinaRepository`/`AssuntoRepository` implementados batendo exatamente com `docs/SPRINT-2-CADASTRO.md` §2. Mentoria encontrou dois defeitos na primeira versão: os três métodos devolviam `Optional<List<...>>` em vez de `List<...>` (coleção não deve vir embrulhada em `Optional` — já representa "nada encontrado" com lista vazia) e, ao corrigir isso, `findByDisciplinaIdAndNomeIgnoreCase` perdeu o `Optional` que deveria manter (é o único método de resultado único, usado no resumo da importação de §5, não como pré-checagem antes de gravar). Ambos corrigidos. `mvn compile` validado. Próximo: item 2.3 (services) |
| 1.9.2 | 2026-08-29 | Item 2.1 **concluído**. Última pendência: `Disciplina` tinha `@OneToMany(mappedBy = "disciplina") Set<Assunto> assuntos`, não previsto em `docs/SPRINT-2-CADASTRO.md` §1.1. Removido — cada entidade tem ciclo de vida próprio (repositório, endpoints e arquivamento independentes; `Assunto` não é filho de agregado de `Disciplina`), então FK basta; navegação implícita de coleção só se paga quando o filho não existe fora do pai (não é o caso). Coerente com a regra já vigente em §1.2 para o sentido inverso: `Assunto.disciplina` é `@ManyToOne(fetch = LAZY)` com busca sempre explícita (`join fetch`), nunca navegação implícita. Consulta "assuntos de uma disciplina" já está coberta por `AssuntoRepository.findByDisciplinaIdAndAtivoTrue` (§2). Próximo: item 2.2 (repositórios) |
| 1.9.1 | 2026-08-28 | Mentoria do item 2.1 (entidades JPA) encontrou duas divergências, corrigidas em `docs/SPRINT-2-CADASTRO.md` v1.1.0: `criadoEm`/`atualizadoEm` descritos como preenchidos pelo banco, mas nenhuma migração tem trigger de `atualizado_em` — corrigido para "gerados pelo Hibernate", decisão apoiada em ADR-016 (monolito, um único escritor, robustez de trigger não se paga); `dificuldadePercebida` documentado como `int`, mas a coluna é `SMALLINT` — corrigido para `Short`, evitando divergência de tipo JDBC sob `ddl-auto=validate`. Item 2.1 segue em andamento (entidades escritas e revisadas, faltam os ajustes: imports sem wildcard, `nullable=false` restaurado em `nome`/`peso`/`dificuldadePercebida`) |
| 1.9.0 | 2026-08-28 | Item 2.0 **concluído** — usuário revisou e aceitou `docs/SPRINT-2-CADASTRO.md` v1.0.0. Revisão encontrou uma contradição entre documentos: `docs/00A_ADR.md` (ADR-011) descrevia o mecanismo de exclusão lógica do Assunto como `status = 'ARQUIVADO'`, resíduo da v1 anterior ao reset; o schema real (`V1__tabelas.sql`) e o próprio `SPRINT-2-CADASTRO.md` usam `ativo BOOLEAN`, igual Disciplina. Corrigido no documento (ADR-011 → v2.1.1), não no código — regra do `CLAUDE.md` de resolver contradição na fonte antes de programar. Definition of Done da Sprint 2 com o primeiro item marcado. Próximo: item 2.1 (entidades JPA), em sessão de mentoria |
| 1.8.0 | 2026-08-27 | Sprint 2 aberta. `docs/SPRINT-2-CADASTRO.md` v1.0.0 escrito por Claude, depois de três decisões de escopo discutidas com o usuário: só API nesta sprint (a tela "Assuntos" também serve J-3, que depende da Sprint 5); importação em dois passos (`validar`/`confirmar`) sem estado de servidor, com `confirmar` revalidando do zero em vez de confiar no resumo de `validar` (mesma família de risco que D-05, sem restrição de banco que cubra); arquivar existe já nesta sprint, D-17 (cancelar revisões pendentes dependentes) fica pendência documentada até a Sprint 4 ter `RevisaoService`. `PROGRESSO.md` §3 criado com proposta de 10 itens (2.0–2.9) e Definition of Done — ainda não confirmada com o usuário |
| 1.7.0 | 2026-08-27 | Item 1.6 **concluído** — `EstruturaSchemaTest` escrito por Claude (docs/SPRINT-1-BANCO.md §5; `03_INVARIANTES` §4.2). Só o caminho de banco é verificável nesta sprint (sem entidade JPA ainda); o de código fica para a sprint que criar as entidades. Decisão de design: lista branca fechada, não lista negra de nomes proibidos — SPRINT-1-BANCO.md §2.2 registra que um teste por nome não distingue o `ativo` legítimo de um `status` ilegítimo, então o teste fixa o conjunto exato de colunas de `assunto` (D-16) e de tabelas do schema (D-28) via `information_schema`; qualquer adição, bem ou mal nomeada, quebra o teste e exige edição consciente. 2/2 verdes contra Postgres real (Testcontainers). Com isso, a Sprint 1 bate todos os itens da Definition of Done — falta só o commit e a confirmação final do usuário |
| 1.6.3 | 2026-08-27 | Item 1.5 **concluído**. Usuário corrigiu os dois pontos bloqueantes apontados na revisão: `j01_` passou a usar o par que difere só em acento/caixa (`"Raciocínio Lógico"` / `"RACIOCINIO LOGICO"`), provando de fato a normalização de `unaccent_imutavel(lower(nome))`; e a inserção de setup em `d45_`/`j01_` saiu de dentro do `catch`. Decisão consciente do usuário: `d04a_` cobre só o ramo ESTUDO+`previsao_percentual` (não os três ramos do CHECK) — aceito como está, DoD só exige um teste por restrição. Suíte inteira rodada pelo usuário: 9/9 verdes |
| 1.6.2 | 2026-08-27 | Item 1.5 revisado (mentor): os 9 métodos existem e compilam, mas o item **não fecha** ainda. Achados: o teste de J-1 insere o mesmo nome duas vezes em vez do par que difere só em acento/caixa — não prova a regra nem cobre `unaccent_imutavel`; `d45_` e `j01_` têm a inserção de setup dentro do `catch`, mascarando a origem real de uma falha; `d04a_` cobre só 1 dos 3 ramos do CHECK; nenhuma evidência em disco de que a suíte inteira rodou verde. Corrigido aqui apenas o administrativo: citação `v1.1.1` → `v1.1.0` (o arquivo real é v1.1.0) e esta linha, que estava desatualizada dizendo "7 de 9 métodos faltam". O código do teste fica para o usuário corrigir |
| 1.6.1 | 2026-08-21 | Item 1.5 — primeiro dos 8 métodos restantes: `d01_sessaoEstudoIdApontandoIdAssuntoInexistente_recusada`, escrito pelo usuário em sessão de mentoria, testa `fk_sessao_d01_assunto`. Passou com Testcontainers real. Duas rodadas de revisão: faltava `executeUpdate()` (teste não executava nada e passava por omissão), índice de parâmetro `?` repetido (sobrescrevia `assunto_id` em vez de setar `tipo`), e `tentativa_id` com literal incompatível em vez de UUID real. Faltam 7 métodos (D-02, D-04a, D-06, D-36, D-41, D-45, J-1) |
| 1.6.0 | 2026-08-21 | Item 1.7 concluído — `EstudosApplication` (classe de boot vazia) e `application.yml` escritos por Claude (nenhuma entidade/repositório/service/controller, conforme DoD). `docker compose up --build` validado localmente: Postgres sobe saudável, as 3 migrações aplicam sem erro, `/actuator/health` responde `UP`. Validação encontrou bug real em `V2__unaccent_imutavel.sql` (item 1.4): `unaccent_imutavel` funcionava em chamada direta mas falhava dentro de `CREATE INDEX` (J-1, item 1.3) com "text search dictionary unaccent does not exist" — o Postgres roda a checagem de imutabilidade de função usada em índice por expressão com `search_path` restrito a `pg_catalog` (mitigação contra sequestro de função), então `unaccent` e o dicionário `unaccent`, ambos em `public`, ficavam invisíveis nesse contexto. Corrigido qualificando ambos com `public.`. V2 e V3 ainda não tinham sido aplicados com sucesso em lugar nenhum, então a correção foi direto no arquivo, sem `V4`. 1.2, 1.3 e 1.4 promovidos de "rascunho pronto" para "feito" — 1.7 era o teste que faltava para eles |
| 1.5.0 | 2026-08-20 | Item 1.5 — `docs/SPRINT-1-TESTES.md` criado (`10_TESTS.md` era v2, não sobreviveu ao reset). Infra escrita por Claude: `RestricaoTestBase` (Testcontainers + Flyway, sem Spring context) e `RestricoesInvariantesTest` com o molde de D-05 completo. Faltam 8 métodos, TODO(human) no arquivo |
| 1.4.0 | 2026-08-20 | Item 1.3 — `V3__restricoes.sql` escrito pelo usuário (D-02, D-04a, D-06, D-36, D-45, J-1; D-05 foi molde meu). Três rodadas de revisão: erro sistemático de `AND` em vez de `NOT P OR Q` nos quatro `CHECK`s compostos, coluna errada em D-06 (`sessao_origem_id` → `sessao_cumpriu_id`), e uma troca de conteúdo entre os nomes de D-02/D-04a. Todas corrigidas |
| 1.3.0 | 2026-08-20 | Item 1.4 — `V2__unaccent_imutavel.sql` escrito (extensão `unaccent` + wrapper `IMMUTABLE`). Feito antes do item 1.3 porque J-1 depende dele, por ordem explícita do mapa da sprint |
| 1.2.0 | 2026-08-20 | Item 1.2 — `V1__tabelas.sql` escrito (5 tabelas + `parametro`). Por decisão do usuário, D-01 (FK `sessao.assunto_id`) e D-41 (`assunto.ordem`) já entram aqui, nomeados; os outros 7 (D-02, D-04a, D-05, D-06, D-36, D-45, J-1) ficam para o item 1.3. `.claude/settings.json`: `src/main/resources/db/migration/**` movido de `deny` para `ask`, a pedido do usuário, para permitir esta escrita |
| 1.1.0 | 2026-08-20 | Item 1.1 (ADR-031) concluído — escrita por Claude, a pedido do usuário, porque estava além do conhecimento atual dele. Ver `docs/00A_ADR.md` v2.1.0 |
| 1.0.0 | 2026-08-19 | Criado. Mapa das 8 sprints e detalhe da Sprint 1. O mapa faltava desde a reordenação da especificação — os documentos técnicos foram adiados de propósito, a lista de sprints caiu junto por descuido |
