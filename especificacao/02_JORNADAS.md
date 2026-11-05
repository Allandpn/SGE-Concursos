# 02 — JORNADAS

Como o sistema é usado, momento a momento. **Precedência 3.**
Subordinado a `00_PRODUTO.md` e `01_DOMINIO.md`.

| Campo | Valor |
|---|---|
| Versão | 1.36.0 |
| Data | 2026-11-05 |
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

~~**Consequência:** o sistema **não** deve tentar exibir o material de estudo.
Nunca. Isso não é só escopo negativo (`00_PRODUTO` §8.5) — é que exibir o
material destruiria o único mecanismo que garante a recuperação de verdade.~~

**Revogado em 2026-11-01, a pedido do usuário.** Com o uso no celular e no tablet
(§1.2), a separação de aparelhos deixou de existir, e o link "Abrir material"
levava o PDF a um toque de qualquer tela, inclusive da recuperação. A regra passa a
ser: **o material só aparece na leitura, nunca na recuperação** (`01_DOMINIO`
§3.17, D-114). Ele abre dentro do app, no leitor (J-10), a partir do bloco, do
assunto e do reestudo; a tela Recuperar (§4.1) não oferece material, leitor nem
marcação. O que protege a recuperação continua sendo o mesmo mecanismo: não
oferecer o atalho.

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

### 2.3 Os avisos do processo do edital

Subir um edital tem esperas que não dependem de quem sobe: a leitura (minutos) e o preparo do material (horas). Dois avisos
cobrem essas esperas (`01_DOMINIO` §3.13, D-96):

1. **"Edital lido, pronto para conferir."** Leva à conferência.
2. **"Material pronto."** Leva a Concursos.

Regras, na mesma linha de §2.2: **opcionais e desligados por padrão**, ligados em **Ajustes → Avisos neste aparelho** (um botão
liga, outro desliga, um terceiro manda um aviso de teste); **no máximo uma vez por edital e por rodada**; **o texto diz o que
aconteceu, não o que falta**; e o fato **sempre aparece também na tela** (a faixa de Hoje, Concursos). Sem permissão do
navegador, ou sem suporte, a tela diz isso e o resto segue.

---

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
| Lote rápido ("Fiz questões" na fila, D-133) | Feitas, certas, previsão; o resto preenchido | **≤ 12 s** |
| Volta do QConcursos (J-17, D-153) | O lote rápido já aberto, com o tempo desde a saída | **≤ 12 s** |
| Discursiva | Nota, previsão, tempo | **≤ 20 s** |

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

**Edital novo, pelo próprio sistema** (`01_DOMINIO` §3.9 e §3.12, ADR-043 e ADR-047):

1. Em **Concursos**, toca "Novo edital" e escolhe o PDF.
2. O sistema lê o texto e mostra o que achou: nome do concurso, banca, data
   da prova (se o edital já fixou) e **a lista de cargos**. Leva até um ou
   dois minutos; dá para sair da tela e voltar — a análise fica guardada e
   continua de onde parou.
3. Escolhe o **cargo**.
4. O sistema de planejamento, em segundo plano, separa disciplinas, assuntos e **partes** daquele cargo e liga cada parte a um
   nó da árvore do QConcursos (`01_DOMINIO` §3.12). Dá para sair da tela e voltar; se uma etapa falha, o edital volta a
   "pendente" com a etapa e o motivo, e há "Tentar de novo".
5. Abre a **conferência do edital**. No topo, as contagens: disciplinas · assuntos · partes · exatas, mais amplas e parciais ·
   sem nó · **pendências abertas** · decididas por você · aplicadas de decisão anterior. Duas visões: **por disciplina**, como
   o edital, e a **fila**, só com o que pede decisão, do mais grave ao menos. Em cada item aparecem a parte, o trecho do
   edital de onde saiu, o nó proposto com o caminho completo e quantos filhos ele tem, a natureza do vínculo e o que a IA e a
   regra disseram.
6. Resolve: **aceitar** (um toque; há "aceitar todos os exatos da disciplina"), **trocar o nó** (busca por texto, navegação
   por ramos, sugestões do sistema; aceita mais de um), **a árvore não tem**, **dividir, juntar ou editar** a parte,
   **descartar** com motivo, comentar. Cada gesto é uma decisão; o escopo é "este edital", ou "todos os editais" com uma
   confirmação que diz quantas partes de outros editais mudariam. As decisões ficam numa lista própria, onde se vê, edita e
   revoga.
7. **Concluir.** Só bloqueiam as divergências que deixam o conteúdo do edital incerto (linha fora de disciplina, trecho sem
   assunto, assunto recusado pela regra, parte com alerta de divisão). Parte sem nó ou com vínculo mais amplo **avisa** —
   "12 partes sem filtro de questões" — no painel e no plano, junto ao assunto, e não impede o estudo. É aqui que algo é
   gravado (D-62, D-92): disciplinas e assuntos novos nascem, os em comum recebem peso e ordem do edital, e o concurso vira o
   alvo (D-60).
