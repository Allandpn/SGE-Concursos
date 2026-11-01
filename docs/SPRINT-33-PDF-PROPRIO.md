# Sprint 33 — PDF próprio

| | |
|---|---|
| Versão | 1.1.0 |
| Data | 2026-11-01 |
| Subordinado a | `00_PRODUTO` v1.14.0 §8.5; `01_DOMINIO` v1.34.0 §3.19, D-129 a D-132; `02_JORNADAS` v1.23.0 J-11; `03_INVARIANTES` v1.22.0; ADR-054 |
| Origem | Pedido do usuário (2026-11-01); wireframe aprovado na conversa |
| Modo de trabalho | Implementação direta (`AGENTS.md`) |

## 0. Escopo

**Entra:** enviar um PDF a um assunto com o tipo de leitura; cortar em blocos no SGE (PDFBox), pelos marcadores quando há; gravar os
blocos como segmentos depois dos existentes; servir em `/material/proprios/**`; listar os PDFs do assunto; "Tirar este PDF"; leitura
sugerida para assunto na escada; perguntas também para blocos próprios.

**Fica de fora:** escolher páginas à mão; renomear o PDF; mover um PDF de assunto; PDF próprio sem assunto; o app Android nativo.

## 1. O desenho em uma página

```
Assunto → "Adicionar meu PDF" ─► POST /api/assuntos/{id}/pdfs (multipart: arquivo, tipo APOSTILA | LEI_SECA)
   SGE, na mesma requisição (segundos):
     abre (protegido/inválido → 422), conta páginas, mede o texto (sem texto → aviso, D-130)
     grava pdf_proprio + EDITAIS_DIR/proprios/<id>/original.pdf
     corta: ~60 min pelo ritmo (2 ou 3 min/página), no início de seção (marcadores) entre 75% e 125% do alvo
     cada bloco → EDITAIS_DIR/proprios/<id>/segmento-NN.pdf + segmento (chave proprio-<id>-<n>, ordem depois das existentes)
   ─► {id, nome, blocos, minutos, semTexto}
Leitor, offline, "Ler": arquivo = /material/proprios/<id>/segmento-NN.pdf (servido do volume editais)
Hoje: proximaContinuacao → assunto em estudo (D-55) ou, depois, bloco próprio não lido de assunto na escada (D-131)
"Tirar este PDF" ─► POST /api/pdfs/{id}/tirar ─► retirado_em + arquivado_em nos segmentos dele (D-132)
```

## 2. Banco — `V26__pdf_proprio.sql`

| Tabela | Colunas | Restrições |
|---|---|---|
| `pdf_proprio` | `id`, `assunto_id` (FK), `nome`, `tipo` (`APOSTILA`, `LEI_SECA`), `paginas`, `sem_texto`, `criado_em`, `retirado_em` | `ck_pdf_proprio_d129_tipo`, `paginas >= 1` |
| `segmento` | ganha `pdf_proprio_id` (nulo para o material do acervo e o anexo) | `fk_segmento_d132_pdf_proprio` |

Nada se apaga (D-18, D-132): tirar marca `retirado_em` e arquiva os segmentos. Os arquivos ficam.

## 3. Rotas

| Rota | Entrada → saída | Regra |
|---|---|---|
| `POST /api/assuntos/{id}/pdfs` | multipart `arquivo` (até 50 MB) e `tipo` → `201 {id, nome, blocos, minutos, semTexto}` | D-129, D-130; não PDF ou protegido `422 PDF_INVALIDO`; assunto inexistente `404` |
| `GET /api/assuntos/{id}/pdfs` | → `[{id, nome, tipo, paginas, semTexto, criadoEm, segmentos: [ids]}]` (só os não retirados) | J-11 passo 3 |
| `POST /api/pdfs/{id}/tirar` | → 204 | D-132 |

`spring.servlet.multipart.max-file-size` passa de 30 MB para 50 MB (o PDF do edital continua sendo conferido pelo próprio limite dele).

## 4. O corte

- Ritmo: `APOSTILA` 2 min/página, `LEI_SECA` 3 min/página. Alvo: 60 min → 30 ou 20 páginas.
- Inícios de seção: as páginas de destino de todos os marcadores (`PDDocumentOutline`, recursivo).
- Do início `i`, o fim ideal é `i + alvo − 1`. Se há início de seção `s` com `s − 1` entre 75% e 125% do alvo, o bloco termina em
  `s − 1` (o mais próximo do ideal); senão, no ideal. Sobra final menor que 25% do alvo junta-se ao último bloco.
