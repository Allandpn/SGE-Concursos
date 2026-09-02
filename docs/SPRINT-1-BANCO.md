# SPRINT 1 — BANCO

O documento de banco da Sprint 1: as tabelas, as restrições que garantem as
invariantes de persistência, os índices e os parâmetros que o schema precisa
para nascer. Nasce agora porque a Sprint 1 é "ambiente e schema"
(`CLAUDE.md`) — nada aqui antecipa sprint futura.

| Campo | Valor |
|---|---|
| Versão | 1.5.0 |
| Data | 2026-09-01 |
| Status | Vigente |
| Subordinado a | `especificacao/01_DOMINIO.md`, `especificacao/02_JORNADAS.md`, `especificacao/03_INVARIANTES.md`, `docs/00A_ADR.md`, `docs/09_CODE_STYLE.md` |

---

## 0. Escopo

Este documento cobre **só o que a persistência garante**. Regra classificada
como **S** (comportamento) ou **H** (política) em `03_INVARIANTES` §3 não
aparece aqui — nasce na sprint que implementa o serviço correspondente. Onde
uma regra S influencia a *forma* de uma tabela ou de um índice (sem virar
restrição), isso está anotado, mas a regra em si continua não implementada.

Nenhum SQL neste documento — é a estrutura e a decisão, não a migração. A
migração Flyway (`V1__...sql`, ADR-017) é o próximo artefato, derivado direto
daqui.

Nenhuma classe Java, entidade JPA, repositório, service, controller ou
endpoint aparece neste documento — isso é sprint de serviço.

---

## 1. Convenções usadas

Herdadas, não inventadas aqui:

- Tabela e coluna: português, `snake_case`, singular (ADR-027, `09_CODE_STYLE`
  §1).
- Chave primária: `id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY`
  (ADR-023).
- Toda tabela principal carrega `criado_em` e `atualizado_em`, `TIMESTAMPTZ
  NOT NULL DEFAULT now()` — metadado, não garantia (`03_INVARIANTES` §11.1).
  Mantidos pela aplicação; nunca por trigger (ADR-021 proíbe lógica no banco).
- Data de domínio (dia civil) é `date`; instante seria `timestamptz` — não há
  instante nesta sprint (ADR-022).
- Exclusão lógica, nunca física (ADR-011, D-18) — cada tabela tem o mecanismo
  específico que a ADR já define; ver §2.
- Nome de restrição: `<prefixo>_<tabela>_d<NN>[_rótulo]`, fundindo o prefixo
  de `09_CODE_STYLE` §1 (`pk_/fk_/ux_/ck_/ix_`) com a exigência de
  `03_INVARIANTES` §9 de que o identificador da regra vá no nome. Só leva
  `d<NN>` a restrição que implementa uma das 46 regras; FK genérica e `CHECK`
  de vocabulário (enum de domínio comum) levam só tabela + rótulo.
- Vocabulário fechado (tipo de sessão, resultado, situação de revisão etc.) é
  `CHECK` direto na coluna, não tabela de *lookup* — nenhum ADR pede tabela
  de domínio, e `CHECK` é o padrão que ADR-021 já elege para coerência
  declarativa.

---

## 2. As tabelas

Cinco: `disciplina`, `assunto`, `sessao`, `revisao`, `erro`. **Simulado fica
fora desta sprint** — ver §2.6.

### 2.1 `disciplina`

| Coluna | Tipo | Nulidade | Motivo |
|---|---|---|---|
| `id` | `BIGINT IDENTITY` | PK | ADR-023 |
| `nome` | `TEXT` | NOT NULL | `01_DOMINIO` §3.1 |
| `peso` | `TEXT CHECK IN ('ALTO','MEDIO','BAIXO')` | NOT NULL | `01_DOMINIO` §3.1 — "peso no edital"; escala decidida aqui, ver §7.1 |
| `ativo` | `BOOLEAN DEFAULT true` | NOT NULL | ADR-011 — mecanismo de exclusão lógica da disciplina |
| `criado_em`, `atualizado_em` | `TIMESTAMPTZ` | NOT NULL | §1 |

Sem coluna de fase, estado calculado ou contagem de assuntos — são
derivações (ADR-007 lista `disciplina.total_assuntos` explicitamente como
campo que não existe).

### 2.2 `assunto`