8. **Pronto.** Os assuntos já estão no turno. O material é preparado sozinho, em segundo plano (`01_DOMINIO` §3.10): em
   Concursos, o concurso-alvo mostra "Material: 12 de 40 prontos · 3 sem material no acervo · 25 a preparar". **Ver por
   assunto** abre a lista do concurso-alvo: cada assunto com a situação (*completo*, *parcial*, *sem material*, *recusado*),
   o tempo estimado de leitura, os segmentos e as **lacunas por parte**, com o motivo; um filtro mostra só os sem material.
   O detalhe do assunto tem **Questões deste assunto** (consulta, fora do turno e da escada) ao lado do atalho para as
   questões do QConcursos, e **Refazer o material** (D-66).
9. **Depois, a qualquer hora.** A conferência continua aberta: vincular uma parte sem nó, trocar o nó, acrescentar nós ou
   trocar a fonte de uma parte. Antes de aplicar, o sistema mostra **o que entra e o que sai** do material sugerido; nada do
   que já existe é removido (D-93). **Atualizar a árvore** do QConcursos é um gesto seu, em Ajustes: ele mostra os nós novos,
   removidos, renomeados e movidos, e devolve à fila as partes afetadas (D-90).
9a. **Lacunas.** Cada parte sem material (lei do órgão, tema que o acervo não cobre) mostra três saídas: **vou estudar por fora**,
   **ignorar**, ou **anexar um PDF** (o texto da lei, por exemplo). O PDF anexado vira segmentos do assunto em alguns minutos, pelo
   mesmo caminho do material automático (`01_DOMINIO` §3.14, D-97); a tela mostra o estado — preparando, pronto ou falhou, com o
   motivo e "anexar de novo".
10. **Avisos.** Com os avisos ligados neste aparelho (§2.3), o sistema avisa quando a conferência está pronta (passo 5) e quando o
   material fica pronto (passo 8); sem eles, a faixa de Hoje diz o mesmo.
11. **Reprocessar** o edital mostra só as pendências novas e um contador "a IA mudou, a sua decisão foi mantida" (D-95).

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

### J-6 · Verificar o material com uma prova

Rara — algumas vezes por concurso —, como a J-5: o orçamento é de **atenção**, não de segundos.
Responde a pergunta do objetivo final: *"com este material, eu acertaria esta prova?"*
(`01_DOMINIO` §3.11).

1. Em **Concursos**, no concurso-alvo, toca **"Verificar com uma prova"**.
2. Escolhe **"Carregar prova"** (PDF) e **"Carregar gabarito"** (PDF com texto ou texto colado). O
   sistema descobre o cargo e o tipo pela capa da prova e **recorta o gabarito certo** (D-83); se
   não tiver certeza, pergunta. Enquanto faltar o gabarito, a prova fica **"aguardando
   gabarito"**: é lida, mas não julgada nem resolvida (D-81).
3. O sistema lê a prova e mostra o que achou **antes de gastar IA no julgamento**: nome (corrigível),
   banca, ano, **quantas questões leu**, quantas não consegue avaliar (figura, tabela, anulada),
   quantas respostas do gabarito casaram e **o recorte escolhido** (cargo, tipo, turno). Mostra
   também 3 questões sorteadas, com a alternativa do gabarito, para o olho pegar uma troca
   grosseira. É o ponto de conferência, como a proposta do edital (D-62).
4. Revisa só o **duvidoso**: questões atribuídas com baixa confiança ou sem assunto. As demais
   atribuições o usuário não precisa ver. "Fora do edital" não é erro (D-74).
5. Confirma. A análise roda em segundo plano — **a tela pode ser fechada**. Em Concursos:
   "Prova X: 34 de 60 questões julgadas".
6. **Relatório:** o número de cobertura com denominador ("42 de 58 avaliáveis · 9 parciais · 7 não",
   D-71), por disciplina, os **assuntos furados** (do mais furado ao menos, com o que faltou), as
   questões fora do edital contadas à parte e, por questão, o assunto, o veredito e a **página de
   sustentação**. Nunca o enunciado fora da prova (D-72).
7. Do assunto furado: ver o motivo, **refazer o material** (D-66) ou aceitar. Depois, **"Analisar de
   novo"** julga só as questões dos assuntos que mudaram (D-79); a prova não é relida.

| Falha | O que acontece |
|---|---|
| PDF é imagem | Lido por OCR; questão com código ou algarismos romanos lidos por OCR leva a marca e a imagem da página (D-85) |
| Leu poucas questões (< 50% do esperado) | Mostra e avisa; o usuário decide seguir ou trocar o PDF |
| Gabarito não casa (nº de questões diferente) | Recusa o gabarito, mantém a prova, diz a diferença (D-82) |
| Gabarito preliminar | Marcado "preliminar"; o definitivo o substitui e recalcula (D-84) |
| IA indisponível / cota esgotada | A análise **pausa** e retoma sozinha (ADR-045); nunca vira veredito (D-75) |

### J-7 · Resolver a prova

Uma prova inteira é um bloco de horas, como o simulado do `00_PRODUTO` §6.7: o usuário a faz uma
vez, de propósito, e o sistema não a mistura com o estudo diário.

1. Em Concursos, na prova com gabarito, toca **"Resolver"**. Pode resolver mesmo sem a cobertura ter
   rodado.
