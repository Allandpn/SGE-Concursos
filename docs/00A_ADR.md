# 00A — ARCHITECTURE DECISION RECORDS

Catálogo único das decisões de arquitetura.

| Campo | Valor |
|---|---|
| Versão do documento | **2.7.0** |
| Status | **Congelado** |
| Data | 2026-09-04 |
| Total | 37 ADRs — 26 vigentes, 10 substituídas, 1 revogada |

---

## 0. Como usar este documento

Um **ADR** registra uma decisão de arquitetura: o que foi decidido, por quê, o
que custa e o que foi descartado.

| Campo | Significado |
|---|---|
| Status | `Aceita`, `Substituída por ADR-nnn`, `Revogada` |
| Contexto | A situação e as forças em jogo |
| Decisão | O que foi decidido. Imperativo, sem hedge |
| Consequências | O que passa a ser verdade — inclusive o que fica pior |
| Alternativas rejeitadas | O que foi considerado e por que não |

**Regras:**

1. Um ADR **nunca é editado** depois de Aceito. Mudou de ideia? Escreva um novo e
   marque o antigo como `Substituída por ADR-nnn`. O histórico do raciocínio é o
   valor do documento — é por isso que as ADRs da era Apps Script continuam aqui,
   e não foram apagadas.
2. Numeração sequencial, **nunca reaproveitada**.
3. Todo ADR tem "Alternativas rejeitadas" preenchida.
4. Código que implementa uma decisão cita o ADR no Javadoc.

---

## 1. Índice

### 1.1 Vigentes

