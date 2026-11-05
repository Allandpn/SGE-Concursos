# Sprint 44 — Ícones do bloco, pontos altos e o atalho do teste

| | |
|---|---|
| Versão | 1.1.0 |
| Data | 2026-11-05 |
| Subordinado a | `01_DOMINIO` v1.45.0 §3.18 (D-172); `02_JORNADAS` v1.37.0 (J-2 com perguntas); `04_FRONTEND` v1.26.0 §8G; nota em ADR-053 |
| Origem | Retorno do usuário nos testes (2026-11-05), decidido numa rodada; wireframe aprovado na conversa |
| Modo de trabalho | Implementação direta (`AGENTS.md`) |

## 0. Escopo

**Entra:** ícone + palavra curta nos botões do bloco (Ler, Foco, Registrar); o sinal de "baixado" no lugar de "sem rede ✓"; os
pontos altos (as marcações do assunto) na Recuperar depois de conferir; o atalho do modo de teste no Mais e no menu lateral.

**Fica de fora:** mudar o que se guarda no aparelho (D-119) ou as marcações em si (§3.17); mostrar qualquer coisa do material antes
de conferir (D-125).

## 1. Servidor

| Peça | O quê |
|---|---|
| `PontosAltosService` | D-172: as marcações vigentes (`MarcacaoService.vigentes`) dos blocos não arquivados do assunto, por bloco, página e altura na página; o destaque leva a nota de trecho junto; a nota de página vem sozinha. Assunto que não existe: 404 `ASSUNTO_INEXISTENTE`. Só leitura |
| Rota | `GET /api/assuntos/{id}/pontos-altos` → `[{chave, segmentoId, bloco, pagina, tipo, cor, citacao, nota}]` |

Sem migração. O modo de teste copia `marcacao` e `marcacao_versao` como qualquer tabela.

## 2. Tela

- **Ícones** (`index.html`): `i-foco` (cronômetro), `i-registrar` (lápis), `i-guardado` (✓), `i-baixado` (seta para a bandeja),
  `i-teste` (frasco); `i-ler` já existia. Agora: o botão principal "Ler o bloco"/"Ler o material" ganha o livro e, com o bloco no
  aparelho, a bolinha verde; Foco e Registrar viram `.acao-icone`. Depois e reestudo: Foco e Ler com ícone e "baixado" à direita.
  Assunto: "Foco neste assunto" e "Ler" com ícone. O `aria-label` diz o nome inteiro e "baixado, abre sem internet".
- **Leitor:** "Guardar para ler sem rede" vira "Baixar para abrir sem internet" (e o aviso do bloco não baixado acompanha).
- **Recuperar** (`paginaRecuperar`): `conferir()` pede os pontos altos; a seção `#pontos-altos` aparece antes de "Conseguiu
  reconstruir?", com a borda e o rótulo na cor da marcação (as do leitor; no escuro, um tom mais claro), o trecho, a nota e
  "p. N →" (com "Bloco N ·" quando há mais de um bloco). Sem perguntas, uma linha: "Compare o que você lembrou com o que marcou".
  Voltar do leitor mantém a etapa (o `abrir` do mesmo assunto não reseta).
- **Modo de teste:** item no Mais ("ligado · data" ou "desligado") e no menu lateral; `#/ajustes/teste` abre Ajustes com o grupo
  aberto e rola até ele.
- `CASCA_VERSAO` sobe para `casca-2026-11-05-8`.

## 3. Testes

| Teste | Prova |
|---|---|
| `PontosAltosTest` | a ordem por bloco, página e altura; a nota de trecho junto do destaque; a nota de página; removida e bloco arquivado fora; a rota vazia, com dados e o 404 |
| Chromium (`e2e-44`) | Depois com os ícones e "baixado"; sem "sem rede"; Recuperar sem os pontos (nem o pedido) nas etapas 1 e 2; depois de conferir, 3 cartões na ordem, com a nota e o atalho; ir ao leitor e voltar na mesma etapa; gravar; Agora de conteúdo com o livro, a bolinha, Foco e Registrar; Mais › Modo de teste abre o grupo; o menu lateral; tema escuro |

## 4. Itens e ordem

| # | Item | Situação |
|---|---|---|
| 44.0 | Especificação e wireframe aprovados | feito |
| 44.1 | `PontosAltosService` e a rota | feito |
| 44.2 | Ícones, o sinal de baixado, os pontos altos na Recuperar, o atalho do teste; `CASCA_VERSAO` | feito |
| 44.3 | Testes e Chromium | feito |
| 44.4 | Deploy pelo Guardian | `release/18` (`2e73bfd`) |

## Changelog

| Versão | Data | Mudança |
|---|---|---|
| 1.1.0 | 2026-11-05 | Itens 44.1 a 44.3 feitos. `PontosAltosTest` (2); suíte local 495 de 497 (os 2 de esquema pedem Docker). Chromium: 28 passos; regressão 36, 38, 39, 40, 41 e 42 verde (o roteiro do 36 abre os grupos de Ajustes, da Sprint 40) |
| 1.0.0 | 2026-11-05 | Criado, com o wireframe aprovado pelo usuário |
