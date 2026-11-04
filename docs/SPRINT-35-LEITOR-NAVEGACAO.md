# Sprint 35 — Leitor: rolagem contínua, deslizar e pinça

| | |
|---|---|
| Versão | 1.1.0 |
| Data | 2026-11-03 |
| Subordinado a | `02_JORNADAS` v1.29.0 J-10 ("passar as páginas"); `01_DOMINIO` §3.17 (D-114 a D-121, sem mudança); ADR-052 |
| Origem | Pedido do usuário (2026-11-03); wireframe aprovado na conversa |
| Modo de trabalho | Implementação direta (`AGENTS.md`) |

## 0. Escopo

**Entra:** rolagem contínua por padrão; "uma página por vez (deslizar)" no menu ⋮, guardado no aparelho; zoom por pinça (os botões − e +
continuam no menu); o contador que abre "ir para a página"; os botões ‹ › saem.

**Não muda:** marcar (seleção, cores, nota de página), a lista de marcações, sem rede, "Terminei", retomar de onde parou. Nada no
servidor: sem migração, sem rota nova.

## 1. Como ficou (`static/js/leitor.js`)

| Peça | O quê |
|---|---|
| `montarPaginas` | Um elemento por página (canvas, marcações, camada de texto, pinos), criado pelo script com o tamanho certo desde já; os gestos de toque, clique e clique duplo presos a cada página |
| `IntersectionObserver` | Desenha a página que chega a até uma tela e meia da vista e solta o canvas da que se afasta (memória do celular) |
| `aoRolar` | Na rolagem contínua, a página mais visível é a do contador e a que o leitor guarda (D-121) |
| `mostrarModo` / `escolherModo` | Uma página por vez esconde as outras; a escolha fica em `localStorage` (`leitor.modo`), com `try/catch` |
| `gestoInicio/Movimento/Fim` | Pinça: escala visual durante o gesto e o zoom de verdade ao soltar (0,6× a 4×), mantendo a página à vista. Deslizar (só no modo página): ≥ 60 px, mais lateral que vertical, < 0,7 s; ignorado com texto selecionado, painel aberto ou página mais larga que a tela |
| Seleção e notas | A seleção sabe em que página está (a camada de texto que a contém); a barra de cores é posicionada em pixels na área das páginas; nota de página e nota de trecho gravam a página certa |
| `index.html` | `touch-action: pan-x pan-y` na área de rolagem (a pinça é do leitor, não do navegador); contador como botão; "ir para a página"; as duas opções no topo do menu |

## 2. Testes

Sem teste de servidor (nada mudou lá). Chromium (`412×900`), com o PDF de 5 páginas:

| Passo | Resultado |
|---|---|
| Abrir | 5 páginas no DOM, 4 desenhadas (a quinta longe da vista), contador 1 / 5 |
| Rolar até o fim | contador 5 / 5 e a página 5 guardada no banco (D-121) |
| Ir para a página 2 e marcar | destaque gravado na página 2 e desenhado nela |
| Clique duplo na página 3 | nota de página gravada na página 3, pino na 3; tocar no destaque da 2 abre o painel dele |
| Pinça (toques sintéticos) | largura da página 394 → 788 px |
| Uma página por vez | 1 página visível; deslizar à esquerda vai à 3, à direita volta à 2, toque curto não troca |
| Recarregar | o modo continua "página" e reabre na página 2 |

## 3. Decisões e alternativas

| Escolhido | Recusado | O que quebraria |
|---|---|---|
| Elementos das páginas criados pelo script | `x-for` do Alpine | O Alpine embrulharia os objetos do PDF.js num Proxy (os campos privados quebram, achado da Sprint 31) e redesenharia a lista a cada mudança |
| Soltar o canvas da página que se afasta | Manter todas desenhadas | Um bloco de 30 páginas em zoom alto passa de centenas de MB de canvas no celular |
| Pinça com escala CSS durante o gesto e redesenho ao soltar | Redesenhar a cada movimento | O PDF.js não redesenha a 60 quadros por segundo no celular; a página tremeria |

## Changelog

| Versão | Data | Mudança |
|---|---|---|
| 1.1.0 | 2026-11-03 | Itens 35.1 e 35.2 feitos, conferidos no Chromium (§2). `CASCA_VERSAO` sobe |
| 1.0.0 | 2026-11-03 | Criado, com o wireframe aprovado pelo usuário |
