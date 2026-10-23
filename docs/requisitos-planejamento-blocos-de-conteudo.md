# Requisitos de Alinhamento: Fatiamento de PDF por Bloco, não por Dia

Documento pra levar ao repositório do planejamento (`Plano-de-Estudos-Automatizado`,
uso com a IA de lá), sobre um atrito identificado entre os dois sistemas. Espelha
o padrão que `docs/requisitos-sge-integracao.md` **de lá** já usa: mesma integração,
escrita do lado de quem está pedindo a mudança.

Este documento **não é sobre o payload de import de `Edital`/`Assunto`**
(isso já está coberto pelo par de documentos existente,
`docs/requisitos-sge-integracao.md` / `docs/arquitetura-integracao-planejamento-sge.md`).
É sobre uma camada anterior: **como o material de estudo (PDF) é fatiado antes de
chegar ao candidato.**

---

## 1. O atrito

O planejamento monta hoje, por concurso, PDFs consolidados **por dia**
(`dias/manifesto_AAAA-MM-DD.json`, `dias/montar_dia.py`): cada PDF tem o tamanho
que cabe na janela de estudo daquele dia, calculado a partir do prazo até a prova.

Isso funcionou bem para este concurso específico (SEFAZ SC, prova em novembro,
prazo curto) — mas **contradiz um princípio que a própria arquitetura de
integração já registra**:

> `docs/arquitetura-integracao-planejamento-sge.md §6`:
> *"O planejamento nunca decide 'quando' estudar dia a dia — só a ordem de
> entrada dos assuntos e o orçamento de tempo agregado por assunto. O dia a dia
> real (o que muda com SUCESSO/PARCIAL/FALHA) é território exclusivo da escada
> do SGE."*

Fatiar por **dia de calendário** é decidir "quando" — exatamente o que essa
regra já reserva para o SGE. O problema não aparece com prazo curto porque, na
prática, o ritmo calculado à mão coincidiu com o que o SGE pediria de qualquer
forma. Ele aparia no próximo concurso, com prazo mais longo: o PDF do "dia N" e
o que a escada do SGE realmente pede naquele dia deixam de coincidir, porque
nenhum dos dois sistemas sabe do plano do outro.

## 2. Como o SGE decide "quando" e "quanto por dia" hoje

Pra desenhar a fatia certa, o planejamento precisa saber o que já existe do
lado de cá — não precisa reimplementar nada disso, só **não competir com isso**:

| Mecanismo | O que faz | Onde |
|---|---|---|
| **Bloco de conteúdo** | Unidade de leitura/teoria: 45–75 min. Recuperação é outra unidade, ~8 min | `01_DOMINIO.md §7.1`, D-26 |
| **Duração do bloco é parâmetro**, não constante fixa — o sistema mede a duração real ao longo do tempo | O "45–75 min" é ponto de partida, ajustável, nunca hardcoded | D-29 |
| **Plano de turno** | Quantos blocos cabem hoje é derivado da janela do turno (1h30 em dia de semana → 1 bloco; 3h no fim de semana → 2 blocos) | `01_DOMINIO.md §7.3`, D-27 |
| **Frente de estudo** | Teto global e por disciplina limitam quantos assuntos estão "abertos" ao mesmo tempo — isso já governa o ritmo entre assuntos concorrentes | D-19, D-20, D-23 (nível-alvo por peso) |
| **Escada por resultado** | `SUCESSO` avança, `PARCIAL` repete, `FALHA` regride **e volta pra fila de estudo** — o número de vezes que um assunto é revisitado não é previsível de antemão | D-07 |
| **O sistema nunca exibe o material** | O candidato estuda no tablet, registra no computador; abrir o PDF é decisão do candidato, sempre | `02_JORNADAS.md §1.1` |

A consequência prática: **quantos blocos por dia, e em qual assunto, já é
100% calculado pelo SGE, dinamicamente, considerando desempenho real.** Um
segundo sistema tentando prever isso de antemão (por dia de calendário) vai
divergir assim que o desempenho real do candidato não bater com a suposição.

## 3. A proposta: fatiar por bloco de assunto, não por dia

Trocar a unidade de fatiamento do PDF de **dia** para **bloco de conteúdo
dentro do assunto**:

