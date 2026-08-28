# SPRINT 2 — CADASTRO

O documento de serviço da Sprint 2: as entidades JPA, os repositórios, as
regras que o serviço aplica sobre `Disciplina` e `Assunto`, os endpoints REST
e o contrato da importação/exportação de CSV (J-1). Nasce agora porque a
Sprint 2 é "cadastro e importação" (`PROGRESSO.md` §1) — nada aqui antecipa
sprint futura.

| Campo | Valor |
|---|---|
| Versão | 1.0.0 |
| Data | 2026-08-27 |
| Status | Vigente |
| Subordinado a | `especificacao/01_DOMINIO.md`, `especificacao/02_JORNADAS.md`, `especificacao/03_INVARIANTES.md`, `docs/00A_ADR.md`, `docs/09_CODE_STYLE.md`, `docs/SPRINT-1-BANCO.md` (schema já existe, não muda) |

---

## 0. Escopo

Três decisões tomadas nesta sprint, fora da especificação conceitual porque
são decisão técnica, não de domínio:

1. **Só API.** Nenhum HTML nesta sprint. A tela "Assuntos" (`02_JORNADAS` §4)
   também serve J-3 (frente × backlog), que depende da Sprint 5 — construí-la
   agora seria antecipar sprint futura pela metade. Testa-se por `curl` ou
   Postman.
2. **Importação em dois passos, sem estado no servidor.** `validar` devolve o
   resumo sem gravar nada; `confirmar` grava. O CSV completo trafega nos dois
   `POST` — nenhuma sessão, nenhum arquivo temporário entre as duas chamadas.
3. **Arquivar existe; D-17 fica pendência documentada.** O endpoint de
   arquivar assunto/disciplina entra nesta sprint (J-1 exige correção de
   cadastro pela interface). O cancelamento de revisões pendentes dependentes
   (D-17) não é possível ainda — `revisao` só ganha serviço na Sprint 4. Ver
   §7.

Fora de escopo, de propósito: divisão de assunto (`01_DOMINIO` §3.2.2 — é
operação de tela composta, não cadastro simples), qualquer coisa de
`Sessão`/`Revisão`/`Erro`, e as métricas (M-1 a M-4, Sprint 7).

---

## 1. Entidades JPA

Mapeiam o schema que já existe (`V1__tabelas.sql`) — nenhuma migração nova
nesta sprint.

### 1.1 `Disciplina`

| Campo | Tipo | Nota |
|---|---|---|
| `id` | `Long` | `@Id`, gerado pelo banco (`IDENTITY`, ADR-023) |
| `nome` | `String` | `NOT NULL` |
| `peso` | `Peso` (enum: `ALTO`, `MEDIO`, `BAIXO`) | `@Enumerated(EnumType.STRING)` — o `CHECK` do banco já é a rede de segurança; o enum evita que o service componha uma string errada |
| `ativo` | `boolean` | `01_DOMINIO` §8 — único estado guardado |
| `criadoEm`, `atualizadoEm` | `Instant` | preenchidos pelo banco (`DEFAULT now()`); a entidade só lê |

### 1.2 `Assunto`

| Campo | Tipo | Nota |
|---|---|---|
| `id` | `Long` | idem |
| `disciplina` | `Disciplina` | `@ManyToOne(fetch = LAZY)` — ADR padrão do projeto; toda consulta que precisa do nome da disciplina busca explícito (`join fetch`), nunca por navegação implícita |
| `nome` | `String` | `NOT NULL`; unicidade por disciplina, sem acento/caixa, é **do banco** (J-1, `ux_assunto_j1_nome_por_disciplina`) — o service não faz pré-checagem, pelo mesmo motivo de D-05 (`CLAUDE.md`) |
| `peso` | `Peso` | mesmo enum de `Disciplina` |
| `dificuldadePercebida` | `int` | `1..5`, `CHECK` no banco |
| `ordem` | `Integer` | `NOT NULL` por `CHECK` (D-41), não por coluna `NOT NULL` nativa — a entidade reflete isso: o campo é `Integer` (não `int`), porque a validação de obrigatoriedade é regra de domínio testada por nome (`ck_assunto_d41_ordem_obrigatoria`), não ausência de valor por acidente |
| `ativo` | `boolean` | idem Disciplina |
| `criadoEm`, `atualizadoEm` | `Instant` | idem |

**Sem coluna de fase.** `EstruturaSchemaTest` (item 1.6) já trava isso — se
`Assunto` ganhar um campo `@Transient` chamado `fase`, calculado em memória,
esse teste não pega (é teste de banco). É a metade "código" de D-16 que
`SPRINT-1-BANCO.md` §5 registra como pendência da sprint que criar entidade —
**esta sprint**. Fica anotado aqui e vira item explícito de revisão (§9).

---

## 2. Repositórios

Spring Data, método em português (`09_CODE_STYLE` §1):

