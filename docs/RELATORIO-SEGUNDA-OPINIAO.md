# Relatório para segunda opinião: o sistema de estudos para concursos e a cadeia que monta o material

**Versão** 1.0.0 · **Data** 2026-10-18 · **Língua** português do Brasil · **Natureza:** documento de apresentação e
pedido de revisão. Não é especificação: não define nada, descreve o que existe, o que mediu e o que se está
considerando fazer.

---

## 0. Como ler este documento

**Quem está pedindo.** O dono de um sistema pessoal de gestão de estudos para concursos públicos. O sistema é
desenvolvido com apoio de uma IA de programação (Claude Code), em sessões curtas, sob uma especificação escrita.
Este documento foi preparado para que **uma segunda IA, sem acesso ao repositório**, avalie as escolhas e sugira
melhorias. Por isso ele tenta ser autossuficiente.

**O que se pede.** Uma avaliação crítica, principalmente da §4 (a cadeia que monta o material de estudo a partir de
um acervo de apostilas), do diagnóstico da §8 e das sugestões da §10. As perguntas concretas estão na §13.

**Como distinguir o que é o quê.** Ao longo do texto:

- **[Medido]** é um número ou fato obtido nesta rodada de trabalho, com a origem dita.
- **[Documentado]** é o que as especificações do projeto dizem, e que não foi reverificado agora.
- **[Inferência]** é conclusão minha a partir de [Medido]; pode estar errada.
- **[Hipótese]** é palpite ainda sem teste.

**O que este documento não é.** Não é uma proposta fechada. A seção de sugestões (§10) lista o que foi cogitado,
com custo e risco, mas nenhuma foi decidida.

**Siglas usadas** estão no glossário (§14.1). A mais importante: **D-xx** é o identificador de uma regra de
domínio do projeto (há 85); **ADR-0xx** é uma decisão de arquitetura registrada.

---

## 1. Quem usa o sistema e para quê

### 1.1 O usuário

Um único usuário, servidor público concursado, que estuda para outro concurso na área de tecnologia da informação.
Os fatos que, cruzados, determinam o produto [Documentado, `00_PRODUTO.md` §1]:

| Fato | Consequência de projeto |
|---|---|
| Um usuário, sempre o mesmo | Sem cadastro, papéis ou multi-tenant (decisões que encareceriam multiusuário são evitadas, ADR-040) |
| Provas a partir de 24 meses; estudo contínuo até sair edital | Otimizar para retenção durável, não para cobertura de véspera |
| 3 a 5 horas por semana para **construir** o sistema | O escopo é o recurso escasso |
| Tempo de estudo próprio, separado disso | O sistema não pode competir com o estudo pelo tempo dele |
| Resolve questões em plataforma externa | O sistema registra a recuperação; não hospeda questões de plataformas |
| Menos de 50 questões por semana | O número mais restritivo: a medição será lenta e precisa ser honesta sobre isso |
| Algumas centenas de assuntos mapeados | Define a carga de revisão |
| Teoria por PDF/apostila e legislação seca | Ele controla a ordem; recomendar teoria faz sentido |

### 1.2 A tese

> O sistema existe para fazer o acerto medido em questões subir ao longo do tempo, e para provar que subiu.

Tudo o mais é instrumental: registrar horas, agendar revisão, classificar erro, recomendar assunto. Cada um desses
só se sustenta enquanto servir à frase acima [Documentado, `00_PRODUTO.md` §2].

### 1.3 As três dores, em ordem de peso

1. "Esqueço o que estudei semanas atrás." (ataca com espaçamento)
2. "Estudo, acho que entendi, e na prova erro." (ataca com calibração: comparar a previsão de acerto com o resultado)
3. "Não mantenho constância." (ataca com atrito baixo: a fila decide o quê; o usuário só executa)

Ele declarou **não** ter a dor "não sei o que estudar quando sento". Por isso a recomendação de assunto existe, mas
foi rebaixada de prioridade e de ambição (não justifica algoritmos de pontuação sofisticados).

### 1.4 Os quatro pilares

- **Recuperação:** tentar lembrar produz aprendizado; reconhecer não.
- **Espaçamento:** distribuir no tempo vence concentrar. A evidência diz que o cronograma exato importa pouco, então
  **é proibido gastar esforço aperfeiçoando o agendador** (`00_PRODUTO.md` §3.2). Qualquer proposta de algoritmo
  adaptativo esbarra aqui.
- **Interleaving:** misturar assuntos só ajuda quando eles são confundíveis.
- **Calibração:** medir se a confiança do usuário corresponde ao acerto.

### 1.5 As métricas de sucesso

- **M-1** acerto ao longo do tempo (a tese, e a mais lenta);
- **M-2** retenção (valida o mecanismo de espaçamento);
- **M-3** calibração (ataca a segunda dor);
- **M-4** aderência (processo, não resultado).

E o que o sistema **deliberadamente não mede**: horas como progresso, sequência de dias (*streak*), confiança de
1 a 5 por lote, e **"cobertura do edital como progresso"** (§8.3 do documento de produto). Este último é relevante
aqui e será retomado na §9 e na §12: o produto decidiu que ter material para um assunto não é progresso. O que se
discute neste relatório é uma pergunta diferente e anterior: **o material que o sistema entrega para cada assunto do
edital é suficiente para responder o que a prova cobra?**

---

## 2. Arquitetura geral

### 2.1 Pilha

Java 21 · Spring Boot · PostgreSQL · Docker num Raspberry Pi 4B de 4 GB · acesso remoto por rede privada
(Tailscale). A interface é uma **SPA estática sem etapa de build**, servida pelo próprio Spring, instalável como
aplicativo no celular e no tablet (ADR-030, ADR-038). Banco: migrações Flyway (hoje 16).

### 2.2 Dois sistemas, dois repositórios

| | **SGE** | **Planejamento** |
|---|---|---|
| Papel | O sistema de estudos: domínio, API, telas, banco | O que transforma o acervo de apostilas em material por assunto; também lê provas |
| Tecnologia | Java/Spring | Python, worker em contêiner ao lado do SGE |
| Dados | PostgreSQL | `indice.db` (SQLite FTS5, 860 MB), PDFs do acervo, PDFs gerados |
| Regra | Fonte da verdade é `especificacao/` | Tem seu próprio documento de instruções |

