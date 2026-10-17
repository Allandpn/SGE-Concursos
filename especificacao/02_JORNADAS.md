# 02 — JORNADAS

Como o sistema é usado, momento a momento. **Precedência 3.**
Subordinado a `00_PRODUTO.md` e `01_DOMINIO.md`.

| Campo | Valor |
|---|---|
| Versão | 1.12.0 |
| Data | 2026-10-15 |
| Status | Vigente |
| Documento anterior | `01_DOMINIO.md` |

---

## 0. Escopo

Este documento define **a sequência de passos** de cada operação e **quanto ela
pode custar em segundos**. Não define cor, componente, tipografia nem layout —
isso é do documento de interface, que vem depois.

A regra que ele existe para cumprir é uma só, e é de vida ou morte
(`00_PRODUTO` §9.2):

> **Se registrar custar caro, o registro não acontece. Sem registro não há
> dado, e sem dado nenhuma métrica deste projeto pode ser calculada.**

Tudo aqui é subordinado a esse teste.

---

## 1. O contexto físico

| | |
|---|---|
| **Estuda** | tablet — PDF, apostila, legislação |
| **Registra** | computador (principal) ou celular |
| **Resolve questões** | plataforma externa, no tablet ou no computador |
| **Prioridade de construção** | **interface web de computador primeiro.** Celular depois, se não custar complexidade |

### 1.1 A separação de aparelhos ajuda, e isso não é acidente feliz

A regra da produção obrigatória (`00_PRODUTO` §5.2.1) exige **fechar o material
antes de tentar lembrar**. Num aparelho só, "fechar" é fechar uma aba — e a aba
volta com um toque, o que torna a regra dependente de força de vontade.

Com tablet e computador separados, fechar o material é **largar um objeto e
virar para outro**. O protocolo passa a ser físico em vez de mental.

**Consequência:** o sistema **não** deve tentar exibir o material de estudo.
Nunca. Isso não é só escopo negativo (`00_PRODUTO` §8.5) — é que exibir o
material destruiria o único mecanismo que garante a recuperação de verdade.

### 1.2 Prioridade de plataforma

Web de computador é o alvo. Celular fica para depois — e o documento de
interface deve evitar decisões que **impeçam** o celular mais tarde (largura
fixa, dependência de teclado, alvo de clique minúsculo), sem pagar hoje o custo
de otimizar para ele.

**Atualização (2026-10-14):** o uso passa a ser **app no celular e no
tablet, além da web**. A mesma SPA vira app instalável (`docs/00A_ADR.md`
ADR-038) — um código só, sem projeto nativo. Consequência para as telas:
alvo de toque de no mínimo 44 px, nenhuma ação que dependa de passar o mouse,
e layout que funciona em largura de celular. O registro de sessão no
celular passa a ser caminho de primeira classe, não de exceção.

**Atualização (2026-10-15):** além da SPA, existe um **app Android nativo**
(`docs/00A_ADR.md` ADR-041). Ele implementa as mesmas sete telas e as mesmas
regras vinculantes deste documento — esta especificação continua sendo a
única definição das jornadas. O que só o app faz: o lembrete diário (§2.2) e
guardar o rascunho de um registro mesmo se o sistema fechar o app.

---

## 2. A rotina real

Derivada de `01_DOMINIO` §7.3, sem invenção.

```
SEGUNDA A SEXTA · 1 h 30
  1. Abre o sistema. A tela Hoje mostra o turno inteiro, item a item:
     as 4 ou 5 recuperações e o bloco de conteúdo.
  2. Clica num item quando fizer. Abre a tela de registro daquele tipo.
  3. Recuperação: registra logo após cada uma.        ⟵ NA HORA
  4. Conteúdo: registra ao terminar o bloco.          ⟵ NO FIM

SÁBADO E DOMINGO · 3 h
  Igual, com dois blocos de conteúdo.
```

### 2.1 O turno é uma lista, não uma sequência

O sistema mostra **tudo do dia de uma vez** e o usuário escolhe a ordem conforme
o dia está rendendo. A ordem sugerida — fila de recuperação antes do conteúdo
(`01_DOMINIO` §7.6) — é **apresentação, nunca imposição**.

Forçar ordem custaria caro e não compraria nada: nada é obrigatório
(`01_DOMINIO` D-14), e um sistema que insiste em qual assunto pegar primeiro é
um sistema que se fecha nos dias ruins. A ordem certa é a que faz você abrir o
sistema.

O sistema **não policia**, mas também não esconde a razão: a recomendação de
fazer a fila primeiro aparece como nota curta, uma vez, não como travamento.

**Por que registrar na hora é diferente de fazer na ordem:** o resultado da
recuperação precisa ser julgado logo após a tentativa, senão vira lembrança de
julgamento e apodrece (`00_PRODUTO` §5.2.1). Isso vale qualquer que seja a ordem
em que os itens foram feitos. O bloco de conteúdo é só tempo e tipo — espera sem
perder nada.

---

### 2.2 O lembrete do turno (app Android)

Constância é a terceira dor (`00_PRODUTO` §4), e um sistema que só existe
quando você lembra de abri-lo não ataca essa dor. O lembrete é o mínimo que
ataca sem virar cobrança:

