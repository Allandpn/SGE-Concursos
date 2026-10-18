# Sprint 24 — Prova e gabarito

| | |
|---|---|
| Versão | 1.0.0 |
| Data | 2026-10-18 |
| Status | Vigente — em andamento |
| Especificação | `01_DOMINIO` v1.26.0 §3.11 e D-67 a D-85 · `02_JORNADAS` v1.15.0 J-6 e J-7 · `03_INVARIANTES` v1.14.0 · `00_PRODUTO` v1.8.0 §5.1.1 · ADR-046 |
| Raciocínio e amostra | `docs/ESPECIFICACAO-PROVA-GABARITO.md` (histórico: formatos reais de 4 bancas, medição do OCR no Pi) |

Este documento diz **como** a especificação vigente é construída. Não define regra nem jornada:
se algo daqui contradiz `01_DOMINIO` ou `02_JORNADAS`, o defeito é deste documento.

---

## 1. O desenho em uma página

```
 usuário ──(prova.pdf + gabarito.pdf|texto)──► SGE ──grava em /dados/provas/entrada/<id>/
                                                │
                    worker (planejamento) ◄─────┤  GET /internal/provas/pendentes
                    │  1. lê: OCR se imagem, IA separa as questões           ┐
                    │  2. lê o gabarito: IA + validação, recorte cargo/tipo  │ etapa LENDO
                    │  3. atribui cada questão a um assunto do alvo (IA)     ┘
                    └──► POST /internal/provas/{id}/leitura  (questões, gabarito, imagens em /dados/provas/paginas/<id>/)
                                                │
   usuário confere (J-6 passo 3-4) ◄── CONFERIR │ ──► confirma
                                                │
                    worker  4. julga (IA, trechos do material) ──► POST /internal/provas/{id}/vereditos
                                                │
   usuário vê o relatório ◄── PRONTA            │   resolve (J-7) ──► entrega ──► Simulado + ResultadoSimulado (M-1)
```

Três decisões de forma, todas herdadas de padrões que já existem:

| Decisão | Herdada de |
|---|---|
| A prova é um **processo com estados** (lendo, conferir, julgando, pronta, falhou), guardado e retomável | `analise_edital` (Sprint 18) |
| O worker **busca trabalho** por rota interna com token e **entrega** o resultado; nada de o SGE chamar o worker | ADR-044, `/internal/**` |
| A nota entra por `simulado` + `resultado_simulado`, **por disciplina**, sem `assunto_id` | Sprint 8, D-13 |

---

## 2. Banco — `V16__prova_gabarito.sql`

Convenção do projeto: toda restrição leva a regra no nome. Colunas em `snake_case`.

### 2.1 `prova`

| Coluna | Tipo | Notas |
|---|---|---|
| `id` | `BIGINT IDENTITY` | |
| `concurso_id` | `BIGINT NOT NULL` | FK `concurso`; é o alvo **no momento do envio** (D-77) |
| `nome`, `banca` | `VARCHAR` | corrigíveis pelo usuário na conferência |
| `ano` | `SMALLINT` | |
| `tipo` | `VARCHAR(10)` | "4", "A"; lido da capa; corrigível |
| `cargo_prova` | `VARCHAR(250)` | cargo/código lido da capa ("E05", "9006") |
| `formato` | `TEXT NOT NULL` | `MULTIPLA_ESCOLHA` · `CERTO_ERRADO` (mesmo enum de `resultado_simulado`) |
| `status` | `TEXT NOT NULL` | §2.1.1 |
| `etapa_falha`, `erro` | `TEXT` | como `analise_edital` |
| `hash_prova` | `CHAR(64) NOT NULL` | SHA-256 do PDF da prova |
| `gabarito_situacao` | `TEXT` | `PRELIMINAR` · `DEFINITIVO`; **NULL = sem gabarito** |
| `gabarito_recorte` | `TEXT` | o que o sistema recortou (cargo, tipo, turno), mostrado na conferência (D-83) |
| `questoes_esperadas` | `INTEGER` | nº de respostas do gabarito, para D-82 |
| `modelo` | `VARCHAR(150)` | modelo de IA da última etapa |
| `criado_em`, `atualizado_em` | `TIMESTAMPTZ` | |

