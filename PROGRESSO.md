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
| 1 | **Ambiente e schema** | Docker, Postgres, migração, as 9 restrições, testes de invariante | **em andamento** |
| 2 | Cadastro e importação | Disciplina, assunto, importação e exportação CSV (J-1) | não começou |
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

**Documento técnico:** `docs/SPRINT-1-BANCO.md` v1.1.1

| | Item | Quem escreve | Estado |
|---|---|---|---|
| 1.0 | Documento de banco, revisado | — | **feito** |
| 1.1 | **ADR-031** — unicidade por tentativa e por revisão pendente | Claude | **feito** |
| 1.2 | Migração `V1` — as 5 tabelas + `parametro` | Claude | *rascunho pronto — falta 1.7 validar* |
| 1.3 | Migração `V1` — **as 9 restrições + J-1** | **você** | *rascunho pronto — falta 1.7 validar* |
| 1.4 | Função `unaccent_imutavel` (exige J-1) | Claude | *rascunho pronto — falta 1.7 validar* |
| 1.5 | Testes de invariante — um por restrição | **você** | *infra pronta (Claude) — 8 de 9 métodos faltam* |
| 1.6 | Teste estrutural de ausência (D-16, D-28) | Claude, você revisa | não começou |
| 1.7 | Docker + Postgres de pé, migração aplicando | Claude | não começou |

**A ordem importa em dois pontos:** 1.4 antes de 1.3 (a restrição J-1 usa a
função), e 1.2 antes de 1.3 (não há onde pendurar restrição sem tabela). O resto
é sequência natural.

### Definition of Done

A sprint fecha quando **todos** passarem:

- [ ] `docker compose up` sobe Postgres e a migração aplica sem erro
- [ ] As 9 restrições existem, com o identificador da regra no nome
- [ ] Cada uma tem teste que prova que a escrita inválida **é recusada pela
      restrição certa**, conferindo o nome — não só que falhou
- [ ] O teste estrutural falha se alguém criar coluna de fase em `assunto` ou
      tabela de turno
- [ ] `parametro` existe e está **vazia** — as chaves nascem com as sprints que
      as consomem
- [ ] ADR-031 escrita e revisada
- [ ] Nenhuma entidade JPA, repositório, service, controller ou endpoint existe

O último item é o mais fácil de violar sem perceber.

---

## 3. Como este arquivo se mantém honesto

1. **Item só vira "feito" quando o teste dele passa** — não quando o arquivo
   existe.
2. **Nada de sprint futura**, nem código nem documento. Se aparecer, é violação
   de escopo, não adiantamento.
3. **Sprint futura não ganha detalhe** antes de começar. O documento técnico
   dela nasce contra a especificação vigente **naquele momento**, não contra a
   de hoje.
4. Ao concluir qualquer item, **atualize a tabela da §2 na mesma sessão**.
   Progresso lembrado é progresso perdido.

---

## 4. Changelog

| Versão | Data | Mudança |
|---|---|---|
| 1.5.0 | 2026-08-20 | Item 1.5 — `docs/SPRINT-1-TESTES.md` criado (`10_TESTS.md` era v2, não sobreviveu ao reset). Infra escrita por Claude: `RestricaoTestBase` (Testcontainers + Flyway, sem Spring context) e `RestricoesInvariantesTest` com o molde de D-05 completo. Faltam 8 métodos, TODO(human) no arquivo |
| 1.4.0 | 2026-08-20 | Item 1.3 — `V3__restricoes.sql` escrito pelo usuário (D-02, D-04a, D-06, D-36, D-45, J-1; D-05 foi molde meu). Três rodadas de revisão: erro sistemático de `AND` em vez de `NOT P OR Q` nos quatro `CHECK`s compostos, coluna errada em D-06 (`sessao_origem_id` → `sessao_cumpriu_id`), e uma troca de conteúdo entre os nomes de D-02/D-04a. Todas corrigidas |
| 1.3.0 | 2026-08-20 | Item 1.4 — `V2__unaccent_imutavel.sql` escrito (extensão `unaccent` + wrapper `IMMUTABLE`). Feito antes do item 1.3 porque J-1 depende dele, por ordem explícita do mapa da sprint |
| 1.2.0 | 2026-08-20 | Item 1.2 — `V1__tabelas.sql` escrito (5 tabelas + `parametro`). Por decisão do usuário, D-01 (FK `sessao.assunto_id`) e D-41 (`assunto.ordem`) já entram aqui, nomeados; os outros 7 (D-02, D-04a, D-05, D-06, D-36, D-45, J-1) ficam para o item 1.3. `.claude/settings.json`: `src/main/resources/db/migration/**` movido de `deny` para `ask`, a pedido do usuário, para permitir esta escrita |
| 1.1.0 | 2026-08-20 | Item 1.1 (ADR-031) concluído — escrita por Claude, a pedido do usuário, porque estava além do conhecimento atual dele. Ver `docs/00A_ADR.md` v2.1.0 |
| 1.0.0 | 2026-08-19 | Criado. Mapa das 8 sprints e detalhe da Sprint 1. O mapa faltava desde a reordenação da especificação — os documentos técnicos foram adiados de propósito, a lista de sprints caiu junto por descuido |