- Cada assunto (já com `volume_estimado`, que a arquitetura de integração já
  calcula — `docs/arquitetura-integracao-planejamento-sge.md §5`) é dividido em
  **N segmentos de ~45–75 min de leitura cada**, na ordem em que aparece no
  material de origem.
- O PDF de cada segmento é gerado exatamente como hoje (consolidado, com
  mapeamento de página rastreável) — só muda o **critério de corte**: page
  count/tempo de leitura por bloco, não "o que cabe no dia X".
- **Nenhum segmento é datado.** O planejamento entrega uma sequência ordenada de
  segmentos por assunto; o SGE decide, turno a turno, qual segmento é "o próximo"
  daquele assunto — do mesmo jeito que já decide qual assunto entra no bloco de
  conteúdo de hoje.
- Prazo curto deixa de ser "PDF menor por dia" e passa a ser "mais blocos por
  dia" (o candidato consome 2–3 segmentos num turno de 3h, se a escada permitir);
  prazo longo é "menos blocos por dia, mais dias" — sem o planejamento saber
  nem precisar saber qual dos dois é o caso.

## 4. O que muda no contrato de dados

`docs/arquitetura-integracao-planejamento-sge.md §5/§8` já previa
`referência_material` (apostila + página) como campo opcional, estático, que
"o SGE só armazena e exibe, não interpreta nem decide a partir dele". Isso
continua valendo — só passa a apontar pra uma sequência, não pra um ponto
único:

| Campo (por assunto) | Antes | Proposto |
|---|---|---|
| `referência_material` | Um valor (apostila + página) | Lista ordenada de segmentos: `(ordem, arquivo/identificador, página inicial, página final, tempo estimado de leitura)` |

**Continua sendo ponteiro, nunca conteúdo nem link que o SGE precise abrir** —
coerente com `02_JORNADAS.md §1.1`. O SGE só grava "candidato está no segmento 2
de 5 do assunto X" como parte do registro de uma sessão `ESTUDO`; quem abre o
PDF é o candidato, no tablet, como já acontece hoje.

## 5. Topologia: como os artefatos chegam ao SGE (substitui a premissa do §3 de vocês)

Ponto novo, fora da discussão de fatiamento, mas que nasce da mesma conversa:
`arquitetura-integracao-planejamento-sge.md §3` supõe contato direto
PC→Pi via Tailscale no momento da exportação (estágio 5). Isso presume os
dois sistemas online ao mesmo tempo — mas **o planejamento roda só sob
demanda** (quando surge um edital novo, num PC pessoal), enquanto o **SGE é
24/7** no Raspberry Pi. São ritmos de disponibilidade diferentes, e exigir
coincidência entre eles é fricção desnecessária.

**Decisão já tomada do lado do SGE** (`docs/00A_ADR.md`, ADR-037): os
artefatos chegam via **Google Drive**, sem chamada de rede direta entre os
dois sistemas:

- O **CSV** (assuntos + segmentos de material) é sincronizado pro Pi por
  `rclone` (mesma ferramenta já usada pro backup do SGE) — o planejamento só
  precisa **subir o arquivo numa pasta do Drive**, sem saber que o Pi existe
  ou está acessível.
- Os **PDFs dos blocos não precisam ir pro Pi**. Ficam no Drive; o candidato
  abre o link direto de lá, em qualquer aparelho. Por isso, cada PDF de bloco
  precisa ser **compartilhado com link** (não só "arquivo na pasta") — é esse
  link que entra na coluna `referência_material` do CSV, não um caminho local.

**Proposta de estrutura de pasta** (pra confirmar/ajustar com vocês):

```
SGE-Importacao/
  <slug-do-concurso>/
    assuntos.csv              ← ou o CSV de segmentos de material, formato do §4
    blocos/
      <assunto-slug>/
        segmento-01.pdf         ← compartilhado com link, o link vai no CSV
        segmento-02.pdf
        ...
```

O SGE só sincroniza (`rclone`) a raiz de `assuntos.csv`/CSVs — nunca a pasta
`blocos/`, que existe só pra vocês organizarem os PDFs antes de gerar os
links.

