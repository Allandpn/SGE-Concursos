# Sprint 31 — Leitor de PDF no app, com marcações

| | |
|---|---|
| Versão | 1.1.0 |
| Data | 2026-11-01 |
| Subordinado a | `00_PRODUTO` v1.12.0 §5.2.1, §8.5; `01_DOMINIO` v1.32.1 §3.17, D-114 a D-121; `02_JORNADAS` v1.21.0 §1.1, J-10, §4.1; `03_INVARIANTES` v1.20.1; ADR-052 |
| Origem | Pedido do usuário (2026-11-01), testando a jornada no MPPR; wireframe aprovado na conversa |
| Modo de trabalho | Implementação direta (`AGENTS.md`) |

## 0. Escopo

**Entra:** a tela Leitor (`#/ler/{segmentoId}`) com PDF.js; destaque em três cores, nota de trecho e nota de página; lista de marcações por
página com filtro; histórico (removidas e versões, só consulta); retomar na última página; "Terminei" que abre o Registrar; guardar blocos para
ler sem rede (os de Hoje sozinhos, os outros por toque) e a fila de marcações sem rede; "Ler" no lugar de "Abrir material" em Hoje, no reestudo e
no detalhe do assunto.

**Fica de fora:** restaurar uma versão antiga (o histórico é só consulta, como no wireframe); "Terminei" sem rede (fica desligado até a rede
voltar); perguntas da IA (Sprint 32); PDFs próprios (Sprint 33); o app Android nativo; zoom por pinça (o leitor ajusta à largura e tem botões
de zoom).

## 1. O desenho em uma página

```
"Ler" (Hoje, reestudo, assunto) ─► #/ler/{segmentoId}
   GET /api/segmentos/{id}/leitura ─► {segmento, assunto, total de blocos, última página, marcações vigentes}
   PDF: /material/... (cache "material" do service worker, senão rede) ─► PDF.js: canvas + camada de texto
Selecionar texto ─► 3 cores + nota ─► mudança {mudancaId (UUID do aparelho), marcacaoChave, …, editadaEm}
   ─► fila no IndexedDB ─► POST /api/marcacoes/mudancas (lote; idempotente por mudancaId, D-118)
Página muda ─► PUT /api/segmentos/{id}/leitura {pagina} (com rede; D-121)
"Terminei" ─► #/registrar/conteudo/{assuntoId}?segmento={id} ─► o Registrar de sempre (foco livre, D-100)
Hoje carregado ─► guarda no aparelho os PDFs e os dados de leitura dos blocos do plano (D-119)
```

## 2. Banco — `V24__marcacao.sql`

```sql
CREATE TABLE marcacao (
    id              BIGSERIAL PRIMARY KEY,
    chave           UUID NOT NULL,              -- gerada no aparelho: marcação criada sem rede já tem nome
    segmento_id     BIGINT NOT NULL REFERENCES segmento(id),
    pagina          INT NOT NULL,
    tipo            VARCHAR(12) NOT NULL,       -- DESTAQUE | NOTA_TRECHO | NOTA_PAGINA
    destaque_id     BIGINT REFERENCES marcacao(id),   -- só a nota de trecho
    retangulos      JSONB,                      -- só o destaque: [[x, y, largura, altura], …] em fração da página
    citacao         TEXT,                       -- só o destaque: o texto selecionado
    posicao_x       NUMERIC(5,4),               -- só a nota de página (fração da página)
    posicao_y       NUMERIC(5,4),
    criada_em       TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_marcacao_chave UNIQUE (chave),
    CONSTRAINT ck_marcacao_d115_pagina CHECK (pagina >= 1),
    CONSTRAINT ck_marcacao_d115_forma CHECK (…)  -- §2.1
);

CREATE TABLE marcacao_versao (
    id              BIGSERIAL PRIMARY KEY,
    marcacao_id     BIGINT NOT NULL REFERENCES marcacao(id),
    mudanca_id      UUID NOT NULL,              -- D-118
    cor             VARCHAR(10),                -- D-116: IMPORTANTE | DUVIDA | DECORAR (só destaque)
    texto           TEXT,                       -- a nota
    removida        BOOLEAN NOT NULL DEFAULT false,
    editada_em      TIMESTAMPTZ NOT NULL,       -- a hora do gesto, no aparelho (D-117)
    recebida_em     TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_marcacao_versao_d118_mudanca UNIQUE (mudanca_id),
    CONSTRAINT ck_marcacao_versao_d116_cor CHECK (cor IS NULL OR cor IN ('IMPORTANTE','DUVIDA','DECORAR'))
);

CREATE TABLE segmento_leitura (               -- D-121: a última página aberta; conveniência, não entra em métrica
    segmento_id     BIGINT PRIMARY KEY REFERENCES segmento(id),
    pagina          INT NOT NULL CHECK (pagina >= 1),
    atualizada_em   TIMESTAMPTZ NOT NULL
);
```

