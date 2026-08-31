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
| Versão | 1.0.0 |
| Data | 2026-08-31 |
| Status | Vigente |
| Subordinado a | `especificacao/02_JORNADAS.md` (jornadas, telas, orçamentos de tempo, §5.1); `docs/00A_ADR.md` ADR-025 (API REST), ADR-026 (erros RFC 9457), ADR-028 (zero build, revisada nesta data), ADR-030 (SPA estática); `docs/09_CODE_STYLE.md` §1/§8 (convenções já vigentes de Alpine/Tailwind) |

---

## 0. Escopo desta primeira rodada

Só duas telas: **Hoje** e **Recuperar** (Sprint 9, `PROGRESSO.md`). As
outras cinco (Registrar sessão, Assuntos, Erros, Progresso, Ajustes) ficam
para sprints de frontend seguintes — cada uma reabre este documento só se
precisar de um padrão novo; do contrário, segue o que já está aqui.

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

Nenhum endpoint novo — os dois já existem e estão testados (`docs/
SPRINT-6-TURNO.md`, `docs/SPRINT-3-SESSAO.md`).

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

Toda tela desta sprint (Hoje, Recuperar) implementa os quatro — é o que
`carregando`/`erro` no `x-data` de §3 já antecipam.

---

## 9. Fora de escopo, registrado

- Fila offline com reconciliação automática (§5, decisão de
  `02_JORNADAS §5.1`).
- Design visual completo — cor, tipografia, biblioteca de componente.
  Usa utilitários padrão do Tailwind por enquanto; refinamento é documento
  futuro, quando houver tela real para servir de referência.
- As cinco telas restantes (Registrar sessão, Assuntos, Erros, Progresso,
  Ajustes) — cada uma decide, na sua sprint, se precisa de padrão novo
  além do que este documento já fixa.
- Otimização para celular (`02_JORNADAS §1.2`: evitar decisões que
  impeçam depois, sem pagar o custo de otimizar agora).

---

## 10. Changelog

| Versão | Data | Mudança |
|---|---|---|
| 1.0.0 | 2026-08-31 | Criado. Sprint 9 aberta (frontend, Hoje + Recuperar). ADR-028/030 reexaminadas contra a exigência de UI otimista de `02_JORNADAS §5.1` e mantidas — decisão fechada com o usuário, nota registrada em `docs/00A_ADR.md`. Os "quatro estados de tela" citados por `09_CODE_STYLE §9` sem nunca terem sido definidos ficam fechados em §8 |
