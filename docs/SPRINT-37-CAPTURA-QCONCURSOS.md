# Sprint 37 — A captura do QConcursos

| | |
|---|---|
| Versão | 1.1.0 |
| Data | 2026-11-05 |
| Subordinado a | `01_DOMINIO` v1.44.0 §3.28 (D-166 a D-171); `02_JORNADAS` v1.36.0 J-22; `04_FRONTEND` v1.25.0 §8F; **ADR-063** |
| Origem | Pedido do usuário (2026-11-04), decidido em duas rodadas (2026-11-05); wireframe aprovado |
| Modo de trabalho | Implementação direta (`AGENTS.md`) |
| Segurança | **Uma porta nova no app** (ADR-063): rotas fora da sessão, com chave de envio. Nenhum arquivo de infraestrutura muda |

## 0. Escopo

**Entra:** a chave de envio por navegador; as rotas de envio (respostas e diagnósticos); o userscript servido pelo SGE; o assunto
pelo par aprendido ou pela árvore; a caixa em Hoje; a previsão na saída do atalho e o lote preenchido; o diagnóstico.

**Sem o HTML real do QConcursos** (o site bloqueia a leitura automática; não foi contornado): o userscript nasce com as pistas do
script do usuário ("Parabéns! Você acertou!", "Você errou!", "Gabarito oficial da banca", os links de disciplina e assunto, o
número Q) e com o **diagnóstico** — automático quando não entende uma questão e "Diagnóstico desta página" a qualquer hora. Os
seletores se acertam com os primeiros recortes reais.

## 1. Banco

`V34__captura_qconcursos.sql`: `captura_chave` (nome, `resumo` SHA-256 único, revogada, último envio); `questao_capturada` (o número
Q, acerto, quando e o dia, o que o QC mostra, o formato pelo gabarito, a chave, o assunto achado, `NA_CAIXA` / `REGISTRADA` /
`DESCARTADA`, a sessão; único por número e dia, D-166); `qconcursos_par` (nome normalizado → assunto, D-167);
`captura_diagnostico` (motivo, endereço, recorte até 20.000 caracteres). O modo de teste não copia as três da captura.

## 2. Servidor

| Peça | O quê |
|---|---|
| `CapturaFiltro` | `/captura/qconcursos/*`: só POST, JSON, com tamanho, até 64 KB, `Authorization: Bearer` de chave que vale (senão 405, 411, 413, 415 ou 401). `SessaoAcessoFilter` e `ModoTesteFiltro` não tratam essas rotas |
| `CapturaChaveService` | Gerar (`eag_` + 32 bytes base64url, mostrada uma vez), listar, revogar, autenticar (marca o último envio) |
| `CapturaEnvioController` | `POST /captura/qconcursos/respostas` → `{ok, repetida, assunto}`; `POST /captura/qconcursos/diagnosticos` |
| `CapturaService` | D-167: o par (do assunto mais específico ao mais geral), senão o nó vigente por número ou nome ligado pela conferência a um só assunto ativo. A caixa por assunto e as sem assunto pelo nome do QC (sem o acerto); parear (grava o par e leva as da caixa); descartar; registrar a caixa (uma sessão de questões por formato, com a previsão, a data da mais recente e o tempo por questão); `desde` e `marcar` para o lote |
| Rotas com sessão | `/api/captura/chaves` (GET, POST, `/{id}/revogar`), `/api/captura/caixa` (GET, `/registrar`, `/descartar`), `/api/captura/pares`, `/api/captura/desde`, `/api/captura/marcar`, `/api/captura/diagnosticos` (GET, DELETE) |
| O userscript | `GET /captura/eagora-qconcursos.user.js` — na lista aberta (não tem segredo: só o endereço do SGE; o Tampermonkey o baixa sem o cookie), com `__SGE__` e `@connect` preenchidos |

A rota de envio chama-se "respostas", não "questões": D-72 proíbe rota de questões fora da prova hospedada, e esta só recebe.

## 3. Tela e userscript

- **Ajustes › IA e integrações › Captura do QConcursos** (`js/captura.js`): o nome do navegador, Gerar chave (mostrada uma vez, com
  Copiar), as chaves com o último envio e Revogar, Instalar o userscript, os diagnósticos (com Apagar).
- **O atalho** passa pela folha "Antes de ir: quanto você acha que vai acertar?"; o link da folha abre o QConcursos e guarda a saída
  com a previsão. Na volta, o lote mostra "Previsão feita antes de sair" e, com captura, "N capturadas desde HH:MM · confira" com
  feitas e certas preenchidas; registrar marca as capturadas.
- **Hoje:** o cartão "Questões do QConcursos" (antes da lista "Depois"): Registrar (a previsão antes de ver o acerto, depois "Crase:
  1 de 2 certas · registrado"), Escolher assunto (para as sem assunto), Descartar.
- **O userscript** (`src/main/resources/captura/eagora-qconcursos.user.js`): observa a página; ao surgir o resultado, sobe até o
  menor bloco com um número Q só, lê e envia por `GM_xmlhttpRequest` (`anonymous`, sem cookie); fila no navegador quando o SGE não
  responde; o selo com a contagem, o campo da chave na primeira vez, "Enviar diagnóstico", "Diagnóstico desta página" e "Trocar a
  chave".
- `CASCA_VERSAO` sobe.

## 4. Testes

| Teste | Prova |
|---|---|
| `CapturaTest` | a porta (sem chave, chave errada, texto, 70 KB, GET, revogada; só o resumo guardado); sem assunto na caixa, parear, a próxima já com assunto, registrar sem previsão recusado e com previsão (3 feitas, 2 certas, a data); o lote (`desde`, `marcar`) e descartar; o diagnóstico e o userscript com o endereço; o nó da árvore por nome e por número |
| `AcessoRotasTest` | as rotas da captura respondem 401 do `CapturaFiltro` sem chave; o userscript na lista aberta |
| Chromium (22 passos) | a chave em Ajustes; uma página do QConcursos simulada com o userscript servido pelo SGE e o `GM_xmlhttpRequest` de verdade até o SGE; o selo pede a chave; duas respostas lidas (número, acerto, gabarito, banca); a sem número vira diagnóstico; a caixa, escolher o assunto, registrar com a previsão antes do acerto; a folha do atalho com a previsão; o lote preenchido pela captura; revogar e o selo avisar |
| Regressão | os roteiros das Sprints 36 (21, ajustado para a folha da previsão), 39 (30), 40 (24), 41 (14), 42 (15) e 43 (15) |

## 5. Itens e ordem

| # | Item | Situação |
|---|---|---|
| 37.0 | Especificação e wireframe aprovados | feito |
| 37.1 | Migração, a porta e as rotas | feito |
| 37.2 | O userscript, Ajustes, a caixa, a folha e o lote | feito |
| 37.3 | Testes e Chromium | feito |
| 37.4 | Deploy pelo Guardian | `release/17` (`a68a360`) |
| 37.5 | Acertar os seletores com os primeiros diagnósticos reais | — |

## Changelog

| Versão | Data | Mudança |
|---|---|---|
| 1.1.0 | 2026-11-05 | Itens 37.1 a 37.3 feitos. A rota de envio passa a `/respostas` (D-72); o userscript fica na lista aberta (o Tampermonkey o baixa sem o cookie). `CapturaTest` (5); suíte local 493 de 495 (os 2 de esquema pedem Docker; `EstruturaSchemaTest` com as tabelas novas). Chromium 22 passos e a regressão |
| 1.0.0 | 2026-11-05 | Criado, com o wireframe aprovado pelo usuário |