Restrições: `ck_prova_status`, `ck_prova_formato`, `ck_prova_gabarito_situacao`,
`ux_prova_concurso_hash` (**idempotência**: reenviar a mesma prova ao mesmo concurso devolve a existente, como
D-45), e **`ck_prova_d81_sem_gabarito_nao_julga`**:
`CHECK (status NOT IN ('JULGANDO','PRONTA') OR gabarito_situacao IS NOT NULL)` — D-81 na camada mais baixa que a
comporta.

#### 2.1.1 Estados

`LENDO` → (`AGUARDANDO_GABARITO` | `CONFERIR`) → `JULGANDO` → `PRONTA`; `FALHOU` de qualquer etapa de leitura ou
julgamento, com `etapa_falha` (`LENDO` ou `JULGANDO`). Enviar o gabarito de uma prova em `AGUARDANDO_GABARITO` a
devolve a `LENDO` (o worker lê só o gabarito) e depois a `CONFERIR`. Indisponibilidade da IA **não é estado**: o
worker não entrega e tenta de novo no ciclo seguinte (D-75). `PRONTA` volta a `JULGANDO` por "Analisar de novo"
(D-79) ou por gabarito definitivo que mudou (D-84).

### 2.2 `questao` e `alternativa`

| `questao` | Tipo | Notas |
|---|---|---|
| `id`, `prova_id` | | FK `prova`; `ux_questao_prova_numero (prova_id, numero)` |
| `numero` | `INTEGER NOT NULL` | |
| `enunciado` | `TEXT` | NULL só quando `motivo_nao_avaliavel = 'SEM_TEXTO'` |
| `texto_base` | `TEXT` | o texto/comando a que o item do CEBRASPE está ligado |
| `cabecalho_disciplina` | `VARCHAR(250)` | pista da prova ("CONHECIMENTOS GERAIS EM TI") |
| `resposta_gabarito` | `VARCHAR(1)` | `A`–`E`, `C`, `E`; NULL se anulada ou sem gabarito |
| `motivo_nao_avaliavel` | `TEXT` | NULL = avaliável · `SEM_TEXTO` · `ANULADA` · `SEM_GABARITO` · `OCR_DUVIDOSO` (D-70, D-85) |
| `assunto_id` | `BIGINT` | FK `assunto`; NULL = ainda sem atribuição **ou** fora do edital |
| `atribuicao` | `TEXT NOT NULL` | `PENDENTE` · `AUTOMATICA` · `REVISAR` · `CONFIRMADA` · `FORA_DO_EDITAL` |
| `lida_por_ocr` | `BOOLEAN NOT NULL` | D-85 |
| `pagina_origem` | `INTEGER` | fatia/página da imagem (`/provas/{id}/paginas/NN`) |

`alternativa (questao_id, letra CHAR(1), texto TEXT)`, `ux_alternativa_questao_letra`. Sem alternativas nas questões
`CERTO_ERRADO`.

Restrições com regra: `ck_questao_d70_motivo` (`motivo_nao_avaliavel` só nos valores da D-70/D-85),
`ck_questao_d74_fora_do_edital_sem_assunto` (`atribuicao = 'FORA_DO_EDITAL'` ⇒ `assunto_id IS NULL`) e
`ck_questao_atribuida_tem_assunto` (`AUTOMATICA`/`CONFIRMADA` ⇒ `assunto_id NOT NULL`). Que o assunto pertença à
**cobertura do alvo** (D-74) depende de outra tabela: é do serviço, não de CHECK.

### 2.3 `veredito_questao` — fato registrado, nunca alterado

| Coluna | Notas |
|---|---|
| `id`, `questao_id` | FK; **sem `UPDATE`** (D-73): reanalisar insere uma linha nova |
| `veredito` | `RESPONDIVEL` · `PARCIAL` · `NAO_RESPONDIVEL` |
| `segmento_id`, `pagina_segmento` | a sustentação: um segmento do assunto e a página dentro dele (D-68) |
| `falta` | texto: o que falta (`PARCIAL`/`NAO_RESPONDIVEL`) |
| `gabarito_considerado` | a resposta do gabarito usada no julgamento (para D-84) |
| `modelo`, `criado_em` | retrato datado (D-76) |