| Coluna | Tipo | Nulidade | Motivo |
|---|---|---|---|
| `id` | `BIGINT IDENTITY` | PK | ADR-023 |
| `disciplina_id` | `BIGINT` | NOT NULL, FK → `disciplina` | `01_DOMINIO` §9 — Disciplina 1─N Assunto |
| `nome` | `TEXT` | NOT NULL | `01_DOMINIO` §3.2 |
| `peso` | `TEXT CHECK IN ('ALTO','MEDIO','BAIXO')` | NOT NULL | §3.2; mesma escala da disciplina, ver §7.1 |
| `dificuldade_percebida` | `SMALLINT CHECK (BETWEEN 1 AND 5)` | NOT NULL | §3.2; escala decidida aqui, ver §7.2 |
| `ordem` | `INTEGER` | NOT NULL | **D-41** — todo assunto tem ordem dentro da disciplina |
| `ativo` | `BOOLEAN DEFAULT true` | NOT NULL | `01_DOMINIO` §8 — único estado realmente guardado (`true` = ativo, `false` = arquivado) |
| `criado_em`, `atualizado_em` | `TIMESTAMPTZ` | NOT NULL | §1 |

**Não existe** coluna de fase, prioridade calculada, total de horas ou
percentual de acerto — são derivações (ADR-007; **D-16**). Verificação de
ausência em §5.

**Nota sobre o nome da coluna (1.1.0):** a v1.0.0 chamava esta coluna de
`status`, seguindo ADR-011 ao pé da letra (que lista `Assunto: status =
'ARQUIVADO'`, diferente do `ativo = false` que a mesma ADR atribui a
`Disciplina` e `Sessão`). Renomeado para `ativo` porque `status` é
exatamente o nome que um teste estrutural de **D-16** teria de recusar — e
um teste que procura "coluna com papel de fase" não consegue, só pelo nome,
distinguir o `status` legítimo (o único estado que §8 permite guardar) do
`status` ilegítimo (fase calculada). Isso diverge do texto literal da tabela
de ADR-011 para `Assunto`; fica registrado aqui como tensão a resolver numa
futura revisão daquele ADR, não corrigido por conta própria — ADR não se
edita depois de aceito.

### 2.3 `sessao`

| Coluna | Tipo | Nulidade | Motivo |
|---|---|---|---|
| `id` | `BIGINT IDENTITY` | PK | ADR-023 |
| `assunto_id` | `BIGINT` | NOT NULL, FK → `assunto` | **D-01** (parte 1) — sessão aponta para exatamente um assunto |
| `tipo` | `TEXT CHECK IN ('ESTUDO','QUESTOES','FLASHCARDS','RECUPERACAO')` | NOT NULL | **D-01** (parte 2) — exatamente um tipo por linha, nunca um conjunto de flags |
| `data` | `DATE` | NOT NULL | `01_DOMINIO` §2 — "quando aconteceu"; dia civil (ADR-022) |
| `tempo_minutos` | `INTEGER CHECK (> 0)` | NOT NULL | toda sessão registra tempo (`01_DOMINIO` §3.3); bound de higiene, ver §7.3 |
| `resultado` | `TEXT CHECK IN ('SUCESSO','PARCIAL','FALHA')` | NULL | **D-02** o restringe — nunca preenchido quando `tipo = 'ESTUDO'` |
| `questoes_corretas` | `INTEGER CHECK (>= 0)` | NULL | só `QUESTOES`/`FLASHCARDS` — §3.3; amarrado ao tipo, ver §3 e §7.9 |
| `questoes_total` | `INTEGER CHECK (> 0)` | NULL | só `QUESTOES`/`FLASHCARDS` — §3.3; percentual apurado é derivado destas duas, nunca gravado (ADR-007); `questoes_corretas ≤ questoes_total` também é `CHECK`, ver §3 |
| `formato` | `TEXT CHECK IN ('MULTIPLA_ESCOLHA','CERTO_ERRADO')` | NULL | **D-36** — só `QUESTOES` |
| `previsao_percentual` | `SMALLINT CHECK (BETWEEN 0 AND 100)` | NULL | M-3 variante objetiva (`00_PRODUTO` §7) — só `QUESTOES`/`FLASHCARDS`, amarrado por **D-04a** (ver §3); representação em duas colunas é decisão minha, ver §7.4 |
| `previsao_reconstrucao` | `TEXT CHECK IN ('SUCESSO','PARCIAL','FALHA')` | NULL | M-3 variante subjetiva — só `RECUPERACAO`, amarrado por **D-04a** (ver §3); mesma escala de três vias que `resultado` (`00_PRODUTO` §7, nota v1.6.0 — decisão de tela em `02_JORNADAS §4.1`, não booleano); ver §7.4 |
| `tentativa_id` | `UUID` | NOT NULL | **D-45** — identificador gerado pelo cliente ao abrir a tela; tipo é decisão minha, ver §7.5 |
| `ativo` | `BOOLEAN DEFAULT true` | NOT NULL | ADR-011 — sessão arquivada sai de **todas** as agregações; semântica diferente do soft-delete de disciplina/assunto (correção, não descontinuação) |
| `criado_em`, `atualizado_em` | `TIMESTAMPTZ` | NOT NULL | §1 |

