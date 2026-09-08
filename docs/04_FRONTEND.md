# 04 — FRONTEND

O documento de arquitetura do frontend: como os arquivos estáticos entram
no JAR, como a SPA consome a API, e o padrão de UI otimista que
`02_JORNADAS.md §5.1` exige. Nasce agora porque a Sprint 9 é a primeira a
tocar frontend — nenhuma sprint anterior escreveu uma linha de HTML/CSS/JS.

Definia cor e tipografia como "doc futuro, quando as primeiras telas já
existirem" (mesma fronteira que `02_JORNADAS §0` traçava: "isso é do
documento de interface, que vem depois") — com quatro telas em produção
(Hoje, Recuperar, Registrar, Assuntos), essa condição se cumpriu; §8A fecha
isso. Continua não definindo **componente visual** (biblioteca própria,
espaçamento sistemático, ícones) — refinamento maior que uma paleta e uma
fonte, ainda sem tela suficiente pra justificar o investimento.

| Campo | Valor |
|---|---|
| Versão | 1.6.0 |
| Data | 2026-09-07 |
| Status | Vigente |
| Subordinado a | `especificacao/02_JORNADAS.md` (jornadas, telas, orçamentos de tempo, §5.1); `docs/00A_ADR.md` ADR-025 (API REST), ADR-026 (erros RFC 9457), ADR-028 (zero build, revisada nesta data), ADR-030 (SPA estática); `docs/09_CODE_STYLE.md` §1/§8 (convenções já vigentes de Alpine/Tailwind) |

---

## 0. Escopo