**`ck_veredito_d68_sustentacao`:** `CHECK (veredito <> 'RESPONDIVEL' OR (segmento_id IS NOT NULL AND pagina_segmento
IS NOT NULL))`. Que o segmento exista, seja **ativo** e do **assunto da questão**, e que a página caiba nele, o
serviço valida antes de gravar. O veredito **vigente** de uma questão é o de maior `id`: `ix_veredito_questao_vigente
(questao_id, id DESC)`. Nada o marca como vigente (D-16).

### 2.4 `tentativa_prova` e `resposta_prova` (resolução)

| `tentativa_prova` | Notas |
|---|---|
| `id`, `prova_id` | |
| `tentativa_id UUID NOT NULL` | gerado no cliente ao abrir a tela; **`ux_tentativa_prova_d80_tentativa`** (D-80) |
| `iniciada_em`, `entregue_em` | `entregue_em` NULL = em andamento |
| `cronometrada BOOLEAN`, `limite_minutos INTEGER` | |
| `simulado_id BIGINT` | FK `simulado`; preenchido só na tentativa que **conta** para M-1 |

**`ux_tentativa_prova_d78_uma_conta`:** índice único parcial `(prova_id) WHERE simulado_id IS NOT NULL` — só uma
tentativa por prova alimenta M-1, e a persistência o garante, mesmo com duas entregas simultâneas.

`resposta_prova (tentativa_id, questao_id, alternativa VARCHAR(1), marcada_revisar BOOLEAN, respondida_em)`,
`ux_resposta_prova_tentativa_questao`. **Não há coluna "acertou":** a correção sai do gabarito na leitura (D-80,
D-16). A nota **não é persistida**, salvo o que o `resultado_simulado` já guarda por regra da Sprint 8.

### 2.5 Arquivos

Um volume novo, **`provas`**, montado nos dois contêineres (`estudos.provas.dir`, `PROVAS_DIR`, padrão
`/dados/provas`):

| Caminho | Quem escreve | Quem lê |
|---|---|---|
| `entrada/<prova_id>/prova.pdf`, `gabarito.pdf` | SGE (no envio) | worker |
| `paginas/<prova_id>/NN.jpg` | worker (extração/OCR) | SGE (serve ao usuário, D-85) |

Convenção, não código de acesso compartilhado: cada lado escreve só no seu subdiretório.

---

## 3. Rotas internas (só para o planejamento)

Mesmo filtro e token de `/internal/**` (Sprint 19 §4).

| Rota | Faz |
|---|---|
| `GET /internal/provas/pendentes` | Provas que pedem trabalho: `LENDO` (com o que ler: prova, gabarito) e `JULGANDO` |
| `GET /internal/provas/{id}/leitura` | Metadados para ler: caminhos em `entrada/`, o concurso-alvo e a lista de assuntos (nome, `chaveExterna`, disciplina) para a atribuição |
| `POST /internal/provas/{id}/leitura` | Entrega as questões, o gabarito lido e o recorte. → `CONFERIR` ou `AGUARDANDO_GABARITO` |
| `POST /internal/provas/{id}/gabarito` | Entrega só o gabarito (prova que esperava) |
| `GET /internal/provas/{id}/julgamento` | As questões **a julgar** (D-79, §5) e, de cada uma, o assunto e seus **segmentos ativos** |
| `POST /internal/provas/{id}/vereditos` | Lote de vereditos. Valida D-68 por item; o lote não é tudo-ou-nada (item recusado volta com o motivo) |
| `POST /internal/provas/{id}/falha` | `{etapa, erro}` → `FALHOU` |

Recusas: prova inexistente `404 PROVA_INEXISTENTE`; entrega fora do estado `409 ESTADO_INVALIDO`; gabarito com número
de respostas diferente do de questões `422 GABARITO_NAO_CASA` (D-82); atribuição a assunto fora da cobertura do
alvo é **descartada** e a questão vira `FORA_DO_EDITAL` (D-74), sem erro.

---

## 4. Rotas públicas

Sob `/api/provas`. Nenhuma lista, busca ou exporta questões **fora do contexto de uma prova** (D-72); toda rota de
questão tem `{provaId}` no caminho.