1. **Opcional e desligado por padrão.** O usuário liga e escolhe o horário.
2. **Uma notificação por dia, no máximo.** Nunca uma segunda "porque você não
   abriu".
3. **Diz o que há para hoje, não o que falta:** "Hoje: 6 recuperações e 1
   bloco de leitura." Sem número de dias seguidos, sem "você está atrasado",
   sem vermelho (`00_PRODUTO` §8.2: nada de *streak*).
4. **Não lembra quem já estudou hoje:** se já existe sessão no dia, não há
   notificação.
5. **Servidor fora do alcance não vira alarme:** sem resposta do servidor, a
   notificação diz só que é a hora do turno, sem números.

---

## 3. As jornadas

Quatro. As duas primeiras acontecem sempre; a terceira toda semana; a quarta é a
que decide se o sistema sobrevive ao primeiro tropeço.

### J-1 · Primeiro uso

Uma vez. Precisa terminar em menos de uma hora, ou o projeto morre no cadastro.

1. **Importa um CSV** com as disciplinas e os ~260 assuntos, já classificados.
2. Confere o resumo da importação e confirma.
3. Define o teto da frente e o teto por disciplina, ou aceita os padrões
   (100 e 12).
4. O sistema monta a frente inicial a partir do backlog, pela **ordem** de cada
   disciplina (`01_DOMINIO` §3.2).

#### A importação por arquivo é requisito, não conveniência

Cadastrar 260 assuntos num formulário de um por vez, a 20 segundos cada, é uma
hora e meia de digitação antes de o sistema servir para alguma coisa. É a forma
mais garantida de o projeto morrer antes de começar — e o usuário já tem os
dados em planilha.

**Formato:**

```csv
disciplina,assunto,peso,ordem
Banco de Dados,Modelagem Conceitual de Dados (MER e DER),ALTO,1
Banco de Dados,Modelagem Lógica e Física de Dados,ALTO,2
Banco de Dados,Normalização e Dependências Funcionais,ALTO,4
```

| Coluna | Obrigatória | Regra |
|---|---|---|
| `id` | não | **Vazio → cria. Preenchido → atualiza aquele registro** |
| `disciplina` | sim | Criada se não existir, com `peso` `MEDIO` — o CSV é por assunto, não tem coluna de peso de disciplina; ajustável depois pela interface |
| `assunto` | sim | Único dentro da disciplina, comparado sem acento e sem caixa |
| `peso` | não | `ALTO` · `MEDIO` · `BAIXO`. Ausente → `MEDIO` |
| `ordem` | não | Inteiro. Ausente → ordem de aparição no arquivo |
| `dificuldadePercebida` | não | Inteiro 1..5. Ausente → `3` (meio da escala). Ajustável depois pela interface |
| `chaveExterna` | não | Opcional, única entre todos os assuntos (`01_DOMINIO` D-52). Identificador dado por um sistema de planejamento de estudos externo — o SGE guarda sem interpretar. É a chave de reimportação quando a linha não tem `id` (tabela acima), e é por ela que `segmentos.csv` acha o assunto (ver abaixo) |

#### Por que existe a coluna `id`

Sem ela, a importação tem um defeito silencioso e caro. Se a chave for
`disciplina + assunto normalizado` e o usuário corrigir um erro de digitação —
*"Bando de Dados"* para *"Banco de Dados"* — a chave muda, e a reimportação
**cria uma disciplina nova com todos os assuntos duplicados**. Como nada é
apagado (`01_DOMINIO` D-18), ele fica com a base suja e sem operação óbvia de
desfazer.

A correção é o **ciclo de ida e volta**: o sistema **exporta** no mesmo formato,
com `id` preenchido. Editar a planilha exportada e reimportar é seguro para
qualquer alteração, **inclusive renomear**.

| Situação | Comportamento |
|---|---|
| Linha sem `id` | Cria. Se já existir assunto com o mesmo nome na disciplina, recusa a linha |
| Linha com `id` conhecido | Atualiza nome, peso, ordem e dificuldadePercebida daquele registro |
| Linha com `id` inexistente | **Recusa o arquivo inteiro** — sinal de arquivo corrompido ou de outra base |
| Linha sem `id`, com `chaveExterna` de assunto já existente | **Atualiza** aquele registro — mesmos campos da linha com `id` (nome, peso, ordem, dificuldadePercebida); a disciplina não muda. Reordenar assuntos existentes no mesmo arquivo (trocar 1↔2) é aceito: a unicidade de ordem (`01_DOMINIO` D-48) vale para o resultado do arquivo, não para cada linha no meio do caminho. É o caminho da reimportação vinda do sistema de planejamento, que nunca conhece o `id` do SGE |
| Linha com `id` **e** `chaveExterna` | O `id` decide qual registro; a `chaveExterna` é gravada nele |
| Linha sem `id`, com `chaveExterna` ainda desconhecida, e já existe na disciplina um assunto **sem** chave externa com o mesmo nome (sem acento e sem caixa) | **Adota** aquele assunto: grava a `chaveExterna` nele e atualiza como acima, em vez de recusar por nome duplicado. É o caminho de convergência de um assunto criado pela análise de edital (J-5) quando o planejamento passa a mandar material para ele |
| Assunto que **não está no arquivo** ocupa a `ordem` que uma linha pede (o caso comum ao trocar de edital) | Vai para o fim da disciplina, na ordem em que estava, em vez de o arquivo ser recusado por ordem duplicada (`01_DOMINIO` D-48). Com o `concurso.json`, a cobertura do edital ainda o coloca depois dos assuntos cobertos (D-60) |