- Cada bloco é um PDF novo com as páginas importadas (`PDDocument.importPage`); `pagina_inicial/final` guardam as páginas no
  original; `tempo_estimado_min` = páginas × ritmo.
- Texto útil (sem espaços) abaixo de 300 caracteres no documento todo → `sem_texto` (D-130): o bloco existe, as perguntas ficam
  `SEM_TEXTO` quando o bloco for lido (D-122 já trata).

## 5. Outras mudanças

- `MaterialEstaticoConfig`: `/material/proprios/**` servido de `EDITAIS_DIR/proprios/`, registrado antes de `/material/**`.
- `TextoDoBloco` (perguntas): resolve também `/material/proprios/...` no volume `editais`.
- `FrenteService.proximaContinuacao`: depois dos assuntos em estudo, o primeiro bloco próprio não lido de um assunto que já tem
  revisão (D-131), pelo PDF mais antigo e pela ordem. `RevisaoService` não muda: com revisão pendente, estudar não agenda outra.
- Tela: "Adicionar meu PDF" no detalhe do assunto; a lista de material agrupada por PDF ("Do acervo" e um grupo por PDF, com
  "Tirar este PDF"); o resultado do envio; `CASCA_VERSAO`.

## 6. Testes

| Teste | Prova |
|---|---|
| `CorteDoPdfTest` (unitário) | O corte por ritmo, pelos marcadores dentro da faixa, a sobra pequena juntada |
| `PdfProprioTest` | Envio → blocos depois dos existentes, arquivos no volume, servidos em `/material/proprios/`; PDF inválido 422; sem texto aceito e marcado; tirar arquiva só os dele e nada some (D-129, D-130, D-132); assunto com revisão e bloco próprio não lido → plano sugere "continuar" e a revisão não muda (D-131) |
| `AcessoRotasTest` (já existe) | As rotas novas nascem fechadas |
| Chromium | Adicionar, ver os grupos, Ler o bloco próprio no leitor, tirar |

## 7. Itens e ordem

| # | Item | Situação |
|---|---|---|
| 33.0 | Especificação e wireframe aprovados | feito |
| 33.1 | `V26`, corte, serviço, rotas, servir os arquivos | feito |
| 33.2 | Continuar leitura (D-131) e perguntas dos blocos próprios | feito |
| 33.3 | Tela (assunto, grupos, envio, tirar); `CASCA_VERSAO` | feito |
| 33.4 | Testes e Chromium | feito |
| 33.5 | Deploy pelo Guardian | — |

## 8. Decisões e alternativas

| Escolhido | Recusado | O que quebraria |
|---|---|---|
| Cortar na própria requisição | Fila em segundo plano | Um PDF de 50 MB corta em segundos no Pi; uma fila acrescentaria estados e uma tela de "preparando" sem ganho |
| Volume `editais` servido em `/material/proprios/` | Volume `material` | O `material` é só leitura no app; dar escrita muda o compose (ADR-054) |
| Listar os PDFs numa rota própria | Campos novos em `SegmentoResponse` e na entidade | Mexeria no contrato de segmentos que o planejamento e outras telas usam |

## 9. Riscos

- PDF muito grande no Pi: o corte carrega o documento inteiro na memória (PDFBox usa arquivo temporário acima de um limite).
- Marcadores ruins (todos na página 1, por exemplo) caem no corte por tempo.
- Sem Docker nesta sessão: os testes de schema não rodam aqui.

## Changelog

| Versão | Data | Mudança |
|---|---|---|
| 1.1.0 | 2026-11-01 | Itens 33.1 a 33.4 feitos. `V26`; `CorteDoPdf` (função pura), `PdfProprioService` e rotas; `/material/proprios/**` servido do volume `editais`; `TextoDoBloco` lê blocos próprios; `proximaContinuacao` com D-131; tela do assunto (adicionar, grupos, tirar). 10 testes novos (`CorteDoPdfTest`, `PdfProprioTest`). Chromium: PDF de 45 páginas com marcadores virou 2 blocos (corte no marcador da 31), Hoje sugeriu o bloco próprio de um assunto na escada, o leitor abriu, a revisão não mudou depois de registrar a leitura, o bloco entrou na fila das perguntas, tirar arquivou os 2 blocos |
| 1.0.0 | 2026-11-01 | Criado, com o wireframe aprovado pelo usuário |