2. Uma questão por tela, uma alternativa por toque; **marcar para revisar**, **pular** e **sair e
   voltar** — o progresso fica guardado, e a mesma tentativa conta uma vez (D-80). Cronômetro
   opcional, com o tempo da prova.
3. **"Entregar":** o sistema corrige **pelo gabarito** e mostra a **nota** — acertos de questões
   avaliáveis, **por disciplina e global**, sempre com denominador (D-71). Entra em M-1 por
   disciplina e global; **só a primeira tentativa** da prova conta, as seguintes ficam como
   informação (D-78).
4. O relatório ganha a **nota real** ao lado da cobertura. Cada erro mostra a questão, a alternativa
   marcada, a correta e, se houve cobertura, a **página que a sustentava**. Onde cobertura e nota
   discordam, por assunto, o relatório diz; é informação, não gatilho: **nenhuma revisão nasce da
   nota** (D-78).

Orçamento: ≤ 1 s entre uma questão e a seguinte (navegação local); entregar e corrigir em ≤ 3 s.

### J-8 · Cronometrar o foco (Pomodoro)

Serve ao registro: o tempo de conteúdo e de questões deixa de ser lembrado e passa a ser medido (`01_DOMINIO` §3.15).

1. Em **Hoje** (ao lado do assunto ou do segmento do plano) ou em **Assuntos**, toca **"Foco"**. A fase de foco começa na hora, com o
   assunto já apontado. Há também um **"Foco"** sem assunto, na barra, para quem começa a estudar antes de decidir o quê.
2. A **barra do Pomodoro** fica fixa em qualquer tela enquanto há fase em curso: o relógio, o assunto (se houver), **pausar** e
   **encerrar**. Não é uma tela: a oitava seria justificada por uma jornada, e a jornada aqui é o turno (J-2).
3. Fechar o app, trocar de tela ou abrir em outro aparelho não altera o relógio (D-98, ADR-050). Só uma fase corre por vez, em
   qualquer aparelho; iniciar outra pede encerrar a atual.
4. No fim do foco, a tela dá sinal (som e vibração, se ligados) e **propõe a pausa**; no fim da pausa, propõe o próximo foco.
   **Nada começa sozinho** (D-102). Depois de N focos, a pausa proposta é a longa.
5. Ao abrir **Registrar sessão** de um assunto, o campo de tempo já vem com a soma dos focos ainda não usados dele (D-100). Confirmar
   custa zero, corrigir custa um toque; cada foco serve a uma sessão só (D-101). Em recuperação o tempo continua o da tela.
6. O foco sem assunto pode ser apontado a um assunto depois, antes de registrar.

Em **Ajustes → Pomodoro**: as quatro durações (D-103), som e vibração, e o aviso de fim de fase neste aparelho (um botão liga,
outro desliga, um terceiro manda um aviso de teste; **desligado por padrão**, D-104). Sem permissão do navegador ou sem suporte, a
tela diz isso e o sinal na tela segue funcionando.

Em **Progresso**, pequeno e sem destaque (D-105): foco por dia, focos completos × encerrados antes do fim. No detalhe do **assunto**: o
foco acumulado e, nos segmentos, o estimado × real.

Orçamento: iniciar, pausar e encerrar em **um toque**, ≤ 1 s de resposta; a barra aparece sem recarregar a tela. O Pomodoro não
acrescenta campo ao registro: ele só preenche um que já existia.

### J-9 · Entrar no app

Serve a quem usa o eAgora fora da rede de casa (`01_DOMINIO` §3.16). A porta é uma página fora das oito telas do estudo: não mostra dado nenhum.

1. **Primeiro acesso, uma vez, pela rede privada.** Antes de existir um passkey, o app só deixa cadastrá-lo a quem chega pela tailnet. A pessoa toca
   **"Cadastrar este aparelho"**, usa a biometria e vê os **8 códigos de recuperação**, uma única vez, com um botão para copiar e a frase
   "guarde fora deste aparelho". Só depois de marcar que guardou o app abre. Vindo de fora, a porta diz que o primeiro acesso é pela rede privada.
2. **Dia a dia.** A porta tem um botão, **"Entrar"**. Toca, usa a biometria e cai em Hoje. Com a sessão de 30 dias válida nem aparece: abrir o app
   leva direto a Hoje (`02_JORNADAS` §5, abrir em < 2 s continua valendo).
3. **Outro aparelho.** Dentro, em Ajustes → Acesso, **"Cadastrar passkey neste aparelho"** pede a biometria do aparelho novo estando logado no antigo, ou
   entra pelo código de recuperação no aparelho novo. A lista mostra os aparelhos, quando cada um entrou por último, e **Sair**, **Sair de todos** e **Remover**.
   O último passkey não tem botão de remover.
4. **Perdi o aparelho.** Na porta, **"Usar código de recuperação"**: digita um dos 8 códigos, cadastra o passkey do aparelho novo e o código é consumido.
   Ajustes → Acesso mostra quantos códigos restam e permite **gerar novos** (os antigos deixam de valer).
5. **Sem passkey e sem código.** Não há caminho. A porta diz isso de forma curta; o convite pelo Guardian é pedido futuro.

Mensagens: a porta não diz se algo existe ("não foi possível entrar" para qualquer falha), e o bloqueio por excesso de tentativas diz quando volta.