## 6. O que **não** muda

A divisão de responsabilidades de `docs/arquitetura-integracao-planejamento-sge.md §2`
continua igual — esta proposta não move nenhuma linha daquela tabela:

- Planejamento continua sem decidir "quando" — só passa a fatiar por unidade
  de leitura em vez de por data.
- SGE continua sendo o único lado que decide dia a dia, com base em desempenho
  real (escada, frente, teto).

## 7. Questões — respondidas pelo planejamento em 2026-09-04

Pra cada uma, três alternativas foram avaliadas do lado de cá; a coluna
"proposta" é a que o SGE recomendou. **As quatro vieram confirmadas sem
contestação**, registradas em `docs/requisitos-alinhamento-fatiamento-pdf-bloco.md`
e `docs/arquitetura-integracao-planejamento-sge.md §3/§6/§8` do lado do
planejamento.

### 7.1 Origem do tamanho do bloco — e separação de dois parâmetros diferentes

Revisão de 2026-09-04: a pergunta original misturava dois números que têm
donos diferentes. Separando:

**(a) Duração de um bloco.** 45–75 min é o ponto de partida do SGE, mas é
**parâmetro ajustável** (D-29 — "o sistema mede a duração real ao longo do
tempo"), não constante fixa.

| Alternativa | Risco |
|---|---|
| Valor fixo, hardcoded nos dois lados | Desalinha em silêncio se o SGE recalibrar o parâmetro e o planejamento não souber |
| Planejamento consulta o SGE em tempo real antes de fatiar | Quebra a autocontenção que `arquitetura-integracao-planejamento-sge.md §3` já promete do lado de vocês — fatiar é análise, não exportação |
| **Proposta: valor único fixo (sugestão: 60 min), documentado nos dois lados** — sem chamada em tempo real; quando o SGE recalibrar (evento raro, registrado em changelog), o número espelha manualmente do outro lado | Baixo — muda em meses, não em dias |

**Confirmado: 60 min fixo, documentado nos dois lados.**

**(b) Quantos blocos por dia.** Isso é diferente de (a) e **não precisa
cruzar pra o SGE em nenhuma hipótese** — é usado só internamente pelo
planejamento, no estágio 4 do pipeline (`arquitetura-integracao-planejamento-sge.md §4`,
"orçamento de tempo/ordem"), pra responder **"com N blocos por dia, o volume
todo cabe até a data da prova?"** e decidir se sugere compressão/corte. Não
determina quantos blocos o candidato vai fazer de fato em cada dia — isso
continua 100% dinâmico, decidido pelo SGE dia a dia (turno + escada). Se o
ritmo real divergir do estimado no planejamento, nada quebra dos dois lados:
o candidato só termina o núcleo mais rápido ou mais devagar do que a
estimativa inicial previu, exatamente como já acontece hoje sem essa
integração.

Sugestão pra vocês, se ainda não é assim: perguntar ao usuário, na hora de
cadastrar o edital, "quantos blocos de estudo por dia, em média" (um número
só, não por dia da semana) — é a mesma simplificação que o SGE já faz internamente
no dimensionamento de `01_DOMINIO §7.3`.

**Confirmado: uso interno do orçamento, nunca exportado.** Implementado como
`calcular_orcamento.py --blocos-por-dia N`, alternativa a `--minutos-dia`.

### 7.2 Manifestos de dia já gerados (concurso atual, SEFAZ SC)

| Alternativa | Risco |
|---|---|
| Reprocessar agora, migrando pro novo formato | Risco desnecessário perto da prova de novembro, sem ganho real pro candidato |
| **Proposta: deixar como estão** — só assunto/concurso novo usa o fatiamento por bloco | Nenhum — dois formatos coexistem por um tempo, sem conflito |

**Confirmado: manifestos do SEFAZ SC ficam intocados.**

### 7.3 Reteach em `FALHA` (D-07: assunto regride e volta pra fila de estudo)

| Alternativa | Risco |
|---|---|
| SGE pede ao planejamento um "segmento de reforço" direcionado à causa do erro | Reabre o atrito original — planejamento decidindo "o que estudar" reativamente a um evento do SGE; exige comunicação em tempo real |
| **Proposta: planejamento não é avisado** — SGE só registra uma sessão `ESTUDO` nova no mesmo assunto; o candidato reabre, por conta própria, o(s) PDF(s) que já tem salvos (reforçado pelo próprio registro de `Erro`, com causa e confiança, que já orienta o candidato) | Nenhum |

**Confirmado: planejamento não é avisado.**

### 7.4 Estrutura de pastas/arquivo no Google Drive (§5)

| Alternativa | Risco |
|---|---|
| Cada PDF de bloco vira um link avulso, sem convenção de pasta | Funciona, mas espalha os artefatos sem rastro — difícil auditar "o que já foi exportado pra qual concurso" |
| **Proposta: convenção de pasta do §5** (`SGE-Importacao/<concurso>/assuntos.csv` + `blocos/<assunto>/segmento-NN.pdf`, cada PDF compartilhado com link) | Baixo — é só convenção de nomenclatura, ajustável sem impacto em código de nenhum dos dois lados |

**Confirmado.** `montar_bloco.py` já gera `blocos/<assunto-slug>/segmento-NN.pdf`
localmente. **Ressalva do planejamento:** o upload pro Drive e a geração do
link compartilhável de cada PDF ainda são passos **manuais** — o campo
`arquivo` fica com o caminho local até alguém trocar pelo link antes de
exportar. **Sem impacto no SGE**: `referência_material` já era desenhado
como ponteiro opaco (`§4`) — não importa se o link foi colocado ali manual
ou automaticamente. Não é gargalo deste lado; automatizar o lado deles é
opcional, prioridade só se virar toil real pra eles.

## 8. Requisito novo (2026-09-06): identificador estável por segmento

Ponto que nasceu já na escrita do documento técnico do lado do SGE
(`docs/SPRINT-10-SEGMENTO.md`), depois das quatro questões de §7 já terem
sido confirmadas — o SGE é quem fecha a especificação primeiro (decisão do
usuário), então este pedido vira requisito do lado de vocês, não sugestão.

**O problema:** o CSV de segmentos (§4) hoje identifica cada linha por
`chaveExternaAssunto` + `ordem`. `ordem` é **posição**, não identidade. Se
vocês reordenarem o material de um assunto depois de já exportado — por
exemplo, inserir um segmento novo entre o que hoje é "02" e "03", empurrando
os seguintes —, o SGE não teria como distinguir "o segmento 3 de antes" do
"segmento 3 de agora": um candidato que já tinha estudado o antigo teria seu
registro histórico silenciosamente associado ao conteúdo novo que passou a
ocupar aquela posição.

**O que muda do lado de vocês:** o CSV de segmentos ganha uma coluna nova,
**`chaveExternaSegmento`** — um identificador **por segmento** (não por
assunto), gerado e mantido por vocês, estável entre reexportações do mesmo
pedaço de material. Mesma natureza que `chaveExterna` já tem do lado do
assunto (§4 original) — o SGE guarda, nunca interpreta.

Hoje `montar_bloco.py` nomeia arquivos só por posição
(`segmento-NN.pdf`, §7.4) — não existe, no pipeline de vocês, um conceito de
identidade de segmento independente da posição no arquivo. Pra gerar essa
chave, algo como um UUID mintado na primeira vez que um segmento é criado, e
persistido num manifesto local (mesmo padrão que `dias/manifesto_AAAA-MM-DD.json`
já usa do lado de vocês) resolveria — a chave nasce junto com o segmento e
viaja com ele nas reexportações seguintes, mesmo que a posição mude.

**Sem isso**, a reimportação do lado do SGE continua funcionando para o caso
comum (corrigir o link de um PDF resubido, mesma posição) — só fica exposta
ao risco de reordenação descrito acima. Registrado como requisito, não
bloqueio: se reordenar segmentos já importados não é algo que aconteça na
prática, o risco pode ficar documentado e aceito em vez de resolvido agora.

## 9. Requisito especulativo (2026-09-06): manifesto do concurso (nome e data da prova)

Diferente do §8 acima, isto **não nasceu de uma sprint fechada** — nasceu
esboçando telas futuras do SGE (protótipo, sem decisão de domínio ainda:
`Concurso`/`Edital` continua fora de `01_DOMINIO.md`). Registrado aqui como
aviso antecipado, não como pedido para agir agora.

**O problema:** hoje o SGE identifica um concurso só pelo **slug da pasta**
em `SGE-Importacao/<slug>/` (ADR-037) — não existe, em `assuntos.csv` nem em
nenhum outro arquivo do contrato atual, um nome de exibição (ex.: "SEFAZ SC")
nem a data da prova. E o SGE **nunca cria** concurso, só importa o que vocês
já prepararam — então, se esses dois dados um dia forem necessários do lado
de cá, eles têm que vir de vocês, não digitados manualmente no SGE.

**Por que isso pode vir a importar:** ao esboçar as telas futuras, o usuário
pediu um indicador de "progresso vs. data da prova" — não um cronograma
dia-a-dia (o SGE decidiu explicitamente não ter tela de calendário,
`00_PRODUTO.md §8.5`). O percentual de cobertura do edital já é calculável
só com o que o SGE tem hoje (backlog/frente/consolidado); os **dias
restantes até a prova**, não — dependem de uma data que hoje não existe em
lugar nenhum do contrato entre os dois sistemas.

**Proposta, só para quando isto virar prioridade de verdade:** um manifesto
pequeno por concurso, na mesma pasta do Drive (ex.:
`SGE-Importacao/<slug>/concurso.json`, ou colunas extras num CSV de
cabeçalho) com `nome` e `dataProva`. Mesmo princípio de `chaveExterna` (§8):
o SGE guarda o valor, nunca decide nada a partir dele.

**Nada bloqueado, nada decidido.** Se vocês quiserem adiantar esse campo do
lado de vocês, ele fica pronto esperando o SGE decidir usá-lo; se não, não
afeta nada do que já está funcionando hoje (Fase 1 e Fase 2, §7).

## 10. Rastreabilidade

Regras do SGE citadas: `01_DOMINIO.md` D-07, D-19, D-20, D-23, D-26, D-27,
D-29, D-50, D-51, D-53 (§8); `02_JORNADAS.md §1.1`, `§7.3`;
`00_PRODUTO.md §8.5` (sem tela de calendário, §9). Decisão de
infraestrutura: `docs/00A_ADR.md` ADR-037 (artefatos via Google Drive +
`rclone`).

## 11. Requisito novo (2026-10-14): formato dos arquivos de importação e concurso-alvo ativo

**Achado** numa análise dos dois repositórios: o exportador de vocês
(`integracao/exportar_sge.py`) gerava **um** CSV
(`uuid,disciplina,assunto,edital,peso,ordem,referencia_material`, segmentos
como JSON dentro de uma célula). O SGE lê **dois** CSVs com outros nomes de
coluna (`especificacao/02_JORNADAS.md` J-1). Importado como estava, o assunto
entrava **sem** `chaveExterna` e todo `segmentos.csv` seria recusado. Também:
peso `"não determinado"` é recusado pelo SGE (vazio vira `MEDIO`).

**O contrato é do lado que consome** (o SGE). Pasta do concurso no Drive,
`SGE-Importacao/<slug>/`:

| Arquivo | Colunas |
|---|---|
| `assuntos.csv` | `disciplina,assunto,peso,ordem,chaveExterna` — `chaveExterna` = uuid canônico; peso `ALTO`/`MEDIO`/`BAIXO` ou vazio; **sem** `id` |
| `segmentos.csv` | `chaveExternaSegmento,chaveExternaAssunto,ordem,arquivo,paginaInicial,paginaFinal,tempoEstimadoMin` — `arquivo` é o link do Drive |
| `concurso.json` | `{"slug", "nome", "dataProva"}` — §9 deixa de ser especulativo do lado de cá: o SGE guarda para o diagnóstico de edital (Sprint 18 do mapa), ainda não lê |

**Mudanças do lado do SGE, já feitas (Sprint 16):**

- Linha de `assuntos.csv` sem `id` com `chaveExterna` já conhecida
  **atualiza** (nome, peso, ordem) em vez de criar — a ressincronização
  automática deixa de ser recusada a partir da segunda vez, e reordenar
  assuntos existentes no mesmo arquivo é aceito.
- **Concurso-alvo ativo** (`docs/00A_ADR.md` ADR-039): a importação automática
  lê só a pasta nomeada em `CONCURSO_ATIVO`. Assunto canônico comum a dois
  editais mantém escada e histórico ao trocar de concurso; peso e ordem
  passam a ser os do concurso ativo.

**Pedido a vocês:** gerar os três arquivos acima. Verificado de ponta a ponta
em 2026-10-14 com uma versão do exportador escrita nesta mesma análise (no
repositório de vocês, na branch de trabalho desta sessão): exportação →
`scripts/importar-segmentos.sh` → SGE, com ressincronização e troca de
concurso-alvo.

**Fechada também a pendência** "o planejamento vai gerar
`chaveExternaSegmento`?" (§8): já gerava, em `dias/montar_bloco.py` —
faltava só sair no arquivo certo.

## 12. Requisito novo (2026-10-14): todo segmento é leitura obrigatória antes da escada

O SGE passou a cumprir o que o §3 prometia ("o SGE decide, turno a turno, qual
segmento é o próximo"), com duas regras novas (`01_DOMINIO.md` v1.20.0):

- **D-54:** num assunto com segmentos, a primeira revisão só é agendada quando
  **todos** os segmentos tiveram uma sessão de estudo. Antes disso, recuperar o
  assunto mediria o que ainda não foi lido.
- **D-55:** o bloco de conteúdo do turno continua o assunto em estudo, pelo
  próximo segmento na `ordem`, antes de abrir um assunto novo.

**O que isso pede de vocês:**

1. **`ordem` é a ordem de leitura.** O SGE oferece os segmentos exatamente nessa
   sequência.
2. **Não exportar material opcional como segmento.** Cada segmento atrasa o início
   das revisões até ser lido. Leitura complementar fica fora do `segmentos.csv`.
   Fixação (questões comentadas, resumo) é leitura obrigatória e continua como
   segmento — é o que o fatiador de vocês já faz.
3. Assunto sem segmentos continua funcionando como antes: a escada começa no
   primeiro estudo.

Nada muda no formato dos arquivos (§11).

## 13. Requisito novo (2026-10-14): `tempoEstimadoMin` passa a ser usado

Até aqui `tempoEstimadoMin` era informativo: o SGE só o mostrava no card do
bloco. A partir da Sprint 17 (`01_DOMINIO.md` v1.21.0, §7.7), ele **preenche o
campo de tempo** na tela de registro do estudo daquele segmento. O usuário
confirma ou corrige, e o valor confirmado é o que entra no histórico de
duração real (D-29).

**O que isso pede de vocês:**

1. **Estimar o tempo de leitura de verdade**, não um valor fixo por bloco. Uma
   estimativa sistematicamente errada vira um campo que o usuário corrige toda
   vez, ou, pior, confirma sem olhar.
2. **Deixar vazio quando não houver base para estimar.** Vazio é aceito: o SGE
   cai na duração de bloco parametrizada.

Nada muda no formato dos arquivos (§11).

## 14. Requisito novo (2026-10-15): o SGE passa a ler o `concurso.json` e a criar assuntos por conta própria

Sprint 18 do SGE (`docs/SPRINT-18-CONCURSOS.md`, ADR-042, ADR-043). Três
mudanças que afetam quem exporta para cá:

1. **O `concurso.json` agora é lido.** A confirmação de `assuntos.csv` recebe o
   `concurso.json` da mesma pasta e registra o concurso pelo `slug`: cria na
   primeira vez, reativa e atualiza nome e `dataProva` nas seguintes. `slug` e
   `nome` são obrigatórios; `dataProva` (`AAAA-MM-DD`) é opcional. Arquivo
   inválido recusa a importação inteira — tudo ou nada, como o CSV.
2. **Assuntos podem nascer no SGE sem `chaveExterna`.** O usuário pode subir um
   edital pelo próprio SGE (análise por IA). Quando o planejamento exportar
   depois uma linha com `chaveExterna` ainda desconhecida e **o mesmo nome**
   (sem acento, sem caixa) de um assunto dessa disciplina **sem** chave, o SGE
   **adota** o assunto: grava a chave nele e atualiza, em vez de recusar por
   nome duplicado. Nome diferente cria um assunto novo — então vale manter o
   nome fiel ao edital, como já é a regra de vocês.
3. **Ordem ocupada por assunto fora do arquivo não recusa mais o arquivo.** Ao
   trocar de edital, o assunto do edital anterior que estava na ordem pedida
   vai para o fim da disciplina. Antes, D-48 recusava o arquivo inteiro.

Nada muda no formato dos arquivos (§11). Ação sugerida do lado de vocês:
nenhuma obrigatória; vale conferir que todo `concurso.json` exportado tem
`slug` e `nome`.

## 15. Resposta (2026-10-15): entrega do concurso ao planejamento

Pedido do planejamento (`requisitos-sge-integracao.md` §10 de lá) atendido na branch
`exportacao-planejamento`: `GET /api/concursos/{id}/planejamento` devolve `slug, nome, cargo,
banca, dataProva` e os assuntos do edital em ordem (`disciplina, assunto, peso, ordem,
chaveExterna`). Fora, por não serem guardados após a confirmação: número de questões por
disciplina e correspondência com assunto existente. Ver `docs/SPRINT-18-CONCURSOS.md` §10.

## 16. Requisito novo (2026-10-17): a busca de candidatos precisa conhecer a disciplina

Achado na primeira preparação real (SEFAZ SC 2026, 97 assuntos). Doze assuntos ficaram
"sem material no acervo"; a auditoria do índice mostrou que **oito deles tinham apostila
no acervo** (Português, lógica, ciência de dados). Causa: `Acervo.candidatos` buscava os
termos do nome do assunto (OR de prefixos) no acervo inteiro, sem olhar a disciplina.
Assunto de nome genérico — "Conectivos", "Morfossintaxe", "Elementos estruturais e processos
de formação de palavras" — recebia como melhores arquivos os de outra disciplina (conectivos
lógicos, diagramas UML), e a IA, com razão, respondia que aquilo não ensinava o assunto.

Pedido: **o planejamento restringe a busca aos cursos afins à disciplina do edital** antes
de buscar no acervo inteiro, e, se a IA recusa o recorte, tenta o acervo inteiro uma vez
antes de registrar a lacuna. Corrigido do lado de vocês na branch `candidatos-por-disciplina`
(afinidade por radicais entre a disciplina e a pasta do curso; 40/40 + 25/25 verdes; conferido
contra o índice real, sem alterar nada).

Do lado do SGE nada muda: a disciplina já vem no `GET /api/concursos/{id}/planejamento`,
e refazer o material (D-66) já reaproveita os assuntos. Dois assuntos ficam como lacuna de
verdade: a Lei Complementar nº 741/2019 e os conhecimentos regionais de Santa Catarina — o
acervo não os cobre. Ação sugerida: **nenhuma obrigatória** (requisito especulativo, só para
quem quiser indexar apostilas de legislação e de conhecimentos de SC).

## 17. Requisito novo (2026-10-22): tela de revisão do edital e vínculo das partes com a árvore do QConcursos

Contexto: o planejamento prototipou a leitura do conteúdo programático do edital em **assuntos** (texto exato do edital) e
**partes** (um assunto de estudo cada, com o contexto sob o qual aparece e, quando depende de outra palavra, o prefixo
herdado: "Windows Server 2019 e 2022" → "Windows Server 2019" e "2022"). Cada parte é ligada a um nó da árvore de
assuntos do QConcursos (disciplina › assunto › subassunto), onde o usuário resolve questões; a resolução continua fora do
SGE. Validado em 9 editais reais: 65 de 66 partes conferidas à mão estavam certas; na ligação aos nós, uma amostra de 110
partes teve cerca de 95% de acerto segundo três IAs avaliadoras, com 12 divergências (6 resolvidas pelo usuário, 6 em aberto).

**Achado que motiva o pedido:** a IA erra, e erra diferente a cada modelo ou prompt (o mesmo nome existe em ramos
diferentes: "Kanban" de estoque x de desenvolvimento ágil; "tipos de dados" de programação x de ciência de dados).
Ajustar a IA não fecha o problema. O que fecha é **a pessoa resolver cada divergência numa tela e o sistema guardar a
decisão**, valendo antes de qualquer IA nas próximas vezes.

**Pedido:** uma tela de revisão por edital. Desenho completo em
`docs/especificacao-revisao-edital-sge.md` do repositório do planejamento (§3 tela e fila, §4 decisões, §6 conclusão e troca
de nó, §7 dados). O essencial:

1. **Fila de revisão** com o que pede decisão, em ordem de gravidade, e contagens no topo (disciplinas, assuntos, partes,
   ligadas por natureza exato/mais amplo/parcial, sem nó, pendências, decididas por pessoa). Tipos: linhas do edital fora de
   qualquer disciplina, trecho sem assunto, assunto recusado pela conferência por regra, alerta de parte, parte mal dividida,
   parte sem nó, vínculo mais amplo/parcial, nó trocado pela segunda checagem, duas execuções discordando, disciplina ligada à
   disciplina errada, nó que mudou na árvore.
2. **Ações:** aceitar (inclusive todos os exatos da disciplina); trocar o nó com busca e navegação na árvore, com mais de
   um nó; marcar "a árvore não tem"; dividir, juntar ou editar a parte; descartar com motivo; comentar.
3. **Decisões aprovadas:** chave = parte + contexto (sem a disciplina), valem antes da IA em qualquer edital; o
   reprocessamento nunca as sobrescreve; escopo "este edital" (padrão) ou "todos", este último com **confirmação extra**
   mostrando quantas partes de outros editais mudariam; podem ser editadas e revogadas; voltam à fila se o nó mudar na árvore.
4. **Conclusão:** bloqueia só quando o conteúdo do edital está incerto (linhas fora de disciplina, trechos sem assunto,
   assuntos recusados, alertas de divisão). Parte sem nó **não bloqueia**: o painel avisa ("12 partes sem filtro de
   questões") e o aviso fica visível no plano, junto ao assunto, até ser resolvido.
5. **Depois de concluído** o usuário pode vincular uma parte sem nó ou trocar o nó de uma já vinculada, a qualquer momento.
6. Uso de uma pessoa só: sem autor nem revisores concorrentes (ADR-040).

**Verificado no código do SGE em 2026-10-22:** o SGE não guarda hoje nó de taxonomia (o qconcursos.com aparece só como
referência visual em `04_FRONTEND.md`); o vínculo é um dado novo. Assunto é identificado por `chaveExterna` e o segmento de
material é preso ao assunto (`V9__segmento.sql`), então plano, escada de revisão e histórico de estudo seguem o assunto, não
o nó: **trocar um nó não quebra nada do que existe**. Trocar o nó só muda as sugestões de material do lado do planejamento,
e material já fatiado ou estudado nunca é removido.

**Contrato de entrega (2026-10-22, versão 0.2.0 da especificação, §11):** o worker do planejamento segue o mesmo desenho da
prova (Sprint 24): consulta os editais pendentes, pega os dados do edital (PDF, cargo e as **decisões aprovadas**),
entrega a revisão no formato `edital-revisao/1` (disciplinas → assuntos → partes, cada parte com `chave` estável, contexto,
prefixo herdado e ligação a nós) ou a falha com a etapa. Exemplo real e gerador testado em `integracao/contrato/` e
`integracao/entrega_revisao.py` do planejamento. Endereços, autenticação e armazenamento são do SGE.

**Cobertura medida em 2026-10-23 (ALESC e SEFAZ-SP TIC), para a tela mostrar desde o início:** das partes, 58% e 44% têm
material do acervo que ensina; por assunto, 14% a 19% não têm material nenhum (lacuna real do acervo). Metodologia e
arquivos em `~/partes-edital/material/` do planejamento.

**O que fica de fora:** leitura de PDF em imagem (OCR), árvore do TEC e usar as ligações para filtrar questões dentro do SGE.

**Nada em `especificacao/` foi alterado.** Novas entidades (parte, decisão, execução de IA) e telas são mudança conceitual e
nascem do lado do SGE, na sprint que a incluir, pela regra de vocês (documento primeiro, versão, changelog). Este texto é o
pedido e o desenho de origem, não implementação.