### 2.1 A forma de cada tipo (D-115), no banco

| Tipo | `destaque_id` | `retangulos`, `citacao` | `posicao_x/y` |
|---|---|---|---|
| `DESTAQUE` | nulo | obrigatórios | nulos |
| `NOTA_TRECHO` | obrigatório | nulos | nulos |
| `NOTA_PAGINA` | nulo | nulos | obrigatórios |

O gatilho `trg_marcacao_d115_nota_de_destaque` recusa nota de trecho cujo `destaque_id` não aponta para um `DESTAQUE` do mesmo segmento e página.
O gatilho `trg_marcacao_versao_d116_cor` recusa versão de destaque sem cor e versão de nota com cor. Nenhum `DELETE` no código (D-18): remover é
uma versão com `removida = true`.

**A versão vigente é derivada** (D-16, D-117): a de maior `editada_em`, empate pela de maior `id` (a que chegou depois). Não há coluna "vigente".

## 3. Rotas

| Rota | Entrada → saída | Regra |
|---|---|---|
| `GET /api/segmentos/{id}/leitura` | → `{segmentoId, assuntoId, assuntoNome, ordem, total, arquivo, paginaInicial, paginaFinal, ultimaPagina, marcacoes[]}` | D-121, D-115 |
| `PUT /api/segmentos/{id}/leitura` | `{pagina}` → 204 | D-121 |
| `POST /api/marcacoes/mudancas` | `[{mudancaId, marcacaoChave, segmentoId, pagina, tipo, destaqueChave?, retangulos?, citacao?, posicaoX?, posicaoY?, cor?, texto?, removida, editadaEm}]` → `{aplicadas, repetidas}` | D-115 a D-118 |
| `GET /api/segmentos/{id}/marcacoes/historico` | → `{removidas[], versoes[]}` | D-117 |
| `GET /api/assuntos/{id}/segmentos` (já existe) | usado pelo reestudo para abrir o primeiro bloco | — |

A mudança que cria uma marcação leva os dados de forma (tipo, retângulos…); as seguintes só a chave e o que mudou. Mudança de uma marcação que
o servidor ainda não conhece e não traz a forma é recusada (422) e fica na fila do aparelho até a criação chegar. Repetir uma mudança já gravada
responde como `repetida`, sem erro (D-118). Marcação de bloco que não é do arquivo local (link antigo do Drive) não existe: o leitor só abre
`/material/**`; o link externo continua no menu como "Abrir fora do app".

## 4. Tela

- **Leitor** (`#/ler/{segmentoId}`), a nona tela. Barra de cima: voltar, "Assunto · Bloco N de M", ☰ (lista), ⋮ (guardar sem rede, abrir fora do
  app, histórico). Página: canvas do PDF.js + camada de texto + camada de marcações, ajustados à largura; botões − / + de zoom. Barra de baixo:
  página N/total, anterior/próxima, **Terminei** (desligado sem rede).
- **Selecionar** texto mostra o menu com as três cores e "nota". **Tocar** num destaque abre o painel (cor, nota, remover). **Tocar e segurar**
  (ou clique duplo no computador) num ponto sem texto cria nota de página.
- **Sem rede:** faixa "Sem rede · marcações por enviar: N"; bloco não guardado mostra "Este bloco não está no aparelho".
- **Hoje:** o bloco de conteúdo e cada reestudo ganham **Ler**; o selo "sem rede ✓" mostra os já guardados. **Assunto:** cada bloco ganha **Ler**.
  O antigo "Abrir material" sai de Hoje e do assunto (fica só como "Abrir fora do app" no menu do leitor).
- **Recuperar** não muda: não tem, e continua sem ter, link de material (D-114); um teste de tela prova.
- `CASCA_VERSAO` sobe; `js/leitor.js`, `js/offline.js` e o PDF.js entram na casca.

## 5. Sem rede (D-119)

- **Cache `material-v1`** no service worker, separado da casca (trocar `CASCA_VERSAO` não o apaga). `/material/**` passa a ser
  "cache primeiro, senão rede", só para o que está guardado; o resto de `/material/**` segue da rede.
- **Guardar** um bloco = guardar o PDF no cache e o `GET /leitura` dele no IndexedDB (`eagora`, depósito `blocos`). Hoje guarda sozinho os blocos
  do plano (bloco de conteúdo e reestudos) quando há rede. Limite: **30 blocos**; passou, sai o guardado há mais tempo que não é de hoje.
- **Fila** de mudanças no IndexedDB (depósito `fila`), em ordem; enviada ao abrir o leitor, ao voltar a rede (`online`) e a cada 30 s com o leitor
  aberto. A fila nunca é limpa por falta de espaço: o limite é só de PDFs (ADR-052).
