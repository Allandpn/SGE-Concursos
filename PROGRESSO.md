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
| 3 | Registrar sessão | O evento central: os 4 tipos, resultado, previsão, idempotência | não começou |
| 4 | Escada e revisão | Agendamento, cumprimento, roteamento por resultado, janela | não começou |
| 5 | Frente de estudo | Backlog, tetos, vaga por consolidação, alerta de represamento | não começou |
| 6 | Plano de turno | A tela Hoje: fila de recuperação + blocos, e o "puxar mais" | não começou |
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

## 4. Como este arquivo se mantém honesto

1. **Item só vira "feito" quando o teste dele passa** — não quando o arquivo
   existe.
2. **Nada de sprint futura**, nem código nem documento. Se aparecer, é violação
   de escopo, não adiantamento.
3. **Sprint futura não ganha detalhe** antes de começar. O documento técnico
   dela nasce contra a especificação vigente **naquele momento**, não contra a
   de hoje.
4. Ao concluir qualquer item, **atualize a tabela da sprint corrente (§2 ou
   §3) na mesma sessão**. Progresso lembrado é progresso perdido.

---

## 5. Changelog

| Versão | Data | Mudança |
|---|---|---|
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