```
DisciplinaRepository
    findByAtivoTrue(): List<Disciplina>

AssuntoRepository
    findByDisciplinaIdAndAtivoTrue(disciplinaId): List<Assunto>
    findByDisciplinaIdAndNomeIgnoreCase(disciplinaId, nome): Optional<Assunto>
        — usado só para montar o resumo da importação (§5), nunca como
          pré-checagem de unicidade antes de gravar
```

Nenhum repositório expõe `delete`. `01_DOMINIO` D-18: nada é apagado.

---

## 3. Serviços

### 3.1 `DisciplinaService` / `AssuntoService`

Operações de cadastro simples (criar, renomear, arquivar) seguem o padrão:
grava, deixa o banco recusar por restrição nomeada, traduz a exceção do
driver para erro de domínio pelo **nome da restrição** — exatamente o gesto
que os testes de `RestricoesInvariantesTest` (item 1.5) já treinaram, e que
ADR-031 previu ser necessário aqui.

| Restrição violada | Erro de domínio | HTTP |
|---|---|---|
| `ux_assunto_j1_nome_por_disciplina` | `NOME_DUPLICADO` | 409 |
| `fk_assunto_disciplina` | `DISCIPLINA_INEXISTENTE` | 404 (a FK aponta para um id que o cliente mandou; se não existe, é recurso não encontrado, não conflito) |
| `ck_assunto_d41_ordem_obrigatoria` | `ORDEM_OBRIGATORIA` | 422 |

### 3.2 Arquivar

`POST /api/assuntos/{id}/arquivar` e `POST /api/disciplinas/{id}/arquivar`
fazem `UPDATE ... SET ativo = false`. Nenhum outro efeito nesta sprint — em
particular, **nenhuma revisão pendente é cancelada** (D-17, §7).

---

## 4. Endpoints REST

Convenção ADR-025 (recurso + sub-recurso de ação para transição de estado):

| Método | Caminho | Corpo | Retorno |
|---|---|---|---|
| `GET` | `/api/disciplinas` | — | `200`, lista de `DisciplinaResponse` |
| `POST` | `/api/disciplinas` | `DisciplinaRequest(nome, peso)` | `201`, `Location: /api/disciplinas/{id}` |
| `PATCH` | `/api/disciplinas/{id}` | `DisciplinaRequest` parcial | `200` |
| `POST` | `/api/disciplinas/{id}/arquivar` | — | `204` |
| `GET` | `/api/assuntos?disciplinaId=` | — | `200`, lista de `AssuntoResponse` |
| `POST` | `/api/assuntos` | `AssuntoRequest(disciplinaId, nome, peso, dificuldadePercebida, ordem)` | `201` |
| `PATCH` | `/api/assuntos/{id}` | `AssuntoRequest` parcial | `200` |
| `POST` | `/api/assuntos/{id}/arquivar` | — | `204` |
| `POST` | `/api/assuntos/importacoes/validar` | `multipart/form-data`, arquivo CSV | `200`, `ResumoImportacao` (§5) |
| `POST` | `/api/assuntos/importacoes/confirmar` | idem | `200`, `ResumoImportacao` com os ids gravados |
| `GET` | `/api/assuntos/exportacao` | — | `200`, `text/csv` — mesmo formato de entrada, com `id` preenchido |

Todos os DTOs são `record` (`09_CODE_STYLE` §3.1). Nenhuma entidade sai do
Service (suspeito de sempre da mentoria).

---

## 5. Importação CSV — contrato

Formato e regras de coluna: `02_JORNADAS.md:124-158`, não repetido aqui —
citado, não redefinido (`CLAUDE.md`, dono do artefato é `02_JORNADAS`).

### 5.1 `validar` e `confirmar` leem o arquivo do mesmo jeito

Ambos os endpoints executam a mesma lógica de leitura e checagem — parse,
resolução de `disciplina` (existe? cria?), resolução de `assunto` por `id`
ou por nome — dentro de uma transação. A diferença é só o `commit`: `validar`
sempre faz `rollback` no fim; `confirmar` faz `commit` se nada recusou o
arquivo inteiro (regra 1 de `02_JORNADAS` §J-1 — tudo ou nada).

```java
record ResumoImportacao(
    int disciplinasNovas,
    int assuntosNovos,
    int assuntosAtualizados,
    List<LinhaRecusada> recusadas
) {}

record LinhaRecusada(int numeroLinha, String motivo) {}
```

Arquivo com qualquer linha recusada por **id inexistente** (`02_JORNADAS`
§J-1, tabela "Situação") recusa o arquivo inteiro — `recusadas` tem uma
entrada só, e nem `validar` nem `confirmar` tocam o banco. Arquivo com linha
recusada por **outro motivo** (ex.: `peso` fora do enum) segue a regra 1
igual: o arquivo inteiro entra ou não entra — não existe importação parcial.

### 5.2 O intervalo entre `validar` e `confirmar` é tempo real, não uma transação