| Rota | Faz | J |
|---|---|---|
| `POST /api/provas` | multipart: `prova` (PDF), `gabarito` (PDF) **ou** `gabaritoTexto`; `tipo` opcional → `202` e a prova em `LENDO`. Sem concurso-alvo `422 SEM_CONCURSO_ALVO`; não é PDF ou passa do limite `422`; mesma prova de novo → a existente | J-6 1-2 |
| `POST /api/provas/{id}/gabarito` | anexa o gabarito a uma prova que o esperava | J-6 2 |
| `GET /api/provas` / `GET /api/provas/{id}` | estado, e na conferência: questões lidas, não avaliáveis (com motivo), gabarito casado, **recorte**, 3 questões sorteadas, as **duvidosas** | J-6 3 |
| `PATCH /api/provas/{id}` | corrige nome, banca, ano, tipo | J-6 3 |
| `PUT /api/provas/{id}/questoes/{numero}/assunto` | corrige a atribuição (assunto da cobertura, ou `null` = fora do edital) → `CONFIRMADA` | J-6 4 |
| `POST /api/provas/{id}/confirmar` | `CONFERIR` → `JULGANDO` | J-6 5 |
| `POST /api/provas/{id}/analisar-de-novo` | volta a `JULGANDO`; só as questões que mudaram (D-79) | J-6 7 |
| `GET /api/provas/{id}/relatorio` | §6 | J-6 6 |
| `GET /api/provas/{id}/paginas/{n}` | a imagem da página de origem (D-85); do diretório `paginas/`, sem sair dele | J-6 / J-7 |
| `POST /api/provas/{id}/tentativas` | abre ou **retoma** a tentativa pelo `tentativaId` (D-80) | J-7 1-2 |
| `GET /api/provas/{id}/tentativas/{tid}` | as questões **sem a resposta do gabarito** e as respostas dadas | J-7 2 |
| `PUT /api/provas/{id}/tentativas/{tid}/respostas/{numero}` | grava a alternativa e a marca de revisar | J-7 2 |
| `POST /api/provas/{id}/tentativas/{tid}/entregar` | corrige, devolve a nota; na tentativa que conta, cria o simulado (§7) | J-7 3-4 |

Durante a tentativa em andamento, **a resposta do gabarito nunca sai do servidor** (só depois de `entregar`): o
contrato do `GET .../tentativas/{tid}` não tem o campo.

---

## 5. Reanálise incremental (D-79) — a consulta

Questão **a julgar** = avaliável, com assunto atribuído, e **uma** destas:

1. não tem veredito;
2. o veredito vigente é anterior à última mudança do material do assunto:
   `max(greatest(segmento.criado_em, coalesce(segmento.arquivado_em, segmento.criado_em))) > veredito.criado_em`,
   sobre todos os segmentos do assunto (ativos e arquivados);
3. `veredito.gabarito_considerado` é diferente da `resposta_gabarito` atual (D-84).

Uma consulta, no `ProvaRepository`; sem coluna nova, sem "sujo". Assunto que ganhou segmento depois de ter ficado
sem material entra pela regra 2.

---

## 6. O relatório (derivado)

`GET /api/provas/{id}/relatorio` calcula tudo na hora (D-16) a partir de questões, vereditos vigentes e da tentativa
que conta:

- **Cobertura:** `{avaliaveis, respondiveis, parciais, naoRespondiveis}`, com `foraDoEdital`, `anuladas`,
  `semTexto` e `ocrDuvidoso` **à parte** (D-70). Sempre com denominador (D-71): o contrato traz os dois números,
  nunca o percentual sozinho.
- **Por disciplina** (a da atribuição, na ordem do edital) e **por assunto furado**: quantas questões, o que faltou,
  a sustentação.
- **Assuntos do edital sem nenhuma questão na prova**, como informação.
- **Por questão:** número, assunto, veredito, sustentação (`segmentoId`, página), `lidaPorOcr` e `paginaOrigem`; **sem
  enunciado fora da prova** — o enunciado só sai em `GET .../tentativas/{tid}` e na revisão pós-entrega.
- **Nota real**, se houver tentativa entregue: por disciplina e global, com denominador, da tentativa que conta; as
  outras tentativas, listadas à parte (D-78). Onde cobertura e nota discordam, por assunto: informação.
- Marcas: `gabaritoPreliminar`, `tipo`, `recorte`.

---

## 7. A nota entra em M-1 (D-78)

Em `entregar`, numa transação:

