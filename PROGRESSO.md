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
| 7 | Métricas e erros | M-1 a M-4 com as regras de `n`, banco de erros como camada explicativa | documento técnico feito, código escrito, sem execução verificada |
| 8 | Simulado e fechamento | Simulado por disciplina, backup testado, polimento | Simulado: doc técnico feito, código escrito, sem execução verificada. Backup: scripts escritos, não testados no Pi real. Polimento: não começou |
| 9 | Frontend — Hoje e Recuperar | SPA estática (Alpine.js/Tailwind, ADR-028/030), as duas primeiras telas | **feito** — testado num navegador real, fluxo completo (Hoje → Recuperar → gravação → escada avança) confirmado contra Postgres real |
| 10 | Integração externa — planejamento | Referência de material por blocos (entidade `Segmento`, `Assunto` ganha `chaveExterna`), alimentada pelo projeto `Plano-de-Estudos-Automatizado` via Google Drive + `rclone` (ADR-037, `docs/requisitos-planejamento-blocos-de-conteudo.md`); `Edital`/import por UUID fica de fora por ora | **feito** — 111/111 testes verdes contra Postgres real, export e automação do disparo incluídos. Restam só duas coisas fora do alcance deste repositório: testar `scripts/importar-segmentos.sh` num Pi real, e a resposta do repositório de planejamento sobre gerar `chaveExternaSegmento` |

> **O sistema fica utilizável ao fim da Sprint 4.** Cadastrar, estudar,
> registrar e revisar já fecham o ciclo. As sprints 5 a 8 melhoram o que já
> funciona — nenhuma delas é pré-requisito para começar a usar. A Sprint 9
> abre a frente de frontend — o mapa original tinha 8, mas "o mapa pode
> mudar" (linha abaixo) sempre incluiu crescer.

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

## 8. Sprint 7 · Métricas e erros

**Documento técnico:** `docs/SPRINT-7-METRICAS.md` v1.2.0 — escrito por
Claude após quatro decisões de escopo discutidas com o usuário (escala de
confiança do erro em três âncoras qualitativas; M-2 reporta `RECUPERACAO`
separada de `QUESTOES`/`FLASHCARDS`, nunca somada; erro não influencia a
ordem da fila nesta sprint — `01_DOMINIO §12` continua em aberto; sem
endpoint de resolver erro). No caminho, uma regra de domínio nova apareceu —
`Erro` não tinha integridade referencial numerada — e foi escrita em
`01_DOMINIO.md` como **D-46** (v1.12.0) antes do documento técnico fechar,
seguindo a regra central do `CLAUDE.md`. Também no caminho: a tabela `erro`
já existia desde a Sprint 1 (`docs/SPRINT-1-BANCO.md` §2.5/§7.6) — a v1.2.0
do documento técnico corrige isso, a migração `V7` vira uma linha (renomear
a restrição), não criação de tabela.

| | Item | Quem escreve | Estado |
|---|---|---|---|
| 7.0 | Documento técnico, revisado | — | **feito** |
| 7.1 | Migração `V7` — renomeia `fk_erro_assunto` (já existia desde V1) para `fk_erro_d46_assunto` (§2 do doc técnico) | Claude, a pedido do usuário (pressa) | **feito** |
| 7.2 | Entidade JPA `Erro` + enums `CausaErro`/`NivelConfianca` | Claude, a pedido do usuário (pressa) | **feito** |
| 7.3 | `ErroRepository` | Claude, a pedido do usuário (pressa) | **feito** |
| 7.4 | `ErroService` — registrar, listar por assunto, tradução de exceção (§3 do doc técnico) | Claude, a pedido do usuário (pressa) | **feito** |
| 7.5 | `MetricaService` — M-1 a M-4 (§4 do doc técnico) | Claude, a pedido do usuário (pressa) | **feito** |
| 7.6 | Controllers — `/api/erros`, `/api/metricas/*` (§5 do doc técnico) | Claude, a pedido do usuário (pressa) | **feito** |
| 7.7 | Testes (§6 do doc técnico) | Claude, a pedido do usuário (pressa) | **feito** — 8/8 verdes (`ErroDominioTest`) + 8/8 (`MetricaServiceTest`) |

### Definition of Done

- [x] `docs/SPRINT-7-METRICAS.md` revisado e aceito
- [x] `D-46` escrita e vigente em `01_DOMINIO.md`
- [x] `POST /api/erros` grava com os 7 valores de causa e as 3 âncoras de
      confiança; `ASSUNTO_INEXISTENTE`/`SESSAO_INEXISTENTE` com status e
      `codigo` certos
- [x] M-1: `n < 10` devolve `—`, nunca `0%`; `n ≥ 100` marca `confiavel`;
      dois formatos de banca no mesmo período nunca somam (D-36)
- [x] M-2: primeira exposição identificada pela sessão mais antiga do
      assunto; `RECUPERACAO` reportada em série própria, nunca somada com
      percentual de questões
- [x] M-3: as duas variantes (objetiva/subjetiva) com o sinal correto,
      nunca somadas
- [x] M-4: janela de 3 dias, distinta da janela de tolerância de D-38
- [x] Nenhum endpoint de métrica grava nada
- [ ] ~~Nenhuma entidade/service/endpoint de `Simulado` existe~~ — linha
      ficou obsoleta: a Sprint 8 (Simulado) abriu antes desta fechar, decisão
      explícita do usuário registrada em §9. Não é falha desta sprint,
      é escopo que cresceu depois dela ser escrita; mantida riscada em vez de
      apagada, por transparência

Todos os itens verificáveis por teste batem — 79→82 testes verdes depois da
correção de 1.21.0 (ver changelog). Falta só a revisão e o commit final do
usuário, mesma ressalva das sprints anteriores.

---

## 9. Sprint 8 · Simulado