Orçamento: com sessão válida, nenhum toque extra; sem sessão, **1 toque + biometria** para entrar.

### J-22 · Resolver no QConcursos e o SGE saber

Frequente: questões no QConcursos pelo atalho ou soltas. Instalar **uma vez**; depois, **zero toques** para capturar.

1. Uma vez por navegador: Ajustes › IA e integrações › **Captura do QConcursos** › **Gerar chave para este navegador**; instalar o
   userscript pelo link da mesma tela e colar a chave no painel dele, no QConcursos.
2. Pelo atalho (J-15/§3.24): a previsão antes de sair; resolver; na volta, o lote já vem com "8 feitas, 6 certas". Conferir e
   registrar.
3. Soltas: em Hoje, o cartão **Questões do QConcursos** ("Crase · 8 questões"); **Registrar** pede a previsão e só então mostra o
   acerto. As sem assunto pedem o assunto uma vez.
4. Se o userscript não entender a página, o painel dele mostra **Enviar diagnóstico**.

### J-21 · Reiniciar o progresso

Raro e sério: recomeçar um concurso ou tudo. Achar em **2 toques**; confirmar **digitando REINICIAR**; desfazer em **2 toques**.

1. Na tela do concurso, **Reiniciar o progresso deste concurso**; em Ajustes, na zona de cuidado, **Reiniciar todo o progresso**.
2. A confirmação diz o que zera ("38 sessões em 12 assuntos; 3 deles também estão no TJ-SP") e o que fica (o cadastro, os
   simulados, as marcações). O botão só libera depois de digitar REINICIAR.
3. Um aviso confirma e oferece **Desfazer**. Depois, Ajustes › **Reinícios** lista cada um (quando, de quê, quantas sessões), com
   **Desfazer** enquanto não tiver sido desfeito.

### J-20 · Tirar da escada as sessões de teste

Ocasional: limpar sessões feitas só para ver a escada. Achar o dia em **2 toques**; tirar várias de uma vez em **1 confirmação**.

1. Em Ajustes, **Sessões registradas**: as sessões por dia, da mais nova para a mais antiga (assunto, tipo, minutos, resultado).
2. Marcar as que não valem; o botão fixo diz **Tirar 3 da escada**. A confirmação diz quais assuntos terão a escada refeita.
3. As tiradas ficam riscadas no mesmo lugar, com **Pôr de volta**. Um aviso diz quais escadas foram refeitas.

### J-19 · Testar sem mexer nos dados reais

Ocasional: conferir a escada, as perguntas ou a reta final antes da data. Entrar em **2 toques**; avançar um dia em **1 toque**.

1. Em Ajustes, **Entrar no modo de teste**. Na primeira vez, o banco de teste recebe a cópia dos dados reais (alguns segundos).
2. A faixa **MODO DE TESTE · quinta, 05/11** aparece no topo de todas as telas, com **+1 dia**, **+7 dias**, **Ir para a data…** e
   **Hoje de verdade**.
3. Estudar, recuperar e registrar como sempre; avançar os dias e ver a escada andar.
4. **Recomeçar** refaz a cópia dos dados reais. **Sair do modo de teste** volta ao real; o teste fica guardado para a próxima vez.

### J-18 · Ver os próximos dias e ensaiar

Ocasional: planejar a semana ou testar uma tela. Orçamento de atenção; abrir uma prévia ou um ensaio em **2 toques**.

1. No computador, Hoje tem à direita a coluna **Próximos 7 dias**; no celular, um bloco recolhido no fim de Hoje. Cada dia: as
   revisões previstas, "6 de 8" contra o teto (ou o aviso "11 · passa do teto de 8"), o simulado e os dias até a prova na reta final;
   no topo, "~3 assuntos novos na semana (estimativa)" (D-157).
2. Tocar num assunto mostra **Ver o material** e **Ensaiar** (D-158, D-159).
3. **Ver o material** abre o leitor com a faixa "Prévia", sem marcar e sem "Terminei".
4. **Ensaiar** avisa que ver antes contamina a recuperação real e pede o tipo (recuperação, questões, discursiva, flashcards). A tela é a
   de sempre, com a faixa "Ensaio · nada é gravado"; no fim, **Ver o que aconteceria**: "88% · reconstruí → a revisão subiria para o
   nível 3 em 12/11".
5. O detalhe de qualquer assunto também tem **Ensaiar**.

### J-17 · Resolver no QConcursos

Frequente: em boa parte dos turnos. Ida em **1 toque**; a volta no orçamento do lote rápido (**≤ 12 s**).

1. Numa recuperação de Hoje (também no detalhe do assunto, no Diagnóstico e no Peso pela banca), **Resolver no QConcursos** mostra
   "Faça 8 questões" e, quando faltar, "filtro pela disciplina" ou "sem filtro de banca" (D-150 a D-152).
2. O QConcursos abre filtrado: no celular, na aba do Chrome por cima do app instalado; no computador, numa nova aba. A pessoa resolve
   lá, logada no QConcursos.
3. **Na volta** (fechar a aba no celular ou voltar à aba do SGE no computador), o lote rápido do assunto já está aberto, com o tempo
   desde a saída: feitas, certas, a previsão e **Registrar** (D-153).
