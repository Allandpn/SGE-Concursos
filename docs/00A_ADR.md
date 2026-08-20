# 00A — ARCHITECTURE DECISION RECORDS

Catálogo único das decisões de arquitetura.

| Campo | Valor |
|---|---|
| Versão do documento | **2.0.0** |
| Status | **Congelado** |
| Data | 2026-08-15 |
| Total | 30 ADRs — 19 vigentes, 10 substituídas, 1 revogada |

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
| Assunto | `status = 'ARQUIVADO'` |
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

## Changelog

| Versão | Data | Mudança |
|---|---|---|
| 2.0.0 | 2026-08-15 | Migração para PostgreSQL + Spring Boot. 16 ADRs novas (015–030); 10 substituídas; ADR-002 revogada; ADR-007, 011 e 014 mantidas |
| 1.0.0 | 2026-08-15 | Versão inicial. 14 ADRs |


## Pendentes de redação

Três decisões já **tomadas** na especificação conceitual, ainda **sem ADR
escrita**. Cada uma deve ser redigida quando a sprint que a implementa começar —
não antes.

### ADR-031 — Unicidade por tentativa e por revisão pendente

**Contexto.** Duas invariantes exigem unicidade sobre um subconjunto, não sobre
a tabela inteira:

- **D-05** — no máximo uma revisão *pendente* por assunto. Revisões concluídas
  e canceladas do mesmo assunto são normais e numerosas.
- **D-45** — a mesma tentativa de registro não pode gravar duas vezes. A chave é
  do **identificador da tentativa**, gerado pelo cliente ao abrir a tela, nunca
  do conteúdo — recuperar o mesmo assunto duas vezes no mesmo dia é legítimo.

**Decisão.** Ambas por unicidade na persistência, restrita ao subconjunto.
**Nunca** por verificação prévia no serviço: entre verificar e escrever cabe
outra transação.

**Escrever quando:** a sprint que cria a tabela `revisao` começar.

### ADR-032 — Controle de versão na escrita

**Contexto.** D-07, D-10 e D-11 leem a série histórica antes de decidir. Duas
transações simultâneas leem o mesmo estado e gravam por cima, sobrepondo degraus
da escada.

**Decisão.** Versionar a **revisão** — e só ela. Como D-05 garante no máximo uma
pendente por assunto, existe uma única revisão em disputa por vez; versionar o
assunto seria mais caro sem cobrir nada a mais.

**Não confundir com ADR-031.** São proteções complementares: o identificador de
tentativa protege contra *a mesma* tentativa reenviada; a versão protege contra
*tentativas diferentes* colidindo. Implementar só uma e achar-se coberto é o
erro provável.

**Escrever quando:** a sprint que implementa o roteamento da escada começar.

### ADR-033 — Derivação por visão simples, nunca materializada

**Contexto.** Frente de estudo e fase do assunto são derivadas cruzando assunto,
sessão e revisão, sobre centenas de linhas. Calcular linha a linha no serviço
produz uma consulta por assunto.

**Decisão.** Se virar visão de leitura, **visão simples**. Visão materializada é
cache e reintroduz exatamente a divergência que D-16 existe para evitar — e
ADR-029 já recusou cache de aplicação pelo mesmo raciocínio.

**Escrever quando:** a consulta da frente aparecer, e não antes de medir se o
problema existe.

---

## Uma vigente que merece reexame

### ADR-028 e ADR-030 — Alpine.js, zero build no frontend

**Não estão revogadas, e provavelmente não devem ser.** Mas `02_JORNADAS` §5.1
passou a exigir **registro otimista com falha não destrutiva**: a tela transita
em milissegundos, a persistência acontece atrás, e se falhar o dado permanece
digitado com reenvio a um clique.

É factível em Alpine. É a primeira exigência que pressiona a escolha de não ter
etapa de build.

**Decida conscientemente antes de escrever a primeira tela**, não depois. Se
mantiver Alpine, vale uma nota na ADR-028 dizendo que a exigência foi
considerada. Se trocar, é ADR nova substituindo as duas — e o custo real não é a
biblioteca, é perder o "abre no navegador sem compilar nada", que era a razão
original.
