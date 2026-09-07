# SPRINT 10 — SEGMENTO

O documento técnico da Sprint 10 (`PROGRESSO.md` §1: "Referência de material
por blocos, alimentada pelo projeto `Plano-de-Estudos-Automatizado` via
Google Drive + `rclone`"). Cobre **Fase 1 e Fase 2** — topologia/parâmetros e
a entidade `Segmento`/mecanismo `chaveExterna`. `Edital`/import por UUID
(Fase 3) fica de fora por decisão já registrada em `PROGRESSO.md` §1.

**Nota de processo, diferente das sprints anteriores:** aqui a decisão
**conceitual** já veio fechada de uma sessão anterior — `01_DOMINIO.md`
v1.17.0 e `02_JORNADAS.md` v1.4.0 já têm §3.7 (Segmento), a coluna
`chaveExterna` e as regras D-50/D-51/D-52; `docs/00A_ADR.md` ADR-037 já
decide a topologia (Google Drive + `rclone`). Este documento **traduz** essa
decisão para schema e código — não a reabre.

| Campo | Valor |
|---|---|
| Versão | 1.2.0 |
| Data | 2026-09-06 |
| Status | Vigente |
| Subordinado a | `especificacao/01_DOMINIO.md` §3.7 (Segmento, chave externa obrigatória), §3.2 (chaveExterna do assunto), §9 (relações), §10 D-50/D-51/D-52/D-53 (v1.18.0); `especificacao/02_JORNADAS.md` §J-1 (segunda importação, `segmentos.csv`, v1.5.0); `especificacao/03_INVARIANTES.md` §3 (D-50 a D-53, v1.8.0); `docs/00A_ADR.md` ADR-037 (topologia Google Drive + `rclone`); `docs/SPRINT-2-CADASTRO.md` §5 (mecanismo validar/confirmar, reaproveitado, não recriado) |

---

## 0. Escopo e decisões tomadas com o usuário

Diferente das sprints anteriores, a maior parte das decisões de escopo já
veio fechada da conversa que produziu `01_DOMINIO` v1.17.0/`02_JORNADAS`
v1.4.0/ADR-037. O que resta aqui é a tradução para schema e API — e um
pequeno número de decisões que só aparecem na hora de desenhar isso, listadas
abaixo como **propostas desta escrita**, para revisão.

**Fora de escopo, por decisão já registrada:**

| Fica de fora | Onde está decidido |
|---|---|
| Entidade `Edital`, import por UUID (Fase 3) | `PROGRESSO.md` §1, linha da Sprint 10 |
| SGE falar com a API do Google Drive | ADR-037 — `rclone` é responsabilidade de infraestrutura, fora do processo Java |
| Servir/abrir o PDF do segmento | `02_JORNADAS.md §1.1` — o sistema nunca exibe material, `arquivo` é ponteiro opaco |

**Decisões propostas nesta escrita** (não fechadas em nenhum documento
anterior — a confirmar na revisão):

| Ponto | Proposta | Por quê |
|---|---|---|
| Endpoint de leitura de segmentos | Entra no escopo desta sprint (`GET /api/assuntos/{id}/segmentos`) | `PROGRESSO.md` §10 registra que a Sprint 9 (frontend) está pausada esperando exatamente esse endpoint ficar estável — não faz sentido fechar a sprint sem ele |
| Mecanismo de D-51 | **FK composta**: `segmento` ganha `UNIQUE (id, assunto_id)`; `sessao.segmento_id` referencia essa dupla, não só `segmento.id` | Mantém a regra na persistência, nunca pré-checagem no serviço — mesmo princípio de D-05/D-45/D-47/D-48/D-46 (`03_INVARIANTES §10`, princípio 2: cada invariante mora na camada mais baixa que a comporte) |
| "Só `ESTUDO` referencia segmento" | `CHECK` próprio, citando D-51 no nome (`ck_sessao_d51_segmento_so_estudo`), ao lado da FK composta (`fk_sessao_d51_segmento_mesmo_assunto`) | Duas regras de banco distintas para a mesma `D-51` numerada — precedente aceito em D-04/D-04a (uma regra de domínio, mais de um mecanismo) |
| Export de `segmentos.csv` | **Revisado em 1.2.0 — entra no escopo.** `GET /api/segmentos/exportacao`, mesmo layout do import (§4) | A v1.1.0 recusou isso porque o CSV de entrada não tinha `id`. Mas `chaveExternaSegmento` (D-53, decidida ainda na 1.1.0) já resolve exatamente esse problema — é uma identidade estável, tanto quanto um `id` seria. Não exportar um round-trip que já funciona seria deixar a pendência aberta por inércia, não por necessidade real |
| Automação do disparo pós-`rclone` | **Revisado em 1.2.0 — entra no escopo.** Script `scripts/importar-segmentos.sh` (§7), agendado por cron/systemd timer no Pi, fora do processo da JVM (mesmo padrão de `scripts/backup.sh`) | ADR-037 deixava isso como "detalhe da Sprint 10" — como o resto da sprint fechou, faz sentido fechar este ponto também enquanto o contexto está fresco, em vez de reabrir depois |

**Reimportação de `segmentos.csv` — revisado nesta versão.** A v1.0.0 deste
documento propunha upsert por `(assuntoId via chaveExterna, ordem)`. Ao
revisar com o usuário, ficou claro que `ordem` é posição, não identidade: se
o material for reordenado depois de já importado, essa chave composta faria
o SGE confundir, em silêncio, o segmento antigo de uma posição com o novo
que passou a ocupá-la — e sessões que já apontavam pro segmento antigo
(D-51) passariam a se referir a um conteúdo diferente do que o candidato
estudou de fato.

**Decisão:** `Segmento` ganha chave externa **própria e obrigatória** —
nova **D-53** em `01_DOMINIO.md` v1.18.0, incorporada a `03_INVARIANTES §3`
v1.8.0 antes deste documento (sem lacuna desta vez). Reimportação passa a
identificar o segmento por essa chave (`chaveExternaSegmento` no CSV, §4),
não mais pela dupla `(assuntoId, ordem)`. Isso resolve o problema de
reordenação por completo: a identidade do segmento nunca depende de onde
ele está na sequência atual.

Como esse repositório é o lado que fecha a especificação primeiro (decisão
do usuário), o sistema de planejamento (`Plano-de-Estudos-Automatizado`)
precisa passar a gerar e manter esse identificador — hoje ele nomeia
arquivos por posição (`segmento-01.pdf`, `segmento-02.pdf`,
`docs/requisitos-planejamento-blocos-de-conteudo.md §5`), sem um
identificador de segmento independente da posição. Isso é uma mudança de
requisito do lado de lá, a comunicar (ver nota ao final deste documento).

---

## 1. Estrutura de dados

### 1.1 `assunto` ganha `chave_externa`

| Coluna | Tipo | Regra |
|---|---|---|
| `chave_externa` | `TEXT`, nula | Identificador opaco de um sistema de planejamento externo — o SGE guarda, nunca interpreta (`01_DOMINIO §3.2`) |

**Única quando presente (D-52)** — comparação **exata**, sem `unaccent`:
diferente do nome (J-1), não é texto para humano ler, é chave técnica.
Índice único simples resolve sozinho: no Postgres, um índice único comum já
trata múltiplos `NULL` como não-conflitantes — não precisa do padrão
`WHERE ... = true` que D-48 usa (ali o `WHERE` filtra *ativos*, não
`NULL`, motivo diferente).

### 1.2 `segmento` (tabela nova)

| Coluna | Tipo | Regra |
|---|---|---|
| `id` | `BIGINT IDENTITY` | PK — surrogate interno, nunca exposto ao sistema de planejamento |
| `assunto_id` | `BIGINT NOT NULL` | FK → `assunto` |
| `chave_externa` | `TEXT` | Identificador do segmento dado pelo sistema de planejamento — obrigatória e única entre todos os segmentos (**D-53**). Identidade de reimportação, não `ordem` |
| `ordem` | `INTEGER NOT NULL` | Única dentro do assunto — **D-50**. Posição de apresentação, não identidade |
| `arquivo` | `TEXT NOT NULL` | Link/identificador do material (Google Drive) — guardado, nunca aberto pelo SGE (`02_JORNADAS §1.1`) |
| `pagina_inicial` | `INTEGER`, nula | Rastreabilidade até a fonte |
| `pagina_final` | `INTEGER`, nula | Idem |
| `tempo_estimado_min` | `INTEGER`, nula | Estimativa de leitura, informativa — não é `Sessao.tempoMinutos` (que é medido, D-29) |
| `criado_em`, `atualizado_em` | `TIMESTAMPTZ` | Hibernate (ADR-016) |

`chave_externa` é **exata**, sem `unaccent` — mesma natureza de
`assunto.chave_externa` (§1.1): identificador técnico opaco, não texto para
humano ler. Diferente de `assunto.chave_externa`, aqui é **obrigatória**, não
nula: `Segmento` só nasce por importação (`01_DOMINIO §3.7`), então todo
segmento sempre tem uma, por origem.

**Sem `NOT NULL` nativo — `CHECK` nomeado, mesmo padrão de D-41
(`V1__tabelas.sql`, `ck_assunto_d41_ordem_obrigatoria`).** `NOT NULL` puro
não carrega identificador de regra no nome da restrição
(`03_INVARIANTES §9.1`: a citação é metadado, vive no nome). D-53 vira dois
mecanismos de banco, mesma família de D-51 (uma regra numerada, mais de um
`CHECK`/índice): `ck_segmento_d53_chave_externa_obrigatoria` (presença) +
`ux_segmento_d53_chave_externa` (unicidade).

**Sem coluna `ativo`, de propósito.** `Segmento` não tem operação de
arquivar/CRUD livre pela API — nasce só por importação (`01_DOMINIO §3.7`) e
uma reimportação **atualiza em vez de apagar** (§0, upsert). D-18 ("nada é
apagado") se satisfaz porque não existe caminho de remoção nenhum, nem
lógica nem física — mesmo raciocínio estrutural que já vale para `Simulado`
(`docs/SPRINT-8-SIMULADO.md §1`).

**`UNIQUE (id, assunto_id)`** — redundante com a PK sozinha, mas é o alvo
técnico que viabiliza a FK composta de D-51 em `sessao` (§1.3). Sem
`D-xx`: é mecanismo, não regra de domínio, mesma família de
`ux_resultado_simulado_simulado_disciplina`.

### 1.3 `sessao` ganha `segmento_id`

| Coluna | Tipo | Regra |
|---|---|---|
| `segmento_id` | `BIGINT`, nula | Opcional, só relevante em `tipo = ESTUDO` |

**D-51**, dois mecanismos:

1. **FK composta** `(segmento_id, assunto_id) → segmento (id, assunto_id)`
   — o banco recusa fisicamente uma sessão referenciando um segmento de
   outro assunto. Não existe checagem prévia no serviço para isso, mesmo
   princípio das outras invariantes de unicidade/integridade do projeto.
2. **`CHECK`** — `tipo = 'ESTUDO' OR segmento_id IS NULL`. As outras três
   sessões continuam apontando só para o assunto (D-01 intacto).

Diagrama de relações (`01_DOMINIO §9`) já está atualizado desde v1.17.0:

```
Assunto    1 ──── N Segmento          (ordenado, D-50)
Segmento   1 ──── N Sessão            (opcional; só sessão ESTUDO aponta, D-51)
```

---

## 2. Migração `V9`

```sql
-- Sprint 10 — Segmento (leitura fatiada de material) e chaveExterna de
-- Assunto. Fonte: docs/SPRINT-10-SEGMENTO.md §1.

ALTER TABLE assunto
    ADD COLUMN chave_externa TEXT;

-- D-52 — chave externa, quando presente, é única entre todos os assuntos.
-- Índice único comum: múltiplos NULL não conflitam entre si no Postgres,
-- diferente de ux_assunto_d48_ordem_por_disciplina (que é PARCIAL por outro
-- motivo — filtrar só os ativos, não lidar com NULL).
CREATE UNIQUE INDEX ux_assunto_d52_chave_externa
    ON assunto (chave_externa);

CREATE TABLE segmento (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    assunto_id          BIGINT NOT NULL,
    chave_externa       TEXT,
    ordem               INTEGER NOT NULL,
    arquivo             TEXT NOT NULL,
    pagina_inicial      INTEGER,
    pagina_final        INTEGER,
    tempo_estimado_min  INTEGER,
    criado_em           TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_segmento_assunto FOREIGN KEY (assunto_id) REFERENCES assunto (id),
    -- D-53 — todo segmento tem chave externa. CHECK, não NOT NULL nativo,
    -- mesmo padrão de D-41 (V1__tabelas.sql, ck_assunto_d41_ordem_obrigatoria):
    -- a regra precisa do identificador no nome da restrição (03_INVARIANTES §9.1).
    CONSTRAINT ck_segmento_d53_chave_externa_obrigatoria CHECK (chave_externa IS NOT NULL),
    -- D-53 — e única entre todos os segmentos. Identidade de reimportação.
    CONSTRAINT ux_segmento_d53_chave_externa UNIQUE (chave_externa),
    -- D-50 — ordem única dentro do assunto. Posição, não identidade.
    CONSTRAINT ux_segmento_d50_ordem_por_assunto UNIQUE (assunto_id, ordem),
    -- Sem D-xx: alvo técnico da FK composta de D-51 (sessao, abaixo).
    CONSTRAINT ux_segmento_id_assunto UNIQUE (id, assunto_id)
);

CREATE INDEX ix_segmento_assunto ON segmento (assunto_id, ordem);

ALTER TABLE sessao
    ADD COLUMN segmento_id BIGINT;

-- D-51 (primeira metade) — segmento pertence ao mesmo assunto da sessão.
ALTER TABLE sessao
    ADD CONSTRAINT fk_sessao_d51_segmento_mesmo_assunto
    FOREIGN KEY (segmento_id, assunto_id) REFERENCES segmento (id, assunto_id);

-- D-51 (segunda metade) — só sessão ESTUDO referencia segmento.
ALTER TABLE sessao
    ADD CONSTRAINT ck_sessao_d51_segmento_so_estudo
    CHECK (tipo = 'ESTUDO' OR segmento_id IS NULL);
```

---

## 3. Camada de serviço

### 3.1 `AssuntoService`

`criar`/`atualizar` ganham `chaveExterna` opcional em `AssuntoRequest`.
`traduzirViolacaoDeIntegridade` ganha uma linha:

| Restrição violada | `codigo` | Status |
|---|---|---|
| `ux_assunto_d52_chave_externa` | `CHAVE_EXTERNA_DUPLICADA` | 409 |

Novo método em `AssuntoRepository`: `findByChaveExterna(String)` — é como o
importador de segmentos (§4) resolve `chaveExternaAssunto` para o `Assunto`
certo, sem expor o `id` interno ao sistema de planejamento (mesmo motivo que
`02_JORNADAS §J-1` já registra para a existência da própria `chaveExterna`).

### 3.2 `SegmentoService` (novo, pacote `br.com.estudos.segmento`)

Sem endpoint de criação livre pela API — `Segmento` "nasce só por
importação" (`01_DOMINIO §3.7`). Três métodos:

- `buscarPorChaveExterna(String)` — usado pelo importador para decidir
  entre atualizar e criar (abaixo).
- `criarOuAtualizar(SegmentoRequest)` — chamado só por
  `ImportacaoSegmentoService` (§4), nunca por um controller de CRUD direto.
  Busca por `chaveExterna`: se existe, atualiza `ordem`/`arquivo`/
  `paginaInicial`/`paginaFinal`/`tempoEstimadoMin` do registro (mesmo `id`
  preservado — qualquer `Sessao.segmento_id` que já apontava pra ele
  continua válida); se não existe, cria. Nome deliberadamente não é só
  `criar`, pra não sugerir que sempre insere. Traduz
  `ux_segmento_d50_ordem_por_assunto` → `ORDEM_DUPLICADA` (409) e
  `ux_segmento_d53_chave_externa` → algo que só aconteceria por corrida
  entre duas importações simultâneas, não em uso normal (o `buscarPorChaveExterna`
  prévio já deveria ter encontrado a linha).
- `listarPorAssunto(Long assuntoId)` — usado pelo endpoint de leitura (§5),
  ordenado por `ordem`.

### 3.3 `SessaoService`

`SessaoRequest` ganha `segmentoId` opcional. `gravar` seta `Segmento` na
`Sessao` quando presente (busca por id, mesmo padrão de `assunto`).
`traduzirViolacaoDeIntegridade` ganha duas linhas:

| Restrição violada | `codigo` | Status |
|---|---|---|
| `fk_sessao_d51_segmento_mesmo_assunto` | `SEGMENTO_DE_OUTRO_ASSUNTO` | 409 |
| `ck_sessao_d51_segmento_so_estudo` | `SEGMENTO_FORA_DE_ESTUDO` | 422 |

---

## 4. Importação de segmentos (`ImportacaoSegmentoService`)

Mesmo papel de `ImportacaoAssuntoService` (`docs/SPRINT-2-CADASTRO.md §5`,
código em `src/main/java/br/com/estudos/importacao/`): `validar`/`confirmar`
processam o mesmo jeito, a diferença é só rollback forçado ou não;
`confirmar` só grava se nada foi recusado (tudo-ou-nada).

**Layout do CSV** (`02_JORNADAS.md §J-1`, "Segunda importação"):

| Coluna | Obrigatória | Regra |
|---|---|---|
| `chaveExternaSegmento` | sim | Identidade do próprio segmento, dada pelo sistema de planejamento — única entre todos os segmentos (D-53). Chave de reimportação, não `ordem` |
| `chaveExternaAssunto` | sim | Precisa bater com `Assunto.chaveExterna` de um assunto já importado |
| `ordem` | sim | Única dentro do assunto (D-50) — posição de apresentação |
| `arquivo` | sim | Link/identificador — guardado, nunca aberto |
| `paginaInicial` / `paginaFinal` | não | Rastreabilidade |
| `tempoEstimadoMin` | não | Informativo |

**Assimetria real com `ImportacaoAssuntoService`, a registrar, não
esconder:** o CSV de assuntos tem coluna `id` — um `id` inexistente ali é
sinal de arquivo corrompido e aborta o arquivo **inteiro** na hora
(`ImportacaoAssuntoService.processar`, catch de `NotFoundException`). O CSV
de segmentos **não tem `id`** — `chaveExternaAssunto` sem correspondência é
tratada como **linha recusada comum** (processamento continua, resultado
final é tudo-ou-nada do mesmo jeito via `resumo.recusadas()`), porque não há
"arquivo corrompido" possível sem uma chave primária sendo referenciada
errado — só uma linha apontando para um assunto que ainda não existe.

**Reimportação (§0, revisado): upsert por `chaveExternaSegmento`.** Linha
com uma `chaveExternaSegmento` já existente atualiza `ordem`/`arquivo`/
`paginaInicial`/`paginaFinal`/`tempoEstimadoMin` do segmento existente
(mesmo `id` interno, D-18 satisfeita — nunca apaga); chave nova cria. Ao
contrário da primeira versão deste documento (upsert por
`assuntoId+ordem`), isso sobrevive à reordenação do material: o segmento
mantém sua identidade mesmo que o sistema de planejamento insira um novo
pedaço no meio da sequência e desloque as posições seguintes.

`ResumoImportacao` equivalente (`ResumoImportacaoSegmento`, mesmo padrão de
record): `segmentosNovos`, `segmentosAtualizados`, `recusadas`.

---

## 5. Endpoints REST

| Método | Caminho | Retorno |
|---|---|---|
| `POST` | `/api/segmentos/importacoes/validar` | `200`, `ResumoImportacaoSegmento`, nunca grava |
| `POST` | `/api/segmentos/importacoes/confirmar` | `200`, `ResumoImportacaoSegmento`, tudo-ou-nada |
| `GET` | `/api/assuntos/{id}/segmentos` | `200`, lista ordenada por `ordem` — destrava a Sprint 9 (`PROGRESSO.md §10`) |
| `GET` | `/api/segmentos/exportacao` | `200`, `text/csv`, mesmo layout do import (§4) — round-trip só é possível porque `chaveExternaSegmento` (D-53) já dá identidade estável, o mesmo papel que `id` cumpre em J-1 |

`ExportacaoSegmentoService` — mesmo padrão de `ExportacaoAssuntoService`
(`docs/SPRINT-2-CADASTRO.md §6`): consulta com `join fetch` do assunto (pra
ler `chaveExterna` sem N+1), monta CSV com `CSVPrinter`. Colunas na mesma
ordem do import: `chaveExternaSegmento,chaveExternaAssunto,ordem,arquivo,
paginaInicial,paginaFinal,tempoEstimadoMin`. Sem filtro de "arquivado" —
`Segmento` não tem esse conceito (§1.2) — exporta todos.

---

## 6. Testes

- **Restrição de banco** (`RestricoesInvariantesTest`, mesmo molde de
  D-47/D-48): `d50_doisSegmentosMesmaOrdemMesmoAssunto_recusado`,
  `d51_sessaoComSegmentoDeOutroAssunto_recusada`,
  `d51_sessaoNaoEstudoComSegmento_recusada`,
  `d52_doisAssuntosMesmaChaveExterna_recusado`,
  `d53_doisSegmentosMesmaChaveExterna_recusado`,
  `d53_segmentoSemChaveExterna_recusado`. Precisa de um `inserirSegmento`
  novo em `RestricaoTestBase`.
- **Erro de domínio via HTTP**: `CHAVE_EXTERNA_DUPLICADA` (409),
  `SEGMENTO_DE_OUTRO_ASSUNTO` (409), `SEGMENTO_FORA_DE_ESTUDO` (422),
  `ORDEM_DUPLICADA` (409, importação).
- **Importação**: tudo-ou-nada (uma linha com `chaveExternaAssunto`
  inexistente recusa o arquivo inteiro, nenhum segmento grava); upsert
  (reimportar a mesma `chaveExternaSegmento` atualiza `arquivo`, não
  duplica, mesmo `id` interno preservado); reordenação não corrompe
  identidade (reimportar com `ordem` trocada para uma `chaveExternaSegmento`
  já conhecida atualiza a posição do segmento certo, nunca confunde com
  outro) — mesmo molde de `ImportacaoAssuntoTest`.
- **Estrutural**, as três listas brancas fechadas a atualizar:
  - `EstruturaSchemaTest.d16_assuntoSemColunaDeFase` — acrescentar
    `"chave_externa"`.
  - `EstruturaSchemaTest.d28_semTabelaDeTurno` — acrescentar `"segmento"`.
  - `AssuntoEstruturaTest.d16_nenhumAtributoMapeadoForaDaListaFechada` —
    acrescentar `"chaveExterna"`.
  - Considerar (decisão em aberto, não urgente): `Sessao` ainda não tem uma
    lista branca de campos mapeados própria — `segmento` seria o primeiro
    caso a expor essa lacuna. Registrado como pendência, não resolvido
    nesta sprint.
- **Exportação**: round-trip — confirmar um CSV, exportar, confirmar o
  arquivo exportado de novo, tudo bate igual (mesmo `id` interno, nenhuma
  linha nova) — mesmo molde de `ExportacaoAssuntoTest`.

---

## 7. Automação do disparo pós-`rclone`

ADR-037 decide que o CSV chega ao Pi via `rclone sync`, mas deixa em aberto
quem chama `/validar`/`/confirmar` depois que o arquivo aparece. Decisão
desta sprint (§0): um script novo, **fora do processo da JVM** (mesmo
princípio de `scripts/backup.sh`, `docs/03E_DEPLOYMENT.md §6` — que
permanece congelado, este script não altera aquele documento).

**Convenção de pasta** (local, no Pi — espelha
`docs/requisitos-planejamento-blocos-de-conteudo.md §5`, que descreve o lado
do Drive):

```
/opt/estudos/importacao-planejamento/
  <slug-do-concurso>/
    assuntos.csv       ← sincronizado por rclone, opcional por rodada
    segmentos.csv       ← idem
```

**`scripts/importar-segmentos.sh`** — roda `rclone sync` e, pra cada
`<slug>` com arquivo novo, chama `validar` e só chama `confirmar` se a
resposta não tiver `recusadas`. Ordem importa: `assuntos.csv` antes de
`segmentos.csv` em cada concurso — um segmento referenciando uma
`chaveExternaAssunto` que só existe no arquivo de assuntos da mesma rodada
precisa que o assunto já tenha sido confirmado.

**Sem controle de "já processado" — roda de novo é seguro.** Confirmar o
mesmo `assuntos.csv`/`segmentos.csv` sem mudança nenhuma é idempotente (D-45
não se aplica aqui, mas o mesmo espírito vale: `AssuntoService.atualizar`
com os mesmos valores não muda nada; `SegmentoService.criarOuAtualizar`
idem). Evita a complexidade de rastrear "o que já foi importado" — no
volume de uso deste sistema (um concurso de cada vez, poucas dezenas de
assuntos), reprocessar tudo a cada rodada custa milissegundos, não segundos.

```bash
#!/usr/bin/env bash
set -euo pipefail

BASE=/opt/estudos/importacao-planejamento
APP=http://localhost:8080
LOG=/var/log/estudos/importacao-planejamento.log

log() { echo "$(date -Iseconds) $*" | tee -a "$LOG"; }

rclone sync remoto:SGE-Importacao "$BASE" --create-empty-src-dirs

importar() {
  local endpoint="$1" arquivo="$2"
  local resumo
  resumo=$(curl -sf -F "arquivo=@${arquivo}" "$APP/api/${endpoint}/importacoes/validar")
  if echo "$resumo" | grep -q '"recusadas":\[\]'; then
    curl -sf -F "arquivo=@${arquivo}" "$APP/api/${endpoint}/importacoes/confirmar" > /dev/null
    log "OK: $arquivo confirmado"
  else
    log "RECUSADO: $arquivo — $resumo"
  fi
}

for pasta in "$BASE"/*/; do
  [ -f "${pasta}assuntos.csv" ] && importar assuntos "${pasta}assuntos.csv"
  [ -f "${pasta}segmentos.csv" ] && importar segmentos "${pasta}segmentos.csv"
done
```

Agendar (mesmo padrão de `backup.sh`, `docs/03E_DEPLOYMENT.md §6.1`):

```cron
*/30 * * * * cd /opt/estudos && ./scripts/importar-segmentos.sh
```

**Não testado em Pi real** — este ambiente não tem um, mesma ressalva que
`scripts/backup.sh` recebeu no Sprint 8 (`PROGRESSO.md` changelog 1.18.0).
`grep` no JSON de resposta é frágil (funciona porque `recusadas:[]` é a
única forma que a lista vazia assume nesta serialização, mas quebraria se o
formato mudasse) — aceitável para um script de operação de baixo risco, não
para código de produção da aplicação.

---

## 8. Changelog

| Versão | Data | Mudança |
|---|---|---|
| 1.2.0 | 2026-09-06 | **Duas pendências fechadas** (§0), a pedido do usuário, depois da primeira execução real (110/110 verdes). **Export de segmentos** (`GET /api/segmentos/exportacao`, §5): revertida a recusa da v1.1.0 — `chaveExternaSegmento` (D-53) já dá a identidade estável que faltava para round-trip, mesmo papel que `id` cumpre em J-1. **Automação do disparo pós-`rclone`** (§7, nova seção): `scripts/importar-segmentos.sh`, fora do processo da JVM, mesmo princípio de `scripts/backup.sh` — roda `rclone sync`, depois `validar`/`confirmar` em cada `assuntos.csv`/`segmentos.csv` encontrado, `assuntos.csv` sempre antes de `segmentos.csv` no mesmo concurso. Sem controle de "já processado": reprocessar é idempotente e barato no volume deste sistema. Não testado em Pi real, mesma ressalva de `backup.sh` no Sprint 8 |
| 1.1.0 | 2026-09-06 | **Reimportação de `segmentos.csv` revisada**: `Segmento` ganha chave externa própria e obrigatória (nova **D-53**, `01_DOMINIO` v1.18.0/`03_INVARIANTES` v1.8.0), substituindo o upsert por `(assuntoId, ordem)` da v1.0.0. Motivo: `ordem` é posição, não identidade — reordenar o material depois de importado faria a versão anterior confundir, em silêncio, um segmento antigo (já referenciado por sessões passadas, D-51) com um novo na mesma posição. CSV ganha coluna `chaveExternaSegmento` (§4); `segmento.chave_externa` entra na migração `V9` (§2) como `NOT NULL UNIQUE`; `SegmentoService.criar` renomeado para `criarOuAtualizar`, buscando por chave externa. Como este repositório fecha a especificação primeiro, o sistema de planejamento (`Plano-de-Estudos-Automatizado`) precisa passar a gerar esse identificador — hoje nomeia arquivos só por posição; requisito novo a comunicar ao lado de lá |
| 1.0.0 | 2026-09-06 | Criado. Traduz para schema/API a decisão conceitual já fechada em `01_DOMINIO.md` v1.17.0/`02_JORNADAS.md` v1.4.0/ADR-037 (entidade `Segmento`, `chaveExterna` de `Assunto`, D-50/D-51/D-52). Seis decisões propostas em §0, para revisão: endpoint de leitura entra no escopo (destrava a Sprint 9); D-51 via FK composta; "só ESTUDO" como `CHECK` irmão citando D-51; reimportação de `segmentos.csv` como upsert por `(assuntoId, ordem)`, nunca delete (D-18); export de segmentos e automação do disparo pós-`rclone` ficam de fora, pendência documentada. Nenhum código escrito ainda — implementação (migração `V9`, entidades, services, importação, testes) fica para a sessão seguinte |