**Nenhuma coluna para "produção"** (a etapa que **D-03** exige antes do
resultado). Nenhuma camada garante D-03 (`03_INVARIANTES` §6 — é honestidade
estrutural, não campo verificável), e `01_DOMINIO` não lista conteúdo de
produção como atributo persistido.

### 2.4 `revisao`

| Coluna | Tipo | Nulidade | Motivo |
|---|---|---|---|
| `id` | `BIGINT IDENTITY` | PK | ADR-023 |
| `assunto_id` | `BIGINT` | NOT NULL, FK → `assunto` | `01_DOMINIO` §3.4 |
| `nivel` | `INTEGER CHECK (>= 1)` | NOT NULL | §3.4 — posição na escada; piso 1 (D-07) |
| `data_prevista` | `DATE` | NOT NULL | §3.4 |
| `sessao_origem_id` | `BIGINT` | NULL, FK → `sessao` | §3.4 — qual estudo iniciou a escada; nullable desde 1.1.0, ver nota abaixo |
| `sessao_cumpriu_id` | `BIGINT` | NULL, FK → `sessao` | **D-06** — preenchida ao ser cumprida; onde vive o resultado |
| `situacao` | `TEXT CHECK IN ('PENDENTE','CUMPRIDA','CANCELADA')` | NOT NULL DEFAULT `'PENDENTE'` | §3.4 |
| `criado_em`, `atualizado_em` | `TIMESTAMPTZ` | NOT NULL | §1 |

**Sem coluna de versão nesta sprint.** O controle de versão na escrita da
revisão (para D-07/D-10/D-11, que leem histórico) é ADR-032 — agendado para a
sprint que implementa o roteamento da escada, regra **S**, fora de escopo
aqui.

**`sessao_origem_id` nullable (1.1.0).** Dois casos legítimos sem sessão de
origem:

- Assunto nascido de divisão (D-42/D-43): a escada do novo assunto começa no
  nível do original, mas a sessão que de fato "iniciou" o estudo pertence ao
  assunto arquivado — D-42 proíbe reapontar sessão de um assunto para outro.
  Não existe sessão de origem válida para o novo assunto.
- Escada retomada após `FALHA` em manutenção (D-11, §5.5): volta ao último
  nível e à fila de estudo sem que uma nova sessão de estudo tenha
  acontecido — a sessão que originalmente consolidou o assunto já está
  associada a uma revisão antiga, cumprida havia muito tempo.

### 2.5 `erro`

| Coluna | Tipo | Nulidade | Motivo |
|---|---|---|---|
| `id` | `BIGINT IDENTITY` | PK | ADR-023 |
| `assunto_id` | `BIGINT` | NOT NULL, FK → `assunto` | `01_DOMINIO` §3.5 |
| `sessao_id` | `BIGINT` | NULL, FK → `sessao` | §3.5 — "opcionalmente a uma sessão" |
| `descricao` | `TEXT` | NOT NULL | §3.5 — a interrogação elaborativa |
| `causa` | `TEXT CHECK IN ('FALTA_CONHECIMENTO','ESQUECIMENTO','INTERPRETACAO','DESATENCAO','PEGADINHA','CHUTE','GESTAO_TEMPO')` | NOT NULL | §3.5 — as sete causas listadas |
| `confianca` | `TEXT CHECK IN ('BAIXA','MEDIA','ALTA')` | NOT NULL | §3.5 — "confiança ao errar"; escala decidida aqui, ver §7.6 |
| `resolvido` | `BOOLEAN DEFAULT false` | NOT NULL | ADR-011; §3.5 — "resolvido ou aberto" |
| `criado_em`, `atualizado_em` | `TIMESTAMPTZ` | NOT NULL | §1 |

### 2.6 Simulado — fora desta sprint

`01_DOMINIO` §3.6: *"Desenho decidido; implementação adiada."* A Sprint 1 não
cria `simulado` nem `resultado_simulado`. Consequência: **D-13** (resultado
de simulado aponta para disciplina, nunca para assunto) não tem onde morar
ainda — fica fora de escopo, registrado em §8.

Quando a sprint de Simulado começar, note-se desde já: `01_DOMINIO` §9 usa
`ResultadoSimulado` no relacionamento (`Disciplina 1─N ResultadoSimulado`),
não `Simulado` — o desenho real são **duas tabelas** (o evento `simulado` —
prova, data, duração — e `resultado_simulado` — a apuração por disciplina),
não uma.

---

## 3. As invariantes de persistência, como restrições