Cada um só escreve no próprio repositório. A troca é por leitura pública do repositório do outro e por **rotas
internas** autenticadas por token (`/internal/**`). O worker busca trabalho no SGE (assuntos sem material, provas a
ler ou julgar) e devolve resultados. **A IA é acessada pelo SGE** (proxy interno) usando a assinatura do Claude do
dono; chamar a IA é caro em cota e o fluxo de estudo do dia a dia não usa IA (ADR-044, ADR-045).

### 2.3 Princípios que governam o código

Os cinco princípios do projeto [Documentado, `03_INVARIANTES.md` §10]:

1. Toda informação derivável **não é persistida** (ex.: a fase de um assunto é calculada, não gravada: D-16).
2. Toda invariante mora na camada mais baixa que a comporte **sem distorção**.
3. Nenhuma política desce para a persistência.
4. Todo comportamento relevante tem teste, com **data congelada** quando depender de prazo.
5. Nenhuma interface é responsável por integridade.

E uma regra metodológica forte: **a especificação é a fonte única de verdade, o código é consequência dela.** Se algo
não está na especificação, não se implementa; se a especificação está errada, corrige-se o documento antes do código.
A documentação **técnica** é escrita por sprint, quando a sprint começa, de propósito, para não gerar retrabalho.

### 2.4 Estado da infraestrutura

[Medido, Pi 4B, 4 núcleos, 4 GB] Com o serviço de acesso remoto gráfico desligado, o servidor usa ~1,3–1,4 GB de
memória. O worker do planejamento roda com teto de 768 MB e 1,5 CPU. O OCR de uma prova de imagem de 10 páginas
levou ~315 s (Tesseract, bandas de 200 dpi). Há outros serviços do dono rodando no mesmo Pi, então o consumo importa.

---

## 3. O domínio em uma página

**Hierarquia do conteúdo:** Disciplina → **Assunto** → **Segmento**.

- **Assunto:** a unidade de estudo e de recuperação. Tem nome (até 250 caracteres), peso, dificuldade, ordem e uma
  `chaveExterna` que o liga ao planejamento. Um assunto pode estar em vários concursos (relação n:n via
  `concurso_assunto`). **O modelo é plano: não existem subitens, filhos nem "conceitos"** dentro de um assunto.
  Dividir um assunto grande em vários é uma operação manual, prevista e posterior (`01_DOMINIO.md` §3.2.2).
- **Segmento:** um pedaço de material de ~60 minutos para um assunto: um PDF com uma ou mais faixas de páginas.
  Um assunto tem N segmentos. É o "material por assunto".
- **Concurso-alvo:** o edital para o qual se estuda. A cobertura é a lista de assuntos que ele cobra.
- **Lacuna de material:** registro de que o acervo "não ensina" um assunto, com motivo. Nunca é apagada (D-65).

**Fluxo de estudo (resumo):** o usuário vê a tela Hoje (fila de recuperação + blocos de estudo), registra
sessões de quatro tipos (conteúdo, questões, flashcards, simulado), o resultado de cada recuperação (SUCESSO,
PARCIAL, FALHA) move o assunto numa **escada de revisão** com no máximo uma revisão pendente por assunto.

**Regras de domínio que mais custam se esquecidas** (de 85):

| Regra | O que é |
|---|---|
| D-05 | No máximo uma revisão pendente por assunto; unicidade na persistência, nunca verificação prévia no serviço |
| D-07 | SUCESSO sobe; PARCIAL repete; FALHA regride e volta para a fila de estudo |
| D-16 | A fase do assunto é derivada; não existe coluna de fase |
| D-18 | Nada é apagado, mas sem filtro global de leitura |
| D-45 | A mesma tentativa de registro conta uma vez (identificador gerado no cliente) |

O estado do material de um assunto (`PRONTO`, `SEM_MATERIAL`, `A_PREPARAR`) é **derivado**: `PRONTO` significa "existe
ao menos um segmento ativo". **Não significa que o segmento cobre o assunto.** Esse é o ponto cego central (§8).

---

## 4. A cadeia de material: do edital aos segmentos (o centro deste relatório)

Esta seção descreve, passo a passo, como o sistema transforma um edital e um acervo de apostilas em material por
assunto. Os parâmetros numéricos são do código atual do planejamento.

### 4.1 Os insumos

- **Edital:** PDF do concurso. É lido por IA em duas etapas (identificar o cargo e extrair o conteúdo programático)
  e o resultado é revisado pelo usuário antes de gravar. **Regra do prompt:** "um assunto por tópico do conteúdo
  programático, na ordem do edital; separar itens agrupados apenas quando forem conteúdos distintos"
  [Documentado, `edital/PromptsEdital.java`]. O resultado é uma lista plana de assuntos.
- **Acervo:** [Medido, `indice.db`] 2.639 PDFs, 264.013 páginas, 160 cursos (pastas). São apostilas de cursos
  preparatórios para concursos (de várias bancas e cargos). O acervo está no PC do dono e uma cópia no SSD do Pi;
  o índice foi gerado no PC porque indexar no Pi levaria horas.

### 4.2 A indexação (`indice/indexador.py`)

1. Percorre a pasta e, para cada PDF novo ou alterado (por data de modificação), extrai o texto **página por página**
   com pdfplumber. Páginas com duas colunas são detectadas por heurística (menos de 2% das palavras cruzam o eixo
   central) e lidas em duas metades.
2. **Remove boilerplate:** linhas que, normalizadas (sem número no início ou fim), aparecem em 60% ou mais das
   páginas do PDF; e linhas que são só um número de 1 a 4 dígitos.
3. Grava três tabelas: `arquivos` (caminho, curso = nome da pasta, nome, nº de páginas, hash, data), `bookmarks`
   (marcadores do PDF), `paginas` (arquivo, página, texto) e uma tabela de busca **FTS5** (`unicode61`, sem
   stemmer) sobre o texto das páginas.
4. **Não há OCR.** Página sem camada de texto entra com texto vazio e fica invisível à busca; não há contagem nem
   alerta. Erros por arquivo vão ao log e o arquivo fica fora do índice.