E, independentemente disso: **renomear assunto e disciplina tem que existir na
interface.** Corrigir um typo não pode exigir exportar, editar e reimportar.

**Três regras que evitam o desastre clássico da importação:**

1. **Valida tudo antes de gravar qualquer coisa.** O arquivo entra inteiro ou
   não entra. Importação parcial deixa o usuário sem saber o que aconteceu, e
   corrigir vira pior que refazer.
2. **Mostra o resumo antes de confirmar** — quantas disciplinas novas, quantos
   assuntos novos, quantos já existiam, quantas linhas recusadas e por quê.
3. **Nunca apaga.** Assunto que sumiu do arquivo permanece; remover é decisão
   explícita na interface, nunca efeito colateral de importação.
4. **A importação não divide assunto.** Dividir tem migração de dados própria e
   é operação de tela (`01_DOMINIO` §3.2.2).

O cadastro manual continua existindo, para o assunto avulso que aparece depois.

#### Segunda importação: segmentos de material

Sprint 10. Um segundo arquivo, próprio — a granularidade é outra: uma linha
por **segmento**, não por assunto (`01_DOMINIO` §3.7).

```csv
chaveExternaSegmento,chaveExternaAssunto,ordem,arquivo,paginaInicial,paginaFinal,tempoEstimadoMin
9c1e7a...,3f2a1c...,1,https://drive.google.com/...,1,12,60
b04f2d...,3f2a1c...,2,https://drive.google.com/...,13,25,60
```

| Coluna | Obrigatória | Regra |
|---|---|---|
| `chaveExternaSegmento` | sim | Identificador do próprio segmento, dado pelo sistema de planejamento — única entre todos os segmentos (`01_DOMINIO` D-53). É a chave de reimportação (ver abaixo) |
| `chaveExternaAssunto` | sim | Precisa bater com a `chaveExterna` de um assunto já importado. Sem correspondência, a linha é recusada |
| `ordem` | sim | Única dentro do assunto (`01_DOMINIO` D-50) — mas não é identidade, ver abaixo |
| `arquivo` | sim | Link ou identificador do material — o sistema guarda, nunca abre (§1.1) |
| `paginaInicial` / `paginaFinal` | não | Rastreabilidade até a fonte |
| `tempoEstimadoMin` | não | Estimativa de leitura, informativa |

Mesmo mecanismo `validar`/`confirmar` do J-1 — tudo-ou-nada, resumo antes de
confirmar, nunca apaga — só alimentado por um arquivo diferente. Chega ao SGE
via Google Drive + `rclone`, não upload manual (`docs/00A_ADR.md` ADR-037).

**Por que a chave é externa, e não o `id` do assunto:** o SGE só decide o
`id` no momento em que importa `assuntos.csv` — o sistema de planejamento
nunca saberia esse valor sem uma chamada de volta ao SGE, o que reabriria o
acoplamento direto que a topologia via Drive já evita. A chave externa nasce
do lado do planejamento e já viaja em `assuntos.csv`, então não depende de
nenhuma resposta do SGE.

**Por que o segmento também tem chave externa própria, e não usa `ordem`
como identidade de reimportação:** `ordem` é posição, não identidade. Se o
material for reordenado depois de já importado (um segmento novo inserido
no meio da sequência, empurrando os seguintes), reimportar identificando
"o segmento 3 deste assunto" faria o SGE achar que o segmento na posição 3
de antes é o mesmo da posição 3 de agora — e sessões antigas que já
apontavam para aquele segmento (`01_DOMINIO` D-51) passariam a se referir,
em silêncio, a um material diferente do que o candidato de fato estudou.
Com uma chave própria por segmento, reimportar reconhece "isto é o mesmo
pedaço de sempre, só mudou de posição/link" — a identidade nunca depende de
onde o segmento está na sequência atual. **Reimportar atualiza um segmento
existente (mesma `chaveExternaSegmento`) em vez de criar um novo — nunca
apaga** (`01_DOMINIO` D-18).

### J-2 · O turno de estudo — a jornada principal

É a §2 acima. O que ela exige do sistema:

| Momento | O que a tela precisa dar | Orçamento |
|---|---|---|
| Abrir | O plano do turno, pronto, sem cliques | < 2 s de carga |
| Cada recuperação | Duas etapas, sem campo de resultado na primeira | **≤ 15 s** de registro |
| Bloco de conteúdo | Assunto já preenchido pelo plano; tempo (já preenchido com a estimativa) e tipo | **≤ 20 s** |
| Lote de questões | Assunto, formato, feitas, certas, previsão, tempo | **≤ 25 s** |

O orçamento total de registro num turno de 1 h 30: **5 × 15 s + 20 s ≈ 1 min
35 s.** Menos de 2% do turno. Acima disso, o sistema começa a competir com o
estudo, que `00_PRODUTO` §1 proíbe.

