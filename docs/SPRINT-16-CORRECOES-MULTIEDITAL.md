# SPRINT 16 — Correções da escada, reestudo, multi-edital e app

| Campo | Valor |
|---|---|
| Versão | 1.0.0 |
| Data | 2026-10-14 |
| Status | Vigente |
| Especificação conceitual | `01_DOMINIO.md` v1.19.0, `02_JORNADAS.md` v1.6.0 |
| ADRs | ADR-038, ADR-039, ADR-040 |

Nasce de uma análise do código contra a especificação feita em 2026-10-14,
depois que o concurso para o qual o sistema foi pensado (SEFAZ SC) deixou de
existir no horizonte. O sistema passa a servir **qualquer edital** e a ser
usado **como app no celular e no tablet, além da web**. Seis itens, todos
aprovados pelo usuário antes da implementação.

---

## 0. Escopo

| # | Item | Natureza |
|---|---|---|
| 1 | `PARCIAL` em manutenção desconsolidava o assunto | defeito (divergia de `01_DOMINIO` §5.5) |
| 2 | `FALHA` nunca devolvia o assunto à fila de estudo | lacuna (D-07, D-11) |
| 3 | Importação de assunto não reconhecia `chaveExterna`; script importava todos os concursos | defeito + ADR-039 |
| 4 | Fase calculada assunto por assunto (centenas de consultas por plano) | desempenho |
| 5 | Frontend dependia de CDN; erro de rede/HTML virava erro genérico | ADR-038 + robustez |
| 6 | Documentação desatualizada (`CLAUDE.md`, `PROGRESSO.md`) | higiene |

**Fora:** entidade `Concurso`, leitura do `concurso.json`, diagnóstico de
edital, modo reta final, multiusuário (ADR-040) — ficam no mapa de evolução
do `PROGRESSO.md`.

---

## 1. Estado de consolidação — uma função, uma fonte

### 1.1 A regra (`01_DOMINIO` §5.5, v1.19.0)

Percorre os resultados das revisões **cumpridas no nível-alvo**, em ordem de
criação (id):

| Estado atual | Resultado | Novo estado |
|---|---|---|
| não consolidado | `SUCESSO` | sequência + 1; com 2, consolida |
| não consolidado | `PARCIAL` ou `FALHA` | sequência zera |
| consolidado | `SUCESSO` ou `PARCIAL` | continua consolidado |
| consolidado | `FALHA` | desconsolida (D-11), sequência zera |

Mora em `revisao.EstadoConsolidacao` (função pura, sem Spring), junto com o
mapeamento peso → nível-alvo (D-23). Usada por:

- `RevisaoService` — roteamento (`SPRINT-4-ESCADA.md` §3.3, tabela intacta;
  só o que "consolidado" significa ficou preciso);
- `FrenteService` — fase derivada (§3 abaixo).

ADR-033 continua valendo: a regra de consolidação existe em um lugar só.

### 1.2 Intervalo da pendente

A janela de tolerância (§3.2 da Sprint 4) e a reativação (D-49) precisavam
do "intervalo da pendente" e o reconstruíam a partir do estado atual — o que
errava depois de um `PARCIAL` em manutenção (agendado na metade do intervalo,
reconstruído como inteiro). Passa a ser o **intervalo que foi de fato
agendado**: `dataPrevista − sessaoOrigem.data`, em dias. Revisão sem sessão
de origem (não acontece pelo código, a coluna admite nulo) cai no intervalo
do nível.

---

## 2. Reestudo (D-07, D-11 — `01_DOMINIO` §5.1, v1.19.0)

### 2.1 Derivação

Assunto em reestudo ⇔ existe revisão `PENDENTE` cuja `sessaoOrigem` tem
resultado `FALHA`, e nenhuma sessão `ESTUDO` do mesmo assunto tem `id` maior
que o dessa sessão de origem. "Depois" é ordem de registro (`id`), não de
`data`: registrar hoje um estudo retroativo também conta como a correção
pedida.

Uma consulta (`RevisaoRepository.listarEmReestudo`), `join fetch` no assunto
e na sessão de origem, ordenada pela data da falha (mais antiga primeiro).
Nenhuma coluna, nenhuma tabela (D-16).

### 2.2 No plano do turno