4. **Não fiz** fecha sem nada gravado; fechar no ✕ deixa para a próxima volta; depois de 6 h a saída é esquecida.
5. Depois de Registrar, **Anotar erros** (opcional): para cada errada, o número Q, uma frase de por que errou, a causa e a confiança
   (D-156). Fora do orçamento do lote; pular não perde nada.
6. Banca do alvo fora do catálogo: em Concursos, colar uma vez um endereço do QConcursos filtrado por ela (D-151).

### J-16 · Registrar um simulado

Uma vez por semana ou menos; orçamento de atenção, não de segundos.

1. Em **Registrar**, a pílula **Simulado** (também pelo cartão do simulado da semana, na reta final, e por "Registrar simulado" em
   Progresso). Data (hoje), duração e formato preenchidos.
2. As disciplinas do edital do alvo, em ordem alfabética: **certas / total** e, se quiser, **em branco**. A que não caiu fica vazia; "outra
   disciplina" acrescenta uma ativa fora do edital.
3. Registrar grava e mostra a nota em acertos e, com régua, em pontos com o corte (D-136, D-148). Certas + brancos acima do total é
   recusado na linha.
4. Em **Progresso**, cada simulado tem **Arquivar** (com confirmação); arquivado some da M-1 e dos pontos e fica num "Arquivados"
   recolhido (D-149).

### J-15 · Peso pela banca

Rara: quando chega edital novo ou prova nova da banca. Orçamento de atenção; aceitar tudo em **2 toques**.

1. Em **Concursos**, no alvo, **"Peso pela banca"**. No topo: a banca, quantas provas e questões dela entraram (últimos 5 anos).
2. Por disciplina: a amostra ("FGV · 3 provas · 64 questões") ou "sem amostra suficiente da banca". Abaixo, só os assuntos cujo peso
   mudaria: nome, **edital → proposto**, "12 de 64 questões", e **Aceitar**. No fim da disciplina, "aceitar todos desta disciplina"; no
   topo, "aceitar todos" (D-146, D-147).
3. Subir para alto mostra: "pede mais dois degraus na escada antes de consolidar".
4. Os aceitos ficam numa lista "Ajustados pela banca", cada um com **Desfazer** (volta ao peso do edital).

Nada muda ao abrir. Prova nova da banca só muda o que é proposto.

### J-14 · Reta final

Rara: uma vez por concurso. Orçamento de atenção; entrar e sair em **2 toques**.

1. **A sugestão.** A 8 semanas da prova do alvo (parâmetro), Hoje mostra: "Faltam 8 semanas para a prova. Entrar na reta final?" e
   o que muda em três linhas (só assunto novo de peso alto; manutenção de médio e baixo depois da prova; simulado no fim de semana).
   *Entrar* ou *Agora não* (D-140). Também dá para entrar em Concursos, no alvo.