### J-3 · O balanço semanal

Uma vez por semana, ~5 minutos, fora do turno de estudo.

#### A ordem das métricas não pode ser a ordem de importância

M-1 é a tese do produto e **é lenta**: com menos de 50 questões por semana, uma
janela de 7 dias nunca chega ao `n ≥ 100` que `00_PRODUTO` §7 exige para exibir
tendência. Colocá-la em primeiro lugar numa tela semanal é liderar com um número
que, por regra própria, não pode dizer nada.

As métricas de ciclo rápido são **M-2 e M-3**: acumulam uma observação por
recuperação, ou seja, 30 a 45 por semana. Elas têm o que dizer toda segunda.

Ordem da tela semanal:

1. **Estou retendo?** — M-2. Amostra suficiente, resposta real.
2. **Eu me conheço?** — M-3, com o **sinal**: superestimando ou subestimando.
3. **Estou melhorando?** — M-1, como número bruto acumulando, **sem seta e sem
   cor**, com a janela trimestral indicada: *"faltam N questões para a
   comparação valer"*.
4. **Estou em dia?** — M-4, pequeno, no rodapé (`00_PRODUTO` §7).

> **Semanal e trimestral são telas diferentes, não a mesma em ritmos
> diferentes.** A semanal responde *como foi a semana*; a trimestral responde
> *estou melhorando*. Misturar as duas é o que faz um número lento parecer
> estagnação.

E uma pergunta operacional: **a frente está saudável?** Quantos consolidaram,
quantos entraram, qual o atraso acumulado.

Se um assunto acumulou 4 `PARCIAL` seguidos, é aqui que o sistema sugere
dividi-lo (`01_DOMINIO` §3.2.1).

### J-4 · O retorno depois de sumir

**A jornada mais importante do documento**, e a que quase nenhum sistema de
estudo desenha.

Com 5 a 7 recuperações por dia, duas semanas fora produzem **70 a 100 revisões
vencidas**. O que acontece nesse reencontro decide se o sistema é usado por dois
anos ou abandonado no terceiro mês.

**O que o sistema não pode fazer:**

- mostrar "97 revisões atrasadas" em vermelho;
- listar as 97;
- calcular um percentual de aderência despencando;
- cobrar, culpar ou lamentar de qualquer forma.

**O que ele faz:**

```
Você esteve fora 16 dias. Sem problema — a escada não reinicia.

Hoje: 8 recuperações       ← o teto diário, nada além
Represado: 89              ← informado uma vez, sem alarde

Enquanto drena, o sistema não vai sugerir assunto novo.
Estimativa para normalizar: 11 dias.
```

Três decisões de projeto sustentam isso:

1. **O teto diário (`01_DOMINIO` §5.3) já resolve o volume.** A fila nunca é
   apresentada inteira.
2. **A escada não reinicia por atraso.** Revisão atrasada continua no nível em
   que estava; atraso não é fracasso, e a §5.1 só regride por **resultado**.
3. **O alerta de represamento vira conselho, não repreensão:** pare de abrir
   assunto novo até drenar. Com uma estimativa de quando termina, porque prazo
   visível é o que transforma acúmulo em tarefa.

#### O outro lado: o dia em que sobra tempo

O teto limita o que é **oferecido**, nunca o que é permitido (`01_DOMINIO`
D-40). Num dia de folga existe **puxar mais**, e são três coisas diferentes:

| Ação | Preço |
|---|---|
| **Drenar mais represado** | Nenhum. É a melhor coisa a fazer num dia de folga |
| **Mais um bloco de conteúdo** | Nenhum, respeitado o teto da frente |
| **Antecipar revisão futura** | **Encurta o espaçamento** — só dentro da janela de tolerância |

A terceira é a única com contraindicação, e o sistema precisa dizer isso na
hora, uma linha: *"esta revisão é para daqui a 62 dias; fazer agora encurta o
intervalo."* Fora da janela de 20% (`01_DOMINIO` §5.4), estudar continua
liberado — a sessão é registrada e conta, só não cumpre a revisão.

Puxar mais é **explícito**: o usuário pede. O sistema nunca aumenta a lista
sozinho porque o dia anterior rendeu.

---

### J-5 · Edital novo, e os editais de antes

Raro — algumas vezes por ano —, mas é quando o sistema decide para onde vão
os próximos meses. Não tem orçamento de segundos; tem orçamento de
**atenção**: a revisão da proposta é o momento em que um erro de leitura
ainda custa um toque, e não meses.

**Edital novo, pelo próprio sistema** (`01_DOMINIO` §3.9, ADR-043):

1. Em **Concursos**, toca "Novo edital" e escolhe o PDF.
2. O sistema lê o texto e mostra o que achou: nome do concurso, banca, data
   da prova (se o edital já fixou) e **a lista de cargos**. Leva até um ou
   dois minutos; dá para sair da tela e voltar — a análise fica guardada e
   continua de onde parou.
