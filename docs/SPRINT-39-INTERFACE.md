# Sprint 39 — Interface: base, navegação, Hoje e estudo

| | |
|---|---|
| Versão | 1.1.0 |
| Data | 2026-11-05 |
| Subordinado a | `docs/04_FRONTEND.md` v1.20.0 §8B; `02_JORNADAS` v1.32.0 J-2; ADR-060 |
| Origem | Pedido do usuário (2026-11-04), decidido em duas rodadas; wireframe aprovado na conversa |
| Modo de trabalho | Implementação direta (`AGENTS.md`) |

## 0. Escopo

**Entra:** o tema claro e escuro (ADR-060) com a escolha em Ajustes; a navegação (barra inferior, menu lateral, "Mais", título da
tela no celular); Hoje com o cabeçalho do dia, o progresso, a próxima ação e a lista "Depois"; Recuperar, Registrar e o leitor em modo
foco, com etapas e o botão fixo; datas humanas nas telas tocadas.

**Fica para a Sprint 40:** Assuntos, Progresso, Concursos, Erros (com a lista geral, que ainda não existe — por isso não está em
"Mais"), Provas, Ajustes completo, a conferência e a porta (`entrar.html`, ainda no tema claro).

## 1. Servidor

| Peça | O quê |
|---|---|
| `TurnoPlanoResponse.feitasHoje` | Sessões ativas com data de hoje — o "1 de 4 feitos" |

## 2. Tela

| Peça | O quê |
|---|---|
| `css/tema.css` | As variáveis de cor, claro em `:root`, escuro em `:root.escuro` e por `prefers-color-scheme` sem `.claro` |
| `tailwind.config` | `white`, `sobre`, `gray`, `primary`, `alerta`, `red`, `green`, `amber` apontam para as variáveis; `text-white` virou `text-sobre` |
| Escolha antes do desenho | Um script no `<head>` aplica `claro`/`escuro` de `localStorage.aparencia` |
| `js/navegacao.js` | `Alpine.store('nav')` (rota, foco, "Mais") e `aparenciaAjustes`; o router marca a rota e o foco (Recuperar, Registrar, leitor) |
| Hoje | `proximaAcao` (reestudo, recuperação, bloco, simulado), `filaDepois`, `reestudosDepois`, `totalDoDia`; avisos de processo no fim; recarrega em `sge:sessao-registrada` |
| Recuperar | Etapas 1 Prever, 2 Reconstruir, 3 Conferir (o gabarito só é pedido aqui), 4 o que faltou; o resultado é escolhido e confirmado no botão fixo; "Sair" pergunta no meio |
| Registrar | Etapas 1 Dados, 2 Previsão, 3 Registrar (questões e flashcards); a previsão em faixas; o botão diz o que vai acontecer |
| `api.js` | `dataHumana`, `atrasoHumano`, `hojeHumano` |

### Ajuste de paleta pelo contraste

Medido no Chromium: o branco sobre o principal de §8A (`#0694a2`) dava 3,64:1 e o laranja de texto (`#e2530a`) sobre o fundo de
atenção 3,55:1, abaixo de 4,5:1. No claro, `primary-600` passa a `#057f8c` (4,75:1), `primary-700` a `#046f7b` e `alerta-700` a
`#b8430a`; o texto em `alerta-600` passou a `alerta-700` (o 600 fica para borda, onde basta 3:1). O teste confere 15 pares nos dois temas.

## 3. Testes

| Teste | Prova |
|---|---|
| `FeitasHojeTest` | o plano conta só as sessões de hoje |
| Chromium (Sprint 39) | 30 passos: cabeçalho, progresso, Agora, Depois, um só botão principal, barra e menu marcados, Mais, o tema (escolha, recarga, aparelho), o contraste de 15 pares nos dois temas, Recuperar e Registrar em foco com etapas e gravação, o leitor em foco, o computador claro e escuro |
| Chromium (Sprints 36 e 38) | refeitos com a tela nova: 23 e 17 passos |

## 4. Itens e ordem

| # | Item | Situação |
|---|---|---|
| 39.0 | Especificação e wireframe aprovados | feito |
| 39.1 | Tema e paleta (ADR-060); navegação | feito |
| 39.2 | Hoje; Recuperar, Registrar e leitor em foco; datas humanas; Aparência | feito |
| 39.3 | Testes e Chromium | feito |
| 39.4 | Deploy pelo Guardian | — |

## Changelog

| Versão | Data | Mudança |
|---|---|---|
| 1.1.0 | 2026-11-05 | Itens 39.1 a 39.3 feitos. O Chromium achou: o contraste do principal e do laranja de texto (paleta ajustada, acima); o cartão "Agora" lendo o item de uma recuperação quando a próxima ação era um bloco (corrigido); Hoje carregando antes de a gravação otimista terminar (recarrega no fim) |
| 1.0.0 | 2026-11-04 | Criado, com o wireframe aprovado pelo usuário |