- Ao abrir um bloco sem rede, o leitor junta as marcações guardadas com as mudanças da fila, para mostrar o que a pessoa fez.

## 6. Testes

| Teste | Prova |
|---|---|
| `MarcacaoRestricoesTest` | Forma de cada tipo (D-115), nota de trecho sem destaque, cor fora das três e destaque sem cor (D-116), mudança repetida (D-118) — direto no banco |
| `MarcacaoServiceTest` | Criar, editar, remover, versão vigente pela hora do aparelho chegando fora de ordem (D-117), lote idempotente (D-118), histórico |
| `LeituraTest` | Última página guardada e devolvida (D-121); `GET /leitura` com as marcações vigentes, sem as removidas |
| `AcessoRotasTest` (já existe) | As rotas novas nascem fechadas (D-106) |
| Chromium (Playwright) | Abrir pelo Hoje, destacar, nota, nota de página, lista, recarregar e ver tudo; sem rede (contexto offline): bloco guardado abre e marca, fila sobe ao voltar; Recuperar sem material; "Terminei" abre o Registrar com o bloco |

## 7. Itens e ordem

| # | Item | Situação |
|---|---|---|
| 31.0 | Especificação (2026-11-01) e wireframe aprovado | feito |
| 31.1 | `V24__marcacao.sql` com as restrições e gatilhos | feito |
| 31.2 | `MarcacaoService`, `LeituraService` e rotas | feito |
| 31.3 | PDF.js em `vendor/`; Leitor: página, camada de texto, destaques, notas, lista, histórico, zoom | feito |
| 31.4 | Sem rede: cache `material-v1`, IndexedDB (blocos e fila), guardar os de Hoje | feito |
| 31.5 | "Ler" em Hoje, reestudo e assunto; "Terminei" → Registrar; `CASCA_VERSAO` | feito |
| 31.6 | Testes e Chromium | feito |
| 31.7 | Deploy pelo Guardian, junto ou depois da Sprint 30 | — |

## 8. Decisões e alternativas

| Escolhido | Recusado | O que quebraria |
|---|---|---|
| Chave da marcação gerada no aparelho (UUID) | Id do servidor | Marcação criada sem rede não teria como ser editada antes de subir |
| Versão vigente derivada (maior hora de edição) | Coluna "vigente" | D-16; e edições chegando fora de ordem exigiriam reescrever a coluna |
| Retângulos em fração da página | Pixels ou coordenadas do PDF | Pixels mudam com o zoom; fração serve em qualquer largura e no celular e no computador |
| Lote de mudanças num só `POST` | Uma rota por gesto | A fila sem rede sobe de uma vez; idempotência por `mudancaId` cobre o reenvio |
| Limite por número de blocos (30) | Limite em bytes | O navegador não diz com precisão quanto o cache ocupa; 30 blocos de ~60 min cabem com folga num celular |
| "Terminei" desligado sem rede | Guardar o "Terminei" e abrir depois | Registrar sem rede seria a fila offline do registro, que o ADR-052 recusou |

## 9. Riscos

- **Selecionar texto no celular** depende da camada de texto do PDF.js e do navegador; o Chromium de teste não é um Android real. Conferir no
  aparelho depois do deploy.
- **PDF escaneado** não tem texto: só nota de página (já dito em §3.17).
- **Espaço:** o navegador pode apagar o cache sem aviso; o bloco volta a pedir rede, as marcações pendentes ficam (IndexedDB separado).
- **Sem Docker nesta sessão**: os testes de schema não rodam aqui.

## Changelog

| Versão | Data | Mudança |
|---|---|---|
| 1.1.0 | 2026-11-01 | Itens 31.1 a 31.6 feitos. `V24`, `MarcacaoService` (cada mudança na sua transação; `MARCACAO_DESCONHECIDA` fica na fila do aparelho), `LeituraService`, rotas; PDF.js 4.10.38 (build *legacy*, para Android e Safari mais antigos) em `vendor/`; `js/leitor.js`, `js/offline.js`; "Ler" em Hoje, reestudo e assunto. 9 testes novos (`MarcacaoTest`). Chromium: Hoje guarda o bloco sozinho; destacar, trocar a cor, nota de trecho, nota de página, recarregar na página guardada, lista e volta à marcação; sem rede o bloco guardado abre e marca, o não guardado avisa, a fila sobe quando a rede volta; "Terminei" abre o Registrar com o bloco, sem gravar; Recuperar sem material; nenhum erro de página. Dois achados (os dois do Alpine, registrados em `04_FRONTEND` §7L): o documento do PDF.js atrás do Proxy quebra campos privados, e o IndexedDB não clona Proxy |
| 1.0.0 | 2026-11-01 | Criado, com o wireframe aprovado pelo usuário |