3. Escolhe o **cargo**.
4. O sistema separa disciplinas e assuntos daquele cargo e mostra a
   **proposta**, por disciplina:
   - o número de questões e o **peso** calculado (D-61) — ou "peso não
     determinado", quando o edital não traz o quadro;
   - cada assunto, na ordem do edital, marcado **novo** ou **já existe:
     <nome>** — o que já existe mantém escada e histórico;
   - no topo, o resumo: quantos novos, quantos em comum, quantos assuntos
     atuais ficam fora do edital (continuam como estão).
5. Revisa: desmarca o que não quer, troca o peso de uma disciplina, desfaz
   uma correspondência errada ("tratar como novo"), corrige nome e data da
   prova.
6. **Confirma.** Só aqui algo é gravado (D-62): disciplinas e assuntos novos
   nascem, os em comum recebem peso e ordem do edital, e o concurso vira o
   alvo (D-60).
7. **Pronto.** Os assuntos já estão no turno. O material de leitura é
   preparado sozinho, em segundo plano (`01_DOMINIO` §3.10): em Concursos, o
   concurso-alvo mostra "Material: 12 de 40 prontos · 3 sem material no
   acervo · 25 a preparar", e cada assunto sem material no acervo mostra o
   motivo no detalhe. Nada a fazer — é só acompanhamento.

A IA fica em **Ajustes → IA** (ADR-045): o token da **assinatura do Claude**
(gerado uma vez com `claude setup-token`) e o modelo, e, como segunda opção, a
chave e o modelo do **OpenRouter**. Segredos são cadastrados uma vez e nunca
mostrados de volta, só os últimos quatro caracteres. A mesma configuração serve
à leitura do edital e à preparação do material.

**Quando a assinatura para** (limite de uso da janela, token vencido), aparece
um aviso no topo de Hoje, em Concursos e em Ajustes, com o motivo e o botão
**Continuar pelo OpenRouter**. Nada troca sozinho: até o usuário decidir, o
material espera e o sistema tenta o Claude de novo a cada ciclo; quando ele
volta a responder, o aviso some.

Falhas, com o que fazer em cada uma: PDF sem texto (escaneado) → "este PDF
é imagem; envie a versão com texto"; IA indisponível ou sem chave
configurada → "Análise por IA indisponível: <motivo>", o resto do sistema
segue normal; resposta que não deu para entender → "Tentar de novo", sem
reenviar o arquivo.

**Os editais de antes.** A tela **Concursos** mostra o concurso-alvo (nome,
cargo, banca, data da prova, desde quando é o alvo) e, abaixo, o
**histórico**: cada concurso que já foi alvo, com o período, quantos
assuntos o edital cobria e quantos tem em comum com o atual. Qualquer um
pode ser **reativado** — a cobertura dele volta a valer (D-60), com
confirmação, porque muda peso e ordem.

**Edital vindo do planejamento pelo Drive** (J-1) continua aceito como
caminho legado (ADR-044): a importação registra o concurso a partir do
`concurso.json` e o ativa — aparece no mesmo histórico.

## 4. As telas

Oito. Cada uma justificada por uma jornada — tela sem jornada não entra.

| Tela | Serve a | Existe porque |
|---|---|---|
| **Hoje** | J-2, J-4 | A porta de entrada. O plano do turno como lista, e o gatilho de puxar mais (§4.3). No topo, o concurso-alvo e os dias até a prova (§4.4) |
| **Recuperar** | J-2 | As duas etapas da §5.2.1. É a tela mais usada do sistema |
| **Registrar sessão** | J-2 | Conteúdo, questões ou flashcards |
| **Assuntos** | J-1, J-3 | Cadastro em lote, peso, frente × backlog — agrupado por disciplina, cada uma com seu resumo (§4.5) |
| **Erros** | J-2, J-3 | Registro com causa e confiança; consulta por assunto |
| **Progresso** | J-3 | As quatro métricas, com as regras de n de `00_PRODUTO` §7 |
| **Ajustes** | J-1, J-3, J-5 | Tetos, limiares, intervalos; a chave e o modelo de IA |
| **Concursos** | J-5 | O concurso-alvo, o histórico de editais e a entrada de edital novo |

**Não existe tela de calendário.** O sistema diz o que fazer hoje; planejar a
semana é decisão que ele não toma (`00_PRODUTO` §8.5).

### 4.1 A tela "Recuperar", em detalhe

É a única cuja sequência é vinculante, porque ela **é** o mecanismo.

```
ETAPA 1                                    ETAPA 2
┌──────────────────────────────┐          ┌──────────────────────────────┐
│ Normalização e dependências  │          │ Normalização e dependências  │
│ funcionais                   │          │                              │
│ Banco de Dados               │          │ Você previu: vou reconstruir │
│ Nível 3 · previsto para hoje │          │                              │
│                              │   ───▶   │ Conseguiu reconstruir?       │
│ O que você espera?           │          │  [ Reconstruí ]              │
│  [ Vou reconstruir ]         │          │  [ Faltou um pedaço ]        │
│  [ Vai faltar um pedaço ]    │          │  [ Não veio ]                │
│  [ Não vai vir ]             │          │                              │
│                              │          │  + registrar um erro         │
│ Feche o material e           │          │                              │
│ reconstrua de memória.       │          └──────────────────────────────┘
│         [ Já tentei ]        │
└──────────────────────────────┘
```

