# SPRINT 1 — TESTES

Convenção de teste que os itens 1.5 e 1.6 precisam. `09_CODE_STYLE.md` §9 cita
`10_TESTS.md` — documento da v2 que não sobreviveu ao reset (só `00A_ADR.md`,
`03C_LOGGING.md`, `03E_DEPLOYMENT.md` e o próprio `09_CODE_STYLE.md`
sobreviveram, `CLAUDE.md`). Este documento nasce agora, escopo mínimo — só o
que a Sprint 1 exige. Ampliado quando a sprint que precisar de mais escrever.

| Campo | Valor |
|---|---|
| Versão | 1.0.0 |
| Data | 2026-08-20 |
| Status | Vigente |
| Subordinado a | `especificacao/03_INVARIANTES.md` §7, `docs/00A_ADR.md` (ADR-017), `docs/SPRINT-1-BANCO.md` |

---

## 1. Testcontainers, não H2

ADR-017 exige que toda migração rode contra Postgres real — H2 não tem índice
parcial (`WHERE` em `CREATE INDEX`) nem aceita a função `IMMUTABLE` que J-1
usa da mesma forma. Testar contra H2 provaria uma coisa e rodaria em produção
outra. Testcontainers sobe um Postgres real (`postgres:17-alpine`, mesma
imagem do `docker-compose.yml`) e aplica as **mesmas** migrações de produção —
sem schema de teste paralelo.

## 2. Sem Spring context nesta sprint

Sprint 1 não tem `@SpringBootApplication`, entidade, repositório ou service —
é proibido por `PROGRESSO.md` §2 (Definition of Done). Os testes de 1.5 e 1.6
não precisam de contexto Spring: sobem o container, aplicam a migração via
`Flyway` (API Java), e conversam com o banco por JDBC puro. Isso muda quando a
sprint de serviço criar a aplicação de verdade.

## 3. Container único por suíte

Uma classe base abstrata sobe o container **uma vez** (bloco `static`),
compartilhado por todos os testes da suíte — não um container por classe.
Subir um Postgres novo custa segundos; a suíte inteira paga esse custo uma vez
só, não por classe.

## 4. Isolamento por transação, não por limpeza manual

Cada teste abre uma transação e faz `ROLLBACK` no fim — nunca `DELETE`
manual. Garante que um teste não vê dado de outro (importante especialmente
para J-1 e D-05, que são sobre unicidade: um `nome` ou uma revisão pendente
"vazando" de um teste para o outro geraria falso positivo ou falso negativo).

## 5. Grupo `integracao`

`pom.xml` já referencia `-Dgroups=unitario` e o perfil `sem-docker`
(`-Psem-docker`), mas nada ainda usa essas tags. Todo teste que sobe
Testcontainers leva `@Tag("integracao")`, para poder ser excluído sem Docker
disponível.

## 6. Como testar uma invariante (`03_INVARIANTES` §7)

> Testar a invariante **pelo nome da restrição violada**, não só pelo fato de
> ter falhado.

Padrão, um método por restrição:

1. Insere o dado válido mínimo que a escrita inválida precisa (ex.: para
   testar D-05, insere uma `revisao` `PENDENTE` primeiro).
2. Tenta a escrita que viola a regra.
3. Captura a exceção do driver JDBC (`PSQLException`) e confere que
   `getServerErrorMessage().getConstraint()` é **exatamente** o nome da
   restrição esperada — não só que algo lançou.

Um teste que só verifica "lançou exceção" passa quando a restrição errada foi
removida por engano, ou quando a escrita falhou por outro motivo qualquer.
Conferir o nome é o que faz o teste significar alguma coisa.

## 7. Organização

Uma classe, `RestricoesInvariantesTest`, um método por regra — as 9
restrições de `docs/SPRINT-1-BANCO.md` §3 (D-18 fica fora: ausência, testada
estruturalmente no item 1.6, não é violação de restrição). Nome do método:
`<regra>_<situacao> `, ex. `d05_segundaRevisaoPendenteMesmoAssunto_recusada`.

---

## 8. Changelog

| Versão | Data | Mudança |
|---|---|---|
| 1.0.0 | 2026-08-20 | Criado, escopo Sprint 1. Testcontainers sem Spring context, container único por suíte, isolamento por rollback, e o padrão de asserção pelo nome da restrição (`03_INVARIANTES` §7) |