5. **Não há metadado** de banca, ano, título ou disciplina. O único metadado semântico é o nome da pasta.

[Medido] Páginas com menos de 20 caracteres: **4.864 de 264.013 (1,8%)**.

### 4.3 A busca de candidatos por assunto (`integracao/acervo.py`)

Para cada assunto sem material, o worker:

1. **Monta os termos** a partir do nome do assunto: tira acentos, mantém palavras de 3+ letras, descarta uma lista de
   palavras genéricas de edital ("conceitos", "princípios", "fundamentos", "tipos"...) e duplicatas. **Só o nome do
   assunto entra; a disciplina não vira termo.**
2. **Consulta o FTS5** com os termos em **OU**, cada um com prefixo: `"t1"* OR "t2"* OR ...`.
3. **Filtro por disciplina:** calcula os "cursos afins" à disciplina comparando radicais de 6 letras do nome da pasta
   com os da disciplina; se houver cursos afins, busca primeiro só neles; se a busca restrita não achar nada, cai
   para o acervo inteiro.
4. **Ranqueia por BM25** e fica com os **150 melhores hits** (páginas).
5. Agrupa por arquivo e fica com os **6 arquivos** de maior soma de pontuação; de cada um, **12 páginas** (as de
   melhor pontuação, reordenadas por número) com um trecho.
6. Monta um **mapa** de cada arquivo: o início (90 caracteres) de cada página da região das ocorrências, com 3 páginas
   de margem e **no máximo 45 páginas a contar da primeira ocorrência**.
7. Anexa o sumário (bookmarks de nível ≤ 2, no máximo 40). Como a maior parte dos PDFs não tem bookmarks, quase
   sempre vem vazio.

### 4.4 A triagem por IA (`integracao/worker.py`, `triagem_ia.py`)

A IA recebe as "partes" do assunto (o nome do assunto partido em `;` e `,`), os candidatos (trecho, sumário, mapa)
e regras: devolver **faixas contínuas de páginas**, indicar se são `teoria` ou `fixacao`, e "**na dúvida sobre uma
página, deixe-a de fora**". Para cada parte do assunto: ou cobre com páginas, ou declara **lacuna com motivo**.

- Há 2 tentativas; na segunda vão os erros da validação.
- Um **validador determinístico** confere forma: o arquivo existe, as páginas existem, sem repetição, todo subitem
  coberto ou com lacuna e motivo. **Não confere qualidade**: aceita uma faixa de uma página; aceita faixa fora da
  região que foi mostrada à IA.
- **Rodadas:** a rodada 1 usa o filtro por disciplina; a rodada 2 (acervo inteiro) **só ocorre se a IA recusar a
  rodada 1**. Se a rodada 1 for aceita com material de pior qualidade, o acervo inteiro nunca é consultado.
- Se a rodada 1 não achar nenhum hit, a lacuna sai direto, sem chamar a IA.
- Duas falhas de validação seguidas viram lacuna. O assunto sai da fila de pendentes e **só volta com um "refazer"
  explícito**.
- Até 10 assuntos por ciclo; ciclo de 5 minutos.

### 4.5 O fatiamento (`dias/montar_bloco.py`)

Com o manifesto validado, o sistema monta PDFs de **segmento** (~60 minutos cada, tolerância de ±25%, o que dá 45 a
75 minutos), com uma capa de rastreabilidade. O tempo é estimado a **4 minutos por página, fixo, para qualquer
tipo de página** (teoria, questões comentadas, lei seca). Cortes caem, preferencialmente, em fronteiras naturais
(troca de fonte, início de seção por bookmark); sem bookmarks, a maioria cai "no meio de seção". `descartes`
(o que a IA deixou de fora) fica sempre vazio no fluxo automático, então o que foi descartado não deixa rastro.

Existe um relatório `_cobertura.md` por assunto que lista, por subitem, quais arquivos e páginas o cobrem. **Ele
não mede percentual de cobertura, não verifica páginas sem texto nas faixas e o fluxo automático nunca o lê.**

### 4.6 O que chega ao SGE

O SGE grava os segmentos (arquivo, página inicial, página final quando é faixa contínua de um único arquivo),
serve os PDFs ao app e deriva o estado `PRONTO`. A tela "Ver por assunto" lista, por assunto, situação,
quantidade de segmentos, minutos e páginas, e o motivo quando há lacuna.

[Medido, primeira preparação real, SEFAZ SC 2026, 97 assuntos] 91 `PRONTO` (15.896 minutos, 3.081 páginas),
6 `SEM_MATERIAL`. Uma auditoria dos 12 que haviam saído sem material mostrou que ~8 eram falsos negativos causados
por a busca ignorar a disciplina; foi corrigido com o filtro de cursos afins (§4.3, item 3). A conferência foi
manual, por leitura; não existe métrica automática.

### 4.7 Em uma frase

Cada assunto vira uma consulta de ~12 palavras em OU, e o material é o que couber em **no máximo 72 páginas** (6
arquivos × 12), escolhidas pela pontuação textual e depois filtradas por uma IA instruída a, na dúvida, cortar.

---

## 5. A prova hospedada (Sprint 24): a régua de cobertura

### 5.1 Por que existe

O dono quer **uma resposta objetiva**: "com o material que foi montado, seria possível responder as questões de uma
prova real e gabaritar?". A resposta que o sistema dava até então ("PRONTO") não dizia isso. Evoluiu para:

- o sistema **hospeda** a prova; o usuário a **resolve** no sistema;
- o **gabarito é obrigatório** (sem ele não há como saber se o material sustenta a alternativa certa; as alternativas
  erradas podem ter vindo de fontes que não estão nos PDFs, então o critério olha só a **alternativa do gabarito**);
- a nota alimenta **M-1 por disciplina e global**; o desempenho por assunto só informa, e nada vira revisão
  automática (a base amostral por assunto é pequena demais, D-77);
- **só para o concurso-alvo**.

[Documentado] `01_DOMINIO.md` §3.11 e as regras D-67 a D-85; jornadas J-6 (preparar) e J-7 (resolver);
ADR-046; detalhamento técnico em `docs/SPRINT-24-PROVA-GABARITO.md`.