**Aberta como exceção consciente** à regra central do `CLAUDE.md` ("não
implemente nada de sprint futura") — a Sprint 7 ainda não tinha testes
executados (§8) quando o usuário pediu para seguir adiante mesmo assim.
Decisão explícita dele, não desvio silencioso.

**Escopo do domínio restrito ao Simulado**, mas o backup entrou também:
`docs/03E_DEPLOYMENT.md` já é doc técnico completo e **congelado** desde
antes desta sprint, com os três scripts inteiros especificados em §6 —
escrevê-los (item 8.8) não precisou de decisão nova nenhuma, só transcrever
o que já estava definido. Testá-los de verdade exige o Raspberry Pi real,
que este ambiente não tem. Polimento continua de fora, sem especificação
própria ainda.

**Documento técnico:** `docs/SPRINT-8-SIMULADO.md` v1.0.0 — escrito por
Claude após quatro decisões de escopo discutidas com o usuário (`formato`
por resultado, para não violar D-36 quando o simulado entrar em M-1;
simulado soma no mesmo agregado de M-1, não em série própria; só Simulado
nesta rodada; `duracaoMinutos` registrado). Nenhuma regra `D-xx` nova — D-13
já cobria a integridade referencial que faltava.

| | Item | Quem escreve | Estado |
|---|---|---|---|
| 8.0 | Documento técnico, revisado | — | **feito** |
| 8.1 | Migração `V8` — tabelas `simulado`/`resultado_simulado` (§1/§2 do doc técnico) | Claude, a pedido do usuário (pressa) | **feito** |
| 8.2 | Entidades JPA `Simulado`/`ResultadoSimulado` | Claude, a pedido do usuário (pressa) | **feito** |
| 8.3 | `SimuladoRepository`/`ResultadoSimuladoRepository` | Claude, a pedido do usuário (pressa) | **feito** |
| 8.4 | `SimuladoService` — tudo-ou-nada, tradução de exceção (§3 do doc técnico) | Claude, a pedido do usuário (pressa) | **feito** |
| 8.5 | `MetricaService.m1` estendido — simulado no mesmo agregado (§4 do doc técnico) | Claude, a pedido do usuário (pressa) | **feito** |
| 8.6 | Controller — `/api/simulados` (§5 do doc técnico) | Claude, a pedido do usuário (pressa) | **feito** |
| 8.7 | Testes (§6 do doc técnico) | Claude, a pedido do usuário (pressa) | **feito** — 9/9 verdes (`SimuladoDominioTest`) |
| 8.8 | `scripts/backup.sh`/`restaurar.sh`/`testar-restauracao.sh` (`docs/03E_DEPLOYMENT.md` §2/§6, doc já congelado — sem decisão nova) | Claude, a pedido do usuário (pressa) | escritos, não testados em Pi real |

### Definition of Done

- [x] `docs/SPRINT-8-SIMULADO.md` revisado e aceito
- [x] `POST /api/simulados` grava tudo-ou-nada; `DISCIPLINA_INEXISTENTE` (404),
      `DISCIPLINA_DUPLICADA_NO_SIMULADO` (409), `RESULTADOS_OBRIGATORIOS` (422)
      com status e `codigo` certos
- [x] `resultado_simulado` não tem coluna `assunto_id` (D-13, teste estrutural
      `EstruturaSchemaTest.d13_resultadoSimuladoSemColunaDeAssunto`)
- [x] M-1 soma simulado e `QUESTOES` do mesmo disciplina+formato no mesmo
      agregado, nunca separado
- [x] Nenhuma revisão, consolidação ou vínculo com assunto nasce de um simulado
- [ ] `backup.sh` roda no Pi e gera arquivo > 10 KB; `testar-restauracao.sh`
      roda numa base descartável com contagens conferidas (checklist de
      `03E_DEPLOYMENT.md` §8) — só o usuário pode fazer isso, exige o Pi real
- [ ] Polimento continua **não começado**, registrado como pendência, não
      escondido

Itens verificáveis por teste automatizado batem. Faltam só: a execução real
dos scripts de backup no Raspberry Pi (exige hardware que este ambiente não
tem) e o polimento, que não começou. Revisão e commit final ficam com o
usuário, mesma ressalva das sprints anteriores.

---

## 10. Sprint 9 · Frontend — Hoje e Recuperar

**Pausada em 2026-09-04**, decisão consciente do usuário: a Sprint 10
(integração externa) muda o formato da API que o frontend consumiria (tela
Assuntos passaria a exibir referência de material por blocos), e construir a
tela antes disso arriscava retrabalho. Nenhum código desta sprint foi escrito
ainda — pausar não descarta nada. Retoma quando o endpoint de leitura de
pedaços de material da Sprint 10 estiver estável.

**Retomada em 2026-09-06.** Conferido antes de retomar: `docs/04_FRONTEND.md
§0` já restringia esta rodada só às telas Hoje e Recuperar — a tela Assuntos
(a única que tocaria em `Segmento`) já estava na lista das "outras cinco,
sprints seguintes". Nem `GET /api/turno/plano` nem `POST /api/sessoes` tipo
`RECUPERACAO` encostam em segmento — só sessão `ESTUDO` referencia um. Ou
seja: a razão original da pausa não bloqueava, na prática, as duas telas que
esta sprint de fato escopava — achado que não muda nada agora, só explica
por que nenhuma delas precisou esperar a Sprint 10. Endpoint de leitura de
segmentos testado manualmente contra Postgres real antes de retomar (§11).

**Primeira sprint de frontend.** Fora do mapa conceitual original de 8
sprints — aberta a pedido do usuário enquanto ele aguardava Docker em casa
para fechar as Sprints 7/8 de backend. Nada de código ainda: só documento e
planejamento, mesma ordem de sempre (documento antes de código, regra
central do `CLAUDE.md`).

**Documento técnico:** `docs/04_FRONTEND.md` v1.0.0 — escrito por Claude
após quatro decisões de escopo discutidas com o usuário:

| Ponto | Decisão | Por quê |
|---|---|---|
| Stack | Mantém Alpine.js + Tailwind, zero build (ADR-028/030) | A exigência de UI otimista de `02_JORNADAS §5.1` é do tipo simples (estado local + reenvio manual na falha); fila offline com reconciliação — o caso que pediria mais — já está fora de escopo por decisão própria da jornada |
| Documentação | `docs/04_FRONTEND.md` primeiro; design visual (cor/tipografia/componente) depois | As primeiras telas servem de referência real quando existirem |
| Primeiras telas | Hoje + Recuperar | Porta de entrada (J-2/J-4) e a tela mais usada, única com sequência vinculante — provam o padrão otimista no caminho mais crítico |
| Rastreamento | No mesmo mapa de `PROGRESSO.md`, como Sprint 9+ | Mesmo padrão de Definition of Done já validado em 8 rodadas |

No caminho: a reconsideração de ADR-028/030 que a nota final de
`docs/00A_ADR.md` pedia ("decida conscientemente antes de escrever a
primeira tela") foi feita — mantidas, nota registrada na própria ADR-028
(`docs/00A_ADR.md` v2.2.0). Os "quatro estados de tela" que
`09_CODE_STYLE §9` citava sem nunca ter definido ficam fechados em
`04_FRONTEND.md §8`.

| | Item | Quem escreve | Estado |
|---|---|---|---|
| 9.0 | Documento técnico, revisado | — | **feito** |
| 9.1 | Estrutura de arquivos estáticos + `index.html` base (§1/§2 do doc técnico) | Claude, a pedido do usuário | **feito** |
| 9.2 | Tela Hoje — consome `GET /api/turno/plano` (§6 do doc técnico) | Claude, a pedido do usuário | **feito** |
| 9.3 | Tela Recuperar — as duas etapas do wireframe de `02_JORNADAS §4.1`, consome `POST /api/sessoes` (§7 do doc técnico) | Claude, a pedido do usuário | **feito** |
| 9.4 | Verificação manual no navegador (Output Style deste projeto exige testar UI de verdade) | Claude | **feito** — ver relato abaixo |

### Definition of Done

- [x] `docs/04_FRONTEND.md` revisado e aceito
- [x] ADR-028/030 reexaminadas conscientemente e nota registrada
- [x] Tela Hoje carrega sem clique extra, mostra fila de recuperação e bloco
      de conteúdo (`02_JORNADAS §5`) — observado instantâneo num Postgres
      local; não medido com cronômetro contra o Pi real
- [x] Tela Recuperar: resultado ausente do DOM na etapa 1 (não escondido);
      previsão não editável na etapa 2 (texto, não campo); sem botão "pular"
- [x] Falha de rede em qualquer registro é não destrutiva: dado digitado
      permanece, reenvio é um clique, mesmo `tentativaId` (D-45) —
      implementado; caminho de falha real não testado (exigiria derrubar a
      rede no meio do clique)
- [~] Os quatro estados de tela tratados: `carregando`/`erro`/`preenchido`
      verificados nas duas telas; `vazio` só implementado (não exercitado
      manualmente — exigiria arquivar tudo antes de testar)
- [x] Nenhuma mudança em `Dockerfile`/`docker-compose.yml` — só arquivos
      novos em `src/main/resources/static/`

**Verificação manual (2026-09-06):** Postgres descartável + `mvn
spring-boot:run` (mesmo padrão de sempre) + Chrome de verdade. Disciplina e
dois assuntos criados via API; uma sessão `ESTUDO` registrada e a revisão
resultante forçada pra atrasada via SQL, pra ter algo na fila. Fluxo
completo percorrido no navegador: Hoje mostrou fila + bloco de conteúdo →
clique no item abriu Recuperar etapa 1 (nome, nível, data prevista, sem
campo de resultado) → "Vou reconstruir" levou à etapa 2 (previsão mostrada,
não editável, as três âncoras certas) → "Reconstruí" navegou de volta pra
Hoje **antes** da resposta confirmar (padrão otimista, §5.1) → conferido no
banco: sessão `RECUPERACAO` gravada com `previsaoReconstrucao`/`resultado`
corretos, `tempoMinutos` medido (não constante, D-29), revisão nível 1
virou `CUMPRIDA` e nível 2 nasceu `PENDENTE` — a escada roteou certo de
ponta a ponta. Ambiente derrubado ao final.

**Uma técnica nova nesta sprint**, fora do que `04_FRONTEND.md` já
especificava: o "handoff" de dados entre Hoje e Recuperar (nível, data
prevista do item clicado) usa um `Alpine.store` global, que
`docs/04_FRONTEND.md §6` deixava como "decisão de código, não de
arquitetura" — fechada agora em `js/pages.js`, comentada no próprio código.

---

## 11. Sprint 10 · Integração externa — planejamento

**Decisão conceitual já fechada** numa sessão anterior — diferente do padrão
das sprints 1 a 9, aqui `01_DOMINIO.md` v1.17.0, `02_JORNADAS.md` v1.4.0 e
`docs/00A_ADR.md` ADR-037 v2.7.0 já chegaram prontos. Esta seção documenta a
tradução para schema/código.

**Documento técnico:** `docs/SPRINT-10-SEGMENTO.md` v1.1.0 — escrito por
Claude, cinco decisões propostas em §0 (endpoint de leitura no escopo,
mecanismo de D-51 via FK composta, "só ESTUDO" como `CHECK` irmão, export de
segmentos e automação do disparo pós-`rclone` fora de escopo). **Revisado e
aceito pelo usuário.**

No caminho: `especificacao/03_INVARIANTES.md` §3 tinha D-47/D-48/D-49 mas não
D-50/D-51/D-52 — lacuna do tipo "regra órfã" (§9 daquele documento),
corrigida em `03_INVARIANTES.md` v1.7.0 **antes** deste documento técnico,
não depois. No mesmo passo, corrigidas três menções esquecidas de "46
regras" (§0, §3, §9.1), desatualizadas desde as revisões 1.4.0-1.6.0.

**Revisão em seguida, ainda na mesma sessão:** a proposta original de
reimportação de `segmentos.csv` (upsert por `assuntoId+ordem`) foi
questionada pelo usuário — `ordem` é posição, não identidade, e reordenar o
material depois de importado confundiria, em silêncio, um segmento antigo
(já referenciado por sessões passadas, D-51) com um novo na mesma posição.
Decisão: `Segmento` ganha **chave externa própria e obrigatória** — nova
**D-53** (`01_DOMINIO.md` v1.18.0, `02_JORNADAS.md` v1.5.0,
`03_INVARIANTES.md` v1.8.0, incorporada **sem lacuna** desta vez, no mesmo
passo em que nasceu). Reimportação passa a identificar o segmento pela
chave, não pela posição. Como o SGE fecha a especificação primeiro (decisão
do usuário — "esse aqui é pai daquele"), isso virou requisito novo para o
repositório `Plano-de-Estudos-Automatizado`, registrado em
`docs/requisitos-planejamento-blocos-de-conteudo.md §8`: eles precisam
passar a gerar e manter um identificador estável por segmento, hoje
inexistente no pipeline deles (nomeiam só por posição).

| | Item | Quem escreve | Estado |
|---|---|---|---|
| 10.0 | Documento técnico `docs/SPRINT-10-SEGMENTO.md`, revisado | — | **feito** |
| 10.0a | Correção de `03_INVARIANTES.md` §3 (D-50/D-51/D-52, depois D-53) | Claude | **feito** |
| 10.0b | Requisito novo comunicado ao planejamento (`docs/requisitos-planejamento-blocos-de-conteudo.md §8`, `chaveExternaSegmento`) | Claude | escrito, aguardando resposta do outro lado |
| 10.1 | Migração `V9` — `assunto.chave_externa`, tabela `segmento` (com `chave_externa`), `sessao.segmento_id` (§1/§2 do doc técnico) | Claude, a pedido do usuário (pressa) | **feito** |
| 10.2 | Entidade JPA `Segmento` + alterações em `Assunto`/`Sessao` | Claude, a pedido do usuário (pressa) | **feito** |
| 10.3 | `SegmentoRepository` + `AssuntoRepository.findByChaveExterna` | Claude, a pedido do usuário (pressa) | **feito** |
| 10.4 | `AssuntoService`/`SessaoService` estendidos (§3 do doc técnico) | Claude, a pedido do usuário (pressa) | **feito** |
| 10.5 | `SegmentoService.criarOuAtualizar` — upsert por chave externa (§3.2 do doc técnico) | Claude, a pedido do usuário (pressa) | **feito** |
| 10.6 | `ImportacaoSegmentoService` — validar/confirmar (§4 do doc técnico) | Claude, a pedido do usuário (pressa) | **feito** |
| 10.7 | Endpoints — import de segmentos + `GET /api/assuntos/{id}/segmentos` (§5 do doc técnico) | Claude, a pedido do usuário (pressa) | **feito** |
| 10.8 | Testes — restrição (D-50/51/52/53, molde D-51 e helper `inserirSegmento` por Claude, os outros cinco métodos completados por Claude a pedido do usuário), erro de domínio, importação, listagem, as três listas brancas fechadas (§6 do doc técnico) | Claude, a pedido do usuário (pressa) | **feito** — 110/110 verdes |
| 10.9 | `GET /api/segmentos/exportacao` — round-trip via `chaveExternaSegmento` (§5 do doc técnico v1.2.0) | Claude, a pedido do usuário (pressa) | **feito** — `ExportacaoSegmentoTest`, 111/111 verdes |
| 10.10 | `scripts/importar-segmentos.sh` — disparo pós-`rclone` (§7 do doc técnico v1.2.0) | Claude, a pedido do usuário (pressa) | escrito, **não testado em Pi real** — este ambiente não tem um |

### Definition of Done

- [x] `especificacao/03_INVARIANTES.md` sem regra órfã (D-50 a D-53 em §3)
- [x] `docs/SPRINT-10-SEGMENTO.md` revisado e aceito
- [x] Migração `V9` aplicando sem erro, as seis restrições novas nomeadas
      com o identificador da regra (D-50, D-51 × 2, D-52, D-53 × 2 — CHECK
      de obrigatoriedade + índice único, mesmo padrão de D-41)
- [x] Import de `segmentos.csv` tudo-ou-nada; reimportar a mesma
      `chaveExternaSegmento` atualiza, nunca duplica nem apaga (D-18);
      reordenar não corrompe identidade (`ImportacaoSegmentoTest`)
- [x] `GET /api/assuntos/{id}/segmentos` respondendo, ordenado por `ordem`
      — destrava a Sprint 9 (`ImportacaoSegmentoTest.listar_...`)
- [x] As três listas brancas fechadas (`EstruturaSchemaTest` × 2,
      `AssuntoEstruturaTest`) atualizadas e verdes
- [x] Export de segmentos: `GET /api/segmentos/exportacao`, round-trip
      testado (`ExportacaoSegmentoTest`) — revertida a recusa da v1.1.0
      do doc técnico, `chaveExternaSegmento` já dava a identidade que faltava
- [x] Automação do disparo pós-`rclone`: `scripts/importar-segmentos.sh`
      escrito — **não testado em Pi real**, mesma ressalva que
      `scripts/backup.sh` recebeu no Sprint 8
- [ ] Resposta do repositório `Plano-de-Estudos-Automatizado` sobre o
      requisito de `chaveExternaSegmento` — bloqueia a importação real de
      `segmentos.csv` em produção, não o código do SGE em si. **Fora do
      controle deste repositório** — depende de outra equipe/sessão

**Itens 10.0 a 10.10 completos** — 111/111 testes verdes contra Postgres real
(Testcontainers, Docker do usuário). Só resta o script de automação testado
de verdade no Pi (fora do alcance deste ambiente) e a resposta do repositório
de planejamento (fora do alcance deste repositório) — nenhuma das duas
bloqueia o código do SGE em si.

**Verificação manual adicional** (2026-09-06, antes de retomar a Sprint 9):
Postgres descartável + `mvn spring-boot:run` (mesmo padrão das Sprints 1-6 —
não o `docker-compose.yml` de produção, que usa bind mount do Pi). Criada
disciplina/assunto reais via API com `chaveExterna`; importado
`segmentos.csv` com as linhas em ordem invertida no arquivo (`ordem=2` antes
de `ordem=1`); `GET /api/assuntos/{id}/segmentos` devolveu na ordem certa
(1, depois 2) — confirma que a ordenação é por `ordem`, não por ordem de
inserção, contra dado real, não só teste automatizado. `GET
/api/segmentos/exportacao` devolveu CSV no formato esperado. Ambiente
descartado ao final (container e processo `mvn spring-boot:run`
derrubados).

---

## 12. Como este arquivo se mantém honesto

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

## 13. Changelog

| Versão | Data | Mudança |
|---|---|---|
| 1.30.0 | 2026-09-06 | **Sprint 9 completa (Frontend — Hoje e Recuperar)**, retomada e fechada na mesma sessão, a pedido do usuário. Antes de retomar: conferido que `docs/04_FRONTEND.md §0` já restringia esta rodada só a Hoje/Recuperar — nenhuma das duas toca `Segmento` (só sessão `ESTUDO` referencia um), então a razão original da pausa não bloqueava de fato o que foi construído; endpoint de leitura de segmentos testado manualmente contra Postgres real antes de prosseguir. Implementado: `src/main/resources/static/` inteiro — `index.html` (casca única, Tailwind + Alpine via CDN, ADR-028), `js/api.js` (wrapper de fetch já especificado em `04_FRONTEND.md §4`), `js/router.js` (roteamento por hash), `js/pages.js` (`paginaHoje`/`paginaRecuperar`, um componente Alpine por página, `09_CODE_STYLE §8`). Tela Recuperar segue à risca as três regras vinculantes de `02_JORNADAS §4.1`: resultado ausente do DOM na etapa 1 (`x-if`, não `x-show`), previsão só texto na etapa 2, sem botão "pular". Decisão de código fechada nesta sessão (`04_FRONTEND.md §6` deixava em aberto): handoff Hoje→Recuperar via `Alpine.store` global — carrega nível/data prevista do item clicado, sem endpoint novo; se a store não tiver o item (acesso direto à URL), a tela cai num estado de erro explícito em vez de fingir dado. `tempoMinutos` da sessão de recuperação é medido (tempo de tela, abrir→confirmar), não constante — D-29. Testado num Chrome de verdade contra Postgres descartável (não o `docker-compose.yml` de produção): fluxo completo Hoje→clique→etapa 1→etapa 2→confirmar→volta otimista pra Hoje→conferido no banco que a sessão gravou e a escada roteou (nível 1 `CUMPRIDA`, nível 2 `PENDENTE`). Não exercitados manualmente: caminho de falha de rede (implementado, não testado por indisponibilidade real) e o estado "vazio" das telas. Itens 9.1–9.4 promovidos a **feito** |
| 1.29.0 | 2026-09-06 | **Sprint 10 — as duas pendências internas fechadas**, a pedido do usuário (ainda tinha tokens no ciclo da semana). `docs/SPRINT-10-SEGMENTO.md` v1.2.0 reverte as duas recusas de v1.1.0: (1) **Export de segmentos** (`GET /api/segmentos/exportacao`, `ExportacaoSegmentoService`/Repository) — a v1.1.0 recusou por falta de identidade estável pro round-trip, mas `chaveExternaSegmento` (D-53, decidida na própria v1.1.0) já resolve isso; `ExportacaoSegmentoTest` prova confirmar→exportar→reimportar sem duplicar. (2) **Automação do disparo pós-`rclone`** (`scripts/importar-segmentos.sh`, novo, bit executável setado) — roda `rclone sync` e chama `validar`/`confirmar` em cada `assuntos.csv`/`segmentos.csv` encontrado, sempre assuntos antes de segmentos no mesmo concurso; sem controle de "já processado" (reprocessar é idempotente e barato no volume deste sistema); **não testado em Pi real**, mesma ressalva de `scripts/backup.sh` no Sprint 8. Suíte: **111/111 verdes**. Fica só a resposta do repositório `Plano-de-Estudos-Automatizado` sobre `chaveExternaSegmento` — fora do controle deste repositório, não bloqueia o código do SGE |
| 1.28.0 | 2026-09-06 | **Sprint 10 completa — primeira execução real, 110/110 testes verdes** contra Postgres real (Testcontainers, Docker do usuário). Migração `V9` aplicou sem erro. Três defeitos reais encontrados e corrigidos: (1) `RestricaoTestBase.inserirDisciplina()` sempre gravava o mesmo nome fixo ("Disciplina de teste") — os três testes novos que precisavam de duas disciplinas na mesma transação (`d51_sessaoComSegmentoDeOutroAssunto_recusada`, `d52_doisAssuntosMesmaChaveExterna_recusado`, `d53_doisSegmentosMesmaChaveExterna_recusado`) colidiam em `ux_d47_nome_disciplina` antes de chegar na restrição que o teste queria provar; corrigido com um overload `inserirDisciplina(String sufixo)`, mesmo padrão já usado em `assuntoId(String sufixo)` de `SessaoErroDominioTest`. (2) `SessaoErroDominioTest.segmentoForaDeEstudo_devolve422` montava uma sessão `QUESTOES` sem `previsaoPercentual` — como `QUESTOES` sempre calcula `resultado` no serviço, a linha caía em `ck_sessao_d04a_previsao_por_tipo` (`PREVISAO_INVALIDA`) antes de chegar em `ck_sessao_d51_segmento_so_estudo`; corrigido preenchendo `previsaoPercentual` pra isolar a regra certa. (3) Lacuna de cobertura, não bug: `GET /api/assuntos/{id}/segmentos` (o endpoint que destrava a Sprint 9) não tinha nenhum teste — `ImportacaoSegmentoTest.listar_devolveSegmentosDoAssuntoNaOrdemDeLeitura` adicionado, confirma ordenação por `ordem`, não por ordem de inserção. Todos os itens 10.0–10.8 promovidos a **feito** |
| 1.27.0 | 2026-09-06 | **Sprint 10 — itens 10.1 a 10.8 código e testes escritos**, a pedido do usuário (pressa, precisava ir estudar). `docs/SPRINT-10-SEGMENTO.md` revisado e aceito antes de qualquer código, regra central do `CLAUDE.md`. Migração `V9` (assunto ganha `chave_externa`; tabela `segmento` nova com `chave_externa`/`ordem`/`arquivo`/páginas/tempo estimado; `sessao` ganha `segmento_id` + FK composta `(segmento_id, assunto_id) → segmento(id, assunto_id)` pra D-51). Entidade `Segmento` (pacote novo `br.com.estudos.segmento`), `Assunto`/`Sessao` estendidas. `SegmentoRepository`/`AssuntoRepository.findByChaveExterna`. `AssuntoService`/`SessaoService` traduzem as quatro restrições novas (`CHAVE_EXTERNA_DUPLICADA` 409, `SEGMENTO_DE_OUTRO_ASSUNTO` 409, `SEGMENTO_FORA_DE_ESTUDO` 422). `SegmentoService.criarOuAtualizar` — upsert por `chaveExterna`, nunca por posição. `ImportacaoSegmentoService`/`ImportacaoSegmentoController` (`/api/segmentos/importacoes/validar|confirmar`), mesmo padrão validar/confirmar de `ImportacaoAssuntoService`, com a assimetria documentada (linha com `chaveExternaAssunto` sem correspondência é recusada, não aborta o arquivo — sem coluna `id` pra distinguir "corrompido" de "ainda não existe"). `SegmentoController` (`GET /api/assuntos/{id}/segmentos`), destrava a Sprint 9. Testes: os cinco métodos de `RestricoesInvariantesTest` que tinham ficado como `TODO(human)` (D-50, D-51 segunda metade, D-52, D-53 × 2) completados por Claude; `AssuntoErroDominioTest`/`SessaoErroDominioTest` ganham um teste por `codigo` novo; `ImportacaoSegmentoTest` novo (tudo-ou-nada + upsert, mesmo molde de `ImportacaoAssuntoTest`); as três listas brancas fechadas atualizadas (`EstruturaSchemaTest.d16_assuntoSemColunaDeFase`/`d28_semTabelaDeTurno`, `AssuntoEstruturaTest`). Achado no caminho: `Segmento.chaveExterna` não pode ter `@Column(nullable = false)` na entidade — a coluna é `NULL`-ável de verdade no banco (obrigatoriedade é `CHECK`, não `NOT NULL` nativo, D-53), anotar `nullable=false` divergiria do schema real sob `ddl-auto=validate` (mesmo cuidado que `Assunto.ordem`/D-41 já exigia, quase esquecido aqui). Adicionar `chaveExterna` a `AssuntoRequest` e `segmentoId` a `SessaoRequest` (records) quebrou 16 chamadas posicionais em 12 arquivos de teste + `ImportacaoAssuntoService` — todas corrigidas. `mvn -o test-compile` limpo (main + testes). **Sem execução real** — Docker não disponível neste ambiente, mesma ressalva das Sprints 7/8; nenhum item pode virar "feito" até alguém rodar `mvn test` com Docker de pé |
| 1.26.0 | 2026-09-06 | **Sprint 10 aberta (documentação) — `docs/SPRINT-10-SEGMENTO.md` escrito e revisado na mesma sessão**, traduzindo para schema/API a decisão conceitual já fechada anteriormente (`01_DOMINIO.md`, `02_JORNADAS.md`, ADR-037). Antes de abrir o documento: `especificacao/03_INVARIANTES.md` corrigida para v1.7.0 — D-50/D-51/D-52 tinham ficado de fora da tabela de §3 (regra órfã, `§9` do próprio documento), mesma classe de lacuna que D-46/47/48/49 já tinham exposto, desta vez pega antes da sprint fechar; de quebra, corrigidas três menções esquecidas de "46 regras", desatualizadas desde 1.4.0-1.6.0. Documento técnico v1.0.0 propôs upsert de `segmentos.csv` por `(assuntoId, ordem)`; **revisado para v1.1.0 na revisão do usuário**, que apontou o risco: `ordem` é posição, não identidade, e reordenar o material depois de importado confundiria em silêncio um segmento antigo (já referenciado por sessões passadas) com um novo na mesma posição. Corrigido com **D-53**, nova: `Segmento` ganha chave externa própria e obrigatória (diferente da de `Assunto`, que é opcional) — `01_DOMINIO.md` v1.18.0, `02_JORNADAS.md` v1.5.0, `03_INVARIANTES.md` v1.8.0 (D-53 incorporada **sem lacuna** desta vez, no mesmo passo em que nasceu). Como o SGE fecha a especificação primeiro, isso virou requisito comunicado ao repositório `Plano-de-Estudos-Automatizado` (`docs/requisitos-planejamento-blocos-de-conteudo.md §8`, novo): eles precisam passar a gerar e manter esse identificador, hoje inexistente no pipeline deles (nomeiam segmento só por posição de arquivo). Nenhum código escrito — implementação fica para a sessão seguinte. `PROGRESSO.md` §11 criado (Sprint 10), seções seguintes renumeradas |
| 1.25.0 | 2026-08-31 | **Itens de baixo risco da auditoria fechados**: (1) numeração 45→46 corrigida em `CLAUDE.md`, `.claude/agents/mentor.md`, `especificacao/03_INVARIANTES.md` (v1.3.0 — D-46 ganhou linha na tabela de §3, que nunca tinha sido atualizada desde a Sprint 7) e `docs/SPRINT-1-BANCO.md` (v1.3.0, quatro menções). (2) Citação de identificador adicionada em código para 9 regras implementadas mas não citadas: D-08 (`RevisaoService.intervaloNivel`), D-15/D-40 (`TurnoService.plano`, fila cortada no teto), D-19/D-20/D-21/D-25 (`FrenteService`), D-27 (`TurnoService`, classe), D-29 (`Sessao.tempoMinutos`, satisfeita por ausência de constante). Nenhuma mudança de comportamento — só comentário/javadoc. D-03 e D-37 continuam sem citação, corretamente: dependem da tela (Sprint 9, não começou), não há o que citar no backend ainda. Suíte: 85/85 verde. Restam da auditoria original: divisão de assunto (D-35/42/43/44, decisão de escopo própria, não tratada aqui) e a tela Ajustes sem endpoint |
| 1.24.0 | 2026-08-31 | **D-31 implementado** (§7.5, teto diário insuficiente para a frente) — mesma auditoria. Nenhum documento tinha a fórmula antes; decidida com o usuário: piso do melhor caso, `tetoDiario × intervaloManutencaoDias < tetoGlobalFrente` (mesmo parâmetro da escada, sem migração nova). `FrenteResumoResponse.avisoTetoInsuficiente`, separado do `alertaRepresamento` reativo. `docs/SPRINT-5-FRENTE.md` v1.1.0, teste novo em `FrenteServiceTest`. Suíte: 85/85 verde. Da auditoria original, restam: divisão de assunto (D-35/42/43/44, sem sprint no roadmap), tela Ajustes sem endpoint, numeração 45→46 de `01_DOMINIO`/`03_INVARIANTES`/`CLAUDE.md`, e as 18 regras implementadas sem citação no código |
| 1.23.0 | 2026-08-31 | **D-09 implementado** (§4.3, lote mínimo) — gap achado na mesma auditoria: `lote_minimo_questoes` existia na tabela `parametro` desde a Sprint 3 (deixado fora de `V4` de propósito, "sem efeito até a escada existir"), mas ninguém tinha voltado para ligá-lo depois que a escada (Sprint 4) passou a existir. Parâmetro entra em `V5` (a migração que o ativa); `RevisaoService.processarRecuperacao` agora checa o lote antes de tocar em revisão pendente ou criar rota espontânea, só para `QUESTOES`/`FLASHCARDS` (`RECUPERACAO` não tem "lote"). `docs/SPRINT-4-ESCADA.md` v1.1.0, dois testes novos em `RevisaoEscadaTest`. Suíte: 84/84 verde. D-31 (aviso de teto abaixo da taxa da frente) e a divisão de assunto (D-35/42/43/44, sem sprint) continuam pendentes |
| 1.22.0 | 2026-08-31 | **Fase 2 do plano combinado com o usuário** (auditoria da API contra a especificação conceitual, `/agents/mentor.md`): achou uma contradição real de domínio entre `00_PRODUTO.md §7` (M-3 subjetiva descrita como par binário — "vai conseguir reconstruir? / conseguiu?") e `02_JORNADAS.md §4.1` (três botões desenhados de propósito, "mesma régua nos dois momentos"). O código tinha ido para binário (`previsaoReconstrucao: Boolean`), mas o `resultado` de `RECUPERACAO` já era três vias (`SUCESSO`/`PARCIAL`/`FALHA`) desde a Sprint 3 — D-07 exige isso de toda sessão que cumpre revisão. Decisão do usuário: três vias, mesmo enum dos dois lados. Documento primeiro: `00_PRODUTO.md` v1.6.0 (nota explicitando que a granularidade é decisão de tela, não da métrica), `docs/SPRINT-1-BANCO.md` v1.2.0, `docs/SPRINT-3-SESSAO.md` v1.1.0, `docs/SPRINT-7-METRICAS.md` v1.4.0 (fórmula de sinal reescrita por ordinal), `docs/DIAGRAMA-ER.md` v2.1.0. Código: `previsao_reconstrucao` vira `TEXT CHECK IN ('SUCESSO','PARCIAL','FALHA')` em `V1__tabelas.sql` (sem banco persistente ainda — confirmado com o usuário, editar migração já aplicada em lugar nenhum é seguro); `Sessao`/`SessaoRequest`/`SessaoResponse.previsaoReconstrucao` viram `ResultadoSessao`; `MetricaService.m3()` compara por ordinal do enum em vez de booleano×categórico. `M3SubjetivaResponse` não muda de forma. Suíte: 82/82 verde. A auditoria também achou outros gaps (D-09, D-31, D-35/42/43/44 sem sprint, numeração 45→46 de `01_DOMINIO`) — ainda pendentes, não tratados nesta entrada |
| 1.21.0 | 2026-08-31 | **Primeira execução real do código das Sprints 7-8** — `mvn -o test-compile` mostrou que ele nunca tinha nem compilado (`target/classes` não tinha `erro`/`metrica`/`simulado`); depois de compilar, `mvn test` com Docker de pé rodou 79/79 verdes. Revisão de status (`/agents/mentor.md`) sobre esse resultado achou que "verde" não provava dois pontos, porque todo teste de listagem das Sprints 7/8 é `@Transactional` (sessão do teste mascara `LAZY` fora de sessão) e o projeto nunca tinha decidido `open-in-view` (default `true` do Spring, nunca citado em `application.yml` nem documento algum): (1) `ErroRepository.findByAssuntoId`/`ResultadoSimuladoRepository.findBySimuladoIdIn` sem `join fetch` — N+1 real em `GET /api/erros`/`GET /api/simulados`, mascarado pela sessão do Hibernate ficar aberta com OSIV ligado; (2) `ErroService.registrar` sem validar `assuntoId` ausente — virava `500` via `getReferenceById(null)`, não `422`. Corrigidos: **ADR-034** decide `open-in-view: false` (coerente com o padrão já usado desde a Sprint 2 — `LAZY` com fetch sempre explícito, nunca implícito, e com ADR-029); os dois repositórios ganharam `join fetch`; `ErroService.registrar` valida `assuntoId` (`ASSUNTO_OBRIGATORIO`, 422). Dois testes novos não-`@Transactional` (`ErroListagemTest`, `SimuladoListagemTest`, mesmo padrão de `ImportacaoAssuntoTest`) provam a correção pelo caminho que os testes transacionais não conseguem provar — com OSIV desligado, teriam lançado `LazyInitializationException` se o `join fetch` estivesse errado. Também corrigidos: comentário faltando em `ux_resultado_simulado_simulado_disciplina` (`V8__simulado.sql`, auditoria de `03_INVARIANTES §9`) e o javadoc de `ErroService.registrar` explicando por que `assuntoId` é eager mesmo coberto por D-46. Suíte final: **82/82 verdes**. DoD das Sprints 7 e 8 marcadas com base nesta execução — as duas ficam pendentes só de backup real no Pi (8) e polimento (8), que não dependem de código |
| 1.20.0 | 2026-08-31 | **Sprint 9 aberta (Frontend — Hoje e Recuperar)**, primeira fora do mapa conceitual original de 8. `docs/04_FRONTEND.md` v1.0.0 criado após quatro decisões de escopo (stack, documentação, primeiras telas, rastreamento — ver §10). ADR-028/030 reexaminadas conscientemente contra a exigência de UI otimista de `02_JORNADAS §5.1` e mantidas — nota registrada em `docs/00A_ADR.md` (v2.2.0), fechando a pendência que a seção final desse documento pedia. `PROGRESSO.md` §1 e §10 criados; seções seguintes renumeradas |
| 1.19.0 | 2026-08-31 | `/code-review high` sobre o diff das Sprints 7-8 (`08c4f6f..HEAD`) achou 5 pontos; 4 corrigidos: (1) M-2 ordenava só por `data`, "primeira exposição" indefinida com duas sessões do mesmo tipo no mesmo dia — desempate por `id`. (2) `GET /api/simulados` fazia N+1 (uma consulta de resultados por simulado) — corrigido pra duas consultas totais, e de quebra ganhou `ORDER BY data DESC` (histórico, mesma família do problema de ordenação do item 1). (3) `ErroService.registrar` e (4) `SimuladoService.registrar` deixavam campo obrigatório ausente (`descricao`/`causa`/`confianca`; `data`/`duracaoMinutos`/campos de cada resultado) virar 500 não traduzido — 8 `codigo` novos de validação eager ao todo, mesmo padrão de `SessaoService.exigirContagemDeQuestoes`. O 5º ponto (tradução de exceção duplicada entre `AssuntoService`/`SessaoService`/`ErroService`/`SimuladoService`) fica registrado como sugestão, não aplicado — mexeria em serviços de sprints anteriores fora do que foi pedido. `docs/SPRINT-7-METRICAS.md` v1.3.0, `docs/SPRINT-8-SIMULADO.md` v1.1.0, testes novos para os 8 `codigo`. Ainda sem execução de testes (mesma ressalva) |
| 1.18.0 | 2026-08-31 | Item 8.8: `scripts/backup.sh`, `restaurar.sh`, `testar-restauracao.sh` escritos, transcrevendo `docs/03E_DEPLOYMENT.md` §6 (doc já congelado, nenhuma decisão nova). `.gitattributes` criado (`*.sh text eol=lf`) — sem isso o `autocrlf` do Windows converteria os scripts pra CRLF no checkout e quebraria o shebang/`set -euo pipefail` no Pi (Linux). Bit executável (`100755`) setado no índice do git. Não testados em Pi real — este ambiente não tem um |
| 1.17.0 | 2026-08-31 | **Sprint 8 aberta (Simulado), exceção consciente à regra de não adiantar sprint futura** — a Sprint 7 ainda não tinha testes executados quando o usuário pediu para seguir adiante. `docs/SPRINT-8-SIMULADO.md` v1.0.0 escrito após quatro decisões de escopo (formato por resultado; simulado soma no mesmo agregado de M-1; só Simulado nesta rodada, backup/polimento ficam de fora; duração registrada). Migração `V8` (tabelas `simulado`/`resultado_simulado`), entidades, repositórios, `SimuladoService`/`Controller`, `MetricaService.m1` estendido, testes — itens 8.1–8.7 código e testes escritos, a pedido do usuário (pressa), ainda sem execução verificada (sem Docker neste ambiente, mesma ressalva da Sprint 7). `EstruturaSchemaTest` atualizado: `d28_semTabelaDeTurno` inclui as duas tabelas novas; novo `d13_resultadoSimuladoSemColunaDeAssunto` prova D-13 pela ausência estrutural da coluna. Nenhuma regra `D-xx` nova — D-13 já cobria a integridade que faltava |
| 1.16.0 | 2026-08-31 | Itens 7.1–7.7 **código e testes escritos**, a pedido do usuário (pressa) — ainda sem execução verificada, Docker não disponível neste ambiente. Achado no caminho, antes de escrever qualquer linha de código: a tabela `erro` já existia desde `V1__tabelas.sql` (Sprint 1, `docs/SPRINT-1-BANCO.md` §2.5/§7.6) com `causa`/`confianca` exatamente como o rascunho do documento técnico propunha — a Sprint 7 não criou tabela nova, só a camada JPA (entidade, repositório, service, controller) e renomeou `fk_erro_assunto` para `fk_erro_d46_assunto` (migração `V7`, uma linha). `docs/SPRINT-7-METRICAS.md` §1 e §2 corrigidos para refletir isso. `MetricaService` (M-1 a M-4) segue ADR-033: sem view, sem cache, agregação em Java sobre projeções/streams. Testes de M-1 isolados por uma janela de tempo bem no futuro (ano 2999) pra não pegar dado de sobra de outras classes de teste não-transacionais (mesmo problema já visto no changelog 1.14.0); M-3/M-4 não têm janela (fiel ao texto de `00_PRODUTO`), então os testes deles comparam antes/depois em vez de valor absoluto. Próximo passo: usuário roda `mvn test` com Docker de pé pra confirmar antes de marcar os itens como **feito** |
| 1.15.0 | 2026-08-31 | Sprint 7 aberta. `docs/SPRINT-7-METRICAS.md` v1.1.0 escrito por Claude após quatro decisões de escopo discutidas com o usuário (escala de confiança do erro, M-2 separando `RECUPERACAO`, erro fora da ordem da fila, sem endpoint de resolver). `01_DOMINIO.md` ganhou **D-46** (v1.12.0) no caminho — integridade referencial de `Erro` com `Assunto` (obrigatório) e `Sessao` (opcional), que não tinha regra numerada ainda; escrita e confirmada antes do documento técnico fechar, seguindo a regra central do `CLAUDE.md` de documento antes de código. `PROGRESSO.md` §8 criado com 8 itens (7.0–7.7) e Definition of Done |
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