1. Corrige: compara cada resposta com `resposta_gabarito`; questão não avaliável ou sem assunto atribuído fica fora.
2. Se **nenhuma tentativa da prova tem `simulado_id`** (consulta + o índice `ux_..._d78_uma_conta` como garantia
   final), cria um `Simulado` (`data` = hoje, `duracao_minutos` = tempo gasto, no mínimo 1) e um `ResultadoSimulado`
   **por disciplina** que tenha ao menos uma questão avaliável atribuída, com o `formato` da prova. Reusa
   `SimuladoService`; **não cria sessão, revisão nem toca o turno**.
3. Se já existe uma que conta, só grava a entrega.

Gabarito definitivo que **muda ou anula** uma resposta (D-84): o serviço recalcula e **atualiza** o
`resultado_simulado` da tentativa que conta (única exceção a "resultado de simulado não muda"; fica no log), e as
questões afetadas entram em §5, regra 3.

---

## 8. O que o planejamento faz (resumo do contrato; o detalhe é do repositório de lá)

Exige o pedido explícito de exceção à regra de escrita cruzada do `CLAUDE.md`, como na Sprint 19.

| Etapa | O que faz |
|---|---|
| **Imagem → texto** | Extrai as imagens embutidas, **empilha as fatias numa tira contínua** e roda o Tesseract em faixas com sobreposição, tirando as linhas repetidas na emenda; a questão que não se remonta fica `SEM_TEXTO`. Grava as imagens em `paginas/<id>/` (pontos L e M). Texto nativo dispensa o OCR |
| **Questões** | A IA separa número, enunciado, alternativas e texto-base; o worker valida a sequência e as alternativas. O que o OCR provavelmente errou (código, algarismos romanos) leva `lida_por_ocr` e, na dúvida, `OCR_DUVIDOSO` (D-85) |
| **Gabarito** | A IA lê PDF ou texto; o worker valida: nº de respostas igual ao de questões, só letras válidas, anuladas (`*`, `X`) reconhecidas. Multi-cargo: recorta pela capa da prova (cargo, tipo, turno, normalizando acento e caixa) e devolve o recorte (D-83). Preliminar × definitivo (D-84) |
| **Atribuição** | Em lotes de ~10: a IA escolhe o assunto da cobertura do alvo, usando o `cabecalho_disciplina` como pista, ou "nenhum"; devolve a confiança. Baixa confiança → `REVISAR` |
| **Julgamento** | Para cada questão a julgar: junta o texto das páginas dos **segmentos ativos** do assunto (os PDFs estão em `/dados/material`), escolhe as ~6 mais próximas da questão por busca (índice temporário em memória) e pergunta à IA o veredito **sobre a alternativa do gabarito**, com a página citada (`segmentoId` + página dentro dele). Na dúvida, o pior (D-69) |
| **Falha da IA** | Não entrega nada; tenta no ciclo seguinte (D-75) |

---

## 9. Telas

Detalhe fino em `04_FRONTEND` §7K, acrescentado no item 24.7. Em linhas gerais, Concursos ganha, no alvo: **"Verificar
com uma prova"** (envio, J-6 1-2); a **conferência** (J-6 3-4); o **relatório** (J-6 6-7, junto de "Ver por
assunto"); e **"Resolver"** (J-7), uma questão por tela. Nenhuma tela mostra a resposta do gabarito antes da entrega.

---

## 10. Testes

| Teste | Prova |
|---|---|
| `ProvaEstruturaTest.d81_semGabaritoNaoJulga` / `d73_veredito_naoTemUpdate` / `d68_respondivelExigeSustentacao` / `d80_tentativaUnica` / `d78_umaTentativaConta` | restrições com a regra no nome |
| `ProvaLeituraTest.entregaDeLeitura_vaiParaConferirOuAguardandoGabarito` / `gabaritoComNumeroDiferente_recusa` (D-82) / `atribuicaoForaDaCobertura_viraForaDoEdital` (D-74) / `mesmaProvaDeNovo_devolveAExistente` | §3, §4 |
| `ProvaVereditoTest.citacaoInexistente_recusaOItem` / `segmentoArquivado_naoSustenta` / `segmentoDeOutroAssunto_naoSustenta` (D-68) | validação do serviço |
| `ProvaReanaliseTest.assuntoComMaterialNovo_reabre` / `semMudanca_naoReabre` / `gabaritoMudou_reabre` (D-79, D-84; data congelada) | §5 |
| `ProvaRelatorioTest.denominadorSempre` / `foraDoEditalNaoContaComoFuro` / `semEnunciadoForaDaProva` (D-70, D-71, D-72) | §6 |
| `ProvaResolucaoTest.gabaritoNaoSaiAntesDaEntrega` / `mesmaTentativaContaUmaVez` / `entregarCriaSimuladoPorDisciplina` / `segundaTentativaNaoAlimentaM1` / `entregarNaoCriaSessaoNemRevisao` (D-78, D-80) | §7 |
| `ProvaGabaritoDefinitivoTest.respostaMudou_recalculaNotaEReabreQuestao` (D-84) | §7 |
| `ProvaEstruturalTest.nenhumaRotaListaQuestoesForaDeUmaProva` (D-72) | teste estrutural sobre o mapeamento |
| No planejamento: leitura e recorte de gabarito contra os **seis arquivos reais** da amostra (FGV ×2, IDECAN, FCC, CEBRASPE); OCR da prova da FCC | D-82, D-83 |