**A crítica que falta resolver aqui, registrada para revisão:** entre o
usuário ver o resumo e clicar em confirmar, outra importação (ou edição pela
interface, quando existir) pode ter mudado o estado — um `id` que existia na
hora do `validar` pode ter sido arquivado. Como as duas chamadas não
compartilham transação nem estado de servidor (decisão §0.2), `confirmar`
**tem que revalidar do zero**, não confiar no resumo que `validar` devolveu.
Se o resultado da revalidação divergir do que foi mostrado, `confirmar`
recusa com um erro específico (`IMPORTACAO_DESATUALIZADA`, 409) em vez de
gravar algo diferente do que o usuário confirmou. Mesma família de problema
que D-05 resolve com unicidade em vez de leitura prévia — aqui não há
restrição de banco que cubra isso, então é o **serviço** que precisa
comparar o resumo recém-calculado com o que veio implícito no clique.

---

## 6. Exportação CSV

`GET /api/assuntos/exportacao` — todas as disciplinas e assuntos ativos, no
mesmo formato de entrada, com `id` preenchido (`02_JORNADAS` §"Por que existe
a coluna `id`"). Assunto arquivado **não** entra na exportação — reexportar,
editar e reimportar um arquivo sem ele não deve reativá-lo nem apagá-lo: ele
já está fora, e a importação nunca apaga (regra 3, `02_JORNADAS` §J-1).

---

## 7. Pendência explícita — D-17

`01_DOMINIO` D-17: "Arquivar assunto ou disciplina cancela as revisões
pendentes dependentes." Esta sprint **não implementa isso** — `revisao` não
tem serviço ainda (Sprint 4). O endpoint de arquivar desta sprint só marca
`ativo = false`.

**Isso é uma violação temporária e rastreada, não um esquecimento.** Fica
registrado para a Sprint 4: ao dar serviço a `Revisão`, o método de arquivar
de `AssuntoService`/`DisciplinaService` precisa ganhar a chamada que cancela
as pendentes. Até lá, arquivar um assunto com revisão pendente deixa a
revisão órfã silenciosamente — comportamento incorreto, aceito por escrito
porque implementá-lo agora exigiria antecipar `RevisaoService` inteiro.

---

## 8. Testes

Sem Spring context ainda? **Não** — esta é a sprint que introduz o service.
`@SpringBootTest` com Testcontainers (mesmo padrão de `RestricaoTestBase`,
generalizado) substitui o JDBC puro do item 1.5: os testes chamam o service
ou o controller (`@AutoConfigureMockMvc`), não `INSERT` cru.

Cobertura mínima:
- Um teste por erro de domínio da tabela de §3.1, verificando o **status
  HTTP e o `codigo`** do `ProblemDetail` (ADR-026) — não só que algo lançou.
- Importação: arquivo válido grava certo; arquivo com `id` inexistente
  recusa **tudo**; arquivo com nome duplicado sem `id` recusa **a linha**,
  não o arquivo (a menos que seja a única linha); `validar` nunca grava,
  confirmado consultando o banco depois.
- Exportação: assunto arquivado não aparece.
- D-16, caminho de código (§1.2): um teste que falha se `Assunto` ganhar
  atributo mapeado que não está na tabela de §1.2 — reflexão sobre os campos
  da entidade, comparando com a lista fechada, no mesmo espírito de
  `EstruturaSchemaTest` (item 1.6).

---

## 9. Decisões tomadas que não estão na especificação conceitual

| Decisão | Por quê | Alternativa rejeitada |
|---|---|---|
| Importação em dois `POST` sem estado de servidor | Nenhum arquivo temporário para limpar, nenhuma sessão para expirar no meio da conferência do usuário — o preço é reenviar o CSV inteiro duas vezes, aceitável para ~260 linhas | Guardar o resultado de `validar` num cache/sessão e `confirmar` só referenciar um id de importação — mais rápido, mas introduz estado de servidor que este sistema evita (ADR-029, sem cache de aplicação) |
| `confirmar` revalida do zero em vez de confiar no resumo de `validar` | §5.2 — o intervalo entre as duas chamadas é tempo real; confiar no resumo antigo arrisca gravar algo que o usuário não viu | Aceitar a janela de corrida como risco baixo (uso pessoal, um usuário) — rejeitada porque o custo de revalidar é uma segunda leitura, e o custo do bug é silencioso |
| Erro de FK (`fk_assunto_disciplina`) vira 404, não 409 | A FK aponta para um recurso que o cliente referenciou por id; se não existe, é "recurso não encontrado", semântica mais específica que "conflito" | Tratar toda violação de restrição como 409 uniformemente — mais simples, mas perde a distinção que RFC 9457/ADR-026 existe para expressar |

---

## 10. Changelog

| Versão | Data | Mudança |
|---|---|---|
| 1.0.0 | 2026-08-27 | Criado. Escopo definido em conversa com o usuário: só API nesta sprint (tela fica para quando a frente×backlog existir), importação em dois passos sem estado de servidor, arquivar existe com D-17 documentada como pendência da Sprint 4 |
