# 09 — CODE STYLE

Convenções de código. **Precedência 8.**
Curto de propósito: uma convenção que ninguém lembra não é convenção.

| Campo | Valor |
|---|---|
| Versão do documento | **2.0.0** |
| Status | **Congelado** |
| Data | 2026-08-15 |
| Java | 21 LTS · Spring Boot 4.1 |

---

## 1. Nomenclatura

Tabela normativa (ADR-027). Nada fora dela.

| Elemento | Convenção | Exemplo |
|---|---|---|
| Pacote | inglês onde é técnico, português onde é domínio | `br.com.estudos.revisao`, `br.com.estudos.shared.audit` |
| Entidade | português, `PascalCase`, **singular** | `Sessao`, `Revisao` |
| Campo de entidade | português, `camelCase` | `questoesCorretas` |
| Enum e valores | português | `TipoSessao.LEITURA_LEI` |
| Repositório | `<Entidade>Repository` | `SessaoRepository` |
| Service | `<Agregado>Service` | `RevisaoService` |
| Controller | `<Agregado>Controller` | `RevisaoController` |
| DTO de entrada | `<Agregado><Acao>Request` | `SessaoCreateRequest` |
| DTO de saída | `<Agregado>Response` / `<Agregado>ResumoResponse` | `SessaoResumoResponse` |
| Mapper | `<Agregado>Mapper` | `SessaoMapper` |
| Exceção | `<Tipo>Exception` | `BusinessRuleException` |
| Constante | `SCREAMING_SNAKE_CASE` | `MAX_TEMPO_MIN` |
| Tabela e coluna | português, `snake_case`, **singular** | `sessao.questoes_corretas` |
| Constraint | `pk_`/`fk_`/`ux_`/`ck_`/`ix_` + tabela + assunto | `ck_sessao_questoes` |
| Migração Flyway | `V<n>__descricao_em_snake_case.sql` | `V2__tabelas.sql` |
| Recurso REST | português, **plural** | `/api/sessoes` |
| Campo JSON | português, `camelCase` | `questoesCorretas` |
| Arquivo de frontend | minúsculas | `styles.css`, `api.js` |
| Classe CSS própria | `kebab-case` com prefixo `ge-` | `ge-skeleton` |
| Texto de UI | português | "Revisões pendentes" |

### 1.1 Singular no banco e na classe, plural na URL

`sessao` (tabela), `Sessao` (classe), `/api/sessoes` (recurso). Não é
inconsistência: a tabela e a classe descrevem **uma** linha; a URL descreve uma
**coleção** (D-006).

### 1.2 Prefixos de verbo

| Prefixo | Significado | Retorno |
|---|---|---|
| `find*` | busca, pode não achar | `Optional` ou `List` |
| `buscar*` | busca, tem de achar | a entidade/DTO; lança `NotFoundException` |
| `listar*` | coleção com filtros | `List` (possivelmente vazia) |
| `criar*` | cria | o criado |
| `atualizar*` | altera | o atualizado |
| `arquivar*` / `cancelar*` / `desativar*` | exclusão lógica | o resultado + efeitos |
| `calcular*` | função pura | número ou objeto |
| `validar*` | valida | `void`; lança |
| `is*` / `tem*` | predicado | `boolean` |

`buscar*` que devolve `null` é defeito. `find*` que lança é defeito.

`find*` é a exceção de idioma da tabela §1: é o prefixo do Spring Data, e
renomeá-lo quebraria a derivação automática de consulta.

### 1.3 Idioma

- **Domínio**: português — do nome da coluna ao texto na tela.
- **Andaime técnico**: inglês — `Repository`, `Service`, `Controller`, `Request`,
  `Response`, `Exception`.
- **Comentários e Javadoc**: português.
- **Mensagens ao usuário**: português.
- **Mensagens de log**: português.

---

## 2. Formatação