`03_INVARIANTES` §4 lista 10 regras de natureza **Invariante** garantidas
pela persistência. Com Simulado fora de escopo (§2.6), **9 das 10** ganham
restrição nesta sprint — D-13 fica pendente. Uma décima linha, fora da
classificação de `03_INVARIANTES` (que só cobre as 46 regras de
`01_DOMINIO`), entra por ter exatamente a mesma natureza: **J-1**, de
`02_JORNADAS`.

| Regra | Restrição | Nome |
|---|---|---|
| **D-01** | FK `sessao.assunto_id NOT NULL` + coluna `tipo` única, `NOT NULL` (não um conjunto de flags) | `fk_sessao_d01_assunto` |
| **D-02** | `CHECK (tipo <> 'ESTUDO' OR resultado IS NULL)` em `sessao` | `ck_sessao_d02_estudo_sem_resultado` |
| **D-04a** | Amarra cada previsão ao tipo que a comporta, no padrão da D-36 — ver detalhe abaixo | `ck_sessao_d04a_previsao_por_tipo` |
| **D-05** | Índice único parcial em `revisao(assunto_id) WHERE situacao = 'PENDENTE'` | `ux_revisao_d05_pendente_por_assunto` |
| **D-06** | `CHECK (situacao <> 'CUMPRIDA' OR sessao_cumpriu_id IS NOT NULL)` em `revisao` | `ck_revisao_d06_cumprida_tem_sessao` |
| D-13 | — | fora de escopo, §2.6 |
| **D-18** | Ausência de qualquer caminho de `DELETE` — não é restrição nomeada, é verificado estruturalmente (nenhuma migração, nenhum código, emite `DELETE FROM` fora de `TRUNCATE` de log de auditoria) | *(sem nome — verificação estrutural)* |
| **D-36** | `CHECK (tipo <> 'QUESTOES' OR formato IS NOT NULL)` em `sessao` | `ck_sessao_d36_questoes_tem_formato` |
| **D-41** | `NOT NULL` em `assunto.ordem` | `ck_assunto_d41_ordem_obrigatoria` |
| **D-45** | Índice único em `sessao(tentativa_id)` | `ux_sessao_d45_tentativa_unica` |
| **J-1** | Índice único por expressão em `assunto(disciplina_id, unaccent_imutavel(lower(nome)))` | `ux_assunto_j1_nome_por_disciplina` |

### 3.1 Detalhe de D-04a (1.1.0) — amarrada ao tipo, não só à presença

A `CHECK` da v1.0.0 exigia só que *alguma* previsão existisse quando havia
resultado — permitia, por exemplo, uma sessão `RECUPERACAO` com
`previsao_percentual` preenchido, ou as duas colunas preenchidas ao mesmo
tempo. Isso quebra a separação das duas variantes de M-3 que `00_PRODUTO`
§7 exige explicitamente ("não se somam"). A restrição agora amarra cada
coluna de previsão ao(s) único(s) tipo(s) que a comportam, no mesmo padrão
que D-36 já usa para `formato`:

```
CHECK (
  (tipo = 'ESTUDO'
     AND previsao_percentual IS NULL AND previsao_reconstrucao IS NULL)
  OR (tipo IN ('QUESTOES','FLASHCARDS')
     AND previsao_reconstrucao IS NULL
     AND (resultado IS NULL OR previsao_percentual IS NOT NULL))
  OR (tipo = 'RECUPERACAO'
     AND previsao_percentual IS NULL
     AND (resultado IS NULL OR previsao_reconstrucao IS NOT NULL))
)
```

### 3.2 Nota sobre J-1 (1.1.0) — fonte fora de `03_INVARIANTES`

