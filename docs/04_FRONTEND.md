# 04 — FRONTEND

O documento de arquitetura do frontend: como os arquivos estáticos entram
no JAR, como a SPA consome a API, e o padrão de UI otimista que
`02_JORNADAS.md §5.1` exige. Nasce agora porque a Sprint 9 é a primeira a
tocar frontend — nenhuma sprint anterior escreveu uma linha de HTML/CSS/JS.

Não define cor, tipografia ou componente visual — isso é doc futuro,
quando as primeiras telas já existirem para servir de referência real
(mesma fronteira que `02_JORNADAS §0` já declarava: "isso é do documento de
interface, que vem depois").

| Campo | Valor |
|---|---|
| Versão | 1.2.0 |
| Data | 2026-09-07 |
| Status | Vigente |
| Subordinado a | `especificacao/02_JORNADAS.md` (jornadas, telas, orçamentos de tempo, §5.1); `docs/00A_ADR.md` ADR-025 (API REST), ADR-026 (erros RFC 9457), ADR-028 (zero build, revisada nesta data), ADR-030 (SPA estática); `docs/09_CODE_STYLE.md` §1/§8 (convenções já vigentes de Alpine/Tailwind) |

---

## 0. Escopo

**Sprint 9** (v1.0.0 deste documento): **Hoje** e **Recuperar**. **Sprint
11**: **Registrar sessão** — reabriu este documento porque introduziu dois
padrões novos (seletor de tipo, campo numérico) que as duas telas anteriores
não tinham, §7A. **Sprint 12** (esta revisão): **Assuntos** — reabre de novo
porque fecha a pendência que a própria Sprint 11 registrou (§9 da v1.1.0):
sem esta tela, Questões/Flashcards e "conteúdo em assunto livre" não tinham
como ser abertos pela interface.

As três telas restantes (Erros, Progresso, Ajustes) ficam para sprints de
frontend seguintes — cada uma reabre este documento só se precisar de um
padrão novo; do contrário, segue o que já está aqui.

Decisão de stack não é feita aqui — é reaproveitada de ADR-028/030,
reexaminadas em 2026-08-31 contra a exigência de §1 abaixo e mantidas
(`docs/00A_ADR.md`, "Revisão" ao final da ADR-028).

---

## 1. Estrutura de arquivos

Tudo em `src/main/resources/static/` (ADR-030) — empacotado no mesmo JAR
pelo Maven, sem etapa de build própria (ADR-028). Nenhuma mudança no
`Dockerfile`/`docker-compose.yml` documentados em `docs/03E_DEPLOYMENT.md`.

```
src/main/resources/static/
├── index.html            # casca única — carrega tudo, navegação por hash
├── css/
│   └── styles.css        # só o que Tailwind (CDN) não cobre
└── js/
    ├── api.js             # wrapper de fetch (§5)
    ├── router.js           # roteamento por hash (§2)
    └── pages.js            # um componente Alpine por página (09_CODE_STYLE §8)
```

Tailwind e Alpine.js carregados via CDN no `<head>` de `index.html`
(ADR-028) — sem `npm`, sem `node_modules`, sem passo de build no Pi.

---

## 2. Roteamento por hash

`#/hoje`, `#/recuperar/{assuntoId}`. Navegação nunca vai ao servidor
(ADR-030) — `router.js` escuta `hashchange`, troca qual seção de
`index.html` fica visível e dispara o `init()` do componente Alpine
correspondente.

```js
window.addEventListener('hashchange', renderizarRotaAtual);
window.addEventListener('DOMContentLoaded', renderizarRotaAtual);
```

Sem rota (`#` vazio) redireciona para `#/hoje` — é a porta de entrada
(J-2/J-4, `02_JORNADAS §4`).

---

## 3. Um componente Alpine por página

`09_CODE_STYLE §8` já fixa a regra: um componente por página, registrado
em `js/pages.js` via `Alpine.data(...)`, nunca inline no HTML.

```js
// js/pages.js
Alpine.data('paginaHoje', () => ({
    carregando: true,
    erro: null,
    fila: [],
    blocoConteudo: null,
    async init() { /* §5 */ },
}));
```

**Sem lógica em atributo Alpine** (`09_CODE_STYLE §8`): um `x-on:click`
chama um método do componente (`@click="registrar()"`), nunca embute
expressão com efeito colateral (`@click="fila = fila.filter(...)"` fica
proibido — vira um método nomeado).

---

## 4. Consumo da API

`js/api.js` — um wrapper fino, sem biblioteca:

```js
async function api(caminho, opcoes = {}) {
    const resposta = await fetch(`/api${caminho}`, {
        headers: { 'Content-Type': 'application/json' },
        ...opcoes,
    });
    if (!resposta.ok) {
        const problema = await resposta.json(); // application/problem+json, ADR-026
        throw new ApiError(problema.detail, problema.codigo, problema.campo);
    }
    return resposta.status === 204 ? null : resposta.json();
}
```

- Sem versionamento de URL, sem envelope de sucesso (ADR-025/026) — o
  corpo de `200`/`201` é o recurso direto.
- `response.ok` decide sucesso/erro — nunca um campo `ok` de envelope
  próprio (ADR-026 já eliminou isso).
- Erro: `problema.detail` é exibido **direto ao usuário**, nunca composto
  a partir de `codigo` (ADR-026 — o texto já vem em português, pronto).

### Endpoints consumidos nesta rodada

| Tela | Método | Caminho | Resposta |
|---|---|---|---|
| Hoje | `GET` | `/api/turno/plano` | `TurnoPlanoResponse` — `filaRecuperacao: FilaRecuperacaoItemResponse[]`, `blocoConteudo: AssuntoResponse \| null` |
| Recuperar | `POST` | `/api/sessoes` | corpo `SessaoRequest` com `tipo: "RECUPERACAO"`; resposta `SessaoResponse` |
| Registrar sessão | `POST` | `/api/sessoes` | mesmo endpoint, `tipo: "ESTUDO"\|"QUESTOES"\|"FLASHCARDS"` (§7A) |
| Registrar sessão (Conteúdo) | `GET` | `/api/assuntos/{id}/segmentos` | `SegmentoResponse[]` — popula o seletor opcional de segmento (Sprint 10) |
| Assuntos | `GET` | `/api/disciplinas` | `DisciplinaResponse[]` — agrupa a lista |
| Assuntos | `GET` | `/api/assuntos?disciplinaId={id}` | `AssuntoResponse[]` — um `fetch` por disciplina ativa |
| Assuntos | `GET` | `/api/assuntos/{id}/fase` | `AssuntoFaseResponse` — um `fetch` por assunto listado (§7B, "N chamadas") |
| Assunto detalhe | `GET` | `/api/assuntos/{id}/segmentos` | mesmo endpoint da linha acima, reaproveitado — mostra o material do assunto |

Nenhum endpoint novo pra gravar — `/api/sessoes` já existe e está testado
(`docs/SPRINT-3-SESSAO.md`). A leitura de segmentos já existe e está
testada desde a Sprint 10 (`docs/SPRINT-10-SEGMENTO.md §5`); a leitura de
fase, desde a Sprint 5 (`docs/SPRINT-5-FRENTE.md §2.1`). A Sprint 12 não
abre nenhum endpoint novo — só passou a consumir o que já existia.

---

## 5. UI otimista com falha não destrutiva (`02_JORNADAS §5.1`)

> "A transição de tela é otimista. O sistema muda o estado localmente e
> devolve a resposta em milissegundos; a persistência acontece atrás. [...]
> A falha tem que ser não destrutiva."

Mecânica concreta, sem biblioteca de estado (avaliado em ADR-028, mantido):

1. O clique atualiza `x-data` do componente **antes** do `fetch` responder
   — a tela já reage.
2. `api(...)` roda em segundo plano.
3. **Sucesso**: nada a fazer — o estado otimista já era o estado final.
4. **Erro**: captura no `catch`, mantém o valor digitado (nunca limpa o
   formulário nem reverte o campo), mostra `problema.detail` e um botão de
   reenviar que chama o mesmo método — reenviar é **um clique**, não
   preencher tudo de novo.

Fila offline com reconciliação automática (retry sem intervenção do
usuário, ordem, conflito) fica **fora de escopo**, por decisão do próprio
`02_JORNADAS §5.1`: "é maquinaria real [...] se o uso mostrar que a conexão
cai com frequência, vira decisão própria, justificada, e não improviso."

---

## 6. Tela Hoje

Serve J-2 e J-4 (`02_JORNADAS §4`). Porta de entrada — orçamento de carga
**< 2 s**, sem clique nenhum até mostrar o plano do turno inteiro.

- Busca `GET /api/turno/plano` no `init()`.
- Renderiza a fila de recuperação (`filaRecuperacao`, ordenada como a API
  já devolve — mais atrasada primeiro) e o bloco de conteúdo
  (`blocoConteudo`, pode vir `null`).
- Cada item da fila é um link para `#/recuperar/{assuntoId}`.
- **Nota de fiação, não resolvida aqui**: a tela Recuperar (§7) precisa do
  nível/data prevista do item clicado, que `FilaRecuperacaoItemResponse` já
  carrega. Passar isso adiante (store Alpine global, ou parâmetro na URL)
  fica pra quem implementar o item 9.3 de `PROGRESSO.md` — é decisão de
  código, não de arquitetura.
- Não mostra "89 represadas" em alarde (J-4, `02_JORNADAS §3`) — só a fila
  já cortada no teto diário, que é o que a API devolve.

---

## 7. Tela Recuperar

A única com sequência vinculante — reproduz o wireframe de
`02_JORNADAS §4.1` (duas etapas, sem campo de resultado na primeira):

**Etapa 1** — nome do assunto, disciplina, "Nível N · previsto para
{data}", três botões de previsão (`vou reconstruir` / `vai faltar um
pedaço` / `não vai vir`), botão "Já tentei".

**Etapa 2** — só aparece depois de "Já tentei". Mostra a previsão feita
(não editável), pergunta "Conseguiu reconstruir?" com três botões
(`Reconstruí` / `Faltou um pedaço` / `Não veio`, âncoras de
`01_DOMINIO §4.1`), link "+ registrar um erro".

**Regras vinculantes** (`02_JORNADAS §4.1`, citadas aqui porque a
implementação depende disso, não porque este documento as define):
- O campo de resultado **não existe** na etapa 1 — ausente do DOM, não
  escondido com `x-show`.
- A previsão não é editável na etapa 2 — só texto.
- **Sem botão "pular"**. Sair da tela é a forma de não responder.

Ao confirmar a etapa 2: `POST /api/sessoes` com `tipo: "RECUPERACAO"`,
`resultado` = o botão clicado, `previsaoReconstrucao` = o que a etapa 1
capturou. Padrão otimista de §5: navega de volta pra Hoje **antes** da
resposta confirmar, com fallback de erro se falhar (§5.4).

Orçamento: **≤ 15 s**, 2 campos visíveis por etapa (`02_JORNADAS §5`).

---

## 7A. Tela Registrar sessão (Sprint 11)

Três variantes de um mesmo tipo de tela — `02_JORNADAS §4`: "Conteúdo,
questões ou flashcards" —, não três telas independentes. Compartilham
componente Alpine (`paginaRegistrar`) e rota por segmento de hash:
`#/registrar/{tipo}` (`tipo` = `conteudo`\|`questoes`\|`flashcards`),
opcionalmente `#/registrar/{tipo}/{assuntoId}` quando já vem com o assunto
definido (link de Hoje, abaixo).

### Seletor de tipo (padrão novo)

Três pílulas no topo (Conteúdo/Questões/Flashcards), `@click` troca
`this.tipo` e reseta os campos abaixo — nunca navega para outra rota (ao
contrário de Hoje→Recuperar, aqui é o mesmo componente trocando de forma).
Primeiro caso deste projeto de UI condicional por tipo dentro da mesma
tela; `09_CODE_STYLE §8` já cobre ("sem lógica em atributo Alpine") — a
troca é um método nomeado (`selecionarTipo(tipo)`), nunca expressão inline.

### Campos por tipo (`02_JORNADAS §5`, orçamento)

| Tipo | Campos | Orçamento |
|---|---|---|
| Conteúdo (`ESTUDO`) | Assunto, Segmento (opcional) | ≤ 20 s |
| Questões (`QUESTOES`) | Assunto, Formato da banca, Feitas, Certas, Previsão | ≤ 25 s |
| Flashcards (`FLASHCARDS`) | Assunto, Feitas, Certas, Previsão | sem linha própria no orçamento — mesmo molde de Questões, sem formato (D-36 só exige formato em `QUESTOES`) |

`tempoMinutos` não é campo visível em nenhuma variante — medido (tempo de
tela, `init()`→confirmar), mesmo padrão já usado em Recuperar (D-29: nunca
constante, sempre medido).

### Segmento (só em Conteúdo)

Se o assunto tem segmentos importados (Sprint 10), `GET
/api/assuntos/{id}/segmentos` popula um seletor opcional — mostra `ordem`
e `arquivo` de cada um. Ausente ou lista vazia: campo não aparece (não é
"desabilitado", é ausente do DOM, mesmo princípio da regra vinculante de
Recuperar §7). Ao enviar, `segmentoId` só vai no corpo se o candidato
escolheu um.

### Confirmar e enviar

`POST /api/sessoes`, mesmo padrão otimista de §5/§7: navega de volta pra
Hoje antes da resposta confirmar; falha volta pra esta tela com os campos
preenchidos intactos e um jeito de reenviar em um clique.

### Como se chega aqui (decisão de escopo desta sprint)

- **Conteúdo, com assunto pré-preenchido**: o card "Conteúdo novo" da tela
  Hoje (§6) passa a ser clicável, mesmo mecanismo de handoff que já existe
  pra Recuperar (`Alpine.store`, §6 da v1.0.0) — leva pra
  `#/registrar/conteudo/{assuntoId}`.
- **Questões, Flashcards, ou Conteúdo em outro assunto**: **sem ponto de
  entrada nesta sprint.** Escolher livremente qualquer assunto exige
  navegar/buscar entre todos os assuntos ativos — isso é a tela Assuntos
  (`02_JORNADAS §4`, ainda fora de escopo, `§9` abaixo). Registrado como
  pendência consciente, não escondida: as telas de Questões/Flashcards
  existem e funcionam se abertas direto pela URL, mas não há link pra elas
  em lugar nenhum da interface ainda.

---

## 7B. Tela Assuntos (Sprint 12)

Serve J-1 e J-3 (`02_JORNADAS §4`: "cadastro em lote, peso, frente ×
backlog"). Duas rotas, dois componentes Alpine — lista e detalhe, mesmo
padrão de mestre-detalhe que Hoje→Recuperar/Registrar já usa:

- `#/assuntos` — lista, agrupada por disciplina ativa, de todos os
  assuntos ativos, cada um com o chip de peso (`TipoPeso`) e o chip de fase
  (`FaseAssunto`, D-16 — derivada, nunca uma coluna).
- `#/assuntos/{id}` — detalhe de um assunto: nome, disciplina, peso, fase,
  e a lista de segmentos importados (Sprint 10) — `arquivo` de cada
  segmento é o link direto pro material (o SGE nunca abre o arquivo, só
  guarda o link, `02_JORNADAS §1.1`).

Não existe `GET /api/assuntos/{id}` (só listagem por disciplina, §4) — em
vez de criar esse endpoint novo, o detalhe reaproveita o mesmo mecanismo de
handoff que Hoje→Recuperar e Hoje→Registrar já usam (§6 da v1.0.0):
`Alpine.store('assuntoDetalhe')`, escrito pela lista no clique da linha
(que já tem `assunto`, `disciplina.nome` e `fase` em mãos, montados pra
renderizar o próprio chip). Só o resto (segmentos) vem de um `fetch` novo,
igual a §7A já faz. Mesma consequência que Recuperar/Registrar já aceitam:
abrir `#/assuntos/{id}` direto (sem passar pela lista antes — bookmark,
refresh) cai em `semDados`, não decisão nova desta sprint.

### Decisão de escopo desta sprint

O wireframe de referência (`Assuntos.dc.html`/`AssuntoDetalhe.dc.html`, já
existente da exploração de design anterior) também mostra botões "Importar
CSV"/"Exportar CSV" no topo da lista. **Ficam de fora nesta sprint**: os
dois endpoints já existem e já são usados na prática — mas por
`scripts/importar-*.sh` disparado por `rclone` (ADR-037,
`docs/SPRINT-10-SEGMENTO.md §7`) ou por `curl` direto, nunca por upload
manual pela interface. Construir upload de arquivo na SPA (`<input
type="file">`, `FormData`, tela de pré-visualização de erro por linha) é
escopo novo de verdade, não uma variação de padrão já existente — igual ao
corte que a Sprint 11 já fez pra Questões/Flashcards, registrado como
pendência consciente, não escondida (§9 abaixo). O que **não** pode
esperar é o motivo original de abrir esta sprint: dar à interface um jeito
de chegar em Questões, Flashcards e "conteúdo em assunto livre" — isso a
tela de detalhe resolve.

### Fase por assunto: N chamadas, não uma nova

`GET /api/assuntos/{id}/fase` já existe (Sprint 5) mas é por assunto, não
em lote — não há (e não se está criando agora) um endpoint que devolva a
fase de todos de uma vez. A lista faz um `fetch` de fase por assunto
carregado. Decisão consciente, não descuido: a escala deste sistema é um
usuário, dezenas de assuntos — N chamadas pequenas em paralelo (`Promise.all`)
custam bem menos que o orçamento de carga que J-3 tolera (balanço semanal,
não a porta de entrada de todo dia — `02_JORNADAS §5` não fixa orçamento
de segundo pra esta tela, ao contrário de Hoje/Recuperar). Se o número de
assuntos crescer a ponto de doer, endpoint de fase em lote é decisão de
sprint própria, não algo a antecipar agora sem o problema ter aparecido
(mesmo princípio de "sem fila offline" de §5).

### Entrada pra Registrar, a partir do detalhe

O detalhe do assunto ganha três ações — Conteúdo, Questões, Flashcards —
que fazem exatamente o que o card "Conteúdo novo" de Hoje já fazia (§7A):
`Alpine.store('registrar').selecionar(assunto)` seguido de navegação pra
`#/registrar/{tipo}/{assuntoId}`. Mesmo store, mesmo formato de item
(`AssuntoResponse` tem `id`/`nome`, que é tudo que `paginaRegistrar.abrir`
lê dele) — nenhum padrão novo aqui, só um segundo lugar que escreve no
mesmo store. Isso fecha a pendência de §9 da v1.1.0: Questões, Flashcards e
Conteúdo em qualquer assunto (não só o sugerido por Hoje) agora têm ponto
de entrada na interface.

### Estados de tela (§8)

Lista: carregando/erro/vazio (nenhuma disciplina ativa, ou nenhum assunto
ativo em nenhuma)/preenchido — os quatro de sempre. Detalhe: mesma
estrutura de Recuperar/Registrar quando o `id` da URL não corresponde a
assunto nenhum (`semDados`, §7A) — em vez de um erro genérico de rede.

---

## 8. Os quatro estados de tela

Referenciado por `09_CODE_STYLE §9` (`04_FRONTEND.md §10` — número que
mudou nesta v1.0.0, o checklist antigo aponta pra uma numeração de
documento que nunca existiu) sem nunca ter sido definido. Fechado aqui:

| Estado | Quando | Tratamento |
|---|---|---|
| **Carregando** | Entre `init()` e a primeira resposta da API | Indicador leve — nunca bloqueia interação com o que já está na tela |
| **Vazio** | Resposta chegou, não há nada a mostrar (ex.: fila de recuperação vazia) | Mensagem curta, nunca uma lista vazia sem explicação |
| **Erro** | `fetch` falhou ou a API devolveu `problem+json` | `problema.detail` exibido, ação de reenviar — nunca destrutivo (§5) |
| **Preenchido** | Estado normal | O conteúdo real da tela |

Toda tela implementa os quatro — é o que `carregando`/`erro` no `x-data`
de §3 já antecipam. Em Registrar sessão (§7A), "vazio" não se aplica do
mesmo jeito que em Hoje (não é uma lista): o estado equivalente é o
seletor de segmento ausente quando o assunto não tem nenhum — coberto em
§7A, não é um quinto estado novo.

---

## 9. Fora de escopo, registrado

- Fila offline com reconciliação automática (§5, decisão de
  `02_JORNADAS §5.1`).
- Design visual completo — cor, tipografia, biblioteca de componente.
  Usa utilitários padrão do Tailwind por enquanto; refinamento é documento
  futuro, quando houver tela real para servir de referência.
- As três telas restantes (Erros, Progresso, Ajustes) — cada uma decide,
  na sua sprint, se precisa de padrão novo além do que este documento já
  fixa.
- Upload de CSV (assunto e segmento) pela interface — os endpoints
  existem e são usados por script/`curl`; a tela Assuntos (§7B) só lê.
  Cadastro em lote continua fora da SPA por ora.
- Endpoint de fase em lote — a lista de Assuntos faz uma chamada por
  assunto (§7B); antecipar um endpoint agregado sem o custo real ter
  aparecido contrariaria o mesmo princípio que manteve a fila offline fora
  de escopo em §5.
- Otimização para celular (`02_JORNADAS §1.2`: evitar decisões que
  impeçam depois, sem pagar o custo de otimizar agora).

---

## 10. Changelog

| Versão | Data | Mudança |
|---|---|---|
| 1.2.0 | 2026-09-07 | Sprint 12 aberta — nova **§7B, tela Assuntos** (lista por disciplina + detalhe, `#/assuntos` e `#/assuntos/{id}`). Fecha a pendência de entrada pra Questões/Flashcards/conteúdo livre que a Sprint 11 tinha deixado registrada: o detalhe do assunto ganha três ações que escrevem no mesmo `Alpine.store('registrar')` que Hoje já usava. Decisão de escopo: upload de CSV pela interface (mostrado no wireframe de referência) fica de fora — os endpoints de importação já são usados por script, não por upload manual; registrado em §9. Fase por assunto é uma chamada por assunto, decisão consciente dado a escala de uso (usuário único, dezenas de assuntos), não descuido. §4 ganha quatro linhas de endpoint, nenhuma nova (todas já existiam de sprints anteriores) |
| 1.1.0 | 2026-09-06 | Sprint 11 aberta — nova **§7A, tela Registrar sessão** (Conteúdo/Questões/Flashcards, um componente Alpine, rota `#/registrar/{tipo}/{assuntoId}`). Dois padrões novos: seletor de tipo por pílulas, e campo de segmento opcional (Sprint 10, `GET /api/assuntos/{id}/segmentos`). Decisão de escopo: só o card "Conteúdo novo" de Hoje vira ponto de entrada nesta sprint — Questões/Flashcards e "conteúdo em outro assunto" ficam sem link na interface até a tela Assuntos existir, registrado como pendência em §9. §4 ganha as duas linhas de endpoint; §8 esclarece que "vazio" em Registrar sessão não é um quinto estado |
| 1.0.0 | 2026-08-31 | Criado. Sprint 9 aberta (frontend, Hoje + Recuperar). ADR-028/030 reexaminadas contra a exigência de UI otimista de `02_JORNADAS §5.1` e mantidas — decisão fechada com o usuário, nota registrada em `docs/00A_ADR.md`. Os "quatro estados de tela" citados por `09_CODE_STYLE §9` sem nunca terem sido definidos ficam fechados em §8 |