| Regra | Valor |
|---|---|
| Indentação | 4 espaços (Java), 2 espaços (SQL, YAML, HTML, JS) |
| Largura de linha | 120 (alvo 100) |
| Chave de abertura | mesma linha |
| Imports | sem *wildcard* (`import java.util.*` é proibido) |
| Ordem dos imports | java · javax/jakarta · org · com · br.com.estudos · estáticos |
| Linha em branco | uma entre métodos; nunca duas seguidas |
| Formatador | `google-java-format` ou o padrão da IDE — o que importa é ser um só |

---

## 3. Java

### 3.1 Obrigatório

```java
final var lista = List.of(1, 2, 3);       // imutável por padrão
record Ponto(int x, int y) { }            // DTO é record
String texto = """
    consulta em bloco
    """;                                   // JPQL e SQL em text block
Optional<Sessao> s = repo.findById(id);   // nunca devolva null
switch (tipo) { case TEORIA -> …; }        // switch de expressão, exaustivo
```

- `var` onde o tipo é óbvio pelo lado direito; tipo explícito onde não é.
- Campos `final` sempre que possível.
- Coleções devolvidas por Service são **imutáveis** (`List.copyOf`, `toList()`).

### 3.2 Proibido

```java
import java.util.*;                        // ✘ wildcard
@Autowired private Repo repo;              // ✘ injeção em campo
@Data @Entity class Sessao { }             // ✘ Lombok (D-008)
public Sessao buscar(...) { return null; } // ✘ retorno null
catch (Exception e) { }                    // ✘ engolir
e.printStackTrace();                       // ✘ use o logger
System.out.println(...);                   // ✘ idem
new Date(); Calendar.getInstance();        // ✘ use java.time
LocalDate.now();                           // ✘ use LocalDate.now(clock)
@Transactional public class …              // ✘ na classe inteira, sem critério
switch (tipo) { … default -> {} }          // ✘ default silencioso em enum de domínio
```

O último merece explicação: `default` num `switch` sobre enum de domínio faz o
compilador parar de avisar quando um valor novo é acrescentado
(`02A_ENUMS.md` §0). O valor novo passa despercebido até produção.

### 3.3 Guard clauses

Sair cedo. Aninhamento máximo: **3 níveis**.

```java
// ✘
if (a != null) { if (a.getB() != null) { if (a.getB().ativo()) { return …; } } }

// ✔
if (a == null || a.getB() == null || !a.getB().ativo()) return Optional.empty();
return Optional.of(…);
```

### 3.4 Tamanho

| Unidade | Alvo | Teto |
|---|---|---|
| Método | 25 linhas | 50 |
| Classe | 200 linhas | 400 |
| Parâmetros | 3 | 4 — acima disso, use um `record` |
| Dependências de um Service | 4 | 5 |

Teto ultrapassado é sinal de responsabilidade a mais. Extraia.

---

## 4. Javadoc

Obrigatório em todo método público de Service. **Toda função que implementa uma
regra cita a regra:**

```java
/**
 * Agenda a revisão de nível 1 a partir de uma sessão.
 * Implementa R-006 e R-007 de 05_BUSINESS_RULES.md.
 *
 * @param sessao sessão recém-criada
 * @return a revisão criada, ou null se nenhuma condição foi atendida
 */
```

É o que mantém código e documentação amarrados: um `R-006` permite achar em
segundos o motivo de uma linha estranha.

Não documente o óbvio. `@param id o id` é ruído.

---

## 5. Comentários

Comentar **por que**, não **o que**.

```java
// ✘ o código já diz
// soma 7 dias
var proxima = base.plusDays(7);

// ✔ explica a decisão
// Piso de +1 dia (R-010 / INV-023): impede que uma revisão nasça já atrasada
// quando a sessão é registrada retroativamente.
var proxima = maxDate(calculada, hoje.plusDays(1));
```

Proibido: código comentado (para isso existe o histórico do Git), `TODO` sem
responsável e data, comentário desatualizado (pior que nenhum).

---

## 6. SQL e migrações