**Sprint 9** (v1.0.0 deste documento): **Hoje** e **Recuperar**. **Sprint
11**: **Registrar sessão** — reabriu este documento porque introduziu dois
padrões novos (seletor de tipo, campo numérico) que as duas telas anteriores
não tinham, §7A. **Sprint 12**: **Assuntos** — reabriu porque fechou a
pendência que a própria Sprint 11 registrou (§9 da v1.1.0): sem essa tela,
Questões/Flashcards e "conteúdo em assunto livre" não tinham como ser
abertos pela interface. **Sprint 13**: **Erros** — reabriu porque fechou o
link inerte que a Sprint 9 já tinha deixado em Recuperar (§7, "+ registrar
um erro"). **Sprint 14** (esta revisão): **Progresso** — M-1, M-3 e M-4
numa tela nova; M-2 (retenção por assunto) entra como seção nova no
detalhe de Assuntos (§7B), não aqui — §7D explica por quê.

**Sprint 15** (esta revisão): **Ajustes**, a sétima e última tela do mapa
de `02_JORNADAS §4`. Diferente das seis anteriores: não existia endpoint
nenhum pra consumir — `docs/SPRINT-15-AJUSTES.md` abre `GET`/`PATCH
/api/parametros` sobre a tabela `parametro` que já existia desde a Sprint
3. É a primeira sprint de frontend que também é sprint de backend.

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
| Erros | `GET` | `/api/erros?assuntoId={id}` | `ErroResponse[]` — consulta por assunto (`02_JORNADAS §4`) |
| Erros | `POST` | `/api/erros` | corpo `ErroRequest` (`assuntoId`, `sessaoId` opcional, `descricao`, `causa`, `confianca`); resposta `ErroResponse` |
| Progresso | `GET` | `/api/metricas/m1?janela={GLOBAL\|POR_DISCIPLINA}` | `M1Response` — pílula troca a janela, refaz a chamada |
| Progresso | `GET` | `/api/metricas/m3` | `M3Response` |
| Progresso | `GET` | `/api/metricas/m4` | `M4Response` |
| Assunto detalhe | `GET` | `/api/metricas/m2?assuntoId={id}` | `M2Response` — nova seção "Retenção" no detalhe (§7B), não na tela Progresso |
| Ajustes | `GET` | `/api/parametros` | `ParametroResponse[]` — as 18 chaves (`docs/SPRINT-15-AJUSTES.md`) |
| Ajustes | `PATCH` | `/api/parametros/{chave}` | corpo `{ valor }`; resposta `ParametroResponse` atualizado — endpoint novo desta sprint |

Nenhum endpoint novo pra gravar — `/api/sessoes` já existe e está testado
(`docs/SPRINT-3-SESSAO.md`). A leitura de segmentos já existe e está
testada desde a Sprint 10 (`docs/SPRINT-10-SEGMENTO.md §5`); a leitura de
fase, desde a Sprint 5 (`docs/SPRINT-5-FRENTE.md §2.1`). `/api/erros` já
existe e está testado desde a Sprint 7 (`docs/SPRINT-7-METRICAS.md §5`) —
111/111 testes verdes confirmados nesta sessão antes de abrir a Sprint 13,
corrigindo o que `PROGRESSO.md` ainda registrava como "sem execução
verificada". Nenhuma sprint de frontend até aqui abriu endpoint novo — só
passou a consumir o que já existia.

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

### Retenção (M-2, adicionado na Sprint 14, §7D)

Nova seção no detalhe, ao lado de Material: `GET
/api/metricas/m2?assuntoId=` no mesmo `abrir()` que já busca segmentos —
mostra `percentualPrimeira` × `percentualMediaSeguintes` por tipo de sessão
(nunca somados entre si). Motivo de morar aqui, não na tela Progresso:
§7D explica.

---

## 7C. Tela Erros (Sprint 13)

Serve J-2 e J-3 (`02_JORNADAS §4`: "registro com causa e confiança; consulta
por assunto"). Uma rota, `#/erros/{assuntoId}`, um componente — formulário
de registro em cima, lista de erros do assunto embaixo (mesmo desenho do
wireframe de referência já existente, `Erros.dc.html`): descrição livre,
sete pílulas de causa (`CausaErro`), três de confiança (`NivelConfianca`),
botão Registrar; abaixo, os erros já registrados desse assunto.

### Decisão de escopo desta sprint

`02_JORNADAS §4.2` é explícito: **"registrar erro é ação global, disponível
em qualquer tela"** — um atalho que existiria em toda tela, não uma tela
que se navega até. Essa sprint **não entrega isso**. O que entrega são dois
pontos de entrada contextuais:

- **De Recuperar** (§7): o link "+ registrar um erro" da etapa 2, inerte
  desde a Sprint 9, passa a levar pra `#/erros/{assuntoId}`.
- **Do detalhe de Assuntos** (§7B): uma quarta ação, "Erros", ao lado de
  Conteúdo/Questões/Flashcards.

**`sessaoId` fica de fora nesta sprint, dos dois pontos de entrada.** O
link de Recuperar aparece na etapa 2 **antes** de `confirmar()` rodar — a
sessão daquela tentativa só é gravada quando um dos três botões de
resultado é clicado, e nesse momento a tela já navega embora (padrão
otimista, §5). Não existe, na experiência atual, um instante em que uma
`sessaoId` concreta esteja disponível no momento de registrar o erro, sem
reestruturar o fluxo vinculante de Recuperar — fora do escopo desta sprint
(regra central do `CLAUDE.md`: não é uma correção que este documento já
precise, então não se antecipa). `ErroRequest.sessaoId` continua existindo
no backend (D-46 já cobre isso desde a Sprint 7); o formulário desta sprint
sempre manda `null`.

O atalho **verdadeiramente global** — visível em qualquer tela, sem
precisar estar em Recuperar ou no detalhe de um assunto — fica de fora,
registrado em §9. Ele exige um padrão de componente que a arquitetura atual
não tem: algo sempre montado fora de uma seção de página (`09_CODE_STYLE
§8` hoje pressupõe "um componente por página"), mais um seletor de assunto
dentro do próprio formulário (as duas entradas desta sprint já carregam o
assunto de contexto; um atalho global não teria de onde puxar isso).
Entregar os dois pontos de entrada contextuais já é estritamente melhor que
o link inerte que existia — e não fecha a porta pro atalho global depois.

### Handoff e `semDados`

Mesmo mecanismo de §7B: `Alpine.store('erro')`, escrito pelas duas telas de
origem com `{ assuntoId, nome }`. Sem `GET /api/assuntos/{id}`
(mesma ausência já documentada em §7B), abrir `#/erros/{assuntoId}` sem vir
de um dos dois pontos de entrada cai em `semDados` — consequência aceita,
mesma de Recuperar/Registrar/Assunto detalhe.

### Sem resolver, por enquanto

`Erro.resolvido` existe na entidade mas nasce sempre `false` — a Sprint 7
não abriu endpoint de atualizar (`ErroService`, javadoc: "Sem
atualizar/resolver nesta sprint"). A lista mostra o erro, não uma ação de
resolver — não é uma omissão desta sprint de frontend, é limite do backend
que ela consome.

### Não é otimista, e é deliberado

Diferente de Recuperar/Registrar (§5, §7A), o registro aqui **não** navega
antes da resposta confirmar — espera o `POST` e só então limpa o formulário
e atualiza a lista. A regra de §5 existe pra proteger o orçamento de tempo
de telas que são **passo de um fluxo** (o turno de estudo não pode travar
esperando rede); Erros não navega embora depois de registrar — a tela
continua aberta, pronta pro próximo erro, então não há navegação nenhuma
pra antecipar. Falha ainda é não destrutiva (texto digitado permanece se o
`POST` falhar), só que sem o salto de tela que caracteriza "otimista".

### Orçamento

`02_JORNADAS §5`: "Registrar erro, 30 s, 3 campos" — bate com o formulário
(descrição, causa, confiança); a lista abaixo não conta orçamento, é
consulta, não parte do registro.

---

## 7D. Tela Progresso (Sprint 14)

Serve J-3 (`02_JORNADAS §4`: "as quatro métricas, com as regras de n de
`00_PRODUTO §7`"). Sem contexto de assunto/sessão — rota `#/progresso`,
novo link no cabeçalho — porque M-1, M-3 e M-4 são leituras globais; **M-2
não mora aqui** (próxima seção).

### Por que M-2 não está nesta tela

M-2 exige `assuntoId` (`M2Response.assuntoId`) — é retenção *dentro* de um
assunto específico (primeira exposição × média das seguintes), não uma
leitura global como as outras três. Colocar M-2 na tela Progresso exigiria
um seletor de assunto só pra essa métrica — padrão novo, evitável: o
detalhe de Assuntos (§7B) já tem o `assuntoId` em mãos. M-2 vira uma nova
seção "Retenção" lá, ao lado de Material — um `GET
/api/metricas/m2?assuntoId=` a mais no `abrir()` que já existe.

Isso também **não** é o mesmo erro que `00_PRODUTO §7` proíbe
explicitamente: "acerto por assunto nunca será medida" (linha 258-260) é
sobre M-1 — comparar assuntos entre si por percentual de acerto, amostra
pequena demais (13 questões/assunto/ano) pra sustentar. M-2 compara um
assunto **consigo mesmo** (primeira exposição vs. depois), pergunta
diferente, já desenhada assim desde a Sprint 7.

### M-1 — pílula de janela

`JanelaMetrica` tem duas opções, com **escopo e período diferentes, não só
período**: `GLOBAL` soma tudo (`disciplinaId` nulo nas linhas), janela
trimestral; `POR_DISCIPLINA` quebra por disciplina ativa, janela anual —
por isso vira pílula (padrão já usado em Registrar §7A), não checkbox: são
duas consultas diferentes, trocar exige novo `fetch`. `POR_DISCIPLINA`
precisa do nome de cada disciplina pra rotular a linha — `GET
/api/disciplinas` de novo, mesmo padrão de N-chamadas-pequenas já aceito em
§7B.

Cada linha é um cartão: rótulo (nome da disciplina, ou "Global" quando
`disciplinaId` é nulo) + formato (`D-36`: nunca soma formatos diferentes) +
percentual. Regra de `n` (`00_PRODUTO §7`, já espelhada nos `@Schema` dos
DTOs): `percentual` nulo vira **"—"**, nunca `0%`; abaixo disso a fração
(`acertos`/`total`) ainda aparece, só o percentual some.

### Sem seta de tendência — limite do backend, não desta sprint

O wireframe de referência (`Progresso.dc.html`) mostra uma seta "↑ 10 pts"
comparando com o período anterior. `00_PRODUTO §7` até prevê isso ("seta de
tendência... exigem n ≥ 100") — mas `M1Response` não carrega o período
anterior, nem existe parâmetro pra pedir dois períodos numa chamada só.
Sem um segundo ponto de dado, não há tendência pra desenhar — inventar um
"anterior" no cliente seria mentir com número (o próprio `00_PRODUTO §7`
condena isso). Fica fora, registrado em §9: implementar exigiria decisão de
backend (nova sprint), não é lacuna de frontend. O campo `confiavel`
(`n ≥ 100`) aparece nesta sprint só como uma marca discreta "amostra
grande" — sem cor, sem seta, porque não há com o que comparar ainda.

### M-3 e M-4

M-3: dois cartões, objetiva (`mediaDesvio` com sinal — positivo
superestimou) e subjetiva (contagem de super/sub/acertou), nunca somadas
(mesmo texto do `@Schema`). `n = 0` em qualquer uma mostra "sem dados
ainda", não um desvio de `0.0` que pareceria calibração perfeita sem ser.

M-4: percentual (ou "—" se `totalCumpridas = 0`) + fração
`dentroDoPrazo`/`totalCumpridas`.

---

## 7E. Tela Ajustes (Sprint 15)

Serve J-1 e J-3 (`02_JORNADAS §4`: "tetos, limiares, intervalos"). Rota
`#/ajustes`, novo link no cabeçalho. Lista as 18 chaves de `Parametro`
(`GET /api/parametros`), agrupadas em três seções — Tetos, Limiares de
resultado, Escada — mesmo agrupamento do wireframe de referência
(`Ajustes.dc.html`), derivado do prefixo da chave (`teto_*`, `limiar_*`, o
resto é Escada). Cada linha: rótulo amigável (mapa fixo em `pages.js`,
`ROTULOS_PARAMETRO` — chave→texto em português, `02_JORNADAS` nunca expôs
a chave crua ao usuário) + campo de valor editável.

### Edição por linha, sem botão "salvar tudo"

Cada campo salva sozinho no `@change` (perde o foco com o valor mudado) —
sem otimismo (mesmo motivo de Erros §7C: não há navegação nenhuma
acontecendo, então não há o que antecipar) e sem um botão de salvar global,
porque os 18 valores são independentes entre si (mudar um não afeta outro
visualmente). O campo usa `x-model="draft[chave]"` — uma cópia editável,
separada de `parametro.valor` (que só muda quando o `PATCH` confirma) —
**não** `:value="parametro.valor"` direto. Tentativa inicial foi essa
ligação direta, pensando que reverteria sozinha em caso de falha; achado
testando no navegador que não reverte: se `PATCH` falha, `parametro.valor`
nunca muda, e o Alpine só reescreve o DOM quando uma propriedade reativa
**muda de valor** — nada muda, nada reescreve, o texto inválido fica preso
no campo mesmo com o estado interno certo. A correção: em caso de falha,
`draft[chave]` é reatribuído pro valor antigo — essa atribuição **é** uma
mudança de verdade (de `"abc"` pra `"10"`, por exemplo), então o Alpine
reage e o campo volta visualmente. Mensagem de erro aparece **ao lado da
linha** que falhou, não um banner de tela inteira — as outras 17 continuam
editáveis.

### Validação é só a do backend

`docs/SPRINT-15-AJUSTES.md §0`: PATCH aceita qualquer número positivo, sem
faixa por chave — decisão do usuário, registrada lá. O frontend não
duplica validação nenhuma: manda o texto que o campo tem, deixa o `422`
(`VALOR_INVALIDO`) do backend decidir, mostra `problema.detail` (ADR-026,
mesmo padrão de toda tela).

### Sem POST/DELETE

A lista de 18 chaves é fechada — `Ajustes` só edita `valor`, nunca cria ou
remove uma chave. Não há formulário de "novo parâmetro" nesta tela, nem
deveria haver (`01_DOMINIO §7.5`: a lista do que é ajustável já é fechada
pela própria especificação).

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

## 8A. Paleta e tipografia

Decisão do usuário: referência visual é o **qconcursos.com** — plataforma
de estudo pra concurso já conhecida, não um mockup inventado. Cores
extraídas de verdade do site (`getComputedStyle`, não achismo de captura de
tela), depois mapeadas por **papel funcional**, não copiadas 1:1 — o
qconcursos usa verde pra CTA de venda de plano, que o SGE não tem.

### Tokens (`tailwind.config`, no `<head>` de `index.html`)

| Token | Papel | Hex | Onde aparece hoje |
|---|---|---|---|
| `primary-600` | Ação principal | `#0694a2` | Botão "Registrar", pílula de tipo/formato selecionada |
| `primary-700` | Hover da ação principal | `#057885` | `:hover` dos mesmos botões |
| `primary-100`/`primary-800` | Destaque suave | `#c7edf0`/`#045e68` | Chip de fase `FRENTE` |
| `alerta-50`/`alerta-600` | Atenção | `#fff4ed`/`#fe6112` | Chip de peso `ALTO` — mesmo par bg/texto que o qconcursos usa no próprio badge de urgência |
| `font-sans` | Tipografia padrão | Open Sans (Google Fonts) | Todo o app — Tailwind Preflight aplica em `html` sem precisar de classe |

**Por que teal e não verde pro `primary`:** no qconcursos, teal é a cor do
botão "Mesa de Estudos" — a ação de estudar em si; verde fica reservado a
preço/upsell, papel que o SGE não tem. `primary` no SGE é literalmente
"a ação que o usuário mais clica" (Registrar, seletor de tipo) — o mesmo
papel do teal lá.

**Fácil personalizar, de propósito:** nenhuma tela usa hex direto — sempre
`primary-*`/`alerta-*`. Trocar a paleta inteira depois é editar só o bloco
`tailwind.config` no `<head>`; nenhum outro arquivo muda. Cores neutras
(cinza de texto/borda, verde de `CONSOLIDADO`, âmbar de peso `MEDIO`, e o
vermelho de estado de erro) continuam nos utilitários padrão do Tailwind —
não são identidade visual, são estado semântico, e ficam de fora da paleta
customizada de propósito (erro precisa continuar "vermelho de erro"
reconhecível, nunca a cor de destaque do produto).

---

## 9. Fora de escopo, registrado

- Fila offline com reconciliação automática (§5, decisão de
  `02_JORNADAS §5.1`).
- Biblioteca de componente própria e espaçamento sistemático — cor e
  tipografia já estão definidas (§8A); o que falta é refinamento maior,
  ainda sem tela suficiente pra justificar o investimento.
- Seta de tendência de M-1 (`00_PRODUTO §7`, "seta... exigem n ≥ 100") —
  `M1Response` não carrega um período anterior pra comparar; precisa de
  decisão de backend (novo parâmetro ou endpoint), não é lacuna de
  frontend. §7D.
- Atalho global de "registrar erro", visível em qualquer tela sem
  depender de contexto (Recuperar ou detalhe de Assunto) — `02_JORNADAS
  §4.2` pede isso, a Sprint 13 entrega só os dois pontos de entrada
  contextuais (§7C). Exige um padrão de componente novo (algo fora de
  "um componente por página") e um seletor de assunto no formulário.
- Resolver um erro (`Erro.resolvido`) — sem endpoint desde a Sprint 7;
  a lista de Erros (§7C) só mostra, não fecha o ciclo.
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
| 1.6.0 | 2026-09-07 | Sprint 15 aberta e fechada — nova **§7E, tela Ajustes**, a sétima e última do mapa de `02_JORNADAS §4`. Primeira sprint de frontend que também é sprint de backend: `docs/SPRINT-15-AJUSTES.md` abre `GET`/`PATCH /api/parametros` sobre a tabela `parametro` (Sprint 3), decisão de validação (número positivo, sem faixa por chave) fechada com o usuário antes do código. As 18 chaves em três seções (Tetos/Limiares/Escada, mesmo agrupamento do wireframe de referência); cada linha salva sozinha no `@change`, reverte pra trás sozinha em caso de erro via `:value` (não `x-model`). §9 perde a última entrada de tela pendente |
| 1.5.0 | 2026-09-07 | Sprint 14 aberta — nova **§7D, tela Progresso** (M-1 com pílula de janela GLOBAL/POR_DISCIPLINA, M-3, M-4). Decisão de escopo: M-2 exige `assuntoId`, então vira seção nova ("Retenção") no detalhe de Assuntos em vez de morar em Progresso — §7B ganha esse parágrafo. Seta de tendência de M-1 (pedida por `00_PRODUTO §7`) fica de fora: `M1Response` não carrega um período anterior pra comparar, precisa de decisão de backend; registrado em §9. Nenhum endpoint novo: `/api/metricas/*` já existia da Sprint 7, testado (17/17 verdes contando Erro+Métrica) antes de escolher esta sprint. §4 ganha quatro linhas de endpoint |
| 1.4.0 | 2026-09-07 | Sprint 13 aberta — nova **§7C, tela Erros**. Fecha o link inerte "+ registrar um erro" que a Sprint 9 deixou em Recuperar. Decisão de escopo: `02_JORNADAS §4.2` pede um atalho global de registrar erro em qualquer tela; esta sprint entrega só dois pontos de entrada contextuais (Recuperar pós-tentativa com `sessaoId`, detalhe de Assuntos sem `sessaoId`) — o atalho verdadeiramente global fica registrado em §9, exige um padrão de componente que a arquitetura ainda não tem. Nenhum endpoint novo: `GET`/`POST /api/erros` já existiam da Sprint 7 e passaram no teste completo (111/111) antes de abrir esta sprint. §4 ganha duas linhas de endpoint |
| 1.3.0 | 2026-09-07 | Nova **§8A, paleta e tipografia** — a pedido do usuário, que quis a paleta e o estilo do qconcursos.com. Cor extraída de verdade do site (`getComputedStyle`) e mapeada por papel funcional, confirmada com o usuário antes de implementar (teal como `primary` — ação principal, no lugar do verde de upsell que o qconcursos usa; laranja só no chip de peso `ALTO`; fonte trocada pra Open Sans). Tokens `primary`/`alerta` no `tailwind.config` do `<head>`, nenhum hex direto nas telas — trocar a paleta depois é editar um bloco só. Aplicado retroativamente às quatro telas já existentes (Hoje, Recuperar, Registrar, Assuntos), sem mudar nenhuma delas de arquitetura. §0/§9 atualizados: "cor e tipografia são doc futuro" fechado, resta só biblioteca de componente/espaçamento como fora de escopo |
| 1.2.0 | 2026-09-07 | Sprint 12 aberta — nova **§7B, tela Assuntos** (lista por disciplina + detalhe, `#/assuntos` e `#/assuntos/{id}`). Fecha a pendência de entrada pra Questões/Flashcards/conteúdo livre que a Sprint 11 tinha deixado registrada: o detalhe do assunto ganha três ações que escrevem no mesmo `Alpine.store('registrar')` que Hoje já usava. Decisão de escopo: upload de CSV pela interface (mostrado no wireframe de referência) fica de fora — os endpoints de importação já são usados por script, não por upload manual; registrado em §9. Fase por assunto é uma chamada por assunto, decisão consciente dado a escala de uso (usuário único, dezenas de assuntos), não descuido. §4 ganha quatro linhas de endpoint, nenhuma nova (todas já existiam de sprints anteriores) |
| 1.1.0 | 2026-09-06 | Sprint 11 aberta — nova **§7A, tela Registrar sessão** (Conteúdo/Questões/Flashcards, um componente Alpine, rota `#/registrar/{tipo}/{assuntoId}`). Dois padrões novos: seletor de tipo por pílulas, e campo de segmento opcional (Sprint 10, `GET /api/assuntos/{id}/segmentos`). Decisão de escopo: só o card "Conteúdo novo" de Hoje vira ponto de entrada nesta sprint — Questões/Flashcards e "conteúdo em outro assunto" ficam sem link na interface até a tela Assuntos existir, registrado como pendência em §9. §4 ganha as duas linhas de endpoint; §8 esclarece que "vazio" em Registrar sessão não é um quinto estado |
| 1.0.0 | 2026-08-31 | Criado. Sprint 9 aberta (frontend, Hoje + Recuperar). ADR-028/030 reexaminadas contra a exigência de UI otimista de `02_JORNADAS §5.1` e mantidas — decisão fechada com o usuário, nota registrada em `docs/00A_ADR.md`. Os "quatro estados de tela" citados por `09_CODE_STYLE §9` sem nunca terem sido definidos ficam fechados em §8 |
