# Sprint 25 — Conferência do edital

| | |
|---|---|
| Versão | 1.3.0 |
| Data | 2026-10-24 |
| Subordinado a | `01_DOMINIO` v1.27.0 §3.12 e D-86 a D-95; `02_JORNADAS` v1.16.0 J-5; `03_INVARIANTES` v1.15.0; ADR-047 |
| Contraparte | `docs/especificacao-revisao-edital-sge.md` §11 (contrato `edital-revisao/1`) e `integracao/entrega_revisao.py`, no repositório do planejamento |
| Modo de trabalho | Implementação direta (`CLAUDE.md`, 2026-10-24): a IA escreve o código e os testes |

## 0. Escopo

**Entra:** receber a leitura do edital do planejamento; carregar e navegar a árvore do QConcursos; a tela de conferência com a
fila, as ações sobre partes e as decisões do escopo "este edital"; concluir (cria os assuntos e ativa o concurso); atualizar a
árvore por gesto do usuário; PDF do edital guardado até a conclusão. D-86, D-88, D-89, D-90, D-91, D-92 e a parte de D-87 que
vale para o escopo "este edital".

**Fica para a Sprint 26:** escopo "todos os editais" com a confirmação extra (D-87), reprocessar preservando decisão e o
contador (D-95), o `material` por assunto na entrega, "Questões deste assunto" (D-94), trocar a fonte de uma parte e a
sugestão de material ao trocar nó (D-93 na parte do material; aqui, só a garantia de que trocar nó não toca o que existe).

## 1. O desenho em uma página

```
Concursos → Novo edital (PDF)
   │ SGE: guarda o PDF, extrai o texto, lê o cabeçalho (nome, banca, data, cargos)      [ADR-043, só a etapa 1]
   ▼ usuário escolhe o cargo → status SEPARANDO_ASSUNTOS
worker (planejamento) ── GET /internal/editais/pendentes ──► pega /dados (PDF, cargo, decisões)
   │ extrai, organiza, quebra em partes, liga aos nós
   ├─ POST /internal/editais/{id}/revisao   (edital-revisao/1)  → EM_CONFERENCIA
   └─ POST /internal/editais/{id}/falha                         → FALHOU (com a etapa)
usuário: Conferência (resumo, por disciplina, fila) ── decide ── concluir
   │ concluir: cria disciplinas/assuntos (chave, D-64), cobertura, ativa o concurso (D-60, D-62, D-92)
   ▼ CONFIRMADA
árvore do QConcursos: o worker a entrega (POST /internal/arvores); o usuário a adota quando quiser (D-90)
```

A análise de edital do ADR-043 deixa de ter a etapa 2 (separar assuntos) e a proposta. `analise_edital` continua sendo o processo
do edital, agora com a conferência pendurada nele.

## 2. Banco — `V17__conferencia_edital.sql`

Todo nome de restrição leva o identificador da regra. Nenhuma tabela guarda o que se deriva (D-16): a **fila** e as **contagens**
são consultas; só o que a pessoa decidiu e o que o planejamento entregou são fatos.

### 2.1 Mudanças em `analise_edital`

- Estados: `LENDO_EDITAL` · `ESCOLHER_CARGO` · `SEPARANDO_ASSUNTOS` (aguarda ou está com o worker) · **`EM_CONFERENCIA`** (no lugar
  de `PRONTA`) · `CONFIRMADA` (conclusão) · `FALHOU`. `etapa_falha` aceita também `EXTRACAO`, `ORGANIZACAO`, `QUEBRA`, `LIGACAO`
  (as etapas do contrato).