| ADR | Título | Origem |
|---|---|---|
| [ADR-007](#adr-007--estado-derivado-nunca-é-persistido) | Estado derivado nunca é persistido | v1 |
| [ADR-011](#adr-011--exclusão-lógica-nunca-física) | Exclusão lógica, nunca física | v1 |
| [ADR-014](#adr-014--revisão-espaçada-por-intervalos-fixos-configuráveis) | Revisão espaçada por intervalos fixos configuráveis | v1 |
| [ADR-015](#adr-015--autenticação-delegada-à-tailscale) | Autenticação delegada à Tailscale | v2 |
| [ADR-016](#adr-016--monolito-modular-em-container-único) | Monolito modular em container único | v2 |
| [ADR-017](#adr-017--migrações-versionadas-com-flyway) | Migrações versionadas com Flyway | v2 |
| [ADR-018](#adr-018--sem-alta-disponibilidade-backup-diário-testado) | Sem alta disponibilidade; backup diário testado | v2 |
| [ADR-019](#adr-019--postgres-em-ssd-usb-nunca-em-cartão-sd) | Postgres em SSD USB, nunca em cartão SD | v2 |
| [ADR-020](#adr-020--postgresql-como-banco-de-dados) | PostgreSQL como banco de dados | v2 |
| [ADR-021](#adr-021--constraints-no-banco-lógica-de-negócio-fora-dele) | Constraints no banco, lógica de negócio fora dele | v2 |
| [ADR-022](#adr-022--tipos-temporais-nativos) | Tipos temporais nativos | v2 |
| [ADR-023](#adr-023--chaves-primárias-identity) | Chaves primárias `IDENTITY` | v2 |
| [ADR-024](#adr-024--transações-acid-substituem-lock-manual) | Transações ACID substituem lock manual | v2 |
| [ADR-025](#adr-025--api-rest-orientada-a-recurso) | API REST orientada a recurso | v2 |
| [ADR-026](#adr-026--erros-em-rfc-9457-problem-details) | Erros em RFC 9457 Problem Details | v2 |
| [ADR-027](#adr-027--domínio-em-português-ponte-por-naming-strategy) | Domínio em português, ponte por naming strategy | v2 |
| [ADR-028](#adr-028--maven-no-backend-zero-build-no-frontend) | Maven no backend, zero build no frontend | v2 |
| [ADR-029](#adr-029--sem-cache-de-aplicação) | Sem cache de aplicação | v2 |
| [ADR-030](#adr-030--spa-estática-servida-pelo-spring) | SPA estática servida pelo Spring | v2 |
| [ADR-031](#adr-031--unicidade-por-tentativa-e-por-revisão-pendente) | Unicidade por tentativa e por revisão pendente | Sprint 1 |
| [ADR-032](#adr-032--controle-de-versão-na-escrita) | Controle de versão na escrita | Sprint 4 |
| [ADR-033](#adr-033--derivação-por-consulta-simples-nunca-materializada) | Derivação por consulta simples, nunca materializada | Sprint 5 |
| [ADR-034](#adr-034--open-in-view-false) | `open-in-view: false` | Sprint 7/8 |
| [ADR-035](#adr-035--bean-validation-para-forma-do-request-não-para-regra-de-domínio) | Bean Validation para forma do request, não para regra de domínio | v2 |
| [ADR-036](#adr-036--openapiswagger-gerado-a-partir-do-código) | OpenAPI/Swagger gerado a partir do código | v2 |
| [ADR-037](#adr-037--artefatos-do-planejamento-chegam-via-google-drive-e-rclone) | Artefatos do planejamento chegam via Google Drive e `rclone` | Sprint 10 |

### 1.2 Históricas

| ADR | Título | Status |
|---|---|---|
| ADR-001 | Google Sheets como banco de dados | Substituída por **ADR-020** |
| ADR-002 | A planilha não contém lógica | **Revogada** (ver ADR-021) |
| ADR-003 | Datas como texto ISO-8601 | Substituída por **ADR-022** |
| ADR-004 | IDs sequenciais com prefixo | Substituída por **ADR-023** |
| ADR-005 | Toda escrita protegida por `LockService` | Substituída por **ADR-024** |
| ADR-006 | Ponto de entrada único da API | Substituída por **ADR-025** |
| ADR-008 | Envelope de resposta padronizado | Substituída por **ADR-026** |
| ADR-009 | Nomenclatura por conversão mecânica | Substituída por **ADR-027** |
| ADR-010 | Sem etapa de build | Substituída por **ADR-028** |
| ADR-012 | Cache por coleção, invalidado por escrita | Substituída por **ADR-029** |
| ADR-013 | SPA servida por `doGet` | Substituída por **ADR-030** |

O texto integral das históricas está em `docs/_apps-script-v1/00A_ADR.md`.
Elas não são erros: foram decisões corretas para a plataforma da época, e a maior
parte delas existia para contornar limitações que deixaram de existir.

---

## ADR-007 — Estado derivado nunca é persistido

**Status:** Aceita · v1 · **mantida integralmente na v2**

### Contexto
É tentador gravar `total_horas` no assunto, ou `atrasada` na revisão. Na v1 isso
era tentador porque leitura era cara; na v2 é tentador porque agora *dá* para
fazer com trigger.

### Decisão
Se uma informação pode ser calculada a partir de outras, ela **não tem coluna** e
**não é gravada**.

Campos que, por esta decisão, não existem: `revisao.atrasada`,
`revisao.dias_atraso`, `assunto.total_horas`, `assunto.percentual_acerto`,
`assunto.ultima_sessao`, `disciplina.total_assuntos`,
`assunto.prioridade_calculada`, `assunto.fase`.

**Fronteira.** `assunto.status` e `assunto.concluido_em` **são** persistidos
porque representam uma **decisão do usuário**, não um cálculo. O teste é: se o
sistema recalcula sozinho, é derivado; se depende de alguém declarar, é dado.

### Consequências
- Nada fica desatualizado, porque nada é armazenado desatualizado.
- Cada leitura paga o cálculo — irrelevante com SQL e índices adequados.
- Campos derivados aparecem no **DTO de resposta**, nunca na tabela.
- **A desnormalização de `disciplina_id` que a v1 tolerava foi removida.** Ela
  existia só porque um JOIN no Sheets custava uma segunda leitura completa. Com
  SQL, o JOIN é grátis, e some junto a invariante que a protegia.

### Alternativas rejeitadas
| Alternativa | Por que não |
|---|---|
| Colunas denormalizadas com trigger de recálculo | Duplica a regra de negócio em PL/pgSQL (viola ADR-021) e produz totais que divergem silenciosamente |
| View materializada | Precisa de `REFRESH`; ou é síncrono (custa na escrita) ou é assíncrono (fica velho). Ver simples resolve |

---

## ADR-011 — Exclusão lógica, nunca física

**Status:** Aceita · v1 · **mantida na v2**

### Contexto
Na v1 o motivo imediato era que apagar linha na planilha deslocava as seguintes.
Esse motivo sumiu — mas o motivo real, que era semântico, permanece.

### Decisão
Nenhuma linha é removida em operação normal.

| Entidade | Mecanismo |
|---|---|
| Disciplina | `ativo = false` |
| Assunto | `ativo = false` |
| Sessão | `ativo = false` |
| Revisão | `situacao = 'CANCELADA'` |
| Erro | sem exclusão; `resolvido = true` não é exclusão |
| Log de auditoria | truncamento físico por rotina de retenção — **única exceção** |

### Consequências
- Registros excluídos não aparecem em listagens, mas continuam contando no
  histórico e continuam sendo alvos válidos de FK.
- **Uma exceção:** sessão arquivada sai de **todas** as agregações (INV-018).
  Desativar disciplina é "parei de estudar isto"; arquivar sessão é "isto nunca
  aconteceu, registrei errado". A primeira é história; a segunda é correção.
- Toda consulta precisa filtrar o marcador. Mitigado por *default methods* nos
  repositórios e índices parciais — nunca por `@Where` do Hibernate, que é
  invisível e vaza para lugares onde você não quer.

### Alternativas rejeitadas
| Alternativa | Por que não |
|---|---|
| `DELETE` com `ON DELETE CASCADE` | Uma sessão apagada levaria junto as revisões que ela gerou, destruindo o histórico estatístico sem aviso |
| `@SQLDelete` / `@Where` do Hibernate | Filtro invisível no código. Quando você precisar listar os arquivados — e vai precisar —, terá de contorná-lo |

---

## ADR-014 — Revisão espaçada por intervalos fixos configuráveis

**Status:** Aceita · v1 · **mantida na v2**

### Contexto
Revisão espaçada é o coração do produto. Duas famílias possíveis: intervalos
fixos e algoritmos adaptativos (SM-2 / Anki).

### Decisão
Escada de intervalos fixos, lida da configuração. Default: `1, 7, 15, 30, 60, 90`
dias. **Não** é algoritmo adaptativo.

### Consequências
- Previsível, auditável e ajustável sem *deploy*.
- Não se adapta ao desempenho por assunto. Mitigado indiretamente: o desempenho
  entra no **score de seleção** (`05_BUSINESS_RULES.md` §4), então assunto mal
  dominado é recomendado com mais frequência mesmo com escada fixa.
- **Evolução preparada.** O cálculo vive isolado em
  `RevisaoService.calcularProximaData()`. Migrar para SM-2 exige alterar essa
  função e acrescentar colunas; nenhuma outra parte muda.

### Alternativas rejeitadas
| Alternativa | Por que não |
|---|---|
| SM-2 / Anki | Exige nota de qualidade a cada revisão, aumentando o atrito do registro — o inimigo nº 1 do sistema (`00_PROJECT.md` §4) |
| Híbrido (fixo × desempenho) | Torna a data imprevisível sem torná-la explicavelmente melhor. O usuário perde a capacidade de planejar a semana |

---

## ADR-015 — Autenticação delegada à Tailscale

**Status:** Aceita · 2026-08-15

### Contexto
Sistema de usuário único, atrás de CGNAT, rodando num Pi doméstico. A v1 herdava
a autenticação da conta Google. Na v2, o serviço não tem IP público e só é
alcançável de dentro da tailnet — uma rede WireGuard onde cada dispositivo foi
explicitamente autorizado pelo dono.

### Decisão
**A aplicação não tem autenticação.** Sem tela de login, sem sessão, sem token,
sem usuários. A rede **é** a autenticação: chegar até a porta 8080 já exige uma
chave WireGuard válida.

- Spring Security fica **fora** do projeto, para não haver a tentação de
  "desabilitar por enquanto" e esquecer ligado o `permitAll`.
- A aplicação escuta em `127.0.0.1` ou na interface da tailnet, **nunca** em
  `0.0.0.0`.
- O container **não publica portas** no host além do necessário.

### Consequências
- Zero código de auth, zero telas, zero gestão de sessão, zero rotação de senha.
- **A superfície de ataque é a tailnet inteira.** Se um dispositivo seu for
  comprometido, o sistema está comprometido. Aceito: o dado é o próprio histórico
  de estudos, e o mesmo dispositivo comprometido teria acesso a coisas piores.
- **Expor publicamente é decisão de arquitetura, não de configuração.** Trocar o
  bind para `0.0.0.0` sem antes escrever um novo ADR e implementar autenticação é
  defeito grave.

### Alternativas rejeitadas
| Alternativa | Por que não |
|---|---|
| Basic Auth com usuário único | Senha em variável de ambiente, no navegador, sem rotação. Segurança de teatro sobre uma rede que já é privada |
| OAuth (Google, GitHub) | Exige callback público — exatamente o que a Tailscale nos poupa. Complexidade sem ameaça correspondente |
| Cabeçalhos de identidade da Tailscale | Úteis para múltiplos usuários. Com um usuário, é infraestrutura que só pode quebrar |

---

## ADR-016 — Monolito modular em container único

**Status:** Aceita · 2026-08-15

### Contexto
Um Pi com 4–8 GB, compartilhado com o DNS que o usuário já roda. Um sistema com
seis agregados e um usuário.

### Decisão
**Uma** aplicação Spring Boot, **um** container, **um** processo Java, mais um
container de Postgres. Organização interna **por funcionalidade**
(`package-by-feature`), com fronteiras claras entre módulos — mas sem separação
física.

### Consequências
- Deploy é `docker compose up -d --build`. Sem orquestrador, sem service
  discovery, sem rastreamento distribuído.
- Refatorar entre módulos é mover classe, não versionar contrato.
- A modularidade fica por disciplina, não por rede — daí a matriz de dependências
  de `01_ARCHITECTURE.md` §2 ser vinculante.
- Se um dia houver necessidade real de separar, o `package-by-feature` já marca
  as linhas de corte.

### Alternativas rejeitadas
| Alternativa | Por que não |
|---|---|
| Microserviços | Seis agregados e um usuário. Cada serviço traria rede, serialização e falha parcial em troca de nada |
| Backend e frontend em containers separados | Adiciona CORS ou proxy reverso e mais um serviço para manter no Pi. Ver ADR-030 |
| JAR direto no systemd, sem Docker | Funciona, mas amarra a versão do Java ao SO do Pi e torna o rollback manual |

---

## ADR-017 — Migrações versionadas com Flyway

**Status:** Aceita · 2026-08-15

### Contexto
O schema vai evoluir. A alternativa preguiçosa — `spring.jpa.hibernate.ddl-auto`
— gera schema a partir das entidades, sem controle de constraint, sem índice
pensado e sem caminho de migração.

### Decisão
- **Todo** schema nasce de migrações Flyway versionadas em
  `src/main/resources/db/migration`, nomeadas `V<n>__<descricao>.sql`.
- `ddl-auto` é **`validate`** em todos os ambientes. Nunca `update`, nunca
  `create-drop` — nem em teste, onde o Testcontainers roda as migrações reais.
- Migração aplicada **nunca** é editada. Corrigir exige nova migração.
- Nenhuma migração destrói dado sem que o comentário no topo do arquivo explique
  por quê.

### Consequências
- O schema no Git é o schema em produção, e o histórico de mudanças é auditável.
- `validate` transforma divergência entidade↔schema em erro na subida, não em
  bug silencioso três semanas depois.
- Testcontainers rodando as mesmas migrações significa que o teste valida a
  migração, e não só o código.
- Custo: toda coluna nova exige escrever DDL à mão. É o ponto.

### Alternativas rejeitadas
| Alternativa | Por que não |
|---|---|
| `ddl-auto: update` | Não gera `CHECK`, gera índices ruins, nunca remove nada e não tem rollback. É como o schema deixa de ser projetado |
| Liquibase | Equivalente. Flyway é SQL puro, que é mais legível para quem já sabe SQL — e aqui o schema é o coração |

---

## ADR-018 — Sem alta disponibilidade; backup diário testado

**Status:** Aceita · 2026-08-15

### Contexto
Na v1, o Google cuidava de durabilidade, backup e histórico de versões, de graça
e invisivelmente. Esse é o **único** risco real que a migração introduz.

### Decisão
- **Sem** réplica, sem failover, sem cluster. `restart: unless-stopped` no Compose
  é toda a resiliência de processo.
- **`pg_dump` diário**, comprimido, com retenção de 30 diários + 12 mensais.
- Cópia **fora do Pi** — Google Drive, `rclone` ou outro host. Backup no mesmo
  disco do banco não é backup.
- **Restauração testada trimestralmente**, em base descartável, com o resultado
  registrado. Backup nunca restaurado não é backup: é esperança.
- Falha do backup **notifica**. Backup que falha em silêncio é pior que backup
  nenhum, porque produz confiança falsa.

### Consequências
- Janela de perda: até 24 h. Aceito — a alternativa (replicação) custa mais
  complexidade do que o dado vale.
- Queda de energia ou internet derruba o sistema. Aceito: você perde o acesso,
  não o dado.
- É a única rotina operacional recorrente do projeto. Procedimento em
  `03E_DEPLOYMENT.md` §6.

### Alternativas rejeitadas
| Alternativa | Por que não |
|---|---|
| Replicação para segunda instância | Dobra a infraestrutura para proteger contra falha de hardware que o backup já cobre com RPO de 24 h |
| Postgres gerenciado na nuvem | Contraria o motivo de ter saído do Apps Script: latência e controle |
| Snapshot do volume Docker | Não é consistente com o banco em execução. `pg_dump` é |

---

## ADR-019 — Postgres em SSD USB, nunca em cartão SD

**Status:** Aceita · 2026-08-15

### Contexto
Cartões SD têm ciclos de escrita limitados e falham **sem aviso e sem sintoma
prévio**. Um banco de dados escreve constantemente: WAL, checkpoints,
`autovacuum`. É o pior caso possível de carga para SD.

### Decisão
O volume de dados do Postgres reside em **SSD via USB 3.0**, montado em caminho
fixo e declarado como *bind mount* no Compose. O cartão SD guarda apenas o SO e
as imagens Docker.

### Consequências
- Um item de hardware a comprar, se ainda não houver.
- Ganho de desempenho relevante como efeito colateral: SSD USB 3.0 supera SD em
  uma ordem de grandeza em IOPS aleatórias, que é exatamente o padrão de acesso
  de um banco.
- O `docker-compose.yml` fica preso a um caminho de host. Documentado em
  `03E_DEPLOYMENT.md` §3.

### Alternativas rejeitadas
| Alternativa | Por que não |
|---|---|
| Banco no SD com backup frequente | Backup cobre perda de dado, não cobre o Pi parar de funcionar no meio da semana de prova |
| Volume Docker nomeado no SD | Mesma superfície física. O tipo de volume não muda o hardware |

---

## ADR-020 — PostgreSQL como banco de dados

**Status:** Aceita · 2026-08-15 · substitui ADR-001

### Contexto
A v1 usava Google Sheets, escolhido pela visibilidade dos dados e por não exigir
infraestrutura. O custo foi ausência de transações, tipos, constraints e índices
— e a latência que motivou toda a migração.

### Decisão
PostgreSQL 17, em container, como único armazenamento persistente.

### Consequências
- Transações ACID (ADR-024), constraints declarativas (ADR-021), tipos reais
  (ADR-022), índices — e consulta em milissegundos.
- **Perde-se a inspeção pelo próprio usuário.** Antes, abrir a planilha resolvia
  qualquer dúvida. Isso era mais valioso do que parece, e a compensação é dupla:
  a interface precisa ser boa o bastante para não haver o que inspecionar, e a
  Sprint 8 entrega exportação CSV.
- Ganha-se responsabilidade de backup (ADR-018).
- Extensão `unaccent` é requisito, para a unicidade insensível a acento (INV-003).

### Alternativas rejeitadas
| Alternativa | Por que não |
|---|---|
| SQLite | Suficiente para um usuário e mais simples de operar. Perdido: `unaccent`, índice parcial com predicado rico, tipos de data com fuso, e a facilidade de crescer. Num Pi que aguenta Postgres folgadamente, a economia não se paga |
| MySQL/MariaDB | Sem `CHECK` historicamente confiável, sem índice parcial. Índice parcial é justamente o que faz INV-007 virar constraint |
| Continuar no Sheets com backend próprio | A API do Sheets tem cota e ~200 ms por chamada. Manteria o gargalo que motivou a mudança |

---

## ADR-021 — Constraints no banco, lógica de negócio fora dele

**Status:** Aceita · 2026-08-15 · substitui ADR-002 (revogada)

### Contexto
A ADR-002 proibia lógica na planilha, porque fórmula é lógica invisível e não
testável. Com Postgres, a mesma tentação reaparece em forma mais poderosa:
*triggers*, *stored procedures*, *rules*. E, junto com ela, uma questão nova: o
banco agora **pode** garantir integridade — e deveria.

### Decisão

**Vai para o banco** — porque é declarativo, verificável e impossível de burlar:

- `PRIMARY KEY`, `FOREIGN KEY`, `UNIQUE` (inclusive parcial e por expressão);
- `NOT NULL`, `DEFAULT`;
- `CHECK` sobre faixa e coerência entre colunas da mesma linha;
- índices.

**Não vai para o banco** — porque é lógica:

- *triggers* de qualquer espécie;
- *stored procedures* e funções que implementem regra de negócio;
- `RULE`;
- lógica em `DEFAULT` que dependa de outra tabela;
- **qualquer regra de `05_BUSINESS_RULES.md`**.

Exceção única: funções `IMMUTABLE` puramente utilitárias usadas em índice por
expressão — por exemplo, a normalização de nome de INV-003. Não são regra: são
o equivalente a uma função de biblioteca.

### Consequências
- ~8 das 23 invariantes viram garantia do banco (`02B_INVARIANTS.md` §1), o que é
  ganho puro: constraint não pode ser esquecida por um `if` faltando.
- O restante fica no Service, testável e depurável.
- Uma violação de constraint chega ao Java como exceção do driver, e precisa ser
  traduzida para erro de domínio legível — trabalho pequeno, feito num único
  `@RestControllerAdvice` (`03_BACKEND.md` §7).

### Alternativas rejeitadas
| Alternativa | Por que não |
|---|---|
| Toda a integridade no Service, banco "burro" | Repete o erro da v1 por escolha. Deixa o banco aceitar dado inconsistente vindo de um `psql` manual ou de um script de importação |
| Regra de negócio em trigger | Invisível para quem lê o Java, indepurável, intestável com Testcontainers de forma decente, e cria duas fontes de verdade sobre o mesmo comportamento |

---

## ADR-022 — Tipos temporais nativos

**Status:** Aceita · 2026-08-15 · substitui ADR-003

### Contexto
A v1 armazenava datas como **texto** ISO-8601, porque o Sheets reinterpretava
`Date` no fuso da planilha e a serialização do Apps Script era inconsistente.
Nenhum desses problemas existe agora.

### Decisão

| Conceito | Postgres | Java | JSON |
|---|---|---|---|
| Data (dia civil) | `date` | `LocalDate` | `"2026-08-15"` |
| Instante | `timestamptz` | `Instant` | `"2026-08-15T17:32:07Z"` |

- Fuso da aplicação e do container: `America/Sao_Paulo`, via `TZ`.
- `timestamptz`, **nunca** `timestamp` sem fuso. Postgres guarda em UTC e
  converte na leitura; `timestamp` puro guarda um número sem significado.
- Datas de domínio (`sessao.data`, `revisao.data_prevista`) são `date`: são dias
  civis, não instantes. Somar 7 dias a uma data não deve envolver horário de
  verão.
- "Hoje" vem de um `Clock` **injetado**, nunca de `LocalDate.now()` direto — é o
  que torna as regras testáveis com data congelada.

### Consequências
- Comparação, ordenação e aritmética de datas são feitas pelo banco, corretamente.
- Índice em coluna de data funciona como se espera.
- O formato **no fio continua idêntico ao da v1**: strings ISO-8601. O frontend
  não percebe a mudança.
- Jackson serializa `LocalDate`/`Instant` em ISO desde que
  `WRITE_DATES_AS_TIMESTAMPS` esteja desligado — configuração explícita, não
  suposição.

### Alternativas rejeitadas
| Alternativa | Por que não |
|---|---|
| Manter texto ISO | Abre mão de tipo, índice e aritmética por hábito herdado de uma limitação que não existe mais |
| `java.util.Date` / `Calendar` | Mutáveis, com mês base zero. Não há motivo em 2026 |
| `timestamp` sem fuso | Guarda um número sem referência. Funciona até a primeira mudança de fuso ou o primeiro backup restaurado em outra máquina |

---

## ADR-023 — Chaves primárias `IDENTITY`

**Status:** Aceita · 2026-08-15 · substitui ADR-004

### Contexto
A v1 usava IDs de texto com prefixo (`SES000901`), gerados lendo o máximo da
coluna e somando 1 — o que exigia lock e uma leitura por inserção. Existiam por
uma razão única: **o Sheets não tem chave primária nem sequence.**

### Decisão
`BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY` em todas as tabelas.
No Java: `@Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id`.

### Consequências
- Some a geração de ID, some a leitura extra, some o motivo pelo qual o lock
  existia (ADR-024 remove o resto).
- URLs REST idiomáticas: `/api/sessoes/901`.
- **Perde-se a legibilidade do prefixo em log.** Compensado: a trilha de
  auditoria registra sempre a entidade junto do id (`entidade=SESSAO id=901`), o
  que dá a mesma informação sem embutir tipo em dado.
- IDs são sequenciais e previsíveis. Irrelevante: não há acesso público
  (ADR-015), logo não há enumeração a temer.
- A invariante INV-020 (prefixo correto) é **revogada**. O tipo agora é garantido
  pela FK, que é mais forte do que uma convenção de string jamais foi.

### Alternativas rejeitadas
| Alternativa | Por que não |
|---|---|
| Manter o formato com prefixo via `DEFAULT` + sequence | Tecnicamente possível. Mantém a legibilidade, mas paga PK de texto em todo índice e JOIN, e preserva uma convenção cujo motivo desapareceu |
| UUID v4 | Índice fragmentado, 16 bytes por chave, ilegível — em troca de unicidade distribuída que um sistema de usuário único não precisa |
| UUID v7 | Resolve a fragmentação, mas continua sendo complexidade sem problema correspondente |

---

## ADR-024 — Transações ACID substituem lock manual

**Status:** Aceita · 2026-08-15 · substitui ADR-005

### Contexto
A v1 serializava **toda** escrita com `LockService`, por 30 segundos, porque não
havia transação: uma operação que gravava sessão + revisão podia deixar metade
feita. O wrapper precisava até ser reentrante, para que um service pudesse chamar
outro.

### Decisão
- Toda operação de escrita roda em uma transação, declarada com `@Transactional`
  **na camada de Service** — nunca no Controller, nunca no Repository.
- Nível de isolamento: **`READ COMMITTED`** (default do Postgres).
- Concorrência é tratada por **constraint**, não por lock: a unicidade de revisão
  pendente (INV-007) é um índice único parcial, e duas requisições simultâneas
  fazem a segunda falhar com violação — que é traduzida em `409 Conflict`.
- Leituras usam `@Transactional(readOnly = true)`.
- Bloqueio pessimista (`PESSIMISTIC_WRITE`) **não é usado** em nenhum ponto. Se
  algum caso o exigir, é sinal de modelagem errada e exige novo ADR.

### Consequências
- Atomicidade real: ou a sessão e a revisão são gravadas, ou nenhuma das duas.
  Some a nota da v1 sobre "sessão sem revisão é problema menor que sessão
  perdida" — o problema deixou de existir.
- Some o wrapper de lock, o contador de reentrância e os testes que os cobriam.
- Serialização deixa de ser global: duas operações em assuntos diferentes não se
  esperam.
- Requer disciplina com o *proxy* do Spring: `@Transactional` em chamada interna
  do mesmo bean **não funciona**. Regra em `03B_TRANSACTIONS.md` §2.

### Alternativas rejeitadas
| Alternativa | Por que não |
|---|---|
| `SERIALIZABLE` | Custo e risco de erro de serialização para proteger contra anomalias que as constraints já cobrem |
| Lock pessimista por assunto | Reintroduz manualmente o que a transação faz melhor, com risco de deadlock |
| Lock otimista com `@Version` | Útil quando dois usuários editam a mesma linha. Só existe um usuário |

---

## ADR-025 — API REST orientada a recurso

**Status:** Aceita · 2026-08-15 · substitui ADR-006

### Contexto
A v1 tinha **uma** função global `api(action, payload)`, porque
`google.script.run` só enxerga funções globais. A limitação era da plataforma.

### Decisão
API REST por recurso, sob `/api`:

```
GET    /api/disciplinas              POST   /api/disciplinas
GET    /api/disciplinas/{id}         PATCH  /api/disciplinas/{id}
POST   /api/disciplinas/{id}/desativar

GET    /api/sessoes?de=&ate=&assuntoId=
POST   /api/sessoes
PATCH  /api/sessoes/{id}
POST   /api/sessoes/{id}/arquivar

GET    /api/revisoes/pendentes
POST   /api/revisoes/{id}/concluir
POST   /api/revisoes/{id}/cancelar

GET    /api/dashboard
GET    /api/recomendacoes
```

- Semântica HTTP de verdade: `200`, `201` com `Location`, `204`, `400`, `404`,
  `409`, `422`.
- Transições de estado que não são CRUD viram **sub-recurso de ação**
  (`POST /{id}/concluir`), não `PATCH` com campo mágico. É explícito, é
  auditável, e o verbo diz o que aconteceu.
- Sem versionamento na URL. Frontend e backend são publicados juntos
  (`03E_DEPLOYMENT.md`); não existe cliente desatualizado.

### Consequências
- Aproveita `@RestController`, validação declarativa, negociação de conteúdo e
  tratamento de erro do Spring — em vez de reimplementar cada um.
- Cacheável, testável com `curl`, inspecionável no DevTools.
- O catálogo de 37 ações da v1 mapeia praticamente 1:1 (`06_API.md` §9).

### Alternativas rejeitadas
| Alternativa | Por que não |
|---|---|
| Manter `POST /api {action, payload}` | Preservaria o `06_API.md` e o `api.js` intactos. Mas joga fora status HTTP, cache e ferramental, e soa estranho em Spring. Portabilidade de documento não é razão para carregar uma limitação de plataforma abandonada |
| GraphQL | Um cliente, consultas conhecidas. Traria schema, resolvers e problema de N+1 em troca de flexibilidade que ninguém vai usar |

---

## ADR-026 — Erros em RFC 9457 Problem Details

**Status:** Aceita · 2026-08-15 · substitui ADR-008

### Contexto
A v1 embrulhava tudo em `{ok, data, meta}` / `{ok, error}`, com 9 códigos de erro
no corpo, porque `google.script.run` não tinha status HTTP. Agora tem.

### Decisão
- **Sucesso:** o recurso, direto no corpo. Sem envelope. O status HTTP diz se
  deu certo.
- **Erro:** `application/problem+json` conforme **RFC 9457**, usando o
  `ProblemDetail` nativo do Spring:

```json
{
  "type": "https://estudos.local/errors/regra-de-negocio",
  "title": "Revisão já concluída",
  "status": 409,
  "detail": "Esta revisão já foi concluída em 16/08/2026.",
  "instance": "/api/revisoes/124/concluir",
  "codigo": "REVISAO_JA_CONCLUIDA",
  "requestId": "k2m9x4qp",
  "campo": null
}
```

- `detail` é português, voltado ao usuário final, sem stack trace. O frontend
  exibe `detail`, nunca compõe mensagem a partir do código.
- `codigo`, `requestId` e `campo` são extensões — permitidas pela RFC.
- `requestId` está **também** no cabeçalho `X-Request-Id` de toda resposta,
  inclusive as de sucesso.
- Um único `@RestControllerAdvice` monta tudo. Nenhum controller trata exceção.

### Consequências
- Um padrão publicado em vez de um formato caseiro; clientes e ferramentas o
  entendem.
- A lista de erros da v1 vira mapeamento código → status
  (`06_API.md` §3): `VALIDATION` → 422, `NOT_FOUND` → 404,
  `BUSINESS_RULE`/`CONFLICT` → 409, e assim por diante.
- O frontend deixa de checar `res.ok` do envelope e passa a checar `response.ok`
  do `fetch` — mais simples, não menos.

### Alternativas rejeitadas
| Alternativa | Por que não |
|---|---|
| Manter o envelope `{ok, data}` | Duplica o que o status HTTP já diz, e obriga a checar duas coisas em vez de uma |
| Só o status, sem corpo estruturado | `422` não diz *qual campo*. A interface precisa disso para destacar o input |

---

## ADR-027 — Domínio em português, ponte por naming strategy

**Status:** Aceita · 2026-08-15 · substitui ADR-009

### Contexto
A v1 misturava: colunas em português `snake_case`, campos em camelCase mecânico,
classes e métodos em inglês. A conversão era feita à mão por `Utils.Text`, e o
resultado eram nomes híbridos como `findByAssuntoId`.

### Decisão
**O domínio é português, do banco até a tela.** O andaime técnico é inglês.

| Elemento | Idioma / caso | Exemplo |
|---|---|---|
| Tabela, coluna | português, `snake_case` | `sessao.questoes_corretas` |
| Entidade, campo Java | português, `camelCase` | `Sessao.questoesCorretas` |
| Sufixo técnico de classe | inglês | `SessaoRepository`, `SessaoService`, `SessaoController` |
| Método de repositório | Spring Data, em português | `findByAssuntoIdAndAtivoTrue` |
| Enum e seus valores | português | `TipoSessao.TEORIA` |
| Recurso REST | português, plural | `/api/sessoes` |
| Campo JSON | português, `camelCase` | `questoesCorretas` |
| Texto de UI | português | "Revisões pendentes" |

A ponte `camelCase ↔ snake_case` é feita pela
`CamelCaseToUnderscoresNamingStrategy` do Hibernate, **nativamente**. Não há
código de conversão, não há dicionário, não há `@Column(name = ...)` — exceto
onde o nome divergir, o que não deve acontecer.

### Consequências
- Um único vocabulário do banco à tela. `questoes_corretas` →
  `questoesCorretas` → `questoesCorretas` no JSON → "Questões corretas" na tela.
- Some `Utils.Text` inteiro, e com ele a exigência de que toda coluna casasse com
  um regex para a conversão ser reversível.
- Somem os nomes híbridos.
- Fica estranho para quem não fala português. Não é o caso.

### Alternativas rejeitadas
| Alternativa | Por que não |
|---|---|
| Tudo em inglês | O domínio é concurso público brasileiro. `subject`, `discipline` e `review` obrigam tradução mental constante, e "assunto" não é bem "subject" |
| Manter o híbrido da v1 | Existia por inércia. Agora que o JPA faz a ponte, não há o que preservar |

---

## ADR-028 — Maven no backend, zero build no frontend

**Status:** Aceita · 2026-08-15 · substitui ADR-010

### Contexto
A v1 proibia qualquer etapa de build, porque o Apps Script não a suporta. Java
exige compilação — mas o frontend, não.

### Decisão
- **Backend:** Maven, via *wrapper* (`./mvnw`). Build multi-estágio no
  Dockerfile.
- **Frontend:** **nenhum** build. Tailwind e Alpine por CDN, arquivos estáticos
  em `src/main/resources/static`, servidos como estão. Sem npm, sem bundler, sem
  PostCSS, sem `node_modules`.

### Consequências
- O que você escreve no HTML é o que roda no navegador. Depuração direta.
- Sem TypeScript e sem JSX no frontend — a tipagem forte fica onde importa, que é
  o backend.
- Tailwind por CDN carrega o runtime completo (~400 KB) e gera classes em tempo
  de execução. Aceitável: um usuário, rede local, carga única.
- Build de imagem ARM64 no próprio Pi leva alguns minutos. Mitigável com cache de
  camadas Maven (`03E_DEPLOYMENT.md` §5).

### Alternativas rejeitadas
| Alternativa | Por que não |
|---|---|
| Gradle | Equivalente. Maven tem integração mais previsível com Spring Boot e um `pom.xml` que se lê de cima a baixo |
| Tailwind compilado com npm | Reduziria 400 KB para ~10 KB, ao custo de Node no pipeline e de um passo que, esquecido, produz estilo faltando em produção — falha silenciosa |
| Vite + React/Vue | Reescreveria `04_FRONTEND.md` e boa parte do `07_UI.md` para ganhar capacidade que este sistema não usa |

### Revisão — 2026-08-31, antes da primeira tela

`02_JORNADAS` §5.1 exige **registro otimista com falha não destrutiva**: a
tela muda de estado em milissegundos, a persistência acontece atrás, e se
falhar o dado digitado permanece na tela com reenvio a um clique. Avaliada
conscientemente contra a decisão de zero build (era a pendência registrada
na seção final deste documento, agora resolvida).

**Mantida.** A exigência é do tipo simples — estado local reativo (`x-data`
do Alpine já cobre) mais um `try/catch` em volta do `fetch` que não limpa o
formulário no erro. Não pede biblioteca de gerência de estado nem etapa de
build. O caso que pediria mais — fila offline com reconciliação automática
— está fora de escopo por decisão própria de `02_JORNADAS §5.1` ("é
maquinaria real... se o uso mostrar que a conexão cai com frequência, vira
decisão própria, justificada"). Mecânica concreta em `docs/04_FRONTEND.md`.

---

## ADR-029 — Sem cache de aplicação

**Status:** Aceita · 2026-08-15 · substitui ADR-012

### Contexto
A v1 cacheava `disciplinas`, `assuntos` e `config` porque cada leitura de aba
custava 100–300 ms. Uma consulta indexada no Postgres custa menos de 5 ms.

### Decisão
**Nenhum cache de aplicação.** Sem `@Cacheable`, sem Caffeine, sem Redis, sem
cache de segundo nível do Hibernate.

O que existe, e é suficiente:

- índices adequados (`02_DATABASE.md` §8);
- *connection pool* (HikariCP, default do Spring Boot);
- cache do próprio Postgres (`shared_buffers`);
- o `PersistenceContext` de primeiro nível, que já deduplica dentro de uma
  transação.

### Consequências
- **Some a classe inteira de bug mais irritante da v1:** dado velho na tela por
  invalidação esquecida. Não há o que invalidar.
- Somem `03B` §2 inteiro, a matriz de invalidação e os testes de cache.
- Se um dia algum endpoint ficar lento, a resposta é índice ou consulta melhor —
  não cache. Cache é a última medida, não a primeira, e exigiria novo ADR com
  medição anexada.

### Alternativas rejeitadas
| Alternativa | Por que não |
|---|---|
| `@Cacheable` em disciplinas e assuntos | Economiza ~4 ms e reintroduz invalidação, que é o problema difícil. Trocaria o gargalo real por um risco de correção |
| Cache de segundo nível do Hibernate | Invalidação implícita, difícil de raciocinar, e efeitos surpreendentes em consulta nativa |

---

## ADR-030 — SPA estática servida pelo Spring

**Status:** Aceita · 2026-08-15 · substitui ADR-013

### Contexto
A v1 servia a SPA por `doGet`, montando o HTML com `include()` porque o
`HtmlService` só inclui arquivos `.html` e cada `doGet` custava 1–3 s.

### Decisão
- Arquivos estáticos em `src/main/resources/static`, servidos pelo próprio
  Spring Boot. **Um container, um processo, zero CORS.**
- Continua **SPA de página única** com navegação por hash (`#/dashboard`), como
  na v1: `index.html` carrega tudo e a navegação não vai ao servidor.
- Componentes viram arquivos `.html` reais, carregados no `index.html` — some o
  `include()` do `HtmlService` e some a exigência de que CSS e JS morem dentro de
  `.html`. Agora `styles.css` é `.css` e `api.js` é `.js`.
- Cache HTTP: `no-cache` no `index.html`, `max-age` longo nos assets versionados.

### Consequências
- Carga inicial cai de 1–3 s para dezenas de milissegundos.
- Deploy é atômico: frontend e backend saem no mesmo JAR, na mesma imagem.
- O `04_FRONTEND.md` fica mais simples que na v1: sem `<base target="_top">`,
  sem `<?!= ?>`, sem as três armadilhas do `HtmlService`.
- Ainda sem *server-side rendering*. Correto: um usuário, sem SEO, sem
  necessidade.

### Alternativas rejeitadas
| Alternativa | Por que não |
|---|---|
| Nginx em container separado | Adiciona CORS ou proxy e mais um serviço para manter no Pi, em troca de separação que não resolve problema nenhum aqui |
| Thymeleaf com renderização no servidor | Volta a pagar ida ao servidor por navegação — o oposto do motivo da migração |

---

## ADR-031 — Unicidade por tentativa e por revisão pendente

**Status:** Aceita · 2026-08-20

### Contexto

Duas invariantes de `01_DOMINIO.md` §10 exigem unicidade sobre um **subconjunto**
de linhas, não sobre a tabela inteira:

- **D-05** — no máximo uma revisão *pendente* por assunto. Revisões concluídas e
  canceladas do mesmo assunto são normais e numerosas; uma unicidade sobre a
  tabela inteira as impediria de existir.
- **D-45** — a mesma tentativa de registro não pode gravar duas vezes. A chave é
  do **identificador da tentativa**, gerado pelo cliente ao abrir a tela — nunca
  do conteúdo, porque recuperar o mesmo assunto duas vezes no mesmo dia é uso
  legítimo, e uma restrição sobre `assunto + data + resultado` produziria falso
  positivo.

`03_INVARIANTES.md` §4.1 aponta D-05 como **a crítica**: é a única com
concorrência real — uma revisão sendo cumprida enquanto outra é agendada para o
mesmo assunto. Verificar-antes-de-escrever no serviço deixa uma janela: entre o
`SELECT` que confirma "não há pendente" e o `INSERT`, cabe outra transação
inserindo a sua. Sob carga baixa (o uso real deste sistema, de um usuário só) a
falha é rara — e é exatamente por isso que testes sem concorrência simulada
nunca a pegam, e ela sobrevive até aparecer em produção como duas escadas
paralelas, silenciosamente.

### Decisão

Ambas por **unicidade na persistência**, restrita ao subconjunto. Nunca por
verificação prévia no serviço.

| Regra | Mecanismo | Nome da restrição |
|---|---|---|
| D-05 | Índice único **parcial** em `revisao(assunto_id) WHERE situacao = 'PENDENTE'` | `ux_revisao_d05_pendente_por_assunto` |
| D-45 | Índice único em `sessao(tentativa_id)` | `ux_sessao_d45_tentativa_unica` |

Dois detalhes do `tentativa_id` que decidem se a proteção de D-45 funciona de
verdade, e não só no caso feliz:

- **Gerado ao abrir a tela, nunca ao enviar.** Gerado no envio, um duplo clique
  dispara duas submissões com dois identificadores diferentes, e a unicidade não
  pega nada.
- **O reenvio devolve sucesso com o resultado original, nunca erro.** O dado já
  foi gravado na primeira tentativa; devolver erro faria a tela mostrar falha
  para algo que teve sucesso, e o usuário não tem como saber que houve reenvio.

### Consequências

- D-05 e D-45 viram garantia do Postgres, não um `if` no Service que uma
  transação concorrente poderia escapar.
- O Service precisa capturar a exceção de violação de unicidade do driver e
  traduzi-la — mas para **destinos diferentes** por regra: D-05 vira erro de
  domínio (a tentativa de agendar uma segunda revisão pendente é inválida);
  D-45 vira sucesso silencioso (o reenvio não é erro, é o mesmo evento). O ponto
  de tradução (`@RestControllerAdvice`, ADR-026) precisa distinguir as duas pelo
  **nome da restrição**, não só pelo código SQLSTATE — as duas colidem na mesma
  classe de exceção do driver.
- Abrir a tela duas vezes de propósito gera dois `tentativa_id` e duas sessões —
  corretamente: são duas tentativas de verdade, não uma reenviada.
- D-45 sozinha **não cobre** duas abas ou dois aparelhos editando a mesma revisão
  pendente ao mesmo tempo — isso são tentativas *diferentes* colidindo, não a
  mesma tentativa reenviada. Essa lacuna é coberta por ADR-032 (versionamento da
  `revisao`). Implementar só D-45 e considerar a concorrência resolvida é o erro
  provável.

### Alternativas rejeitadas

| Alternativa | Por que não |
|---|---|
| Verificar no serviço antes de inserir (`existe pendente? se não, grava`) | Race condition clássica de check-then-act: cabe outra transação entre a leitura e a escrita. Passa em qualquer teste sem concorrência simulada e falha em produção sob uso real |
| Restrição sobre `assunto + data + resultado` para D-45 | Falso positivo: recuperar o mesmo assunto duas vezes no mesmo dia é legítimo. A persistência não distingue clique repetido de segunda tentativa real pelo **conteúdo** — só um identificador próprio da tentativa resolve |
| `tentativa_id` gerado no momento do envio | O duplo clique produziria dois identificadores diferentes, e a unicidade nunca colidiria — a proteção depende do id nascer com a tela, não com o clique |
| Reenvio duplicado devolver erro HTTP ao cliente | O dado já foi persistido na primeira gravação; erro faria a tela reportar falha para uma operação que, do ponto de vista do usuário, teve sucesso |

---

## ADR-032 — Controle de versão na escrita

**Status:** Aceita · 2026-08-30 · Sprint 4

### Contexto

D-07, D-10 e D-11 (`01_DOMINIO.md` §5) leem a série histórica da revisão antes
de decidir o próximo nível — sobem, repetem ou regridem a escada. Duas
transações simultâneas escrevendo a mesma revisão pendente (duas abas, dois
aparelhos, o mesmo assunto sendo recuperado "ao mesmo tempo") leem o mesmo
estado e gravam por cima, sobrepondo um degrau da escada sem que nenhuma das
duas perceba.

### Decisão

Versionar a **revisão** — e só ela.

> **`revisao` ganha uma coluna de versão (`@Version`). A segunda gravação
> concorrente sobre a mesma linha é recusada.**

Como D-05 (ADR-031) já garante no máximo uma revisão *pendente* por assunto,
existe uma única revisão em disputa por vez — versionar o `Assunto` também
seria mais caro e não cobriria nada a mais.

**Não confundir com ADR-031.** São proteções complementares, contra ameaças
diferentes:

| Proteção | Contra o quê |
|---|---|
| Identificador de tentativa (D-45, ADR-031) | **A mesma** tentativa reenviada — rede ruim, botão clicado duas vezes |
| Versão na escrita (esta ADR) | **Tentativas diferentes** colidindo — duas abas, dois aparelhos, mesma revisão |

Implementar só uma das duas e considerar a concorrência resolvida é o erro
provável (já registrado como risco em ADR-031).

### Consequências

- Colisão vira `OptimisticLockingFailureException` do Spring Data — traduzida
  pelo `RevisaoService` para `ConflictException` (`codigo` `REVISAO_CONCORRENTE`,
  409), mesmo gesto de tradução de exceção das sprints anteriores.
- O cliente que perder a corrida recebe 409 e pode tentar de novo — a revisão
  não fica num estado inconsistente, só a escrita perdedora não vale.
- `Sessao` continua sem versão: ela é o registro do evento, imutável depois de
  criada (sem `PATCH`, `docs/SPRINT-3-SESSAO.md` §4) — não há "segunda
  gravação concorrente" para proteger ali.

### Alternativas rejeitadas

| Alternativa | Por que não |
|---|---|
| Versionar `Assunto` também | Mais caro (toda escrita de assunto participaria do controle) sem cobrir cenário a mais — só existe uma revisão em disputa por vez, D-05 garante isso |
| Lock pessimista (`SELECT ... FOR UPDATE`) | Seguraria conexões em espera num sistema de um usuário só, onde a colisão real é rara — custo permanente por um risco ocasional |
| Confiar só em D-45 (identificador de tentativa) | Protege reenvio da mesma tentativa, não duas tentativas genuinamente diferentes colidindo — é exatamente a lacuna que esta ADR fecha |

---

## Changelog

| Versão | Data | Mudança |
|---|---|---|
| 2.7.0 | 2026-09-04 | **ADR-037 escrita e aceita** (Sprint 10) — artefatos do planejamento (`Plano-de-Estudos-Automatizado`) chegam ao Pi via Google Drive + `rclone`, reaproveitando o mecanismo já aceito em ADR-018 (backup), em vez de API do Drive na aplicação (contradiria ADR-015/016) ou contato direto Tailscale PC→Pi (exigiria os dois sistemas online ao mesmo tempo). PDFs ficam só no Drive, referenciados por link — nunca chegam ao Pi. Total passa a 37 ADRs, 26 vigentes. No caminho: §1.1 (índice de vigentes) corrigida — ADR-034/035/036 nunca tinham sido adicionadas à lista, só ao total (mesma classe de lacuna que a 2.4.0 já tinha corrigido para a contagem) |
| 2.6.0 | 2026-09-01 | **ADR-036 escrita e aceita** — OpenAPI/Swagger via `springdoc-openapi-starter-webmvc-ui`, gerado do código (`@Tag`/`@Operation`/`@Schema`). Aplicado a todo Controller e todo `*Request`/`*Response`, a pedido do usuário — escopo completo desde o início, diferente do piloto de ADR-035. Total passa a 36 ADRs, 25 vigentes |
| 2.5.0 | 2026-09-01 | **ADR-035 escrita e aceita** — Bean Validation para forma do request (presença/faixa de campo), piloto em `AssuntoRequest`/`criar`. `@Valid` nunca em `atualizar` (PATCH): campo ausente lá é instrução, não erro. Constraint do banco continua como garantia final. Total passa a 35 ADRs, 24 vigentes |
| 2.4.0 | 2026-09-01 | Acerto de contagem: ADR-034 (`open-in-view: false`, aceita 2026-08-31) tinha sido escrita sem atualizar versão/total deste documento. Total passa a 34 ADRs, 23 vigentes — achado ao escrever ADR-035 |
| 2.3.0 | 2026-08-30 | **ADR-033 escrita e aceita** (Sprint 5) — sai de "Pendentes de redação" para vigente. A decisão final ficou mais simples que a prevista: nem visão de banco entrou, a classificação é uma consulta Spring Data + Java no `FrenteService`. Lista de "Pendentes de redação" removida — as duas que existiam (ADR-032, ADR-033) estão escritas. Total passa a 33 ADRs, 22 vigentes |
| 2.2.0 | 2026-08-30 | **ADR-032 escrita e aceita** (Sprint 4) — sai de "Pendentes de redação" para vigente, exatamente no momento previsto ("quando a sprint que implementa o roteamento da escada começar"). Total passa a 32 ADRs, 21 vigentes |
| 2.1.1 | 2026-08-28 | Correção administrativa: mecanismo de exclusão lógica do Assunto na ADR-011 dizia `status = 'ARQUIVADO'`, resíduo da v1 que sobrou do reset. O schema real (`V1__tabelas.sql`) e `docs/SPRINT-2-CADASTRO.md` §1.2 usam `ativo BOOLEAN`, igual Disciplina — corrigido para bater com o que existe. Achado durante a revisão do documento técnico da Sprint 2 (item 2.0 do `PROGRESSO.md`) |
| 2.1.0 | 2026-08-20 | **ADR-031 escrita e aceita** (Sprint 1, item 1.1) — sai de "Pendentes de redação" para vigente. Total passa a 31 ADRs, 20 vigentes |
| 2.0.0 | 2026-08-15 | Migração para PostgreSQL + Spring Boot. 16 ADRs novas (015–030); 10 substituídas; ADR-002 revogada; ADR-007, 011 e 014 mantidas |
| 1.0.0 | 2026-08-15 | Versão inicial. 14 ADRs |


## ADR-033 — Derivação por consulta simples, nunca materializada

**Status:** Aceita · 2026-08-30 · Sprint 5

### Contexto

Frente de estudo, backlog e consolidado (`01_DOMINIO.md` §6) são derivados
cruzando `Assunto`, `Sessao` e `Revisao` — sobre algumas centenas de linhas,
escala de um usuário só. Calcular a fase de cada assunto pede saber se ele
tem alguma `Sessao` e, se tiver, se está consolidado (mesma regra de D-10 da
Sprint 4).

### Decisão

**Nenhuma visão de banco, nem simples nem materializada.** Uma consulta
Spring Data busca os assuntos ativos de disciplinas ativas; a classificação
em `BACKLOG`/`FRENTE`/`CONSOLIDADO` acontece em Java, no `FrenteService`,
reaproveitando `RevisaoService.estaConsolidado` em vez de duplicar a regra.

Isso ainda é "visão simples" no espírito que a decisão original previa —
**nada aqui é persistido nem cacheado** — só que a simplicidade não pediu
nem uma `CREATE VIEW`: a query é uma só, e o cruzamento de "tem sessão" e
"está consolidado" é barato o bastante em Java para ~200 linhas que não
compensa mover pro SQL.

> Visão materializada continua fora de cogitação, pelo mesmo motivo de
> sempre: reintroduziria a divergência que D-16 existe para evitar, e
> ADR-029 já recusou cache de aplicação pelo mesmo raciocínio.

### Consequências

- Nenhuma coluna, tabela ou view nova nesta sprint.
- Se o volume real crescer a ponto da consulta em Java pesar (ordens de
  grandeza acima do que o produto foi dimensionado para atender, `01_DOMINIO`
  §6.6), a saída documentada é uma `CREATE VIEW` simples, não materializada —
  não um cache de aplicação.

### Alternativas rejeitadas

| Alternativa | Por que não |
|---|---|
| `CREATE VIEW` de leitura agora | Nenhum problema de desempenho a resolver ainda — decidir a favor de SQL sem medir é o "aperfeiçoar antes de precisar" que `00_PRODUTO` §3.2 proíbe para o agendador, e o raciocínio se aplica igual aqui |
| Coluna `fase` persistida, atualizada por trigger/serviço | É exatamente D-16: fase nunca tem coluna própria. Column persistida diverge do estado real assim que uma sessão nova é registrada sem passar pelo mesmo caminho |
| Cache de aplicação do resumo da frente | ADR-029 já recusou cache de aplicação neste sistema — mesmo motivo aqui: fonte de verdade dupla, uma delas eventualmente errada |

---

## ADR-034 — `open-in-view: false`

**Status:** Aceita · 2026-08-31 · Sprint 7/8

### Contexto

`spring.jpa.open-in-view` nunca foi decidido neste projeto — nem citado em
`application.yml`, nem em documento algum. O default do Spring Boot é
`true`: a sessão do Hibernate fica aberta até a view (aqui, a serialização
JSON do Controller) terminar, então qualquer acesso `LAZY` esquecido no
mapper "funciona" mesmo sem `join fetch`, silenciosamente, com uma consulta
extra por associação.

Isso escondeu dois casos reais: `ErroMapper.toResponse` e
`SimuladoMapper.toResultadoResponse` leem `.getAssunto().getId()` /
`.getDisciplina().getId()` de associações `@ManyToOne(LAZY)` sem
`join fetch` na consulta (`ErroRepository.findByAssuntoId`,
`ResultadoSimuladoRepository.findBySimuladoIdIn`) — N+1 real em
`GET /api/erros` e `GET /api/simulados`, só visível com dado suficiente pra
notar a diferença.

Achado durante a revisão de status das Sprints 7/8 (`/agents/mentor.md`):
com OSIV ligado, **nenhum teste consegue provar a ausência desse bug** — os
testes de listagem são todos `@Transactional`, então a sessão já fica aberta
pelo teste, com ou sem OSIV. A suíte inteira passa hoje (79/79) sem que isso
prove nada sobre o caminho real de produção.

### Decisão

**`open-in-view: false`.** A sessão do Hibernate fecha quando o Service
termina; qualquer `LAZY` sem `join fetch` explícito na consulta vira
`LazyInitializationException` **na hora de escrever o repositório**, não uma
consulta extra silenciosa em produção. Coerente com o padrão que o projeto
já segue desde a Sprint 2 (`AssuntoRepository.listarParaExportacao`,
`listarAtivosDeDisciplinasAtivas`: `LAZY` com `join fetch` sempre explícito,
nunca navegação implícita) e com ADR-029 (sem cache — a resposta a "isso
ficou lento" é consulta melhor, não mecanismo automático).

`ErroRepository.findByAssuntoId` e
`ResultadoSimuladoRepository.findBySimuladoIdIn` corrigidos com
`join fetch` (o segundo, com `left join fetch` onde a associação é opcional)
na mesma mudança que desliga o OSIV — sem isso a aplicação nem sobe para os
dois endpoints afetados.

### Consequências

- Qualquer DTO que precisar de um campo de associação `LAZY` daqui pra
  frente exige `join fetch` na consulta do repositório, igual já valia para
  `Assunto.disciplina`. Isso já era o padrão; agora é **também** o que
  impede a aplicação de subir se for esquecido, não só o checklist de
  `09_CODE_STYLE.md` §9.
- Testes que precisam provar o caminho real de produção (não só HTTP/JSON)
  não podem ser `@Transactional` — mesma disciplina que
  `IntegracaoTestBase` já documenta para provar commit.

### Alternativas rejeitadas

| Alternativa | Por que não |
|---|---|
| Deixar `true` (default) | É exatamente a escolha de tutorial que o projeto evita em outro lugar (Lombok, `ddl-auto: update`, DTO de erro próprio) — aqui teria entrado por omissão, não por decisão |
| `@EntityGraph` nos repositórios em vez de `join fetch` | Mesmo efeito, mas o projeto já tem o padrão de `@Query` com `join fetch` comentado (`AssuntoRepository`, `RevisaoRepository`) — duas formas de resolver o mesmo problema não vale a pena introduzir agora |

---

## ADR-035 — Bean Validation para forma do request, não para regra de domínio

**Status:** Aceita · 2026-09-01

### Contexto

Campo obrigatório de Request hoje só é garantido pela constraint do banco,
traduzida em `traduzirViolacaoDeIntegridade` — ex.: `AssuntoRequest.ordem`
(D-41) via `ck_assunto_d41_ordem_obrigatoria`. Isso gasta um `INSERT` inteiro
contra o Postgres pra rejeitar algo decidível só olhando o corpo da
requisição. É diferente de D-05/D-45 (ADR-031): aquelas *precisam* do banco
porque têm concorrência real (janela entre checar e escrever); presença de
campo não tem disputa nenhuma — nada mais preenche o campo entre eu ler o
JSON e eu decidir se ele veio.

Pior: nem todo campo obrigatório tem tradução. `nome`, `peso` e
`dificuldade_percebida` ausentes ou fora de faixa (`ck_assunto_peso`,
`ck_assunto_dificuldade_percebida`) não têm `branch` em
`traduzirViolacaoDeIntegridade` — caem em `DataIntegrityViolationException`
não tratada, HTTP 500, não o 422 com `codigo` que o resto da API garante
(ADR-026). Achado ao revisar `AssuntoRequest` (`/agents/mentor.md`).

### Decisão

Bean Validation (`spring-boot-starter-validation`) para validação de
**forma**: presença e faixa de campo, decidível sem consultar nada. Anotação
no record de Request; `@Valid` só no endpoint de **criação** — nunca no de
atualização parcial, porque lá "campo ausente" é uma instrução legítima ("não
mude isto"), não um erro. Aplicado primeiro em `AssuntoRequest` /
`AssuntoController.criar`, como piloto — cada Request migra quando alguém for
mexer nele, não todos de uma vez (mesma disciplina de "documento nasce quando
a sprint usa", `CLAUDE.md`).

`GlobalExceptionHandler` ganha um `@ExceptionHandler(MethodArgumentNotValidException.class)`,
convertendo pro mesmo `ProblemDetail` (ADR-026): o `codigo` vem da própria
mensagem da anotação (`@NotNull(message = "ORDEM_OBRIGATORIA")`), reusando a
convenção `<CAMPO>_OBRIGATORIO(A)` que os erros de domínio já seguem. Só o
primeiro erro de campo vira resposta — a API já devolve um erro por vez em
todo o resto.

A constraint do banco **não sai**. Continua a garantia final — mesmo
princípio de sempre (D-05/ADR-031, D-47/D-48): nunca confiar só na camada de
aplicação. Bean Validation intercepta o caminho feliz mais cedo; o
`CHECK`/`NOT NULL` é o que de fato torna a regra inviolável, inclusive contra
quem pular o Controller (chamada direta ao Service, script, bug futuro).

### Consequências

- `criar` fica mais barato no caminho de erro: rejeita sem abrir transação
  nem tocar o banco.
- `atualizar` continua sem `@Valid` — se um dia precisar validar forma de
  campo *presente* nessa rota, é checagem manual; "ausente" nunca é erro ali.
- A constraint do banco correspondente deixa de ser exercida pelo caminho
  HTTP normal — só dispara se algo pular a validação. O teste estrutural que
  a prova continua existindo (`RestricoesInvariantesTest`), não só o teste
  HTTP da anotação.

### Alternativas rejeitadas

| Alternativa | Por que não |
|---|---|
| Checagem manual (`if (x == null) throw ...`) no Service, como `ErroService.assuntoId` | Funciona, mas não escala: cada campo obrigatório vira um `if` a mais pra manter. Bean Validation é declarativo e padrão do ecossistema — a ferramenta certa já existe |
| Generalizar para todo `*Request` na mesma mudança | Maior superfície pra revisar de uma vez, sem ainda ter visto o padrão funcionando contra um caso real. Migra Request por Request |
| `@Valid` também em `atualizar` (PATCH) | Forçaria todo campo a estar presente numa atualização parcial — contradiz a própria razão de `atualizar` existir |

---

## ADR-036 — OpenAPI/Swagger gerado a partir do código

**Status:** Aceita · 2026-09-01

### Contexto

O projeto chegou a 10 controllers e mais de vinte DTOs sem nenhuma
documentação de API navegável — quem for consumir (o frontend da Sprint 9,
ou teste manual) só tem o código-fonte ou os arquivos `.http` de
`http/`. Os `.http` cobrem cenário de teste; não substituem uma referência
de forma de payload por endpoint.

### Decisão

`springdoc-openapi-starter-webmvc-ui` (3.1.0, compatível com Spring Boot
4 — o projeto está em 4.1.0). Gera `/v3/api-docs` e serve Swagger UI em
`/swagger-ui.html`, direto das anotações do código — não há YAML/JSON de
especificação escrito à mão pra divergir do código com o tempo.

Convenção:

| Anotação | Onde | Pra quê |
|---|---|---|
| `@Tag(name, description)` | Classe do Controller | Agrupa os endpoints na UI por recurso |
| `@Operation(summary, description)` | Cada método de endpoint | O que aquela chamada faz, em uma frase |
| `@Schema(description, example)` | Cada campo de Request/Response | O que o campo significa; exemplo de valor |

`@Schema` não repete `required` — springdoc já deriva isso sozinho das
anotações de Bean Validation (`@NotNull` etc., ADR-035) no mesmo campo.
Erro (`ProblemDetail`/`codigo`/`campo`/`requestId`, ADR-026) não ganha
`@Schema` por endpoint: é o mesmo formato em toda a API, documentado uma vez,
não replicado quarenta vezes.

Rede da Tailscale (`CLAUDE.md`), não a internet pública — Swagger UI fica
habilitado sem perfil separado. Reavaliar se a exposição de rede mudar.

### Consequências

- Nenhuma mudança de comportamento de runtime além da nova rota
  `/v3/api-docs` e `/swagger-ui.html`.
- A fonte de verdade continua sendo `especificacao/` e `docs/` — o Swagger
  documenta **forma de payload**, não decide regra. Divergência entre o
  `@Schema` e o texto de `01_DOMINIO`/`02_JORNADAS` é defeito de anotação,
  não motivo pra reabrir a regra.
- Todo `*Request`/`*Response` e todo Controller ganham a anotação nesta
  mudança — ao contrário de ADR-035 (que migrou um Request só), aqui o
  usuário pediu escopo completo de uma vez.

### Alternativas rejeitadas

| Alternativa | Por que não |
|---|---|
| Escrever o YAML/JSON do OpenAPI à mão | Caro de manter, diverge do código na primeira mudança de campo esquecida |
| Não documentar (manter só os `.http`) | Já era o estado anterior — não cobre forma de payload, só cenário de teste |
| `@Schema(required = true)` replicando o que `@NotNull` já diz | Duplicação: duas fontes pra a mesma informação divergem — springdoc já lê Bean Validation sozinho |

---

## ADR-037 — Artefatos do planejamento chegam via Google Drive e `rclone`

**Status:** Aceita · 2026-09-04 · Sprint 10

### Contexto

O sistema de planejamento (`Plano-de-Estudos-Automatizado`, projeto Python
separado) gera, por edital, o cadastro de assuntos e o material de estudo
fatiado em blocos (PDFs referenciados por link, `docs/requisitos-planejamento-blocos-de-conteudo.md`).
Ele roda **sob demanda**, num PC pessoal, só quando surge um edital novo. O
SGE roda **24/7** no Raspberry Pi (ADR-016). Os dois nunca precisam estar de
pé ao mesmo tempo por natureza de uso — mas a premissa original do lado do
planejamento (`arquitetura-integracao-planejamento-sge.md §3`, do outro
repositório) supunha contato direto entre os dois via Tailscale no momento
da exportação, o que exigiria justamente essa coincidência.

ADR-015 já rejeitou abrir superfície de autenticação nova no Pi (OAuth com
callback público) pelo mesmo raciocínio que se aplicaria aqui. ADR-016 já
rejeita peso extra numa JVM que compartilha 4–8 GB com o Pi-hole. E ADR-018
**já aceitou** Google Drive + `rclone` como mecanismo padrão para mover
arquivo entre o Pi e fora dele — hoje usado para backup.

### Decisão

- O CSV de assuntos/pedaços de material chega ao Pi por **`rclone sync`** de
  uma pasta do Google Drive — a mesma ferramenta já aceita em ADR-018,
  aplicada na direção inversa (puxar, não só enviar). Agendado por
  cron/systemd timer, fora do processo da JVM.
- O import continua pelo mecanismo já existente (`validar`/`confirmar`,
  Sprint 2) — só passa a ser alimentado por um arquivo que aparece numa pasta
  local sincronizada, em vez de upload manual multipart.
- Os **PDFs dos blocos de conteúdo não precisam chegar ao Pi**. Ficam no
  Drive; `referência_material` (Sprint 10) guarda o link como texto — o SGE
  nunca abre nem serve esse arquivo (`02_JORNADAS.md §1.1`: o sistema nunca
  exibe material de estudo). O candidato abre o link direto do Drive, em
  qualquer aparelho, tablet inclusive.
- **Nenhum código Java fala com a API do Google Drive.** A sincronização é
  responsabilidade da camada de infraestrutura (`docs/03E_DEPLOYMENT.md`),
  fora da aplicação.

### Consequências

- Planejamento e SGE nunca precisam estar online ao mesmo tempo — o Drive
  funciona como caixa-postal assíncrona. Estritamente melhor que o contato
  direto Tailscale PC→Pi no momento da exportação, porque o planejamento roda
  só ocasionalmente.
- Nenhuma credencial de API dentro do container: `rclone` já gerencia sua
  própria autenticação com o Drive, fora do processo do SGE — mesma
  configuração que já existe para o backup (ADR-018).
- A garantia "tudo ou nada" do import (Sprint 2) não muda — a origem do
  arquivo é irrelevante para a transação.
- **Não é automático de ponta a ponta por esta decisão sozinha**: alguém
  (script ou disparo manual pelo endpoint já existente) ainda precisa iniciar
  `validar`/`confirmar` depois que o arquivo chega na pasta sincronizada.
  Automatizar esse último passo é detalhe da Sprint 10, não desta ADR.
- `referência_material` passa a guardar link do Drive, não caminho de arquivo
  local — reflexo direto no payload que a Sprint 10 vai desenhar.

### Alternativas rejeitadas

| Alternativa | Por que não |
|---|---|
| SGE integra com a API do Google Drive (client Java, OAuth/service account) | Contradiz ADR-015 (evita exatamente essa classe de superfície de autenticação nova) e ADR-016 (peso extra numa JVM que já disputa memória com o Pi-hole) |
| Contato direto Tailscale PC→Pi no momento da exportação (planejamento chama um endpoint de import do SGE) | Exige os dois sistemas online ao mesmo tempo, mas o planejamento roda só ocasionalmente sob demanda enquanto o SGE é 24/7 — um requisito de disponibilidade simultânea que a própria natureza de uso já torna desnecessário |
| Upload manual do CSV via tela/endpoint, como hoje | É exatamente a dependência de "subir na mão" que motivou esta decisão |
| Sincronizar os PDFs pro Pi também, servidos pelo SGE | O SGE nunca exibe material (`02_JORNADAS.md §1.1`) — sincronizar um arquivo que a aplicação nunca vai ler é custo sem função |

---

## Reexames concluídos

### ADR-028 e ADR-030 — Alpine.js, zero build no frontend

~~Não estão revogadas, e provavelmente não devem ser. Mas `02_JORNADAS` §5.1
passou a exigir registro otimista com falha não destrutiva... decida
conscientemente antes de escrever a primeira tela.~~ **Reexaminada e
mantida em 2026-08-31**, antes de `docs/04_FRONTEND.md` e da primeira tela
— ver "Revisão" ao final da ADR-028.