`03_INVARIANTES` classifica só as 46 regras de `01_DOMINIO` — J-1 é de
`02_JORNADAS` e não tem lugar formal naquela classificação. Entra nesta
tabela porque a natureza é idêntica à de D-05/D-45: unicidade que só a
persistência pode garantir sem risco de corrida (verificação prévia no
serviço deixaria a janela entre checar e escrever). O mecanismo exige a
função wrapper `unaccent_imutavel` — `IMMUTABLE`, para poder entrar em
índice por expressão; `unaccent()` puro é `STABLE` e o Postgres recusa
(`CLAUDE.md`; exceção já aberta em ADR-021 para "funções `IMMUTABLE`
puramente utilitárias usadas em índice por expressão"). A função em si é
infraestrutura da migração, não regra de negócio — não entra na tabela de
`parametro` (§6) nem tem `d<NN>` no nome.

### 3.3 Nota sobre D-05 — a crítica

`03_INVARIANTES` §4.1 é explícito: D-05 **tem de** ser unicidade na escrita,
nunca verificação prévia no serviço — entre verificar e escrever cabe outra
transação. O índice único parcial é a única forma correta; não há
alternativa de serviço a considerar aqui.

### 3.4 Nota sobre D-06 — só uma direção

A regra, como escrita em `01_DOMINIO`, diz "revisão cumprida aponta para a
sessão que a cumpriu" — uma única direção. O `CHECK` acima implementa
exatamente isso. A direção inversa (uma revisão `PENDENTE` ou `CANCELADA`
com `sessao_cumpriu_id` preenchido) **não é proibida** pelo texto da regra;
fica registrada como ponto em aberto em §8, não como restrição adicional —
não invento regra que a especificação não pediu.

### 3.5 Restrições estruturais adicionais (sem regra numerada) — 1.1.0

Higiene de dado, não uma das 46 regras nem J-1 — por isso sem `d<NN>` no
nome (`09_CODE_STYLE` §1: só tabela + assunto).

| Restrição | Nome |
|---|---|
| `CHECK (questoes_corretas <= questoes_total)` em `sessao` | `ck_sessao_questoes_corretas_limite` |
| `CHECK (tipo IN ('QUESTOES','FLASHCARDS') OR (questoes_corretas IS NULL AND questoes_total IS NULL))` em `sessao` | `ck_sessao_questoes_por_tipo` |
| `CHECK (tipo = 'QUESTOES' OR formato IS NULL)` em `sessao` | `ck_sessao_formato_por_tipo` |

A segunda decide, de propósito, uma pergunta que a v1.0.0 tinha deixado
solta: contagem de questões é amarrada ao tipo, no mesmo padrão que a
previsão (D-04a, §3.1). Justificativa completa em §7.9.

A terceira fecha uma lacuna real: `ck_sessao_d36_questoes_tem_formato` só
garante formato **presente** quando `QUESTOES` — sozinha, deixava formato
**preenchido** em `ESTUDO`/`FLASHCARDS`/`RECUPERACAO` passar. Esta linha
tinha sido escrita (v1.1.0) achando que já existia; não existia. Achado
testando o fluxo manualmente, não em auditoria de código.

---

## 4. Índices

Além dos que as restrições de unicidade já criam (D-05, D-45, J-1):

| Índice | Consulta que justifica |
|---|---|
| `ix_assunto_disciplina` em `assunto(disciplina_id)` | Toda leitura de assunto por disciplina |
| `ix_assunto_backlog` em `assunto(disciplina_id, ativo, ordem)` | Preenchimento de vaga pela menor ordem do backlog da mesma disciplina (§6.7 de `01_DOMINIO`; regra **S**, mas o padrão de consulta já é conhecido) |
| `ix_sessao_assunto_data` em `sessao(assunto_id, data)` | "O que fiz neste assunto" — consulta mais frequente citada em `01_DOMINIO` §11 |
| `ix_revisao_assunto` em `revisao(assunto_id)` | Join assunto → revisões |
| `ix_revisao_pendente_data` em `revisao(situacao, data_prevista) WHERE situacao = 'PENDENTE'` | Fila de revisões vencidas, ordenada por urgência (`01_DOMINIO` §5.3) |
| `ix_erro_assunto` em `erro(assunto_id)` | Histórico de erros por assunto |

Nenhum índice especulativo além destes — os seis vêm de um padrão de
consulta já nomeado na especificação conceitual, não de suposição sobre tela
futura.

---

## 5. Verificação de ausência — D-16 e D-28

`03_INVARIANTES` §4.2: *"Ausência não é garantia. Ausência + teste estrutural
é."* Dois caminhos, obrigatórios os dois:

| Caminho | O que verifica | Situação nesta sprint |
|---|---|---|
| **Schema** | Nenhuma tabela ou coluna com papel de fase (em `assunto`) ou de turno existe | Verificável já — consulta contra `information_schema.columns`/`tables` que falha se aparecer coluna com esse papel. Roda no build, junto das migrações |
| **Código** | Nenhuma entidade mapeia atributo de fase; nenhuma classe representa turno | Não verificável ainda — Sprint 1 não cria entidade JPA. Fica registrado como obrigação da sprint que criar as entidades |

D-28 (nenhuma entidade de turno persistida) segue o mesmo padrão: não há
tabela de turno entre as cinco de §2, e a obrigação de não criar uma classe
de turno é do código, quando ele existir.

---

## 6. Parâmetros de negócio da Sprint 1

**Correção de 1.1.0.** A v1.0.0 concluía que nenhum parâmetro entrava no
schema, seguindo o padrão de ADR-014 (configuração de aplicação). Isso
contradiz o próprio teste de **D-29**, já em `03_INVARIANTES` §3: "alterar o
parâmetro muda a duração sugerida **sem reinício**". Configuração de
aplicação (`application.yml`) exige reinício do processo para recarregar —
não passa nesse teste. A tabela nasce **nesta** sprint; as chaves nascem
com as sprints que as consomem.

### 6.1 A tabela `parametro`

Não é uma das cinco tabelas de domínio de §2 — é o "sétimo conceito" que
`01_DOMINIO` §3 já nomeia: "configuração, não domínio". Chave-valor,
deliberadamente simples:

| Coluna | Tipo | Nulidade | Motivo |
|---|---|---|---|
| `chave` | `TEXT` | PK | identifica o parâmetro (convenção de nome em §6.2) |
| `valor` | `TEXT` | NOT NULL | valor bruto; a sprint que consome faz o parse tipado |
| `descricao` | `TEXT` | NOT NULL | autoexplicação da linha — não há outro documento listando o que cada chave faz em produção |
| `atualizado_em` | `TIMESTAMPTZ` | NOT NULL DEFAULT `now()` | quando o valor mudou por último |

`valor` é `TEXT`, não `NUMERIC` — nem todo parâmetro é escalar único (os
intervalos da escada são uma lista de 6 números). Parâmetro composto vira
várias chaves, nunca uma chave com valor estruturado. Justificativa completa
em §7.10.

**A tabela nasce vazia.** Nenhuma linha inserida por esta migração — cada
sprint futura insere as chaves que consome, na sua própria migração, quando
a implementa.

### 6.2 Catálogo de chaves esperadas

Convenção: `<área>.<nome>`, minúsculo, sem acento. Este catálogo é
documentação de intenção — as linhas só existem no banco quando a sprint da
coluna "Nasce em" as inserir.

| Chave sugerida | Partida | Regra que sustenta | Nasce em |
|---|---|---|---|
| `frente.teto_global` | 100 | D-19, D-20 | Sprint da frente de estudo |
| `frente.teto_disciplina` | 12 | D-20, D-25 | Sprint da frente de estudo |
| `revisao.lote_minimo` | 5 questões | D-09 | Sprint do roteamento (`QUESTOES`/`FLASHCARDS`) |
| `revisao.parcial_seguidos_max` | 4 | D-35 | Sprint da sugestão de divisão |
| `revisao.janela_tolerancia_pct` | 20% do intervalo | D-38, D-39 | Sprint de cumprimento de revisão |
| `questoes.limiar.multipla_escolha.sucesso_pct` / `.parcial_pct` | 80 / 60 | D-36, D-37 | Sprint de apuração de `QUESTOES` |
| `questoes.limiar.certo_errado.sucesso_pct` / `.parcial_pct` | 90 / 75 | D-36, D-37 | Sprint de apuração de `QUESTOES` |
| `manutencao.intervalo_min_dias` / `.intervalo_max_dias` | 120 / 180 | D-11, §5.5 | Sprint de manutenção |
| `escada.intervalo.nivel1` … `.nivel6` | 1, 7, 15, 30, 60, 90 dias | D-08, D-23 | Sprint do roteamento da escada |
| `revisao.teto_diario` | ainda em aberto em `01_DOMINIO` §12 (entre 5 e 8) | D-15 | Sprint do plano de turno |

---

## 7. Decisões tomadas que não estão na especificação conceitual

Nenhuma delas contradiz `01_DOMINIO` ou `03_INVARIANTES` — preenchem lacuna
onde a especificação não fixa representação. Se a especificação vier a fixar
uma escala diferente, este documento muda primeiro, o schema depois.

### 7.1 `peso` como categórico `ALTO`/`MEDIO`/`BAIXO`

`01_DOMINIO` nunca define escala numérica para peso (nem em disciplina, nem
em assunto). D-23 já fala nesse vocabulário categórico ("peso alto → nível
6, peso médio → nível 4, peso baixo → nível 3"); reaproveitar o mesmo
vocabulário evita inventar uma escala numérica sem lastro.

### 7.2 `dificuldade_percebida` como `SMALLINT` 1–5

Sem escala definida na especificação. 1–5 é convenção comum de percepção
subjetiva; não há regra numerada que dependa do valor exato nesta sprint.

### 7.3 `sessao.tempo_minutos CHECK (> 0)`

Bound de higiene de dado, não regra numerada — toda sessão registra tempo
(§3.3), um valor zero ou negativo não é um tempo.

### 7.4 `previsão` dividida em duas colunas

`00_PRODUTO` §7 (M-3) define duas variantes que **não se somam**: objetiva
(percentual esperado, para `QUESTOES`/`FLASHCARDS`) e subjetiva ("vai
conseguir reconstruir?", para `RECUPERACAO`). A existência das duas
variantes está na especificação; a representação em duas colunas nullable é
decisão minha — a alternativa (uma coluna genérica) obrigaria um tipo de
dado que servisse às duas formas, perdendo a tipagem forte que ADR-021/022
pedem.

### 7.5 `tentativa_id` como `UUID`

`01_DOMINIO` (D-45, §3.3.1) exige "identificador gerado pelo cliente,
único", sem fixar o tipo. `UUID` é gerável no cliente sem coordenação com o
servidor, o que a regra exige textualmente ("gerado ao abrir a tela").

### 7.6 `confianca` do erro como ordinal `BAIXA`/`MEDIA`/`ALTA`

`01_DOMINIO` §3.5 e `00_PRODUTO` §7.1 descrevem a existência do dado ("a
confiança que havia ao errar") e seu uso (efeito de hipercorreção), nunca a
escala. Três níveis ordinais é o mínimo que distingue "eu tinha certeza" do
resto sem inventar precisão que ninguém vai calibrar.

### 7.7 Vocabulário fechado via `CHECK`, não tabela de domínio

Nenhum ADR pede tabela de *lookup* para enum; ADR-021 já elege `CHECK` como
a forma declarativa padrão para coerência de dado. Menos uma tabela, sem
perda de garantia.

### 7.8 Nenhuma coluna para "produção" (D-03)

`03_INVARIANTES` §6 é explícito: D-03 não é garantível por nenhuma camada —
o sistema não sabe se o usuário reconstruiu de memória ou espiou a resposta.
`01_DOMINIO` não lista conteúdo de produção como atributo de `Sessão`. Criar
uma coluna para isso inventaria persistência que a especificação não pede.

### 7.9 Contagem de questões amarrada ao tipo — decisão, não frouxidão (1.1.0)

A v1.0.0 deixava `questoes_corretas`/`questoes_total` soltos — preenchíveis
em qualquer tipo de sessão, sem `CHECK` de coerência. **Decisão: amarrada.**
Mesmo padrão que já existe para `formato` (D-36) e que a correção da D-04a
(§3.1) acabou de estender à previsão: nenhuma coluna específica de tipo
fica preenchível fora do tipo que a usa. Deixar solto permitiria, por
exemplo, uma sessão `ESTUDO` com `questoes_corretas` preenchido — dado que
nenhuma leitura de `01_DOMINIO` §3.3 sustenta. Restrição em `ck_sessao_questoes_por_tipo`
(§3.3).

### 7.10 `parametro.valor` como `TEXT`, não `NUMERIC` (1.1.0)

Nem todo parâmetro de `01_DOMINIO` é um escalar único — os intervalos da
escada (D-08) são uma lista de 6 números, e os limiares por formato (D-36,
D-37) são dois números por formato. Modelar `valor` como `NUMERIC` obrigaria
ou uma coluna incapaz de representar os parâmetros compostos, ou um tipo
estruturado (array, `jsonb`) por linha — o que reintroduziria a tentação de
lógica de parse dentro do banco (ADR-021 proíbe). A saída mais simples:
parâmetro composto vira várias chaves (`escada.intervalo.nivel1` …
`.nivel6`), cada uma um escalar; `valor TEXT` serve a todas sem exceção, e o
parse tipado fica inteiramente na sprint que consome — nunca no schema.

### 7.11 `assunto.ativo` diverge da tabela de ADR-011 (1.1.0)

ADR-011 lista, para `Assunto`, o mecanismo `status = 'ARQUIVADO'` —
diferente do `ativo = false` que a mesma tabela atribui a `Disciplina` e
`Sessão`. §2.2 desta versão usa `ativo BOOLEAN` também para `Assunto`,
contrariando o texto literal daquela linha da ADR. A razão: `status` é
exatamente o nome que um teste estrutural de **D-16** teria de recusar (uma
coluna com "papel de fase"), e o teste não consegue distinguir, só pelo
nome, entre um `status` legítimo (o único estado que `01_DOMINIO` §8 permite
guardar) e um `status` ilegítimo. Fica registrado aqui como algo a
resolver numa revisão futura de `00A_ADR.md` — ADR não se edita depois de
aceito; se a decisão for definitiva, quem mantém o catálogo de ADRs escreve
uma nova substituindo a tabela de ADR-011 para `Assunto`.

---

## 8. O que fica em aberto

- **Simulado / `ResultadoSimulado` e D-13** — adiados para a sprint que
  implementar Simulado, por `01_DOMINIO` §3.6 ("implementação adiada").
  Quando essa sprint escrever seu próprio documento de banco, ele nasce
  contra a especificação conceitual vigente naquele momento, não contra
  este.
- **D-06, direção inversa** — este documento não proíbe uma revisão
  `PENDENTE`/`CANCELADA` com `sessao_cumpriu_id` preenchido, porque
  `01_DOMINIO` só afirma a direção "cumprida aponta para a sessão". Se isso
  for indesejável, é decisão a tomar (e registrar) na sprint de serviço, não
  aqui.
- **Formato exato da citação de regra no código** — `03_INVARIANTES` §11.2
  deixa isso aberto; este documento não fecha, porque é decisão de código,
  não de schema. O que já é possível fixar agora: a restrição de banco leva
  o identificador **no nome**, o que este documento já faz.
- **Log de auditoria** — ADR-023 e ADR-011 mencionam uma "trilha de
  auditoria" com truncamento por retenção. É infraestrutura cross-cutting,
  não uma das cinco tabelas de domínio desta sprint; schema próprio fica
  pendente, para quando houver o que auditar (nenhum código escreve ainda).
- **Teto diário de recuperações** — `01_DOMINIO` §12 deixa o valor de
  partida em aberto (5 cobre o cenário médio e sufoca o fraco; 8 cobre o
  fraco e nunca aperta o bom). Não é decisão de schema; a chave
  `revisao.teto_diario` está catalogada em §6.2, sem valor definido até essa
  decisão fechar.
- **Inconsistência entre `01_DOMINIO` e `02_JORNADAS` sobre o enum de peso**
  — `01_DOMINIO` (D-23) e §2.1/§2.2 deste documento usam `ALTO`/`MEDIO`/
  `BAIXO` (masculino, concordando com "o peso"); a coluna `peso` do CSV de
  `02_JORNADAS` J-1 usa `ALTA`/`MEDIA`/`BAIXA` (feminino). Não decidido
  aqui — o mapeamento do texto do CSV para o enum do banco é problema da
  sprint de importação (regra **S**), não deste schema. Registrado para não
  se perder.

---

## 9. Changelog

| Versão | Data | Mudança |
|---|---|---|
| 1.5.0 | 2026-09-01 | Nova `ck_sessao_formato_por_tipo` (§3.5) — `ck_sessao_d36_questoes_tem_formato` só garantia formato presente quando `QUESTOES`, nunca ausente fora disso; a v1.1.0 já descrevia formato como "amarrado ao tipo" (§3.5), mas a segunda direção nunca foi escrita. Achado testando o fluxo de sessão manualmente |
| 1.4.0 | 2026-09-01 | `proxima_sessao_data`/`proxima_sessao_descricao` removidas de `sessao` (V1) — `00_PRODUTO` v1.7.0/`01_DOMINIO` v1.16.0 tiram o conceito de "próxima sessão pretendida" |
| 1.3.0 | 2026-08-31 | Quatro menções a "45 regras" corrigidas para 46 (`01_DOMINIO` ganhou D-46 na Sprint 7, este documento nunca foi atualizado) — achado em auditoria (`/agents/mentor.md`) |
| 1.2.0 | 2026-08-31 | `previsao_reconstrucao`: `BOOLEAN` → `TEXT CHECK IN ('SUCESSO','PARCIAL','FALHA')`, mesma escala de `resultado`. Fecha contradição achada em auditoria entre `00_PRODUTO §7` (fraseado solto, lido como binário) e `02_JORNADAS §4.1` (três botões, desenhados de propósito) — `00_PRODUTO` v1.6.0 explicita que a granularidade é decisão de tela, não da métrica. D-04a (§3.1) não muda: a `CHECK` já era só de nulidade por tipo, indiferente ao tipo de dado da coluna |
| 1.1.0 | 2026-08-19 | Revisão do usuário, 7 correções. **Parâmetros entram no schema**: nova tabela `parametro` (§6), corrigindo a conclusão da v1.0.0 à luz do próprio teste de D-29 ("sem reinício"). Nova `CHECK` de higiene `questoes_corretas ≤ questoes_total`. `revisao.sessao_origem_id` passa a `NULL` (assunto de divisão, retomada após falha em manutenção). **D-04a** reescrita para amarrar cada previsão ao tipo que a comporta, no padrão de D-36. `assunto.status` renomeado para `assunto.ativo BOOLEAN` (colisão de nome com o que D-16 proíbe; diverge da tabela de ADR-011, registrado em §7.11). Contagem de questões amarrada ao tipo, decidido em §7.9. Nova restrição **J-1** (`02_JORNADAS`, agora fonte do documento): nome de assunto único por disciplina, sem acento/caixa, via `unaccent_imutavel`. Encontrada e registrada (não resolvida) inconsistência de enum de peso entre `01_DOMINIO` e `02_JORNADAS` J-1 |
| 1.0.0 | 2026-08-19 | Criado. Cinco tabelas (`disciplina`, `assunto`, `sessao`, `revisao`, `erro`); 9 das 10 invariantes de persistência como restrições nomeadas; Simulado e D-13 adiados por decisão confirmada com o usuário; índices justificados por padrão de consulta já nomeado na especificação; nenhum parâmetro de negócio entra no schema desta sprint |