---

## 11. Itens e ordem

| | Item | Quem escreve | Depende de |
|---|---|---|---|
| 24.1 | Especificação: `00_PRODUTO` 1.8.0, `01_DOMINIO` 1.26.0, `02_JORNADAS` 1.15.0, `03_INVARIANTES` 1.14.0, ADR-046 | Claude, a pedido do usuário | — |
| 24.2 | SGE — `V16`, entidades, `ProvaService`, rotas internas (§2, §3), testes de estrutura, leitura e vereditos | Claude | 24.1 |
| 24.3 | SGE — rotas públicas de envio, conferência, confirmar, reanálise e relatório (§4, §5, §6) | Claude | 24.2 |
| 24.4 | SGE — resolução: tentativas, respostas, entregar → simulado (§7), gabarito definitivo | Claude | 24.2 |
| 24.5 | Planejamento — OCR, questões, gabarito, atribuição (§8), contra a amostra real | Claude, com exceção explícita de escrita | 24.2 |
| 24.6 | Planejamento — julgamento por trechos e vereditos (§8) | Claude, com exceção explícita de escrita | 24.5 |
| 24.7 | Frontend — telas de J-6 e J-7 (§9); `CASCA_VERSAO` | Claude | 24.3, 24.4 |
| 24.8 | Pi — volume `provas`, Tesseract na imagem do worker, **ensaio com a prova da FCC**: conferência de ~20 vereditos à mão (`ESPECIFICACAO-PROVA-GABARITO` §1) | **você**, com Claude por SSH | 24.5, 24.6, 24.7 |

**Definition of Done:** os testes da §10 verdes; a prova e o gabarito da FCC percorrem J-6 do envio ao relatório no Pi;
o usuário confere ~20 vereditos e a taxa de discordância fica abaixo de 1 em 5; a prova resolvida entra em M-1 por
disciplina e `ux_..._d78_uma_conta` impede a segunda; nenhuma sessão, revisão ou mudança de turno nasce da prova.

---

## 12. Riscos conhecidos

| Risco | Mitigação |
|---|---|
| OCR erra código e algarismos romanos (medido) | `lida_por_ocr`, `OCR_DUVIDOSO`, imagem da página ao usuário (D-85) |
| Questão cortada na emenda das fatias | Tira contínua com sobreposição; senão `SEM_TEXTO`. Medir no ensaio da FCC |
| Gabarito do tipo errado, em silêncio | Recorte mostrado e 3 questões sorteadas na conferência (J-6 3) |
| Cota da assinatura: ~70 chamadas por prova | Reanálise só do que mudou; a IA pausa e retoma (ADR-045) |
| Julgamento de IA severo ou frouxo demais | Conferência de ~20 vereditos antes de valer; nota real ao lado da cobertura |
| `resultado_simulado` editado pelo gabarito definitivo | Exceção única, só na tentativa que conta, registrada em log |

## 13. Fora desta sprint

Consolidado de várias provas; prova discursiva ou estudo de caso; **qualquer** efeito da nota sobre o turno, a escada
ou a revisão; limiar de "gabaritar" e nota de corte (Sprint 23); prova de concurso do histórico (D-77).

---

## Changelog

| Versão | Data | Mudança |
|---|---|---|
| 1.0.0 | 2026-10-18 | Criado, na abertura da sprint, contra `01_DOMINIO` v1.26.0 |