2. **Ao entrar**, uma linha diz quantas manutenções de peso alto foram antecipadas e até quando ("12 revisões de peso alto
   distribuídas até 05/03"). Hoje passa a mostrar a faixa **Reta final · 54 dias** no topo.
3. **No dia a dia**, o plano é o mesmo de sempre com as três mudanças (D-142 a D-144). No sábado e no domingo, sem simulado na
   semana, o segundo bloco é **Simulado**, com "Registrar simulado".
4. **A saída.** No dia seguinte à prova a faixa some sozinha; "Sair da reta final" está em Concursos a qualquer hora (D-141). As
   manutenções que esperavam voltam vencidas e o teto diário as espalha.

### J-13 · Diagnóstico de um edital

Serve a quando um edital chega ou se aproxima (`01_DOMINIO` §3.21). Rara; orçamento de atenção, mas abre em **< 2 s**.

1. Em **Concursos**, cada concurso tem **"Diagnóstico"** (o alvo e os do histórico, ou um recém-importado antes de ativar).
2. No topo, as duas faixas: **consolidado** e **encaminhado**, em assuntos e em pontos da prova ("41 de 120 assuntos · 38% dos
   pontos"). Abaixo, por disciplina, a barra empilhada das quatro fases (D-137, D-138).
3. **O que cabe.** Com data de prova futura: "no seu ritmo (3,5 assuntos novos por semana), cabem 38 dos 54 nunca vistos até
   12/03; ficam de fora 16" e "tudo visto em 19/04". A lista dos que ficam de fora, com peso e tempo de material. Sem data, um campo
   **"simular com a data"**; sem ritmo medido, **"simular com o ritmo"** — nenhum dos dois é gravado (D-139).
4. Informativos no fim: arquivados da cobertura e assuntos ativos fora do edital, cada número levando à lista em Assuntos.

Nada muda ao abrir nem ao simular.

### J-12 · Treino discursivo e nota de corte

1. **Discursiva.** Em Registrar (ou no detalhe do assunto), **Discursiva**: o assunto, a previsão em três níveis (*vou bem / vai faltar
   um pedaço / não vai vir*), depois de escrever a nota de 0 a 100 e o tempo (pré-preenchido pelo foco). O resultado sai da nota (D-134)
   e a revisão pendente é cumprida.
2. **Régua da prova.** Em Concursos, no alvo, **Pontuação da prova**: pontos por questão de cada disciplina (padrão 1), "errada anula
   certa" e a nota de corte. Salvar não mexe em nada gravado (D-135).
3. **A comparação.** No relatório da prova resolvida e em Progresso (simulados): "Em pontos do edital: 62 · corte 70 · faltaram 8",
   global e por disciplina, ao lado dos acertos. Sem régua, só os acertos (D-136).

### J-11 · Adicionar meu PDF

Serve a quem estuda um assunto por material que não veio do acervo (`01_DOMINIO` §3.19). Rara, como a J-5: orçamento de atenção.

1. Em Assuntos, no detalhe do assunto, **"Adicionar meu PDF"**: escolhe o arquivo e o tipo — **apostila ou resumo** ou **lei seca**.
2. O sistema confere e corta na hora (segundos): "3 blocos · ~2 h 40". PDF sem texto avisa: "sem texto: dá para ler e marcar, mas sem
   perguntas". Arquivo que não é PDF, protegido ou acima de 50 MB é recusado com o motivo.
3. A lista de material do assunto mostra os blocos novos com o nome do PDF, depois dos que já existiam; cada um com **Ler**.
4. Se o assunto já está na escada, Hoje passa a sugerir os blocos novos em "Continuar leitura"; a revisão marcada continua a mesma.
5. **"Tirar este PDF"**, no grupo daquele PDF, arquiva os blocos dele (com confirmação). Nada se apaga.

### J-10 · Ler um bloco e marcar

Serve à leitura de cada bloco do material (`01_DOMINIO` §3.17). É a única jornada em que o material aparece.

1. **Abrir.** Em Hoje, no bloco de conteúdo, no reestudo ou no detalhe do assunto, o bloco tem **"Ler"**. O leitor abre na última página que a
   pessoa abriu desse bloco, em qualquer aparelho (D-121). O link para baixar o PDF continua no menu do leitor, para quem quiser abrir fora.
   **Passar as páginas.** Por padrão, **rolagem contínua**: as páginas ficam uma embaixo da outra e o dedo rola o bloco inteiro; cada página
   é desenhada quando chega perto da tela. No menu, **"Uma página por vez (deslizar)"**: deslizar o dedo para o lado troca a página, e o gesto
   é ignorado enquanto há texto selecionado ou com a página ampliada além da largura. A escolha fica guardada no aparelho. **Pinça** com dois
   dedos amplia e reduz as páginas (não a tela do app); os botões − e + continuam. O contador "12 / 30" no topo acompanha a página mais
   visível e, tocado, abre "ir para a página". Na rolagem contínua, "a última página aberta" de D-121 é a mais visível.
2. **Marcar.** Selecionar um trecho mostra três cores — **importante** (amarelo), **dúvida** (vermelho), **decorar** (verde) — e "nota". Tocar num
   destaque mostra a nota dele e permite trocar a cor, editar a nota ou remover. Tocar e segurar num ponto da página sem texto cria uma **nota de
   página** (é como se marca uma página escaneada). Uma lista lateral mostra as marcações do bloco por página, com filtro por cor.
3. **Sem rede.** Os blocos do plano de Hoje já estão no aparelho; qualquer outro se guarda com **"Guardar para ler sem rede"**. Sem rede, o leitor
   abre só os guardados, marca normalmente e mostra "marcações por enviar: N" até a rede voltar. Bloco não guardado diz que precisa de rede.
4. **Terminar.** **"Terminei"** (na última página, ou no menu a qualquer hora) abre o Registrar de conteúdo com assunto e bloco preenchidos e o tempo
   dos focos livres (D-100). Confirmar grava a sessão e volta a Hoje. Fechar o leitor sem "Terminei" não registra nada (D-120).
5. **Histórico.** No menu, **"Marcações removidas e versões"** mostra o que saiu da vista e as versões anteriores; nada some de vez (D-117).

O leitor não existe dentro de Recuperar: nem botão, nem lista de marcações (D-114).

Orçamento: abrir um bloco guardado em **< 2 s**; criar um destaque em **2 toques** depois de selecionar o texto; "Terminei" até a sessão gravada em
**2 toques** (o mesmo do registro de hoje).

## 4. As telas

Nove de estudo, mais a **porta** do login (J-9), que não mostra dado. Cada uma justificada por uma jornada — tela sem jornada não entra.

| Tela | Serve a | Existe porque |
|---|---|---|
| **Hoje** | J-2, J-4 | A porta de entrada. O plano do turno como lista, e o gatilho de puxar mais (§4.3). No topo, o concurso-alvo e os dias até a prova (§4.4) |
| **Recuperar** | J-2 | As duas etapas da §5.2.1. É a tela mais usada do sistema |
| **Registrar sessão** | J-2 | Conteúdo, questões ou flashcards |
| **Leitor** | J-10, J-2 | O PDF do bloco, com destaques e notas; nunca aberto a partir de Recuperar (§1.1) |
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
- **Nenhuma das duas etapas oferece o material** (`01_DOMINIO` D-114): sem
  "Ler", sem "Abrir material", sem as marcações do assunto. A etapa 1 diz para
  fechar o material; a tela não o reabre.

#### Com perguntas (`01_DOMINIO` §3.18)

Quando o assunto tem perguntas preparadas, a mesma tela ganha duas coisas, e a sequência não muda:

```
ETAPA 1                                       ETAPA 2
┌─────────────────────────────────┐          ┌─────────────────────────────────┐
│ Coesão textual · Nível 3        │          │ 1. Quais são os mecanismos de   │
│ Feche o material e responda:    │          │    coesão referencial?          │
│                                 │          │    Você escreveu: "anáfora e…"  │
│ 1. Quais são os mecanismos de   │          │    ☑ anáfora e catáfora (p. 7)● │
│    coesão referencial?          │   ───▶   │    ☐ elipse (p. 7)              │
│    [ responder (opcional) ]     │          │    ☑ sinônimos e pronomes (p. 8)│
│ 2. A ____ retoma; a ____        │          │ 2. …                            │
│    antecipa.                    │          │    pergunta ruim                │
│ 3. Por que a elipse é coesão?   │          │                                 │
│                                 │          │ Sugestão: Faltou um pedaço      │
│ O que você espera?  [3 botões]  │          │ ( Reconstruí ) (●Faltou) ( Não )│
│              [ Já tentei ]      │          │ Faltou: elipse — ler p. 7 →     │
└─────────────────────────────────┘          └─────────────────────────────────┘
```

- **Etapa 1:** 3 a 5 perguntas e, em cada uma, um campo de resposta **opcional**. A previsão continua aqui, antes.
- **Etapa 2:** cada pergunta com o que a pessoa digitou ao lado do gabarito em pontos; ● marca o ponto principal. A pessoa marca o que
  veio; a sugestão vem pré-selecionada e muda conforme marca; ela confirma ou troca. "Pergunta ruim" em cada pergunta.
- **Depois de Faltou um pedaço**, a etapa 2 lista os pontos que faltaram com "ler p. N →", que abre o leitor só depois de gravar o
  resultado. **Depois de Não veio**, os mesmos pontos vão para o reestudo em Hoje (cada um abre o leitor na página).
- Sem perguntas, a tela é exatamente a de cima.

Orçamento: marcar os pontos custa um toque por ponto; com a sugestão certa, confirmar continua sendo **1 toque**.

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
| Registrar simulado | 90 s | 2 a 3 por disciplina (certas, total, brancos) |
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
| J-9 Entrar no app | A porta (fora das oito), Ajustes | D-106 a D-113 |
| J-8 Cronometrar o foco | Barra do Pomodoro, Hoje, Assuntos, Registrar, Ajustes, Progresso | D-98 a D-105 |
| J-10 Ler um bloco e marcar | Leitor, Hoje, Assuntos, Registrar | D-114 a D-121, D-100, D-54 |
| J-2 com perguntas (recuperar) | Recuperar, Hoje (reestudo), Leitor | D-122 a D-128, D-07, D-14 |
| J-11 Adicionar meu PDF | Assuntos (detalhe), Hoje, Leitor | D-129 a D-132, D-54, D-55, D-122 |
| J-2 lote rápido e J-12 | Hoje, Registrar, Concursos, Progresso, relatório da prova | D-133 a D-136 |
| J-22 Captura do QConcursos | Ajustes (a chave), o lote, a caixa em Hoje, o userscript | `04_FRONTEND` §8F, `01_DOMINIO` §3.28, ADR-063 |
| J-21 Reiniciar o progresso | O concurso; Ajustes (zona de cuidado e Reinícios) | `04_FRONTEND` §8E, `01_DOMINIO` §3.27, D-163 a D-165 |
| J-20 Tirar sessões da escada | Ajustes › Sessões registradas | `04_FRONTEND` §8D, `01_DOMINIO` §3.26, D-160 a D-162 |
| J-19 Modo de teste | Ajustes e a faixa em todas as telas | `04_FRONTEND` §8C, ADR-061 |
| J-18 Próximos dias e ensaio | Hoje, Leitor, Recuperar, Registrar, Assuntos (detalhe) | D-157 a D-159, D-121, D-143, D-144 |
| J-17 Resolver no QConcursos | Hoje, Assuntos (detalhe), Concursos (Diagnóstico, Peso pela banca, banca no QConcursos), Ajustes | D-150 a D-156, D-133 |

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
| 1.36.0 | 2026-11-05 | Nova **J-22** (a captura do QConcursos), com `01_DOMINIO` v1.44.0 §3.28, `04_FRONTEND` v1.25.0 §8F e ADR-063 |
| 1.35.0 | 2026-11-05 | Nova **J-21** (reiniciar o progresso), com `01_DOMINIO` v1.43.0 §3.27 e `04_FRONTEND` v1.23.0 §8E |
| 1.34.0 | 2026-11-05 | Nova **J-20** (tirar sessões da escada), com `01_DOMINIO` v1.42.0 §3.26 e `04_FRONTEND` v1.22.0 §8D |
| 1.33.0 | 2026-11-05 | Nova **J-19** (modo de teste), com `04_FRONTEND` v1.21.0 §8C e ADR-061. Sem regra de domínio nova: o teste usa as mesmas regras sobre outra cópia dos dados |
| 1.32.0 | 2026-11-04 | **J-2** ganha a forma da tela (`04_FRONTEND` §8B): Hoje abre na próxima ação, em destaque, com o progresso do dia; as telas de estudo ficam em modo foco, com as etapas à vista e a ação fixa embaixo. A sequência e os orçamentos de J-2 não mudam |
| 1.31.0 | 2026-11-04 | Nova **J-18** (os próximos 7 dias, a prévia do material e o ensaio), com `01_DOMINIO` v1.41.0 (D-157 a D-159) e ADR-059 |
| 1.30.0 | 2026-11-04 | Nova **J-17** (resolver no QConcursos: o atalho filtrado e a volta com o lote rápido aberto), com `01_DOMINIO` v1.40.0 (D-150 a D-156) e ADR-058; a volta entra no orçamento do lote rápido; anotar erros fica fora dele |
| 1.29.0 | 2026-11-03 | Pedido do usuário. **J-10** ganha "passar as páginas": rolagem contínua por padrão, "uma página por vez (deslizar)" no menu, pinça para o zoom, o contador que leva a "ir para a página"; na rolagem contínua, a página de D-121 é a mais visível. Sem regra nova |
| 1.28.0 | 2026-11-02 | Nova **J-16** (registrar um simulado) e o orçamento dele, com `01_DOMINIO` v1.39.0 (D-148, D-149). As disciplinas vêm em ordem alfabética (ajustado ao escrever o código: o edital não ordena as disciplinas entre si) |
| 1.27.0 | 2026-11-02 | Nova **J-15** (peso pela banca), com `01_DOMINIO` v1.38.0 (D-145 a D-147) e ADR-057 |
| 1.26.0 | 2026-11-01 | Nova **J-14** (reta final), com `01_DOMINIO` v1.37.0 (D-140 a D-144) e ADR-056 |
| 1.25.0 | 2026-11-01 | Nova **J-13** (diagnóstico de um edital), com `01_DOMINIO` v1.36.0 (D-137 a D-139) |
| 1.24.0 | 2026-11-01 | J-2 ganha o lote rápido ("Fiz questões", ≤ 12 s, D-133) e a discursiva (≤ 20 s); nova **J-12** (treino discursivo e nota de corte), com `01_DOMINIO` v1.35.0 (D-133 a D-136) |
| 1.23.0 | 2026-11-01 | Nova **J-11** (adicionar meu PDF), com `01_DOMINIO` v1.34.0 (D-129 a D-132) e ADR-054 |
| 1.22.0 | 2026-11-01 | §4.1 ganha a tela Recuperar com perguntas (etapa 1: perguntas e resposta opcional; etapa 2: gabarito em pontos, sugestão, pergunta ruim, o que faltou abrindo o leitor), com `01_DOMINIO` v1.33.0 (D-122 a D-128) |
| 1.21.0 | 2026-11-01 | Pedido do usuário. **§1.1 revogada**: o material deixa de ficar fora do app e passa a aparecer só na leitura, nunca na recuperação. Nova **J-10** (ler um bloco e marcar) e nona tela, **Leitor**, com `01_DOMINIO` v1.32.0 (D-114 a D-121) e ADR-052. §4.1 ganha a regra de que Recuperar não oferece material |
| 1.20.0 | 2026-10-30 | **J-9** (entrar no app) com `01_DOMINIO` v1.31.0 (D-106 a D-113) e ADR-051. A porta do login fica fora das oito telas do estudo; Ajustes ganha "Acesso" (passkeys, códigos de recuperação, sair de todos) |
| 1.19.0 | 2026-10-30 | **J-8** (cronometrar o foco, Pomodoro) com `01_DOMINIO` v1.30.0 (D-98 a D-105) e `00_PRODUTO` v1.11.0. A barra do Pomodoro não é tela nova: as oito telas de §4 ficam. Ajustes ganha "Pomodoro"; o campo de tempo do registro passa a vir do foco medido quando houver |
| 1.18.0 | 2026-10-27 | **J-5, passo 9a**: lacuna com "estudar por fora", "ignorar" e **anexar PDF**, com `01_DOMINIO` v1.29.0 (D-97) e `00_PRODUTO` v1.10.0 |
| 1.17.0 | 2026-10-27 | **§2.3** (os avisos do processo do edital) e um passo novo em J-5, com `01_DOMINIO` v1.28.0 (D-96) e `00_PRODUTO` v1.9.0. Ajustes ganha "Avisos neste aparelho" |
| 1.16.0 | 2026-10-24 | **J-5 refeita do passo 4 em diante** com `01_DOMINIO.md` v1.27.0: a proposta de assuntos dá lugar à **conferência do edital** (partes, nós do QConcursos, fila de divergências, decisões com escopo, conclusão que grava), ao acompanhamento do material por parte e ao material de consulta. Atualizar a árvore passa a ser gesto do usuário. Pedido do usuário |
| 1.15.0 | 2026-10-18 | **J-6** (verificar o material com uma prova e gabarito) e **J-7** (resolver a prova), com `01_DOMINIO` v1.26.0 (§3.11, D-67 a D-85). Telas e orçamento detalhados no documento da sprint |
| 1.14.0 | 2026-10-17 | J-5 passo 7: lista do material por assunto (situação, minutos, páginas, motivo) |
| 1.13.0 | 2026-10-17 | J-5 passo 7: refazer o material de um assunto ou do concurso (D-66) |
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