Na etapa 2, os três botões usam as **âncoras** de `01_DOMINIO` §4.1 — não
"tudo / parte / nada", que é escala ruim porque *parte* vai de 10% a 90%:

| Botão | Abaixo, em texto menor |
|---|---|
| **Reconstruí** | os pontos principais vieram; faltou detalhe, não estrutura |
| **Faltou um pedaço** | a estrutura veio, mas um bloco inteiro não |
| **Não veio** | preciso reler antes de tentar de novo |

A âncora é **estrutura × detalhe**, que é respondível — quantidade lembrada não
é. E resolve a dúvida que a escala antiga produzia: *faltou pouco* é
**Reconstruí**, não *parte*.

A previsão da etapa 1 usa as mesmas três palavras, no futuro: *vou reconstruir /
vai faltar um pedaço / não vai vir*. Mesma régua nos dois momentos — é o que
torna a comparação da M-3 legível.

Regras vinculantes:

- **O resultado não existe na etapa 1.** Não escondido, não desabilitado: não
  está na tela. É o que impede responder por familiaridade.
- **A previsão não é editável na etapa 2.** Ela é mostrada — ver a própria
  previsão ao lado do resultado é o instante em que a calibração é percebida, e
  é o valor inteiro da M-3.
- Não existe botão "pular". Existe sair da tela: nada é obrigatório
  (`01_DOMINIO` D-14), e o sistema não precisa de um botão para dizer isso.

---

### 4.2 O erro se registra de qualquer lugar

Erro não pertence a um fluxo. Ele acontece resolvendo questões, lendo teoria,
numa recuperação — e às vezes horas depois, quando você percebe que tinha
entendido errado.

> **Registrar erro é ação global, disponível em qualquer tela.** Nunca uma etapa
> obrigatória de outro fluxo.

Quando invocada de dentro de uma sessão, ela **chega com o contexto preenchido**
— assunto e sessão de origem já vêm; o usuário completa descrição, causa e
confiança. Invocada do nada, pede o assunto.

Isso resolve duas coisas ao mesmo tempo:

1. O erro percebido fora de qualquer sessão tem onde entrar.
2. O orçamento de 15 s da recuperação fica protegido: o atalho para registrar
   erro é **oferta**, não passo. Quem não clicar termina em 15 s.

O momento de maior valor continua sendo logo após errar (`00_PRODUTO` §7.1) —
mas "logo após" não pode ser cobrado, ou vira campo pulado às pressas, que é
pior que campo vazio.

---

### 4.3 Onde vive o "puxar mais"

A mecânica da J-4 precisava de lugar, e não tinha. Ela fica **no fim da lista da
tela Hoje**, discreta, e só é oferecida quando existe algo a mais:

```
  ── fim do turno sugerido ──────────────────────

  Quer adiantar?
   · drenar mais represado (89)
   · outro bloco de conteúdo
   · antecipar revisão futura  ⚠ encurta o espaçamento
```

Três regras:

1. **Fica no fim, nunca no topo.** No topo, "89 represadas" vira ansiedade logo
   ao abrir — exatamente o que a J-4 existe para evitar.
2. **É sempre explícito.** O sistema nunca aumenta a lista sozinho porque ontem
   rendeu.
3. **A terceira opção carrega o aviso junto**, não escondido atrás de
   confirmação: antecipar revisão futura encurta o espaçamento
   (`01_DOMINIO` §5.4).

---

### 4.4 O concurso-alvo no topo de Hoje

Uma linha, discreta, acima do plano: **nome do concurso · cargo**, e abaixo
"prova em N dias" — ou "sem data de prova" quando o edital ainda não a
fixou; "prova hoje" no dia. Tocar abre **Concursos**. Sem concurso-alvo, a
linha convida: "Nenhum concurso-alvo — adicionar edital". Nunca contagem
regressiva em vermelho, nunca cobrança: é contexto, não alarme (mesmo tom
de J-4). Os dias vêm calculados pelo servidor.

### 4.5 Disciplinas em Assuntos

A lista de Assuntos abre **pelas disciplinas**: cada disciplina é um cartão
com o peso, quantos assuntos estão em backlog, na frente e consolidados.
Tocar no cartão mostra os assuntos dela, como antes. É a visão que J-3 pede
("a frente está saudável?") olhada por disciplina — sem métrica nova, só
contagem do que a fase já deriva (`01_DOMINIO` §8).

## 5. Os orçamentos de tempo

Requisitos, não metas. Medidos com cronômetro, no aparelho real.

| Operação | Orçamento | Campos visíveis |
|---|---|---|
| Registrar recuperação | 15 s | 2 (previsão, resultado) |
| Registrar bloco de conteúdo | 20 s | 3 (assunto, tipo, tempo) — tempo já preenchido (`01_DOMINIO` §7.7) |
| Registrar lote de questões | 25 s | 6 (assunto, formato, feitas, certas, previsão, tempo) |
| Registrar erro | 30 s | 3 (descrição, causa, confiança) |
| Cadastrar uma disciplina inteira | 2 min | lista colada + peso em bloco |

Estourar qualquer um desses é **defeito**, tratado com a mesma gravidade de um
cálculo errado.

### 5.1 O orçamento é de tempo do usuário, não da rede