### 5.2 O fluxo

`LENDO → (AGUARDANDO_GABARITO | CONFERIR) → JULGANDO → PRONTA` (ou `FALHOU`).

1. **Envio** (PDF da prova e do gabarito, ou texto do gabarito). Mesma prova de novo devolve a existente (hash).
2. **Leitura (worker):** extrai o texto; se o PDF for imagem (a maioria das provas da FCC e do IDECAN), faz **OCR**
   (Tesseract, em bandas de 2048 px com 256 px de sobreposição, ordem de leitura preservada, confiança por linha).
3. **Separação das questões: determinística, sem IA** (regras por formato, validadas). Razão: uma prova de 70
   questões são ~25 mil tokens que a IA teria de devolver reescritos, a cada prova, para um resultado que regras
   dão de graça e que os testes provam. A IA fica onde só ela serve (ler o gabarito, atribuir o assunto, julgar).
4. **Gabarito:** escolha determinística da página (palavras do cargo; o tipo da prova desempata), a IA lê e o sistema
   valida; falha de casamento volta como aviso e a prova espera.
5. **Atribuição:** a IA, em lotes de ~10, escolhe para cada questão o assunto do edital-alvo ou "nenhum"
   (**fora do edital**, D-74), com confiança; confiança baixa vira `REVISAR`.
6. **Conferência pelo usuário** (J-6): amostra espalhada das questões, com a imagem da página para checar o OCR;
   pode corrigir texto e atribuição.
7. **Julgamento (worker):** para cada questão avaliável, monta um índice em memória (FTS5) **dos segmentos ativos do
   assunto atribuído**, escolhe ~6 páginas pela questão e pela alternativa do gabarito, pergunta à IA o veredito
   e exige a **página citada** (`S<segmento>-p<página>`) entre as oferecidas, senão rebaixa (D-68).
   Assunto sem segmento, ou sem página candidata, vira `NAO_RESPONDIVEL` **sem gastar IA**.
8. **Resolução** (J-7): o usuário responde no sistema; a primeira tentativa, e só ela, entra em M-1 (D-78).

### 5.3 Os vereditos

- `RESPONDIVEL`: o material, nas páginas citadas, sustenta a alternativa do gabarito.
- `PARCIAL`: sustenta parte (a alternativa tem dois pontos e o material só cobre um).
- `NAO_RESPONDIVEL`: não sustenta; o campo `falta` descreve o que faltou.

Vereditos são imutáveis (D-73; o vigente é o de maior id). A IA indisponível **não é veredito** (D-75). Questões
anuladas, sem gabarito, sem texto, com OCR duvidoso ou fora do edital **saem do denominador e são declaradas à
parte** (D-70, D-71), para nunca "mentir com número verdadeiro".

### 5.4 Medições da leitura (prova da FCC)

[Medido, prova id 2, Analista Judiciário, Infraestrutura de TI, tipo 4, 60 questões, imagem]

- Questões separadas: 60 de 60 registradas; **56 com texto** e 4 sem texto (OCR não leu o número ou as alternativas).
- Gabarito: recorte correto (cargo e tipo) e 60 respostas casadas.
- 39 questões avaliáveis, 10 com OCR duvidoso, 7 fora do edital, 4 sem texto.
- 19 atribuições marcadas `REVISAR`.
- Defeitos conhecidos da leitura: o cargo da capa saiu como "s Vagos de" (regex), banca e ano vieram vazios, o
  rodapé da página entrou no texto de duas questões recuperadas, e uma questão ficou colada ao fim da anterior.

---

## 6. Estado atual do projeto

[Documentado, `PROGRESSO.md`]

| Sprints | O quê | Situação |
|---|---|---|
| 1–6 | Ambiente e schema; cadastro e importação; registrar sessão; escada e revisão; frente de estudo; plano de turno | feitas |
| 7–8 | Métricas M-1..M-4 e banco de erros; simulado por disciplina e backup testado (backend) | feitas (backend); as telas vieram nas sprints seguintes |
| 9–16 | Frontend (Hoje, Recuperar, Registrar, Assuntos, Erros, Progresso, Ajustes); integração externa com o planejamento (10); correções, multi-edital e app (16) | feitas |
| 17 | Uso real no Pi, 4 a 8 semanas antes de qualquer funcionalidade nova | em andamento |
| 18 | Concurso-alvo e edital lido por IA | em andamento |
| 19 | Material preparado pelo próprio sistema (o worker da §4) | em andamento |
| 20–23 | Diagnóstico de edital; reta final; peso por incidência da banca; registro de baixo atrito | não começaram |
| 24 | Prova e gabarito (§5) | em andamento: itens 24.1 a 24.6 prontos e implantados no Pi; falta a tela (24.7) e a conferência manual de vereditos (24.8) |

Há 277 testes automatizados no SGE (todos passando) e ~170 no planejamento. O sistema roda no Pi.
[Inferência] O projeto está, portanto, **adiantado em engenharia de domínio e atrasado em validar a utilidade
do material** que sustenta o estudo; a Sprint 17 ("uso real") ainda não terminou.

---

## 7. O resultado do ensaio da FCC

### 7.1 Placar

[Medido, relatório da prova id 2] O concurso-alvo no SGE **é o mesmo edital da prova** (TJ-CE, Analista Judiciário,
Infraestrutura de TI). Portanto a prova é uma régua justa para o material montado.

| Das 39 questões avaliáveis | Quantas |
|---|---|
| Respondíveis | **6** (15%) |
| Parciais | 5 |
| Não respondíveis | **28** (72%) |

### 7.2 Por disciplina

| Disciplina | Avaliáveis | Respondíveis | Parciais | Não respondíveis |
|---|---|---|---|---|
| Língua Portuguesa | 8 | 3 | 1 | 4 |
| Matemática, Estatística e Raciocínio | 3 | 0 | 1 | 2 |
| Direito Administrativo | 1 | 0 | 1 | 0 |
| Conhecimentos Específicos (TI) | 27 | 3 | 2 | 22 |

### 7.3 O assunto mais furado

