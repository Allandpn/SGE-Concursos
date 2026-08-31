# SPRINT 7 — MÉTRICAS E ERROS

O documento de serviço da Sprint 7: M-1 a M-4 (`00_PRODUTO.md` §7) como
consultas derivadas, e o banco de erros (§7.1) como entidade nova — a camada
explicativa das quatro métricas. Nasce agora porque a Sprint 7 é "métricas e
erros" (`PROGRESSO.md` §1).

| Campo | Valor |
|---|---|
| Versão | 1.2.0 |
| Data | 2026-08-31 |
| Status | Vigente |
| Subordinado a | `especificacao/00_PRODUTO.md` §7 (M-1 a M-4), §7.1 (banco de erros); `especificacao/01_DOMINIO.md` §3.5 (Erro), §10 D-12/D-36/**D-46**; `especificacao/02_JORNADAS.md` J-3, §4.2; `docs/00A_ADR.md` (ADR-033, reaproveitada — nenhuma ADR nova nesta sprint); `docs/SPRINT-1-BANCO.md` §2.5/§7.6 (tabela `erro`, `causa`, escala de `confianca` — já existiam, não mudam); `docs/SPRINT-3-SESSAO.md` (`Sessao` já existe, não muda) |

---

## 0. Escopo e decisões tomadas com o usuário

Quatro pontos que nem `01_DOMINIO.md` nem nenhum outro documento fechava,
decididos antes de escrever este documento:

| Ponto | Decisão | Por quê |
|---|---|---|
| Escala de confiança do erro | Três âncoras qualitativas, mesma régua da previsão de M-3 (§1.2 abaixo) | Número (1–5) finge precisão que o autorrelato não tem — mesmo raciocínio que já derrubou "confiança de 1 a 5 por lote" em `00_PRODUTO` §8.4 |
| M-2 × `RECUPERACAO` | Reportada **separada por tipo de sessão**, nunca somada com `QUESTOES`/`FLASHCARDS` | Mesmo princípio de D-36 (M-1 por formato) e da própria M-3 (variantes objetiva/subjetiva não se somam) — três vezes o mesmo problema, três vezes a mesma solução |
| Erro na ordem da fila | **Fora de escopo.** `TurnoService` (Sprint 6) não muda | `01_DOMINIO.md` §12 deixa a pergunta em aberto; decidir agora seria fechar por pressa, não por análise |
| Resolver erro | **Fora de escopo.** Erro nasce aberto, sem endpoint de transição | Jornada (`02_JORNADAS` §4.2) desenha só o registro; mesmo tratamento que D-17 recebeu na Sprint 2 (pendência documentada, não escondida) |

Também fora de escopo, por já estar decidido em outro lugar: **Simulado**
(`01_DOMINIO` §3.6 — "desenho decidido, implementação adiada", é Sprint 8).

### 0.1 O que já vinha fechado pela especificação, sem decisão nova

- `01_DOMINIO.md` §3.5: `Erro` tem descrição, causa (7 valores fixos),
  confiança ao errar, `resolvido`/`aberto`; pertence a um assunto,
  opcionalmente a uma sessão.
- `00_PRODUTO.md` §7: `n ≥ 100` pra seta de tendência/cor/comparação;
  `n < 10` mostra `—`, nunca `0%`; janela trimestral global, anual por
  disciplina; M-1 nunca soma formatos de banca (D-36); M-4 é "revisões
  concluídas em até 3 dias do previsto" — **prazo fixo de métrica**, não se
  confunde com a janela de tolerância de 20% do intervalo que já existe em
  D-38 pra cumprir a revisão. São dois conceitos diferentes medindo coisas
  diferentes.

---

## 1. `Erro` — tabela já existe (Sprint 1), camada JPA é nova

**Achado só ao desenhar a migração, corrigido antes de escrever código:** a
tabela `erro` já existe desde `V1__tabelas.sql` (`docs/SPRINT-1-BANCO.md`
§2.5), junto com `causa`/`confianca`/`resolvido` e as duas FKs — a Sprint 1
já construiu o schema completo dos cinco conceitos de domínio (§2.5: "Cinco:
`disciplina`, `assunto`, `sessao`, `revisao`, `erro`"), deixando só a camada
JPA (entidade, repositório, service, controller) para a sprint que precisar
dela. Esta sprint não cria tabela nova nenhuma — só a entidade `Erro` e o
que a cerca, mais uma migração de uma linha (§2) pra dar nome à regra que
faltava.

### 1.1 D-46 — escrita em `01_DOMINIO.md` (v1.12.0)

`Erro` referencia `Assunto` (obrigatório) e `Sessao` (opcional) — o mesmo
formato de integridade referencial que `D-01` já cobre para
`sessao.assunto_id`. Confirmada com o usuário e escrita em
`01_DOMINIO.md` §10/§3.5 antes deste documento fechar:

> **D-46** — Todo erro aponta para um assunto existente; se apontar para uma
> sessão, ela também precisa existir. (§3.5)

A restrição da migração (`fk_erro_d46_assunto`) cita esse identificador.

### 1.2 Causa e confiança — os dois enums

`causa`, os 7 valores já fixados em `01_DOMINIO.md` §3.5, sem decisão nova:

```
FALTA_CONHECIMENTO · ESQUECIMENTO · INTERPRETACAO · DESATENCAO ·
PEGADINHA · CHUTE · GESTAO_TEMPO
```

`confianca` — os três níveis ordinais (`BAIXA`/`MEDIA`/`ALTA`) já foram
decididos na Sprint 1 (`docs/SPRINT-1-BANCO.md` §7.6: "o mínimo que distingue
'eu tinha certeza' do resto sem inventar precisão"). O que a Sprint 7
decidiu, e não existia em documento nenhum, foi a redação das âncoras — o
texto que a tela mostra para cada nível:

| Nível | Rótulo | Âncora |
|---|---|---|
| `ALTA` | Tinha certeza | Eu apostaria nessa resposta |
| `MEDIA` | Fiquei em dúvida | Cogitei outra alternativa, mas fui nessa |
| `BAIXA` | Chute | Não sabia, marquei algo |

O valor do campo é o que sustenta o efeito de hipercorreção do `00_PRODUTO`
§7.1: `ALTA` errado é o achado mais valioso que o sistema registra.

### 1.3 Colunas (já existentes desde V1, para referência da entidade JPA)

| Coluna | Tipo | Regra |
|---|---|---|
| `id` | `BIGINT IDENTITY` | — |
| `assunto_id` | `BIGINT NOT NULL` | FK, D-46 |
| `sessao_id` | `BIGINT` | FK opcional, D-46 |
| `descricao` | `TEXT NOT NULL` | a interrogação elaborativa (§7.1) |
| `causa` | enum `NOT NULL` | 7 valores de §1.2 |
| `confianca` | enum `NOT NULL` | 3 valores de §1.2 |
| `resolvido` | `BOOLEAN NOT NULL DEFAULT FALSE` | sem endpoint de transição nesta sprint (§0) |
| `criado_em`, `atualizado_em` | `TIMESTAMPTZ` | Hibernate, mesmo padrão de `Sessao`/`Revisao` (ADR-016) |

---

## 2. Migração `V7`

Uma linha: renomeia a restrição que já existia desde V1 (`fk_erro_assunto`,
sem identificador de regra) para `fk_erro_d46_assunto`, agora que D-46 tem
número (§1). `fk_erro_sessao` mantém o nome — sem `D-xx`, é integridade
referencial simples sobre um campo opcional, não regra de negócio nova.

Sem parâmetro novo em `parametro`, confirmado com o usuário: os limiares de
`n` (`≥ 100`, `< 10`) e o prazo de M-4 (3 dias) são **regras vinculantes de
exibição** (`00_PRODUTO` §7), não parâmetros de negócio como o teto diário —
ficam como constante no service. A diferença para os limiares de
`SUCESSO`/`PARCIAL`/`FALHA` (que são `parametro` desde a Sprint 3) é que
aqueles são recalibráveis pelo usuário e estes são regra de apresentação
fixa da tese do produto.

---

## 3. `ErroService`

CRUD mínimo: `registrar` (grava, sem `validar`/`confirmar` — não é
importação em lote) e `listarPorAssunto`. Sem `atualizar`/`resolver`
(§0). Segue o padrão de tradução de exceção de `AssuntoService`/
`SessaoService` (§3.1 dos documentos anteriores) para `ASSUNTO_INEXISTENTE`/
`SESSAO_INEXISTENTE`.

---

## 4. `MetricaService` — M-1 a M-4, consultas puras

Mesmo desenho de `ADR-033`: nenhuma view, nenhum cache — consulta Spring
Data + agregação em Java, reaproveitando `SessaoRepository`/
`RevisaoRepository` existentes. Nenhum endpoint desta seção grava nada.

### 4.1 M-1 — acerto por disciplina, por formato, por janela

```
por disciplina, por formato (MULTIPLA_ESCOLHA | CERTO_ERRADO):
  acertos = Σ questoesCorretas (QUESTOES da janela, do formato)
  total   = Σ questoesTotal
  n       = total
```

Resposta sempre carrega `acertos/total` explícito (nunca só percentual) e um
`confiavel: boolean` (`n ≥ 100`) — a tela decide, com esse booleano, se
mostra seta/cor ou número puro. Abaixo de `n = 10`, o campo de percentual
vem `null`, não `0`.

Janelas: `GLOBAL` (trimestral) e `POR_DISCIPLINA` (anual) — dois parâmetros
de consulta, não duas métricas.

### 4.2 M-2 — retenção

Por assunto: acerto da **primeira** `Sessao` de `QUESTOES`/`FLASHCARDS`
(`data` mais antiga) comparado ao acerto de cada revisão seguinte do mesmo
tipo. `RECUPERACAO` sai como série própria: percentual de
`resultado != FALHA` na primeira tentativa vs. nas seguintes — mesma
comparação, sem inventar equivalência numérica com o percentual de questões
(§0).

### 4.3 M-3 — calibração

Duas variantes, nunca somadas (já era regra fixa, `00_PRODUTO` §7 M-3):

- **Objetiva** (`QUESTOES`/`FLASHCARDS`): `previsaoPercentual − percentualReal`, com sinal.
- **Subjetiva** (`RECUPERACAO`): `previsaoReconstrucao` (booleano, "vai
  reconstruir?") comparado a `resultado != FALHA` ("reconstruiu"). Sinal:
  previu `true` e saiu `FALHA` → superestimou; previu `false` e saiu
  `SUCESSO`/`PARCIAL` → subestimou; acertou a previsão → sem viés. Nenhum
  documento define essa fórmula — é a extração mais direta do par
  booleano×categórico que já existe no schema desde a Sprint 3, confirmada
  com o usuário, registrada aqui por transparência (mesmo tratamento que
  `ASSUNTO_INEXISTENTE` recebeu na Sprint 2).

### 4.4 M-4 — aderência

`revisões cumpridas com |dataCumprimento − dataPrevista| ≤ 3 dias` ÷
`revisões que deveriam ter sido cumpridas na janela`. Prazo fixo de 3 dias
(§0.1) — não usa a janela de tolerância percentual de D-38.

---

## 5. Endpoints REST

| Método | Caminho | Retorno |
|---|---|---|
| `POST` | `/api/erros` | `201`, `ErroResponse` |
| `GET` | `/api/erros?assuntoId=` | `200`, lista |
| `GET` | `/api/metricas/m1?janela=GLOBAL\|POR_DISCIPLINA` | `200` |
| `GET` | `/api/metricas/m2?assuntoId=` | `200` |
| `GET` | `/api/metricas/m3` | `200`, as duas variantes |
| `GET` | `/api/metricas/m4` | `200` |

---

## 6. Testes

- `Erro`: erro de domínio `ASSUNTO_INEXISTENTE`/`SESSAO_INEXISTENTE`
  (status + `codigo`, mesmo padrão de §3.1 dos documentos anteriores).
- M-1: `n < 10` devolve `—`; `n ≥ 100` marca `confiavel = true`; dois
  formatos no mesmo período nunca somam.
- M-2: primeira exposição corretamente identificada por `data` mais antiga;
  `RECUPERACAO` reportada em série própria.
- M-3: sinal correto nos quatro casos de superestimar/subestimar/acertar,
  nas duas variantes.
- M-4: revisão cumprida no dia 4 não conta; no dia 3 conta.

---

## 7. Changelog

| Versão | Data | Mudança |
|---|---|---|
| 1.2.0 | 2026-08-31 | Corrigido antes de escrever qualquer código: a tabela `erro` (com `causa`, `confianca` como três níveis ordinais, `resolvido`, as duas FKs) já existe desde `V1__tabelas.sql` (`docs/SPRINT-1-BANCO.md` §2.5/§7.6) — só a redação das âncoras de confiança (§1.2) era decisão nova desta sprint. §1 e §2 reescritos: a migração `V7` vira uma linha (renomear `fk_erro_assunto` para `fk_erro_d46_assunto`), não criação de tabela. Nenhuma decisão de escopo mudou — só a premissa errada de que a Sprint 7 criava schema novo |
| 1.1.0 | 2026-08-31 | Confirmados os quatro pontos que o rascunho 1.0.0 deixou em aberto: D-46 escrita em `01_DOMINIO.md` v1.12.0 (erro pode apontar para sessão, opcionalmente); âncoras de confiança (`ALTA`/`MEDIA`/`BAIXA`) aceitas como redigidas; fórmula de sinal de M-3 subjetiva confirmada; limiares de `n` ficam como constante, não `parametro`. Documento passa de rascunho para **Vigente** |
| 1.0.0 | 2026-08-31 | Rascunho criado. Escopo das quatro decisões de §0 discutido e confirmado com o usuário; faltava confirmar os quatro pontos acima, entre eles a regra de domínio nova (D-46), que precisava ser escrita em `01_DOMINIO.md` antes de qualquer código — regra central do `CLAUDE.md` |