Os segundos acima medem **ler, decidir e clicar**. Eles não podem ser consumidos
por espera de rede — e num Raspberry Pi doméstico atrás de uma VPN, a latência
existe: dentro de casa é desprezível, mas em rede móvel a ida e volta pesa.

> **A transição de tela é otimista.** O sistema muda o estado localmente e
> devolve a resposta em milissegundos; a persistência acontece atrás. O usuário
> nunca espera o servidor para continuar registrando.

Duas ressalvas honestas:

- **A falha tem que ser não destrutiva.** Se a gravação não completar, o dado
  digitado permanece na tela e reenviar é um clique. Otimismo que perde o
  registro do usuário é pior que espera.
- **Fila offline com repetição automática fica fora**, por ora. É maquinaria
  real — armazenamento local, ordem, conflito, reconciliação — e o frontend não
  tem etapa de build. Se o uso mostrar que a conexão cai com frequência, vira
  decisão própria, justificada, e não improviso.

O documento de interface é quem amarra isso.

---

## 6. Rastreabilidade

| Jornada | Telas | Regras que a governam |
|---|---|---|
| J-1 Primeiro uso | Assuntos, Ajustes | D-19, D-24, D-33, D-34, D-50, D-52, D-53 |
| J-2 Turno de estudo | Hoje, Recuperar, Registrar, Erros | D-01 a D-09, D-26 a D-29, D-36, D-51 |
| J-3 Balanço semanal | Progresso, Assuntos | M-1 a M-4, D-35 |
| J-4 Retorno | Hoje | D-15, D-20, §5.1 |
| J-5 Edital novo e histórico | Concursos, Hoje, Assuntos | D-58 a D-63 |

---

## 7. O que fica em aberto

- O texto exato da J-4. É o único lugar em que o tom do sistema é decisivo, e
  tom não se especifica em tabela.
- Se **Hoje** deve mostrar, ao lado de cada recuperação, há quantos dias o
  assunto não é tocado. Informa e motiva; também polui a lista mais usada do
  sistema.
- Qual o comportamento quando o usuário registra uma recuperação de um assunto
  que **não estava no turno** — conta normalmente (é recuperação espontânea,
  `01_DOMINIO` §3.4) ou pede confirmação?

**Resolvidas nas revisões:** ordem dos itens no turno (§2.1), momento de
registrar erro (§4.2), cadastro em massa (J-1), reimportação sem duplicar
(coluna `id`), divisão de assunto (`01_DOMINIO` §3.2.2), ordem das métricas no
balanço semanal (J-3), lugar do "puxar mais" (§4.3) e latência × orçamento
(§5.1).

---

## 8. Changelog