"**Infraestrutura e redes**: servidores, virtualização, contêineres, modelos OSI e TCP/IP, protocolos, VLAN, NAT,
VPN" recebeu **15 questões** da prova; 14 não respondíveis e 1 parcial. As faltas registradas pelo julgador mostram
que o material trouxe só OSI, VLAN, IP e NAT, e que a prova cobrou: Disaster Recovery (RPO/RTO), ARP spoofing e
DAI, RSTP, BGP e LOCAL_PREF, IPv6 ULA, NTP, PoE, IKEv2 e TLS 1.3, hiperconvergência, clusters de alta
disponibilidade, Zabbix, DiffServ, Active Directory e LDAP, estratégias de backup imutável.

### 7.4 Como ler este resultado, honestamente

- **É uma prova, de uma banca, com 60 questões.** Não é uma estimativa estatística da cobertura do edital inteiro.
- **O veredito é de uma IA** e **não foi conferido à mão** (a conferência de ~20 vereditos é o critério de pronto da
  Sprint 24; o dono não a fez ainda). O julgador pode estar rigoroso demais.
- **A atribuição de questão a assunto também é de IA** e 19 foram marcadas para revisão. Se a atribuição está errada,
  o veredito é julgado contra o material do assunto errado (causa E, §8).
- Duas questões (Q21 e Q42) foram reconstruídas por uma regra de recuperação de número perdido e o rodapé de página
  entrou no texto delas; o `NAO_RESPONDIVEL` delas pode ser falso negativo.

---

## 8. Diagnóstico: onde o conteúdo se perde

### 8.1 Método

[Medido] Para cada uma das 33 questões não respondíveis ou parciais, o campo `falta` do veredito diz o conceito
que as páginas oferecidas não ensinavam. Cada conceito foi **buscado no índice completo** (sem filtro de curso).
Causas: **A** o acervo não tem · **B** tem mas a página está vazia ou o arquivo não foi indexado · **C** tem e
está no índice, mas a busca do assunto não o trouxe · **D** foi trazido mas a IA ou o fatiamento o descartou ·
**E** a questão foi atribuída ao assunto errado · **F** depende de algo que não é material (texto-base ou gráfico
da própria prova).

**Limite do método:** "tem" significa que o termo aparece em páginas do índice. **Não se leu o texto dessas
páginas**: que elas ensinem o conceito é indício forte, não prova.

### 8.2 Resultado

