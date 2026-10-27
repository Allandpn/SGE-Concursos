# Sprint 28 — PDF anexado a uma lacuna do material

| | |
|---|---|
| Versão | 1.1.0 |
| Data | 2026-10-27 |
| Subordinado a | `00_PRODUTO` v1.10.0 §8.5; `01_DOMINIO` v1.29.0 §3.14, D-97; `02_JORNADAS` v1.18.0 J-5 passo 9a; `03_INVARIANTES` v1.17.0; ADR-049 |
| Origem | `docs/REDESENHO-FLUXO.md` (passo D) |
| Contraparte | `integracao/anexo_worker.py` no repositório do planejamento |

## 0. Escopo

**Entra:** anexar um PDF a uma parte que o acervo não ensina; o worker corta em segmentos e o SGE os entrega ao assunto; o estado
(preparando, pronto, falhou) na tela, com "anexar de novo".

**Fica de fora:** anexar a assunto sem lacuna; mais de um PDF por parte; remover anexo pronto (o segmento só se arquiva pelo gesto
de sempre); escolher páginas do PDF; ler texto do PDF anexado.

## 1. O desenho em uma página

```
Concursos → Ver por assunto → lacuna → [Anexar PDF] ─► POST /api/assuntos/{id}/lacunas/anexo (multipart: parte, arquivo)
   SGE: confere que abre e tem páginas, grava em editais/anexos/<uuid>.pdf, cria lacuna_anexo PENDENTE
Worker (no próximo ciclo, alguns minutos) ─► GET /internal/editais/anexos/pendentes
   monta o manifesto (uma fonte, todas as páginas) ─► montar_bloco.py ─► material/blocos/<assunto>-anexo-<id>/segmento-NN.pdf
   ─► importa o CSV de segmentos (chave externa do assunto, ordem continuando a do assunto)
   ─► POST /internal/editais/anexos/{id}/pronto {segmentos, minutos}     (ou /falha {erro})
SGE mostra "pronto: 3 segmentos · 3 h" ao lado da lacuna; o assunto passa a ter segmentos (turno, "Abrir material")
```

## 2. Banco — `V21__lacuna_anexo.sql`

`lacuna_anexo` (`id`, `assunto_edital_id` → `edital_assunto`, `parte`, `nome_original`, `arquivo` relativo ao volume `editais`,
`paginas`, `estado` PENDENTE | PRONTO | FALHOU, `erro`, `segmentos`, `minutos`, `criada_em`, `atualizada_em`), única por
(`assunto_edital_id`, `parte`).

## 3. Rotas

| Rota | O que faz |
|---|---|
| `POST /api/assuntos/{id}/lacunas/anexo` | `multipart`: `parte` e `arquivo` (PDF, até 30 MB). A parte tem de ser uma lacuna do assunto (`404 LACUNA_INEXISTENTE`); PDF que não abre ou sem páginas: `422 PDF_INVALIDO`; parte que já tem anexo PRONTO ou PENDENTE: `409 ANEXO_JA_EXISTE`; anexo FALHOU é substituído |
| `DELETE /api/assuntos/{id}/lacunas/anexo?parte=` | tira o anexo PENDENTE ou FALHOU e o arquivo; PRONTO: `409 ANEXO_JA_PRONTO` |
| `GET /internal/editais/anexos/pendentes` | os PENDENTES: `id`, `chaveExterna` do assunto, `assunto`, `parte`, `arquivo` (relativo ao volume), `paginas`, `nome`, `proximaOrdem` |
| `POST /internal/editais/anexos/{id}/pronto` | `{segmentos, minutos}`; vira PRONTO |
| `POST /internal/editais/anexos/{id}/falha` | `{erro}`; vira FALHOU |

A lacuna em `material-da-conferencia` e em `Ver por assunto` ganha `anexo`: `{estado, nome, paginas, segmentos, minutos, erro}`.

## 4. Worker

`AnexosDeLacuna` (ciclo ao lado dos outros): para cada pendente, lê o PDF do volume `editais`, monta o manifesto de uma fonte (teoria,
3 min por página: texto de lei é leitura corrida), fatia com `montar_bloco.py` num destino próprio (`<assunto>-anexo-<id>`), numera
as `ordem` a partir de `proximaOrdem`, importa o CSV de segmentos e entrega o resultado. Falha de qualquer etapa devolve `falha`
com a mensagem; o PDF não é apagado.

## 5. Tela

Em cada lacuna: **Anexar PDF** (escolhe o arquivo, envia). Depois: "preparando…", "pronto: N segmentos · tempo" ou "falhou: motivo",
com **Anexar de novo** (FALHOU) e **Tirar** (PENDENTE e FALHOU). Enquanto houver anexo preparando, a lista se atualiza sozinha.

## 6. Testes

| Garante | Teste |
|---|---|
| D-97 | anexar cria pendente; só aceita parte que é lacuna; PDF inválido é recusado; segundo anexo na mesma parte é recusado; FALHOU se substitui |
| D-97 | pronto e falha mudam o estado; o estado aparece na lacuna; remover só PENDENTE e FALHOU |
| D-97 | `proximaOrdem` continua a numeração dos segmentos do assunto |
| Planejamento | o ciclo fatia, numera depois dos segmentos existentes, importa e entrega; falha vira `falha` |

## 7. Itens e ordem

| # | Item | Estado |
|---|---|---|
| 28.0 | Este documento, ADR-049 e a especificação | feito |
| 28.1 | V21, serviço e rotas do SGE | feito |
| 28.2 | Tela (anexar, estados) | feito |
| 28.3 | `AnexosDeLacuna` no planejamento | feito (8 testes, um com o fatiador de verdade) |
| 28.4 | Pi: implantar os dois lados e anexar uma lei do MPPR de verdade | a fazer |

## Changelog

| Versão | Data | Mudança |
|---|---|---|
| 1.1.0 | 2026-10-27 | Itens 28.0 a 28.3 feitos; falta o 28.4 (anexar uma lei do MPPR de verdade no Pi) |
| 1.0.0 | 2026-10-27 | Primeira versão |