| Versão | Data | Mudança |
|---|---|---|
| 1.12.0 | 2026-10-17 | J-5: IA pela assinatura do Claude, OpenRouter como segunda opção; aviso quando a assinatura para, e a troca é sempre decisão do usuário (ADR-045) |
| 1.11.0 | 2026-10-17 | Pedido do usuário. J-5 ganha o passo 7: depois de confirmar, o material é preparado sozinho e Concursos mostra o andamento (`01_DOMINIO` §3.10). Chave e modelo de IA passam a ser cadastrados em Ajustes. O caminho pelo Drive vira legado (ADR-044) |
| 1.10.0 | 2026-10-15 | Pedido do usuário. J-5: o concurso confirmado pode ser lido pelo planejamento (`GET /api/concursos/{id}/planejamento`, `docs/SPRINT-18-CONCURSOS.md` §10) — o SGE é o sistema principal e o planejamento parte dos assuntos confirmados aqui |
| 1.9.0 | 2026-10-15 | Pedido do usuário. Nova **J-5**: edital novo lido por IA no próprio sistema (PDF → cargos → proposta revisável → confirmação) e histórico de editais com reativação. Oitava tela, **Concursos**. §4.4: concurso-alvo e dias até a prova no topo de Hoje. §4.5: Assuntos abre por disciplina, com resumo de peso e fases. J-1: importação **adota** assunto sem chave externa de mesmo nome, e o `concurso.json` passa a registrar o concurso (`01_DOMINIO` v1.23.0, ADR-042, ADR-043) |
| 1.8.0 | 2026-10-15 | §1.2: app Android nativo como segundo cliente (ADR-041), implementando as mesmas telas e regras. Nova §2.2, **lembrete do turno**: opcional, uma notificação por dia no máximo, diz o que há para hoje, nunca cobra, não lembra quem já estudou no dia. Pedido pelo usuário |
| 1.7.0 | 2026-10-14 | Sprint 17, atrito 2 (`01_DOMINIO` v1.21.0 §7.7). O campo **tempo** de conteúdo, que a §5 sempre listou, passa a existir de fato — a tela media tempo de tela e gravava um minuto para uma hora de leitura. Vem preenchido com a estimativa do segmento ou com a duração de bloco parametrizada. Questões ganham o mesmo campo, vazio (6 campos, orçamento de 25 s mantido). Recuperação continua medida pela tela. Decidido pelo usuário |
| 1.6.0 | 2026-10-14 | Sprint 16. (1) J-1: linha sem `id` mas com `chaveExterna` já conhecida **atualiza** em vez de tentar criar — sem isso, a reimportação automática vinda do planejamento (ADR-037) recusava toda linha já importada por nome duplicado, e o arquivo inteiro deixava de ser confirmado a partir da segunda sincronização. (2) §1.2: uso passa a ser app no celular e no tablet além da web (ADR-038); requisitos mínimos de toque e largura. Aprovado pelo usuário |
| 1.5.0 | 2026-09-06 | `segmentos.csv` (J-1, §"Segunda importação") ganha coluna obrigatória `chaveExternaSegmento` — identidade própria do segmento, dada pelo sistema de planejamento (`01_DOMINIO` D-53, nova). Reimportação passa a identificar o segmento por essa chave, não por `ordem`: `ordem` é posição, e reordenar o material depois de importado não pode confundir, em silêncio, um segmento antigo (já referenciado por sessões passadas, D-51) com um novo na mesma posição. §6 ganha D-53 em J-1. Decidido com o usuário ao discutir o mecanismo de reimportação de `docs/SPRINT-10-SEGMENTO.md`, antes do documento técnico fechar — mesma ordem que D-46 e D-52 já seguiram |
| 1.4.0 | 2026-09-05 | J-1 ganha coluna opcional `chaveExterna` no CSV de assuntos e uma **segunda importação**, `segmentos.csv` (uma linha por segmento, `01_DOMINIO` §3.7), chegando via Google Drive + `rclone` (`docs/00A_ADR.md` ADR-037), não upload manual. Referencia o assunto pela chave externa em vez do `id` do SGE, porque o sistema de planejamento nunca saberia esse `id` sem uma chamada de volta que reabriria o acoplamento que a topologia via Drive evita. §6 ganha D-50 a D-52 nas jornadas J-1/J-2. Decidido com o usuário na abertura da Sprint 10 |
| 1.3.0 | 2026-09-01 | §2: removido o passo "declara a próxima sessão pretendida" do fluxo de turno. §4.1 (J-2): removida a linha "Encerrar" do orçamento — `00_PRODUTO` v1.7.0 tira a intenção de implementação como substituto da terceira dor (§8.2 de lá); campo nunca era lido de volta por nada. Soma do orçamento total não muda — essa linha nunca entrava na conta |
| 1.2.2 | 2026-08-30 | Duas lacunas encontradas implementando a importação (Sprint 2, item 2.5), mesma causa raiz: o CSV é definido por linha de **assunto**, então nenhum atributo que só existe em **disciplina** tem coluna. (1) Tabela de colunas não tinha `dificuldadePercebida`, mas `Assunto.dificuldadePercebida` é `NOT NULL` no banco (`docs/SPRINT-2-CADASTRO.md` §1.2) — assunto novo criado via importação não tinha valor para essa coluna. (2) Disciplina criada pela importação (linha nova referenciando disciplina inexistente) também não tinha de onde tirar `peso`, que também é `NOT NULL`. Corrigidas as duas com o mesmo padrão já usado para o `peso` do assunto: default fixo (`3` para dificuldade, `MEDIO` para peso de disciplina), ajustável depois pela interface. A tabela "Situação" (linha com `id` conhecido) também estava desatualizada — dizia que só nome/peso/ordem eram atualizados; `dificuldadePercebida` entra na mesma lista, mesmo tratamento dos outros três campos opcionais |
| 1.2.1 | 2026-08-19 | Correção: o enum de peso do CSV estava no feminino (`ALTA`/`MEDIA`/`BAIXA`), divergindo de `01_DOMINIO` D-23. *Peso* é masculino — `ALTO`/`MEDIO`/`BAIXO`. Divergência encontrada ao derivar o schema da Sprint 1 |
| 1.2.0 | 2026-08-19 | Segunda revisão. **Coluna `id` no CSV** e ciclo exportar–editar–importar: sem isso, corrigir um typo de disciplina duplicava a base inteira. Renomear passa a ser exigência de interface. J-3: **ordem das métricas invertida** — M-1 é lenta demais para liderar tela semanal; M-2 e M-3 assumem, e semanal × trimestral viram telas distintas. Nova §4.3, lugar do **puxar mais**, no fim da lista e nunca no topo. Nova §5.1: orçamento é tempo do usuário, transição otimista, falha não destrutiva, fila offline deliberadamente fora |
| 1.1.0 | 2026-08-19 | Revisão do usuário. §2.1: o turno vira **lista clicável**, não sequência — a ordem é apresentação, nunca imposição. J-1: cadastro em massa vira **importação CSV** com layout definido, validação tudo-ou-nada e reimportação idempotente. J-4 ganha **o outro lado** — puxar mais num dia de folga, com as três modalidades e o preço de cada uma. §4.1: botões passam a usar as âncoras de estrutura × detalhe. Nova §4.2: **registrar erro é ação global**, nunca etapa de fluxo |
| 1.0.0 | 2026-08-19 | Criado. Contexto físico (tablet estuda, computador registra) e por que a separação de aparelhos protege a regra da produção obrigatória. Quatro jornadas, com **J-4, o retorno depois de sumir**, tratada como a que decide a sobrevivência do sistema. Sete telas, cada uma justificada por jornada. Orçamentos de tempo como requisito. Web de computador primeiro, celular depois |