`TurnoPlanoResponse` ganha `reestudos: List<ReestudoItemResponse>`
(`assuntoId`, `nome`, `nivel`, `dataFalha`). Sem teto: é estudo de assunto
que já está na frente, não ocupa vaga nova. A tela Hoje mostra a lista entre
a fila de recuperação e o bloco de conteúdo.

---

## 3. Fases em lote

`FrenteService.fasesDe(List<Assunto>)` calcula a fase de um conjunto de
assuntos com **duas consultas**, qualquer que seja o tamanho do conjunto:

1. `SessaoRepository.assuntosComSessao(ids)` — quais têm alguma sessão;
2. `RevisaoRepository.listarCumpridasDosAssuntos(ids)` — projeção
   `(assuntoId, nivel, resultado)` das cumpridas, em ordem de id.

O resto é memória: disciplina inativa → `BACKLOG` (D-24); sem sessão →
`BACKLOG`; `EstadoConsolidacao` sobre os resultados no alvo → `CONSOLIDADO`
ou `FRENTE`. `resumo`, `proximaVaga` e `proximaSugestaoDeConteudo` passam a
usar o mapa; o plano calcula as fases uma vez só.

Teste de equivalência: para um conjunto de assuntos em todas as fases, o
lote devolve exatamente o mesmo que `fase(id)` de cada um.

---

## 4. Frontend (ADR-038)

- `static/vendor/tailwindcss-play-3.4.16.js` e `static/vendor/alpinejs-3.14.3.min.js`,
  baixados uma vez e versionados; `index.html` aponta para eles.
- `manifest.webmanifest` + ícones (`static/icons/`) + `sw.js`:
  - cache só da casca estática (`CASCA_VERSAO` no topo do arquivo — **trocar a
    cada mudança de arquivo estático**);
  - `/api/**` sempre vai à rede, nunca é cacheado nem enfileirado;
  - navegação sem rede cai no `index.html` do cache.
- `api.js`: falha de rede vira `ApiError` com `codigo = REDE_INDISPONIVEL`;
  resposta de erro que não é `application/problem+json` (502 do proxy, HTML)
  vira `codigo = ERRO_SERVIDOR` com o status. Nenhuma tela perde o dado
  digitado — caminho não destrutivo de `04_FRONTEND.md` §5 intacto.
- Tela Hoje: seção "Reestudo" (§2.2), que leva a Registrar sessão `ESTUDO`
  daquele assunto.
- Requisitos de toque de `02_JORNADAS` §1.2: `meta viewport` já existia;
  alvos de toque mínimos de 44 px nos botões principais.

---

## 5. Importação multi-edital

### 5.1 `chaveExterna` como chave de reimportação (`02_JORNADAS` J-1 v1.6.0)

`ImportacaoAssuntoService`: linha sem `id` com `chaveExterna` já existente →
`atualizar` naquele assunto. O `validar` conta essa linha como "atualizado",
não "novo". Linha com `id` continua decidindo pelo `id`.

### 5.2 Concurso-alvo ativo (ADR-039)

`scripts/importar-segmentos.sh` lê `CONCURSO_ATIVO` (slug da pasta em
`SGE-Importacao/`). Sem a variável, não importa nada e registra o motivo.
Com ela, importa só `assuntos.csv` e depois `segmentos.csv` daquela pasta.

---

## 6. Testes

| Teste | Prova |
|---|---|
| `EstadoConsolidacaoTest` (unitário) | a tabela de §1.1 linha a linha, incluindo reconsolidar após `FALHA` |
| `RevisaoEscadaTest.parcialEmManutencao_mantemConsolidado` | `PARCIAL` em manutenção agenda metade e o `SUCESSO` seguinte volta à manutenção |
| `RevisaoEscadaTest.janela_usaIntervaloAgendado` | depois de `PARCIAL` em manutenção, a janela é 20% da metade, não do inteiro |
| `TurnoServiceTest.falha_colocaEmReestudo_estudoTira` | D-07 |
| `TurnoServiceTest.falhaConsolidado_colocaEmReestudo` | D-11 |
| `FrenteServiceTest.fasesEmLote_igualAFaseIndividual` | §3 |
| `ImportacaoAssuntoTest.reimportarPorChaveExterna_atualiza` | §5.1 |

---

## 7. Changelog

| Versão | Data | Mudança |
|---|---|---|
| 1.0.0 | 2026-10-14 | Criado |