| Regra | Detalhe |
|---|---|
| Palavras-chave em maiúsculas | `SELECT`, `CREATE TABLE`, `CHECK` |
| Identificadores em minúsculas | `sessao`, `questoes_corretas` |
| Uma coluna por linha no `CREATE TABLE` | tipos alinhados |
| Toda constraint nomeada | `02_DATABASE.md` §7 |
| Migração aplicada **nunca** é editada | corrigir exige nova migração |
| Migração destrutiva traz comentário no topo | explicando por quê |
| JPQL e SQL em *text block* | nunca concatenação de string |

---

## 7. Tratamento de erros

| Camada | Faz |
|---|---|
| `shared.util` | funções puras; lançam `IllegalArgumentException` em contrato violado |
| Repository | devolve `Optional`; não valida regra |
| Service | lança `NotFoundException`, `ValidationException`, `BusinessRuleException`, `ConflictException` |
| Controller | **não** captura |
| `GlobalExceptionHandler` | captura tudo e converte em `ProblemDetail` |
| `AuditoriaService` | **não** engole: falha derruba a operação (`03C` §4.2) |

```java
// ✘ engolir
try { fazer(); } catch (Exception e) { }

// ✘ relançar sem contexto
try { fazer(); } catch (Exception e) { throw e; }

// ✔ enriquecer e relançar
try {
    revisaoService.agendarAPartirDe(sessao);
} catch (RuntimeException e) {
    log.error("falha ao agendar revisao sessaoId={} assuntoId={}",
              sessao.getId(), assunto.getId(), e);
    throw e;
}
```

---

## 8. Frontend

| Regra | Detalhe |
|---|---|
| Sem lógica em atributo Alpine | leitura de propriedade ou chamada de método (`04_FRONTEND.md` §4) |
| `x-for` sempre com `:key="item.id"` | |
| Classes Tailwind agrupadas | layout → espaçamento → tipografia → cor → estado |
| Sem classe própria quando existe utilitária | |
| Sem estilo inline | exceto largura de barra calculada |
| Sem `!important` | |
| Um componente Alpine por página, em `js/pages.js` | nunca inline no HTML |
| `const` por padrão, `let` quando reatribuir | `var` proibido |
| `===`, nunca `==` | |

---

## 9. Checklist de revisão

Antes de considerar qualquer arquivo pronto:

- [ ] respeita a matriz de dependências (`01_ARCHITECTURE.md` §2);
- [ ] toda associação JPA é `LAZY`;
- [ ] toda consulta cujo DTO usa associação tem `join fetch`;
- [ ] `@Transactional` só no Service; `readOnly` nas leituras;
- [ ] nenhum `LocalDate.now()` sem `clock`;
- [ ] nenhum `findById` dentro de laço;
- [ ] entidade nunca é serializada — o Controller devolve DTO;
- [ ] nenhum número mágico — está em constante;
- [ ] nenhum campo derivado sendo persistido (ADR-007);
- [ ] Javadoc presente, com `R-nnn` citado quando aplicável;
- [ ] mensagens de erro em português, voltadas ao usuário;
- [ ] nenhuma coluna, enum ou endpoint fora de `02_DATABASE`, `02A` e `06_API`;
- [ ] toda constraint nova tem nome e entrada na tradução (`02B` §4);
- [ ] `switch` sobre enum de domínio é exaustivo, sem `default`;
- [ ] os quatro estados de tela tratados (`04_FRONTEND.md` §10);
- [ ] teste correspondente escrito e passando (`10_TESTS.md`).

---

## 10. Changelog

| Versão | Data | Mudança |
|---|---|---|
| 2.0.0 | 2026-08-15 | Reescrito para Java 21 + Spring Boot. Domínio integralmente em português (ADR-027); proibições de Lombok, injeção em campo, `LocalDate.now()` solto e `default` em `switch` de domínio |
| 1.0.0 | 2026-08-15 | Versão inicial (JavaScript / Apps Script) |