| Causa | Questões | N |
|---|---|---|
| A. acervo não tem | 22 (restore SQL Server), 23 (PgBouncer), 39 (NTP / Kiss-o'-Death), 34 (DAI; ARP spoofing existe) | 4 (12%) |
| F. depende da prova | 2 (texto de Sêneca), 12 (gráfico) | 2 |
| Assunto sem nenhum segmento, e o acervo tem o tema | 4, 7, 8 (flexão verbal, morfossintaxe) | 3 |
| C/D. o acervo tem, a cadeia não trouxe | 13, 14, 28, 29, 30, 33, 35, 36, 37, 38, 40, 41, 42, 46, 47, 49, 54, 56, 59 | 19 |
| A verificar (conceito não buscado) | 6, 21, 24, 27, 45 | 5 |

Páginas no índice, por conceito [Medido]: RPO e RTO 63 · RSTP 116 · spanning tree 147 · BGP 110 · Zabbix 54 ·
DiffServ 53 · DSCP 31 · DKIM 37 · DMARC 22 · Dockerfile 165 · Kubernetes 725 · LDAP 513 · Active Directory 364 ·
hiperconvergente 14 · endereços fc00 (IPv6) 12 · checksum 162 · função hash 385 · supply chain 81 ·
"razão e proporção" 291 · "variação percentual" 324. O acervo traz cursos de TI completos
("Redes e Segurança" de quatro preparações diferentes, "Especialidade TI").

**Conclusão [Inferência apoiada em Medido]:** no máximo 4 das 33 (12%) são ausência real do acervo. Pelo menos
22 são falha da cadeia. **Nenhuma é OCR do acervo** (só 1,8% das páginas estão vazias).

### 8.3 O mecanismo, reproduzido offline

[Medido: o módulo `acervo.py` executado sobre o índice real, com o nome exato do assunto]

1. A consulta vira 12 termos em OU: `infraestrutura, redes, servidores, virtualizacao, conteineres, modelos, osi, tcp,
   protocolos, vlan, nat, vpn`. Palavras comuns (*infraestrutura, redes, modelos, protocolos*) fazem a consulta casar
   **63.013 páginas, 24% do acervo**. O BM25 não distingue o tema.
2. Fica o topo de 150 páginas, depois 6 arquivos, depois 12 páginas por arquivo: **no máximo 72 páginas** para um
   assunto que, pela prova, abrange mais de 20 subtemas.
3. O filtro por disciplina restringe a busca a 6 "cursos afins". **Os cursos que realmente ensinam redes para este
   cargo** (as pastas "Redes e Segurança" de SEFAZ-CE 2026, TCE-SC e TSE) **não entram no filtro**: a disciplina
   do edital é "Conhecimentos Específicos – Ciências da Computação", que não compartilha radical com "Redes e
   Segurança". Entram duas aulas genéricas de "Redes de Computadores" e três do curso da Área Fiscal.
4. A IA só pode escolher o que recebeu. O segmento ensina OSI/VLAN/IP e o resto some. O julgador depois reporta a
   falta, corretamente.

Os três assuntos de português (flexão verbal, morfossintaxe) nem chegaram a ter segmento: foram tratados como sem
material, embora o acervo tenha dezenas a centenas de páginas sobre eles.

### 8.4 O que isso diz sobre o desenho

[Inferência]

- O defeito é de **granularidade**: um assunto largo do edital vira uma consulta e um orçamento de ~72 páginas.
  Nenhuma etapa lista os subitens que o assunto realmente cobra nem confere se cada um chegou ao material.
- Cada elo, isoladamente, cumpriu seu contrato (a busca devolveu o top-N, a IA escolheu entre o que viu, o julgador
  apontou a falta). **Falta um elo que pergunte "o que este assunto cobra, e isso chegou?"**
- Não existe medida de cobertura **por subitem**. `PRONTO` quer dizer "existe segmento".
- A prova só enxerga os subitens que sorteia; não mede o edital todo.

---

## 9. Pontos fracos conhecidos

Cada ponto com evidência e confiança (**alta** = visto no código ou medido; **média** = inferido; **baixa** =
suspeita).

| # | Ponto fraco | Evidência | Confiança |
|---|---|---|---|
| 1 | Assunto largo do edital vira uma única consulta e um único orçamento | §8.3; edital plano | alta |
| 2 | Orçamento fixo de 6 arquivos × 12 páginas, independente da amplitude do assunto | `acervo.py` | alta |
| 3 | Consulta por palavras comuns do nome do assunto, em OU; 24% do índice casa | medido | alta |
| 4 | Filtro de curso por radicais de nome de pasta exclui os cursos certos | medido para este caso | alta |
| 5 | Rodada 2 (acervo inteiro) só roda se a IA recusar a rodada 1 | `worker.py` | alta |
| 6 | "Na dúvida, deixe de fora" na triagem; `descartes` sempre vazio | `triagem_ia.py`, `worker.py` | alta |
| 7 | Validador confere forma, não se o material ensina o subitem | `montar_bloco.py` | alta |
| 8 | Lacuna nunca reabre sozinha; 2 falhas de validação viram lacuna | `worker.py` | alta |
| 9 | `PRONTO` só significa "existe segmento"; sem medida de cobertura por subitem | `MaterialService` | alta |
| 10 | Mapa de 45 páginas contado a partir da 1ª ocorrência; resto do arquivo some para a IA | `acervo.py` | alta |
| 11 | Sem OCR no acervo; sem contagem de páginas vazias; arquivos com erro ficam fora sem registro | `indexador.py` | alta (efeito: 1,8%) |
| 12 | Remoção de boilerplate a 60% pode apagar conteúdo legítimo em PDFs curtos; some linha só numérica | `indexador.py` | média |
| 13 | Extração em duas colunas é heurística; tabelas podem sair embaralhadas | `indexador.py` | média |
| 14 | 4 min/página fixo, inclusive para questões comentadas e lei seca | `montar_bloco.py` | alta |
| 15 | Sem bookmarks na maioria dos PDFs: cortes "no meio de seção", sumário vazio | log do indexador | média |
| 16 | `fingerprint` por nome de arquivo; PDFs homônimos podem colidir | `montar_bloco.py` | média |
| 17 | O julgador (IA) pode ser rigoroso demais; vereditos não conferidos à mão | §7.4 | média |
| 18 | A atribuição de questão a assunto (IA) pode errar, e o veredito herda o erro | §7.4 | média |
| 19 | Metadados da capa da prova (cargo, banca, ano) mal extraídos | medido | alta (baixo impacto) |
| 20 | Dependência de cota de IA: preparar tudo de novo é caro | ADR-045 | alta |
| 21 | Teste de acento: a consulta é sem acento; o texto indexado mantém acento | **não verificado** | baixa; na prática os termos acentuados casaram nos testes |

---

## 10. Sugestões já apresentadas (Fase 2)

Nenhuma decidida. Para cada uma: o que resolve, o que custa, o risco.

**S1. Buscar por subitem, não pelo nome inteiro do assunto.** Decompor cada assunto em uma lista curta de conceitos
(uma vez, por IA, gravada) e consultar cada conceito. Resolve #1, #2, #3. Custo: uma chamada de IA por assunto
(uma vez) e uma estrutura nova. Risco: **mudança de domínio** (o modelo hoje é plano); a lista de conceitos pode
inventar tópicos que o edital não cobra. Mitigação: gerar a lista a partir do texto do edital, não do conhecimento
geral da IA, e permitir revisão.

**S2. Escolher os cursos sem depender de radical de nome de pasta.** Resolve #4. Custo baixo. Alternativas:
classificar cada pasta do acervo por disciplina uma vez (IA ou manual); ou ranquear por densidade do termo em vez de
filtrar.

**S3. Orçamento proporcional ao assunto.** Substituir 6 × 12 por um orçamento que cresça com o número de
subitens, e consultar o acervo inteiro mesmo quando a rodada filtrada "deu certo". Resolve #2, #5, #10. Custo:
mais páginas à IA (mais cota); mais segmentos por assunto.

**S4. Medir cobertura por subitem para todo o edital.** Um relatório "subitem → páginas que o ensinam", com
`PRONTO` redefinido. Resolve #9 e dá ao dono a certeza que ele pediu. Custo: o mais alto (domínio + planejamento + tela).
Tensão: `00_PRODUTO.md` §8.3 rejeita "cobertura do edital como progresso" (cobertura não é aprendizado). A medida
proposta é de **adequação do material**, não de progresso do usuário; é preciso dizer isso explicitamente.

**S5. Relatório do que o acervo não tem.** Lista, por subitem, o que não existe nas apostilas, para o dono saber o
que obter. Resolve a causa A (12%). Custo baixo, depende de S4 ou de uma varredura simples.

**S6. Validador que confira que cada subitem tem páginas que o ensinam**, trocar "na dúvida deixe de fora" por um
critério que não perca teoria, e registrar `descartes`. Resolve #6, #7. Custo: mais uma chamada de IA por
subitem (cota); risco de aceitar páginas de menção de passagem.

**S7. Atribuição de questão por conceito**, não só por assunto, e divisão de assuntos grandes (prevista no
domínio, §3.2.2, D-33/D-34). Resolve #18 e reforça S1. Custo: moderado.

**S8. OCR do acervo** (reaproveitando o OCR da leitura de provas). Resolve #11 (1,8%). **Baixa prioridade**:
o diagnóstico não aponta isso como causa relevante.

**S9. Reabrir lacunas automaticamente** quando o acervo, o índice ou o algoritmo mudarem (#8). Custo baixo.

**S10. Métricas de qualidade da própria cadeia**: contagem de páginas vazias, arquivos fora do índice, tempo por
etapa, e a conferência manual de ~20 vereditos como linha de base.

Ordem sugerida até aqui [Inferência]: **S10 → S2 → S3 → S1/S7 → S4 → S5**, com S6 e S9 junto. É o ponto que mais
precisa de revisão (pergunta (b) da §13).

---

## 11. Restrições que qualquer proposta tem de respeitar

1. **Escopo**: o dono tem 3 a 5 horas por semana para construir; o escopo é o recurso escasso.
2. **IA é cara**: usa a cota da assinatura do dono. O fluxo de estudo do dia a dia não usa IA; só a preparação. Uma
   proposta que triplique chamadas por assunto precisa se justificar. Hoje há 97 assuntos num edital; outros editais
   virão.
3. **Hardware**: Raspberry Pi 4B, 4 GB, compartilhado com outros serviços; worker com teto de 768 MB e 1,5 CPU;
   OCR de 10 páginas ≈ 5 minutos. Busca vetorial local grande exige cuidado de memória.
4. **Documento primeiro, código depois**: mudança de domínio (por exemplo, assunto com subitens) começa por
   `01_DOMINIO.md`, ADR e documento técnico da sprint, e só depois por código.
5. **Dois repositórios, sem escrita cruzada**: mudar o planejamento exige pedido explícito do dono, caso a caso.
6. **Um usuário, sem multiusuário** (ADR-040).
7. **Proibido otimizar o agendador de revisão** (`00_PRODUTO.md` §3.2): a evidência é que o cronograma exato
   importa pouco.
8. **Honestidade de métrica** (`00_PRODUTO.md` §9.3, "nunca mentir com número verdadeiro"): qualquer medida nova de
   cobertura precisa declarar denominador, o que ficou de fora e o que ela não mede.
9. **A IA que ajuda a construir é a mesma que julga**: cuidado com vieses de autoavaliação; a conferência manual do
   dono é a defesa.

---

## 12. Alternativas que o revisor pode querer considerar

Listadas sem recomendação; algumas contradizem sugestões da §10.

- **Busca semântica local** (embeddings) em vez de, ou além de, FTS5/BM25, com reranqueamento. Resolve parte do
  problema das "palavras comuns" (§8.3), mas exige gerar embeddings de 264 mil páginas num Pi de 4 GB, ou no PC e
  copiar. Qual o custo real e o ganho sobre BM25 com consulta por subitem?
- **Curadoria por curso/aula em vez de por página.** O acervo vem de cursos com aulas por tema (por exemplo,
  "Redes e Segurança, aula 04: RSTP"). Em vez de buscar páginas, mapear **aulas** a assuntos (a pasta e o nome do
  arquivo já dizem muito) e entregar a aula inteira.
- **Decompor o edital em conceitos uma única vez** com revisão do usuário, e tratar esses conceitos como a unidade
  de planejamento, no lugar do assunto largo.
- **Usar provas passadas como régua permanente** de cobertura (já iniciado): cada prova nova mede o material do
  assunto, e as questões `NAO_RESPONDIVEL` alimentam uma lista de lacunas por conceito.
- **Reduzir o número de assuntos** (assuntos maiores, menos segmentos) em vez de aumentar a granularidade.
- **Inverter o fluxo**: em vez de "assunto → buscar material", partir de "material (aulas do acervo) → mapear a
  assuntos", e então ver o que sobrou sem cobertura.
- **Aceitar a lacuna e comprar/obter o conteúdo que falta**, tratando a ausência real (causa A) como o único
  problema de acervo, e consertar o resto na cadeia.

---

## 13. O que pedimos ao revisor

Respostas curtas e fundamentadas ajudam mais do que uma lista longa.

**(a) Diagnóstico.** A causa dominante (granularidade + orçamento fixo + filtro de curso) está bem identificada? Há
um erro de método na §8 (por exemplo, "tem" medido só por aparição do termo)? O que mais você mediria, com
quais dados, antes de mexer em qualquer coisa?

**(b) Ordem.** A ordem sugerida na §10 (S10 → S2 → S3 → S1/S7 → S4 → S5) é a certa? O que você faria primeiro
para obter o maior ganho com menos esforço e menos cota de IA?

**(c) Raiz.** Existe uma mudança de desenho que evite o problema pela raiz, em vez de remendar a busca (por exemplo,
as alternativas da §12)? Qual seria o custo de migração?

**(d) Superdimensionado.** O que na cadeia atual (ou nas sugestões) é complexidade desnecessária para um usuário e
3 a 5 horas por semana?

**(e) Métrica de cobertura honesta.** Que definição de "cobertura por subitem" é operacionalizável e não mente?
Como evitar que ela vire o que o produto proíbe (cobertura como progresso)? Como validar a métrica sem depender de
uma IA julgando a si mesma?

**(f) IA como juiz.** O julgador e o atribuidor são IAs. Como calibrá-los com poucos dados (uma prova, ~20 vereditos
conferidos à mão)? Há risco de o julgador ser sistematicamente rigoroso?

**(g) Riscos que não vimos.** Pontos cegos, em especial os que só aparecem no uso (meses de estudo com material
incompleto), na manutenção (acervo muda, editais mudam) ou na dependência de cota de IA.

**(h) O que você não recomendaria fazer.**

---

## 14. Glossário e apêndices

### 14.1 Glossário

- **Acervo**: o conjunto de apostilas em PDF do dono (2.639 arquivos).
- **Assunto**: unidade de estudo do edital; plano, sem subitens.
- **BM25**: função de ranqueamento de busca textual usada pelo FTS5.
- **Cobertura (do edital)**: lista de assuntos que um concurso cobra (`concurso_assunto`).
- **D-xx**: regra de domínio numerada (85 no total).
- **Escada de revisão**: sequência de intervalos de revisão de um assunto.
- **FTS5**: módulo de busca textual completa do SQLite.
- **Lacuna de material**: registro de que o acervo não ensina um assunto (ou parte dele), com motivo.
- **M-1..M-4**: métricas de sucesso do produto.
- **Planejamento**: o outro sistema (Python) que prepara o material e lê provas.
- **PRONTO / SEM_MATERIAL / A_PREPARAR**: estados derivados do material de um assunto.
- **Segmento**: pedaço de material de ~60 minutos para um assunto.
- **SGE**: o sistema de gestão de estudos (Java/Spring).
- **Veredito**: resultado do julgamento de uma questão (RESPONDIVEL, PARCIAL, NAO_RESPONDIVEL).

### 14.2 Parâmetros numéricos da cadeia

| Parâmetro | Valor | Onde |
|---|---|---|
| Hits considerados por assunto | 150 | `acervo.py` |
| Arquivos por assunto | 6 | `acervo.py` |
| Páginas listadas por arquivo | 12 | `acervo.py` |
| Margem do mapa | 3 páginas | `acervo.py` |
| Janela máxima do mapa | 45 páginas | `acervo.py` |
| Sumário (bookmarks) | nível ≤ 2, até 40 | `acervo.py` |
| Radical de curso afim | 6 letras | `acervo.py` |
| Tentativas da IA por assunto | 2 | `worker.py` |
| Assuntos por ciclo; ciclo | 10; 300 s | `worker.py` |
| Boilerplate removido | linha em ≥ 60% das páginas | `indexador.py` |
| Alvo de segmento; tolerância | 60 min; ±25% | `montar_bloco.py` |
| Minutos por página | 4 (fixo) | `montar_bloco.py` |
| Páginas oferecidas ao julgamento | ~6 por questão | `prova_julgamento.py` |
| Lote de julgamento; por ciclo | 5; 20 questões | `prova_julgamento.py` |

### 14.3 Dados da prova (33 questões não respondíveis ou parciais)

Legenda de causa: A acervo não tem · F depende da prova · S assunto sem segmento · C/D acervo tem e a cadeia não
trouxe · ? a verificar. Veredito: NR = não respondível, P = parcial.

| Q | Vered. | Assunto atribuído (resumo) | Conceito que faltou (resumo) | Causa |
|---|---|---|---|---|
| 2 | NR | Compreensão e interpretação de texto | Texto-base da própria prova | F |
| 4 | NR | Flexão nominal e verbal | (assunto sem segmento) | S |
| 6 | P | Pronomes | Pronome oblíquo átono como objeto direto | ? |
| 7 | NR | Morfossintaxe | (assunto sem segmento) | S |
| 8 | NR | Morfossintaxe | (assunto sem segmento) | S |
| 12 | P | Estatística descritiva | Gráfico de barras da prova | F |
| 13 | NR | Raciocínio verbal, matemático... | Razão e proporção, divisão proporcional | C/D |
| 14 | NR | Estatística descritiva | Variação percentual | C/D |
| 21 | NR | Infraestrutura e redes | Gestão de mudanças, análise de riscos | ? |
| 22 | NR | Bancos de dados | Backup e restore no SQL Server | A |
| 23 | NR | Bancos de dados | Pool de conexões, PgBouncer | A |
| 24 | P | Lei 14.133/2021 | Um artigo específico da lei | ? |
| 27 | P | Segurança da informação | Desvantagem do UTM (ponto único de falha) | ? |
| 28 | NR | Git, CI/CD, DevOps, DevSecOps | Segurança da cadeia de suprimentos | C/D |
| 29 | NR | Segurança da informação | Funções hash e checksums | C/D |
| 30 | NR | Segurança da informação | SPF, DKIM, DMARC, TLS 1.2+ | C/D |
| 33 | NR | Infraestrutura e redes | DRP, RPO e RTO | C/D |
| 34 | NR | Infraestrutura e redes | ARP spoofing e DAI | A (DAI) |
| 35 | NR | Infraestrutura e redes | RSTP (802.1w) | C/D |
| 36 | NR | Infraestrutura e redes | BGP-4, LOCAL_PREF | C/D |
| 37 | P | Infraestrutura e redes | IPv6 ULA | C/D |
| 38 | NR | Infraestrutura e redes | Proteção de dados: snapshots, imutabilidade | C/D |
| 39 | NR | Infraestrutura e redes | NTP, Kiss-o'-Death | A |
| 40 | NR | Infraestrutura e redes | PoE, cabeamento de par trançado | C/D |
| 41 | NR | Infraestrutura e redes | IKEv2/MOBIKE, TLS 1.3 | C/D |
| 42 | NR | Infraestrutura e redes | Infraestrutura hiperconvergente | C/D |
| 45 | NR | Infraestrutura e redes | Clusters ativo-passivo, failover | ? |
| 46 | NR | Infraestrutura e redes | Zabbix e seus componentes | C/D |
| 47 | NR | Infraestrutura e redes | QoS: DiffServ, IntServ | C/D |
| 49 | NR | Computação em nuvem | Modelos de implantação da nuvem | C/D |
| 54 | NR | Computação em nuvem | Dockerfile, CronJob do Kubernetes | C/D |
| 56 | NR | Infraestrutura e redes | Active Directory e LDAP | C/D |
| 59 | NR | Segurança da informação | Segurança de e-mail: SPF, DKIM, DMARC | C/D |

**Observação sobre a atribuição.** Vinte e três das 33 questões foram para só quatro assuntos largos:
"Infraestrutura e redes" (15), "Segurança da informação" (4), "Computação em nuvem" (2) e "Bancos de dados" (2). Vários
conceitos acima (DRP, backup imutável, gestão de mudanças, Zabbix, hiperconvergência, AD/LDAP) são, num edital de
TI, normalmente tópicos próprios. [Hipótese] Parte do problema de "assunto largo" é que o edital agrupou muita coisa
num só item; a pergunta (c) da §13 toca nisso.

### 14.4 Documentos de origem

No repositório do SGE: `especificacao/00_PRODUTO.md`, `01_DOMINIO.md` (§3.2, §3.11, D-xx), `02_JORNADAS.md` (J-6, J-7),
`03_INVARIANTES.md`; `docs/00A_ADR.md` (ADR-030, 038, 040, 044, 045, 046); `docs/SPRINT-24-PROVA-GABARITO.md`;
`docs/AUDITORIA-CADEIA-MATERIAL.md`; `PROGRESSO.md`. No repositório do planejamento: `docs/arquitetura-integracao-
planejamento-sge.md`, `integracao/acervo.py`, `worker.py`, `triagem_ia.py`, `dias/montar_bloco.py`,
`indice/indexador.py`. Se a revisão precisar de algum trecho, o dono pode fornecê-lo.