- Sai a coluna `proposta` (a proposta por IA do SGE deixou de existir). Entra `pdf_arquivo` (caminho relativo no volume).
- A migração converte as análises `PRONTA` em `FALHOU` (etapa `SEPARANDO_ASSUNTOS`, erro "Análise anterior à conferência; envie o
  edital de novo"): havia só proposta, nenhum cadastro, então nada se perde. `CONFIRMADA` e `FALHOU` ficam como estão.

### 2.2 Árvore

| Tabela | Colunas | Restrições |
|---|---|---|
| `arvore_versao` | `id`, `rotulo` (`qconcursos-2026-10-19`), `fonte`, `data`, `quantidade_nos`, `vigente`, `carregada_em` | `ux_arvore_versao_rotulo`; **índice único parcial** `ux_arvore_d90_uma_vigente` (`WHERE vigente`) |
| `no_arvore` | `versao_id`, `no_id` (id do QConcursos), `nome`, `pai_id` (nulo = disciplina da árvore), `ordem`, `raiz_id` | PK (`versao_id`, `no_id`); índice (`versao_id`, `pai_id`); índice de busca por `unaccent_imutavel(lower(nome))` (V2) |
| `no_arvore_diferenca` | `versao_id`, `no_id`, `tipo` (`NOVO`, `REMOVIDO`, `RENOMEADO`, `MOVIDO`), `nome_anterior`, `pai_anterior_id` | PK (`versao_id`, `no_id`, `tipo`); calculada ao carregar, contra a versão vigente naquele momento |

O caminho de um nó **não é coluna**: sobe-se por `pai_id` (profundidade pequena), em memória (a versão vigente cabe em poucos MB; a
busca e o caminho dispensam o banco). `raiz_id` existe só para dizer a disciplina sem subir a árvore.

**Id de disciplina é negado.** No QConcursos o id da disciplina e o do assunto vêm de espaços diferentes e colidem (50 ids na árvore de
2026-10-19, ex.: 273 é a disciplina Antropologia e também "Arquitetura de Banco de Dados"). `no_id` guarda `-id` para a disciplina e o
id original para o assunto; o arquivo da árvore não muda, quem o carrega aplica o sinal, e o planejamento entrega os nós já assim
(`Arvore` em `casar_taxonomia.py`, achado de 2026-10-24). A busca nunca devolve a disciplina sozinha como alvo de ligação.

### 2.3 A leitura entregue

| Tabela | Colunas principais | Restrições |
|---|---|---|
| `edital_execucao` | `id`, `analise_id`, `modelo`, `prompt_versao`, `arvore_versao_id`, `executada_em`, `recebida_em`, `hash` | `ux_edital_execucao_d89` (`analise_id`, `hash`): a mesma entrega duas vezes é uma só |
| `edital_disciplina` | `id`, `analise_id`, `ordem`, `nome`, `restricao`, `comum`, `questoes` (do quadro de provas, se o planejamento entregar), `peso_pessoa` (nulo = o que D-61 calcula das questões), `qconcursos_ids` (texto JSON, ids de disciplina negados), `tentativas` | FK; `ux_edital_disciplina` (`analise_id`, `nome`) |
| `edital_assunto` | `id`, `disciplina_id`, `chave`, `ordem`, `texto`, `erros` (JSON), `alertas` (JSON), `aceito` (boolean: aceitou assim mesmo), `aparece` (boolean), `assunto_id` (nulo até concluir) | `ux_edital_assunto_d89` (`disciplina_id`, `chave`); FK opcional para `assunto` |
| `edital_parte` | `id`, `edital_assunto_id`, `chave`, `ordem`, `texto`, `contexto`, `herdado`, `natureza`, `origem` (`IA`, `REFINO`, `DECISAO`, `PESSOA`), `estado` (`PROPOSTA`, `ACEITA`, `DECIDIDA`, `DESCARTADA`), `arvore_versao_id`, `aparece`, `prop_natureza`, `prop_origem`, `execucao_id`, `comentario` | `ux_edital_parte_d89` (`edital_assunto_id`, `chave`); CHECK da natureza em (`EXATO`, `MAIS_AMPLO`, `PARCIAL`) ou nula quando não há nó |
| `edital_parte_no` | `parte_id`, `ordem`, `no_id`, `proposto` (boolean: nó da IA que a pessoa não adotou) | PK (`parte_id`, `ordem`, `proposto`); no máximo 3 nós por parte e lado (CHECK em `ordem`) |
| `edital_trecho` | `id`, `analise_id`, `disciplina_id` (nulo para linha fora de disciplina), `tipo` (`LINHA_FORA`, `SEM_ASSUNTO`), `paginas`, `linhas` (JSON), `estado` (`ABERTO`, `DESCARTADO`, `INCLUIDO`), `motivo` | FK |

`aparece = false` marca o que o reprocessamento não trouxe mais (D-89): a linha **nunca é apagada**; entra na fila.

### 2.4 Decisões e histórico

| Tabela | Colunas principais | Restrições |
|---|---|---|
| `edital_decisao` | `id`, `parte_chave_norm`, `contexto_norm` (a chave de reaproveitamento, sem a disciplina), `nos` (JSON), `natureza`, `escopo` (`EDITAL`, `TODOS`), `analise_id` (de origem), `arvore_versao_id`, `criada_em`, `ativa`, `revogada_em`, `comentario` | CHECK do escopo; índice (`parte_chave_norm`, `contexto_norm`) **não único**: duas decisões divergentes são conflito, não erro (D-87) |
| `edital_evento` | `id`, `analise_id`, `parte_id`, `tipo`, `antes` (JSON), `depois` (JSON), `em` | **só inserção**: gatilho de recusa a `UPDATE` e `DELETE`, como o das provas (D-73); é o histórico de cada gesto |

Decisão revogada fica (`ativa = false`); nada é apagado (D-18).

### 2.5 Arquivos

Volume novo **`editais`**, montado nos dois contêineres (`estudos.editais.dir`, `EDITAIS_DIR`, padrão `/dados/editais`; o compose é do
item 25.9). O SGE escreve `<analise_id>/edital.pdf` ao enviar o PDF; o worker só lê. O arquivo sai quando a análise vira
`CONFIRMADA` ou é descartada; enquanto a conferência está aberta, fica.

## 3. A chave e a conferência por regra (Java)

O SGE precisa recalcular, quando a pessoa **edita, divide ou junta** uma parte, o que o planejamento calcularia:

1. **`chave`** da parte: `sha1(base + "|" + ocorrência)[:16]`, com `base` = disciplina, assunto, herdado, texto e contexto, cada um
   passado por `normalizar` e unidos por `|`. A do assunto: `sha1("assunto|" + base + "|" + ocorrência)[:16]`.
2. **`normalizar`**: NFKD sem acento, minúsculas, junta palavra quebrada por hífen de fim de linha, só `[a-z0-9]` e espaço.
3. **Conferência do assunto** (`partes_edital.conferir_assunto`, porte em `RegrasEdital.conferirAssunto`; precisa do `texto` da disciplina, que a entrega passou a levar): o assunto é trecho do edital; sem parte é erro; **cobertura**: toda palavra do assunto (sem as vazias de `partes_edital.VAZIAS`, com o plural simples tirado) está numa parte
   ou no contexto. **Fidelidade**: a parte só usa palavras do assunto.

A fonte da verdade é `integracao/partes_edital.py` e `integracao/entrega_revisao.py`. **Os dois lados testam com o mesmo arquivo de
casos**, `integracao/casos-de-teste/vetores-conferencia.json`, gerado por `integracao/vetores_conferencia.py` (vetores de `normalizar`,
`palavras`, das duas chaves e 11 conferências de assunto): o teste de lá exige que o arquivo seja o que o código gera, o daqui
(`RegrasEditalTest`, cópia em `src/test/resources/conferencia/vetores.json`) exige as mesmas respostas em Java. Mudar a regra em um
lado quebra o teste do outro. Editar o **texto** do assunto refaz a conferência do assunto; os trechos sem assunto ficam como
entregues até o reprocessamento (Sprint 26).

Editar a parte muda o texto, logo a **chave**: a edição cria uma parte nova (`origem = PESSOA`) e marca a antiga `aparece = false` com
`estado = DESCARTADA`. A decisão que existia para o texto antigo continua presa a ele.

## 4. Rotas internas (só para o planejamento)

`/internal/**`, `X-Internal-Token` (`TokenInternoFilter`). Mesmo desenho de `/internal/provas`.

| Rota | O que faz |
|---|---|
| `GET /internal/editais/pendentes` | análises em `SEPARANDO_ASSUNTOS`: `id`, `slug`, `cargo`, `nomeArquivo` |
| `GET /internal/editais/{id}/dados` | `pdf` (caminho relativo ao volume), `slug`, `cargo`, `arvoreVigente` (rótulo) e `decisoes`: as ativas, `escopo = EDITAL` desta análise (as `TODOS` entram na 26) no formato do contrato §11.3 |
| `POST /internal/editais/{id}/revisao` | entrega `edital-revisao/1`. Idempotente pelo hash do corpo (D-89); versão do contrato desconhecida → `422 CONTRATO_DESCONHECIDO`; nó que não existe na árvore da execução → `422 NO_INEXISTENTE`; árvore da execução não carregada → `409 ARVORE_NAO_CARREGADA`. Grava a execução, as disciplinas, os assuntos, as partes e os trechos; devolve o resumo. Em `SEPARANDO_ASSUNTOS` vai a `EM_CONFERENCIA`; em `EM_CONFERENCIA` (nova execução) entra pelo caminho da Sprint 26 e aqui responde `409 REPROCESSAR_NA_26` |
| `POST /internal/editais/{id}/falha` | `{etapa, disciplina?, mensagem}` → `FALHOU`, `etapa_falha` e `erro` |
| `POST /internal/arvores` | `{rotulo?, fonte, data, disciplinas:[...]}` (o formato do `taxonomia/qconcursos-*.json`; `rotulo` padrão `fonte-data`; ids de disciplina originais, o SGE nega); carrega uma versão **sem torná-la vigente** e calcula a diferença contra a vigente. A primeira versão carregada vira vigente sozinha. Idempotente pelo `rotulo` |

## 5. Rotas públicas

| Rota | O que faz |
|---|---|
| `POST /api/editais/analises` | como hoje, agora também guarda o PDF (`pdf_arquivo`); só lê o cabeçalho |
| `POST /api/editais/analises/{id}/cargo` | escolhe o cargo → `SEPARANDO_ASSUNTOS` (não chama mais a IA) |
| `GET /api/editais/analises/{id}/conferencia` | cabeçalho com as contagens (disciplinas, assuntos, partes, exatas/mais amplas/parciais, sem nó, pendências abertas por bloqueio, decididas por pessoa, aplicadas de decisão anterior) e a execução |
| `GET .../conferencia/disciplinas` | a árvore do edital: disciplinas → assuntos → partes, com ligação e estado |
| `GET .../conferencia/fila` | só o que pede decisão, por gravidade (§6) |
| `POST .../partes/{id}/aceitar` · `PUT .../partes/{id}/nos` · `POST .../partes/{id}/sem-no` | aceitar; trocar os nós (até 3, com natureza); declarar "a árvore não tem" |
| `POST .../partes/{id}/dividir` · `/juntar` · `PUT .../partes/{id}` | corpo com os textos; recalcula a chave e a conferência por regra (§3) e devolve o assunto atualizado |
| `POST .../partes/{id}/descartar` · `POST .../partes/{id}/comentario` | descartar com motivo; comentário livre |
| `POST .../disciplinas/{id}/aceitar-exatos` | aceita de uma vez as partes `EXATO` ainda `PROPOSTA` |
| `PUT .../disciplinas/{id}/peso` | a pessoa troca o peso da disciplina (D-62); sem isso vale D-61: `questoes` do quadro de provas, ou `MEDIO` "não determinado" quando o edital não entrega (hoje o `edital-revisao/1` não traz `questoes`; campo opcional a acrescentar) |
| `POST .../assuntos/{id}/aceitar` | aceita o assunto recusado pela regra assim mesmo (`aceito = true`) |
| `POST .../trechos/{id}/resolver` | `DESCARTAR` (com motivo) ou `INCLUIR` (como assunto ou disciplina) |
| `POST .../concluir` | §7 |
| `GET /api/arvore/busca?q=&limite=` · `GET /api/arvore/nos/{id}` · `GET /api/arvore/nos/{id}/filhos` | busca por texto sem acento; um nó com o caminho completo e o número de filhos; os filhos |
| `GET /api/arvore/versoes` · `GET /api/arvore/versoes/{id}/diferenca` · `POST /api/arvore/versoes/{id}/adotar` | versões carregadas; o diff; adotar (§8) |
| `GET /api/decisoes` · `PUT /api/decisoes/{id}` · `DELETE /api/decisoes/{id}` | ver, editar e revogar (o `DELETE` só marca `ativa = false`) |

Toda ação grava um `edital_evento`. Resposta de erro em RFC 9457 (ADR-026), como o resto da API.

## 6. A fila (derivada)

Cada item tem `tipo`, `gravidade` (1 = a mais grave), `bloqueia` e o alvo. Ordem: bloqueia primeiro, depois gravidade, depois a ordem
do edital.

| Tipo | Gravidade | Bloqueia | Sai da fila quando |
|---|---|---|---|
| `LINHA_FORA` | 1 | sim | `estado ≠ ABERTO` |
| `SEM_ASSUNTO` | 1 | sim | `estado ≠ ABERTO` |
| `ASSUNTO_RECUSADO` | 2 | sim | `erros` vazio ou `aceito` |
| `ALERTA_DE_PARTE` | 3 | sim | o assunto é aceito (`aceito`), ou a edição/divisão deixa o assunto sem alertas (a entrega traz o alerta por assunto, não por parte) |
| `PARTE_SEM_NO` | 4 | não | tem nó, ou foi declarada "a árvore não tem" |
| `VINCULO_AMPLO_OU_PARCIAL` | 5 | não | parte `ACEITA` ou `DECIDIDA` |
| `NO_TROCADO_NO_REFINO` | 5 | não | idem (origem `REFINO` com `antes`) |
| `NO_MUDOU_NA_ARVORE` | 3 | não | a pessoa reconfirma o nó (§8) |
| `NAO_APARECE_MAIS` | 3 | não | a pessoa descarta ou reaproveita |

`DISCIPLINA_LIGADA_ERRADA` e "duas execuções discordam" dependem do reprocessamento e do mapa editável: Sprint 26. Aqui o mapa de
disciplinas vem do planejamento e é só mostrado.

## 7. Concluir (D-62, D-91, D-92)

`POST .../concluir`, numa transação:

1. Recusa (`422 CONFERENCIA_COM_BLOQUEIO`, com a lista) se houver item da fila com `bloqueia = true`.
2. Constrói o que `AnaliseEditalService.confirmar` já constrói: disciplinas e assuntos (reaproveita homônimo ativo, D-47/D-48), peso
   por D-61 a partir das questões, chave externa em todo assunto sem ela (D-64), cobertura (D-60), ativação do concurso (D-58) —
   com a mesma rotina, extraída para um método comum, sem duplicar.
3. Preenche `edital_assunto.assunto_id`; marca a análise `CONFIRMADA`; apaga o PDF do volume.
4. Parte `PARTE_SEM_NO` e vínculo amplo **não** bloqueiam: ficam como aviso (§9).

Antes da conclusão **nenhum** assunto, disciplina ou concurso existe por causa da análise (D-92): teste de ausência.

## 8. A árvore e o gesto de atualizar (D-90)

`POST /api/arvore/versoes/{id}/adotar` tem duas etapas no mesmo endpoint: sem `confirmar=true`, devolve o **impacto** (partes cujo nó
saiu, foi renomeado ou foi movido, por edital); com ele, adota. Adotar: a versão passa a `vigente`; para cada parte com nó na
versão antiga, se o nó **sumiu** ou foi **movido** → `NO_MUDOU_NA_ARVORE` (a parte volta à fila, nada é trocado); se foi
**renomeado** → só segue (o caminho se lê da versão nova); o `arvore_versao_id` da parte passa para a nova nos casos sem mudança.
A pessoa nunca vê um nó trocado sem ter pedido.

## 9. Aviso no painel e no plano

`PARTE_SEM_NO` e `VINCULO_AMPLO_OU_PARCIAL` de uma análise **concluída** aparecem: em **Concursos** ("12 partes sem filtro de
questões") e no detalhe do assunto afetado. Fica como derivação (D-16): conta as partes do assunto cujo estado é essas filas.
O plano do turno (Hoje) mostra um selo discreto no assunto afetado; sem bloquear nada.

## 10. Telas (SPA estática, Alpine + Tailwind, zero build)

Uma tela nova, **Conferência do edital**, aberta a partir de Concursos. `pages.js` ganha o bloco; `router.js` a rota; `sw.js` e
`CASCA_VERSAO` são atualizados (`04_FRONTEND` §7F).

- Cabeçalho com as contagens; abas **Por disciplina** e **Fila**.
- Linha de parte: o texto, o contexto, o trecho do edital, o nó com o caminho completo e o número de filhos, a natureza, o que a IA
  e a regra disseram, e os botões Aceitar · Trocar nó · A árvore não tem · Dividir · Juntar · Editar · Descartar.
- Seletor de nó: busca por texto, ramos navegáveis, sugestões (os nós que a IA propôs). Até 3 nós.
- Lista de **Decisões** (aba própria) e, em **Ajustes**, **Árvore do QConcursos** com a versão vigente, as carregadas e "Atualizar"
  (mostra o impacto antes).
- Orçamento de atenção (J-5): nenhum gesto exige mais de dois toques; "aceitar todos os exatos" é um.

## 11. Testes

| Garante | Teste |
|---|---|
| D-86 | decisão da pessoa não muda com uma nova entrega; a ligação nova que discorda não vira pendência |
| D-87 (escopo `EDITAL`) | a decisão de um edital não aparece em `/dados` de outro; a ativa é servida antes da IA |
| D-88 | o texto do assunto entregue é guardado como veio; o que a regra recusou chega à fila; nenhum texto é reescrito |
| D-89 | a mesma entrega duas vezes é uma só (`ux_edital_execucao_d89`); chave que sumiu é `aparece = false` e entra na fila; `edital-revisao/2` é recusado |
| D-90 | adotar versão só por `POST`; nó removido ou movido devolve a parte à fila; nada muda sem o gesto; o impacto é mostrado antes |
| D-91 | concluir recusado com bloqueio aberto, aceito com parte sem nó; o aviso aparece |
| D-92 | teste de ausência antes da conclusão; conclusão cria assuntos com chave e ativa o concurso |
| D-93 (parcial) | trocar nó de parte concluída não toca segmento, escada, sessão, revisão nem erro (teste estrutural de ausência) |
| Chave e regra | os vetores compartilhados com o planejamento (§3), incluindo os casos reais |
| Contrato | o exemplo `integracao/contrato/exemplo-edital-revisao-alesc.json` entrega sem erro e gera as contagens esperadas |
| Invariante de histórico | `UPDATE` e `DELETE` em `edital_evento` recusados pelo banco |

Banco real (Postgres), como o resto da suíte. Data congelada onde houver prazo.

## 12. Itens e ordem

| # | Item | Depende de |
|---|---|---|
| 25.0 | Este documento, revisado | — |
| 25.1 | `V17`: tabelas, restrições, gatilho de `edital_evento`, migração das análises `PRONTA` | 25.0 |
| 25.2 | Árvore: carga (`POST /internal/arvores`), diferença, busca e navegação (`/api/arvore/**`) | 25.1 |
| 25.3 | Cabeçalho apenas no `AnaliseEditalService` (sai a etapa 2 e a proposta); guardar o PDF; `/cargo` passa para `SEPARANDO_ASSUNTOS` | 25.1 |
| 25.4 | Rotas internas do edital (`pendentes`, `dados`, `revisao`, `falha`) e a gravação da entrega | 25.1, 25.2 |
| 25.5 | A chave e a conferência por regra em Java, com os vetores compartilhados | 25.1 |
| 25.6 | Conferência: contagens, por disciplina, fila (derivada) e as ações sobre partes, assuntos e trechos; decisões e evento | 25.4, 25.5 |
| 25.7 | Concluir (rotina de criação extraída de `confirmar`), aviso no painel e no plano | 25.6 |
| 25.8 | Adotar versão da árvore com impacto | 25.2, 25.6 |
| 25.9 | Telas: Conferência, Decisões, Árvore em Ajustes; compose com o volume `editais`; `03E` adendo | 25.6 a 25.8 |
| 25.10 | Pi: carregar a árvore, subir um edital real (MPPR) pelo worker do planejamento e resolver a fila | 25.9 e o worker (Sprint 26 no planejamento) |

O worker do planejamento (chamar o pipeline, `preparar_edital.py`, e as quatro chamadas) é trabalho do repositório de lá; entra
pela regra de escrita nos dois repositórios, com o OK do usuário para cada envio. O item 25.10 depende dele.

## 13. Riscos

| Risco | Tratamento |
|---|---|
| A regra em Java e a em Python divergirem | Os mesmos vetores nos dois lados (§3) |
| A entrega de um edital grande (300 partes, 19 mil nós) ser lenta ou pesada | A árvore é carregada à parte, uma vez por versão; a entrega do edital referencia nós por id; medir com o MPPR em 25.10 |
| Edição humana quebrar a conferência de cobertura do assunto | A conferência reroda na hora e a divergência volta à fila; nunca grava texto que a regra recusou sem o `aceito` explícito |
| Memória do Pi ao carregar 19 mil nós | Inserção em lote por JDBC, sem entidade JPA por nó |

## 14. Fora desta sprint

Escopo "todos os editais", reprocessamento, mapa de disciplinas editável, material por assunto e "Questões deste assunto" (Sprint
26); árvore do TEC; OCR do edital.

## Changelog

| Versão | Data | Mudança |
|---|---|---|
| 1.3.0 | 2026-10-24 | A decisão guarda o texto da parte e do contexto como a pessoa os viu (o planejamento os normaliza); a disciplina guarda `texto` e `referencias`; `ALERTA_DE_PARTE` por assunto; ao editar, os alertas da IA se perdem e os da regra voltam. Itens 25.6 e 25.7 feitos |
| 1.2.0 | 2026-10-24 | A entrega leva o `texto` da disciplina (a conferência do assunto precisa dele); vetores compartilhados. Itens 25.4 e 25.5 feitos |
| 1.1.0 | 2026-10-24 | Id de disciplina negado (colisão de 50 ids na árvore real); peso da disciplina (`peso_pessoa`, `questoes`); busca em memória. Itens 25.1 a 25.3 feitos |
| 1.0.0 | 2026-10-24 | Primeira versão, contra `01_DOMINIO` v1.27.0 |
