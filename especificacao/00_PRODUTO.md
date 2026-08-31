# 00 — PRODUTO

Para que este sistema existe, e como saberemos se funcionou.
**Precedência 1.** Nenhum documento pode contrariá-lo.

| Campo | Valor |
|---|---|
| Versão | 1.6.0 |
| Data | 2026-08-31 |
| Status | Vigente |
| Documento seguinte | `01_DOMINIO.md` |

---

## 0. Por que este documento existe, e por que é o primeiro

A especificação anterior tinha 20 documentos e nenhum deles respondia a esta
pergunta: *para que serve o sistema, e como eu saberei que ele ajudou?*

Havia schema, invariante, algoritmo, endpoint e token de cor — tudo derivado de
uma premissa que nunca foi escrita. Documento derivado de premissa ausente pode
estar certo por acidente, e não há como saber quais estavam.

Este documento é a premissa. Tudo o que vier depois deve poder ser justificado
apontando para uma linha daqui. **Funcionalidade que não se justifica aqui não
entra**, por mais razoável que pareça isoladamente.

---

## 1. Quem usa

Um usuário. Servidor público concursado na Dataprev, estudando para outro
concurso.

| Fato | Consequência de projeto |
|---|---|
| Um usuário, sempre o mesmo | Sem cadastro, sem papéis, sem multi-tenant |
| Provas a partir de **24 meses**; estudo contínuo até sair edital | Otimizar para **retenção durável**, não para cobertura de véspera |
| 3 a 5 h/semana para *construir* o sistema | Escopo é o recurso escasso |
| Tempo de estudo próprio, separado disso | O sistema não pode competir com o estudo pelo tempo dele |
| Resolve questões em **plataforma externa** | O sistema registra recuperação; não a hospeda |
| **Menos de 50 questões/semana** | O número mais restritivo de todo o documento. Ver §5.3 |
| **171 assuntos** mapeados no núcleo inicial (8 disciplinas de TI); centenas depois | Define a carga de revisão e mata uma ideia inteira. Ver §5.3 e §6.7 |
| Teoria por **PDF/apostila e legislação seca** | Ele controla a ordem — recomendar teoria faz sentido |
| Simulados: raramente hoje, pretende aumentar | Registrado, não modelado. Ver §11 |

Dois desses fatos, cruzados, determinam o produto mais do que qualquer decisão
de projeto: **24 meses de horizonte** e **50 questões por semana**. O primeiro
diz que o problema é retenção. O segundo diz que a medição vai ser lenta e
precisa ser honesta sobre isso.

---

## 2. A tese

> **O sistema existe para fazer o acerto medido em questões subir ao longo do
> tempo — e para provar que subiu.**

Tudo o mais é instrumental. Registrar horas, agendar revisão, classificar erro,
recomendar assunto: nada disso é objetivo. São meios, e cada um só se sustenta
enquanto servir à frase acima.

---

## 3. Os quatro pilares

O que a evidência sustenta, e o que decorre de cada um para este sistema.

### 3.1 Recuperação

Tentar lembrar produz aprendizado; reconhecer não produz. É a técnica mais bem
estabelecida da literatura, junto com espaçamento.

Para transferência — o que importa em concurso, porque a prova nunca repete a
questão treinada — a meta-análise de Pan & Rickard estima d ≈ 0,40, caindo para
d ≈ 0,28 quando a resposta cobrada não coincide com a treinada. **E o efeito
encolhe até desaparecer quando o desempenho inicial é ruim.**

### 3.2 Espaçamento

Distribuir no tempo bate concentrar. Mas o cronograma exato importa pouco: um
estudo de 8 semanas comparando escada expansiva com intervalo fixo encontrou
retenção final estatisticamente indistinguível (.49 contra .46).

**Consequência vinculante:** é proibido gastar esforço aperfeiçoando o
agendador. O ganho está em espaçar, não em espaçar com elegância. Qualquer
proposta de algoritmo adaptativo esbarra aqui.

### 3.3 Interleaving — e onde ele *não* funciona

Misturar assuntos numa mesma prática melhora discriminação. Mas depende
criticamente de os assuntos serem **confundíveis**.

Uma revisão sistemática separa os dois mecanismos: espaçamento funciona por
recuperação cognitiva e é independente do material; interleaving funciona por
**contraste discriminativo** e só entrega ganho quando há o que discriminar. A
revisão identifica cinco estudos com efeito nulo — todos com domínios
obviamente distintos.

Portanto:

| Isto é espaçamento, não interleaving | Isto é interleaving de verdade |
|---|---|
| 30 min Constitucional, 30 min Redes, 30 min Português | controle difuso × concentrado, misturados |
| Alternar disciplinas ao longo do dia | `INNER` × `LEFT` × `CROSS JOIN`, misturados |
| | crase antes de pronome × de topônimo × de masculino |

Ninguém confunde crase com normalização. Alternar disciplinas é bom — mas por
ser espaçamento, e o crédito tem que ir para o mecanismo certo.

**Consequência:** o interleaving mora no **lote de questões**, não no
cronograma do dia. Como o lote é montado na plataforma externa, o papel do
sistema é **sugerir lotes mistos entre assuntos irmãos** e não obstruir. O
sistema não organiza o seu dia.

### 3.4 Calibração

Julgamento de aprendizagem é sistematicamente inflado, e a inflação é invisível
por dentro. A única forma de detectá-la é registrar a previsão **antes** da
medida e comparar.

É o único pilar que ataca frontalmente "estudo, acho que entendi, e na prova
erro".

### 3.5 O princípio que atravessa os quatro

> **Recuperação malsucedida sem correção não é aprendizado — é desgaste.**

Retrieval practice sem feedback perde boa parte do efeito; é o mesmo fenômeno
do achado de §3.1 visto por outro ângulo. Daí sai a regra operacional mais
importante do sistema:

> **Desempenho não muda o intervalo. Desempenho muda o caminho.**

Foi bem → sobe um degrau, no intervalo fixo de §3.2.
Foi mal → **não sobe**, e o assunto volta para a fila de **estudo**, não de
revisão. O sistema nunca trata um resultado ruim como "revisão feita".

Isso honra §3.1 (falha e sucesso são eventos diferentes) sem violar §3.2 (não
complicar o agendador). O desempenho roteia; não recalcula.

---

## 4. As três dores

Declaradas pelo usuário, em ordem de peso.

| Dor | Pilar que a ataca | Existe hoje na especificação antiga? |
|---|---|---|
| "Esqueço o que estudei semanas atrás" | Espaçamento (§3.2) | **Sim**, e bem |
| "Estudo, acho que entendi, e na prova erro" | **Calibração (§3.4)** | **Não.** Nada ataca isto |
| "Não mantenho constância" | Atrito baixo + intenção de implementação | Parcial, e pelo caminho errado (§8.2) |

### 4.1 A dor que ele não tem

Ele **não** marcou *"não sei o que estudar quando sento"*.

A especificação anterior dedicava a Sprint 7 inteira a isso: cinco fatores
ponderados, balanceador, decomposição de score, doze casos de teste. É o
subsistema mais complexo do projeto — e ataca a única dor que o usuário
declarou não ter.

**Decisão:** a recomendação continua no escopo, porque decorre de §3.5 (alguém
precisa dizer "leia" em vez de "resolva"), mas **desce de prioridade e de
ambição**. Ela não justifica cinco pesos configuráveis antes de o sistema medir
uma única recuperação.

---

## 5. Onde a recuperação acontece

### 5.1 Fora do sistema

O usuário resolve questões em plataforma externa. Ela já tem volume, banca e
correção. Reconstruir isso seria absurdo.

O papel do sistema é ser **o único lugar onde esse resultado vira série
histórica por assunto** — coisa que a plataforma externa não faz, porque não
conhece o recorte de assuntos dele.

| O sistema faz | O sistema não faz |
|---|---|
| Registra assunto, quantas, quantas certas, quando | Hospeda enunciado, alternativa ou gabarito |
| Amarra o resultado ao assunto e à revisão | Corrige questão |
| Acumula a série e mostra a tendência | Sincroniza com a plataforma externa |
| Guarda o erro classificado por causa e por confiança | Guarda a questão errada |

### 5.2 Duas formas de recuperar

Uma revisão pode ser **um lote de questões** — resultado objetivo, `17/25`.
Essa é a forma boa: mede em vez de perguntar.

Mas ela não pode ser a única, e a §5.3 mostra por quê. A outra forma é
**recuperação livre**: fechar o material, reconstruir o assunto de memória, e
registrar como foi.

Isso é uma reversão consciente. A intuição inicial — "com plataforma externa,
toda revisão vira lote de questões, e some a autoavaliação" — não sobrevive à
aritmética.

### 5.2.1 A regra que salva a recuperação livre

Recuperação livre tem um modo de falha que a destruiria: virar uma pergunta
sobre sensação. *"Você sente que lembra de crase?"* é julgamento por
familiaridade — exatamente o cue que produz a ilusão de fluência que a §3.4
existe para combater. Um sistema que pergunta isso mede a ilusão e a chama de
dado.

> **Regra da produção obrigatória.** A recuperação livre exige **produzir**
> antes de julgar: escrever, explicar em voz alta, recitar. Só depois da
> tentativa o sistema pergunta o resultado.
>
> Nunca *"você acha que lembra?"*. Sempre *"tente lembrar — conseguiu?"*.

A diferença parece de redação e é de mecanismo, por dois motivos independentes:

1. **O valor pedagógico está na tentativa, não no relato.** Sem produção não
   houve recuperação, não houve efeito de teste, e a "revisão" não aconteceu —
   só foi marcada.
2. **O julgamento depois da tentativa é muito melhor calibrado.** Julgamento de
   aprendizagem feito com o material à vista lê familiaridade; feito depois de
   uma tentativa de evocação, lê o resultado da evocação. É o efeito de JOL
   atrasado, e a meta-análise sobre ele mostra que adiar o julgamento reduz de
   forma confiável — ainda que não elimine — as ilusões de metamemória.

**Consequência de interface, vinculante:** a tela de recuperação livre tem duas
etapas. A primeira mostra só o nome do assunto e a instrução de reconstruir. O
campo de resultado **não existe na tela** até a tentativa ser declarada. O
sistema não consegue verificar que você produziu — mas consegue não te oferecer
o atalho.

### 5.3 A aritmética que decide isto

50 questões/semana = **2.600 por ano**.

| Se cada revisão fosse um lote de 20 questões | |
|---|---|
| 200 assuntos, manutenção 2×/ano | 400 revisões → **8.000 questões/ano** |
| Disponível | **2.600/ano** |
| Déficit | **3×** — e isso antes das 1.200 revisões da escada |

| Se a maioria for recuperação livre de ~5 min | |
|---|---|
| 400 revisões de manutenção | **33 h/ano**, ~1,1 revisão/dia |

A conta fecha na segunda linha e não fecha na primeira. **A recuperação livre é
o caminho principal; o lote de questões é o caminho de maior valor, usado onde
cabe.**

E a mesma aritmética mata outra ideia, mais silenciosa:

| Assuntos | Questões por assunto **por ano** | Margem de erro (95%) |
|---|---|---|
| 100 | 26 | ± 19 pontos |
| 200 | 13 | ± 27 pontos |
| 300 | 9 | ± 33 pontos |

Treze questões por assunto por ano. **Acerto por assunto nunca será medida — é
sinal para decidir o que revisar, e só.** Qualquer tela que mostre tendência
por assunto está mentindo com números verdadeiros.

---

## 6. Definições operacionais

Termos que o sistema usa e que precisam significar uma coisa só.

### 6.1 Assunto — e a regra de granularidade

A unidade de estudo é o **assunto**.

Um lote de 25 questões sobre "Banco de Dados" que só cobrava `JOIN` significa
que você recuperou `JOIN`, não Banco de Dados. A métrica atribuiria o mérito ao
rótulo errado, invalidando M-1 e M-2 em silêncio.

> **Regra de granularidade:** um assunto é pequeno o bastante para que **uma
> recuperação de ~8 minutos o cubra de forma significativa**, e coerente o
> bastante para que *"eu sei isto"* seja uma frase que faça sentido sobre o
> todo.

O critério é o **evento de recuperação**, não a contagem de questões. Ele
decorre da §7.1 de `01_DOMINIO`: recuperação é toque curto, e assunto que não
cabe num toque nunca é recuperado inteiro — só uma fatia dele, diferente a cada
vez, sem que ninguém perceba.

"Banco de Dados" é disciplina. "Normalização e dependências funcionais" é
assunto. "Linguagem SQL (DDL, DML, DQL, DCL, TCL)" é **grande demais**: são
cinco corpos distintos, e nenhuma recuperação de 8 minutos alcança os cinco.

A granularidade fina é para **agendar e rotear** — você revisa `JOIN`, não
Banco de Dados. A medição agrega **para cima**, para o nível de disciplina, que
é onde o n existe (§7.1).

### 6.2 Sessão

> **Sessão é a menor unidade registrável de estudo: a aplicação de uma única
> estratégia de aprendizagem sobre um único assunto, produzindo um único
> conjunto coerente de métricas.**

Exemplos, cada um uma sessão distinta:

- 30 minutos de teoria sobre JVM;
- 20 questões de JVM;
- 15 flashcards de JVM;
- 10 minutos de recuperação livre de JVM.

Usou duas estratégias no mesmo assunto em sequência? São **duas sessões**. O
motivo não é purismo: é que misturar tempo de leitura com acerto em questões no
mesmo registro produz métricas incompatíveis, e nenhuma análise depois consegue
separá-las.

**Consequência de modelagem:** a relação sessão → assunto é **1:1**. Um assunto
tem inúmeras sessões ao longo do tempo.

**Turno não existe no modelo.** Uma noite de estudo contém várias sessões
consecutivas, mas "turno" é organização temporal, não evento pedagógico. Se um
dia ele for necessário, é derivável por data.

#### O registro em lote é interface, não domínio

Registrar quatro assuntos irmãos de uma vez é obrigatório pelo orçamento de
atrito (§9.2) e é o mecanismo de interleaving da §3.3. Mas isso se resolve com
**uma tela que grava quatro sessões**, não com uma sessão que aponta para
quatro assuntos.

O domínio fica 1:1 e limpo; a interface absorve o atrito. Esta é a resposta
definitiva à questão que a v1.1.0 deixou aberta.

### 6.3 O resultado da recuperação

Toda recuperação — de qualquer tipo — produz **um resultado em três níveis**:

```
SUCESSO · PARCIAL · FALHA
```

O tipo da recuperação muda apenas **como o resultado é apurado**, nunca o que
ele significa:

| Tipo | Como vira resultado |
|---|---|
| Lote de questões | Percentual de acerto, contra limiares parametrizados |
| Recuperação livre | Autoavaliação **depois da produção** (§5.2.1): conseguiu / parcialmente / não |

Isso é o que torna o resto do sistema type-agnostic. Consolidação, roteamento
de §3.5 e M-2 passam a depender do **resultado**, não do tipo — e a v1.1.0
estava errada ao falar em "acima do limiar", que não significa nada para uma
recuperação livre.

**Ressalva honesta:** com lotes pequenos, uma questão a mais ou a menos vira
outro resultado. Aceitável, porque a classificação serve para **rotear** — e
roteamento errado se corrige na revisão seguinte. Ela **não** é usada para
medir (§7 M-1), onde a regra do n vale integralmente.

### 6.4 Consolidado e manutenção

**Não existe "aprendido".** Existe consolidado, e existe mantido.

> **Consolidado** = escada concluída **e** os dois últimos resultados
> `SUCESSO`.

Assunto consolidado entra em **manutenção**: uma revisão a cada 120–180 dias,
indefinidamente.

Sem isso, a escada termina em ~203 dias e um assunto dominado em outubro fica
**17 meses intocado** antes da prova. O sistema estaria ajudando por seis meses
e abandonando em silêncio justamente o que você fechou primeiro.

### 6.5 Cadastrado não é o mesmo que em aprendizagem

> **O sistema administra centenas de assuntos cadastrados, mas só uma fração
> deles está simultaneamente em aprendizagem.** A abertura gradual de assunto
> novo é o que controla a carga de revisão e mantém a preparação sustentável
> num horizonte longo.

Essa fração tem nome — **frente de estudo** — e é o conceito que governa a
estratégia inteira. Sem ele, "171 assuntos" se lê como 171 assuntos recebendo
teoria ao mesmo tempo, que é exatamente o comportamento que o sistema existe
para evitar.

Quatro conjuntos, todos derivados, nenhum guardado:

| Conjunto | O que é |
|---|---|
| **Backlog** | Cadastrado, ainda não entrou na frente. Fila, não descuido |
| **Frente de estudo** | Recebe teoria, questões e recuperação **agora** |
| **Consolidado** | Saiu da frente; abriu vaga |
| **Manutenção** | Recuperação esparsa e perpétua |

Consolidar **abre vaga automaticamente**, e a vaga é preenchida do backlog. O
mecanismo, o dimensionamento e os tetos estão em `01_DOMINIO.md` §6 — inclusive
o achado de que o tamanho da frente **não é escolha**, é consequência de
quantos assuntos, que horizonte e quantas horas por semana.

Consequência para o algoritmo de recomendação: ele passa a responder **duas**
perguntas, e a primeira quase nenhum aplicativo de estudo faz.

```
1. Há espaço para abrir assunto novo?
2. Qual assunto deve ocupar esse espaço?
```

### 6.6 Revisão sugerida, nunca obrigatória

O sistema sugere e registra o desvio. **Nunca bloqueia, nunca julga.** Sistema
que repreende é sistema que se abandona — e M-4 já mede aderência sem precisar
ser polícia.

### 6.7 Simulado — a exceção honesta

Um simulado de 120 questões cobre cinco disciplinas. Forçá-lo em uma sessão de
um assunto seria mentira, e distribuí-lo por assunto exigiria classificar 120
questões à mão — custo que a §9.1 recusa para um evento que hoje é raro.

> **Simulado não é sessão.** É um evento próprio, registrado **por disciplina**,
> não por assunto.

Isso não é concessão: é coerência. A §7 já estabelece que prova de evolução vive
no nível de disciplina e global. O simulado é a **entrada mais pura** que M-1
pode ter — misto, cronometrado, e é o melhor preditor isolado de desempenho na
prova real. Registrá-lo no nível onde a medição funciona é o tratamento certo.

Ele não gera revisão e não alimenta consolidação, porque não há atribuição
confiável por assunto. Alimenta M-1 e nada mais.

**Implementação adiada**, não o desenho: o usuário faz poucos hoje. A forma
fica decidida aqui para que, quando entrar, não seja improviso.

---

## 7. As métricas de sucesso

Quatro. A ordem mudou na v1.1.0: no volume real do usuário, M-2 e M-3
funcionam desde os primeiros meses e M-1 é lenta. A tese continua sendo M-1 —
mas o sistema não pode depender só dela para se provar útil.

### M-1 — Acerto ao longo do tempo · *a tese, e a mais lenta*

Acertos ÷ questões, por janela.

Com 2.600 questões/ano, e sabendo que provar 60% → 70% exige ≈ 356 questões na
janela:

| Nível | Volume por trimestre | O que dá para afirmar |
|---|---|---|
| **Global** | ~650 | Melhora de 10 pontos, **por trimestre** |
| **Disciplina** (5 disciplinas) | ~130 | Melhora de 15 pontos em ~4 meses; de 10 pontos em ~8 meses |
| **Assunto** | ~3 | **Nada.** Ver §5.3 |

Regras vinculantes para qualquer tela:

- Percentual **nunca** aparece sozinho: sempre `17/25 (68%)`. A fração torna o
  ruído visível; o percentual sozinho o esconde.
- Seta de tendência, cor de melhora/piora e comparação entre períodos exigem
  **n ≥ 100** na janela. Abaixo disso: número puro, sem adjetivo.
- Abaixo de n = 10, exibir `—`, nunca `0%`.
- Janela padrão de prova: **trimestral no global, anual por disciplina.**

### M-2 — Retenção · *a que valida o mecanismo*

Acerto na revisão comparado ao acerto na primeira exposição do mesmo assunto.

Se o espaçamento funciona, a revisão empata ou supera a primeira exposição
**apesar do tempo decorrido**. Se despenca de forma sistemática, a escada está
longa demais para você — e isso é acionável: muda um parâmetro.

Acumula uma observação por revisão, com ou sem questões. **~400 observações por
ano.** É a métrica que dá sinal cedo.

### M-3 — Calibração · *a que ataca a segunda dor*

Antes de medir, o usuário declara o que espera. A métrica é `previsto − real`,
**com sinal**. Ela tem duas variantes, e elas **não se somam**:

| Variante | Previsto | Real | Força |
|---|---|---|---|
| **Objetiva** (lote de questões) | percentual esperado | percentual apurado | Forte: o real é medida |
| **Subjetiva** (recuperação livre) | vai conseguir reconstruir? | conseguiu? (§5.2.1) | Mais fraca: compara dois autorrelatos |

A variante subjetiva parece circular e não é. O primeiro julgamento é feito
**com o material à vista** e lê familiaridade; o segundo é feito **depois da
tentativa** e lê o resultado dela. A distância entre os dois **é** a ilusão de
fluência, medida diretamente — e é a medida mais barata dela que existe.

Quantos níveis cada julgamento admite é decisão de tela, não desta métrica —
este documento fixa o quê (previsto × real, com sinal), não o quanto.
`02_JORNADAS §4.1` usa três (a mesma régua nos dois momentos, e a mesma que já
vale para o resultado de qualquer sessão que cumpre revisão, `01_DOMINIO`
§3.3) — o par vira SUCESSO/PARCIAL/FALHA de ambos os lados, não sim/não.

São reportadas **separadas**. Misturar as duas numa média é fingir precisão que
a segunda não tem.

O sinal importa mais que o módulo, e são diagnósticos opostos:

| Sinal | Significa | O remédio |
|---|---|---|
| **Superestimou** | Você para de estudar cedo demais. **É o perigoso** | Mais recuperação, menos releitura |
| **Subestimou** | Você regasta o que já domina | Avançar, confiar mais |

Deve encolher ao longo dos meses — não porque você acerta mais, mas porque
passa a saber onde não sabe. Uma observação por revisão, mesmo custo de M-2.

### M-4 — Aderência · *processo, não resultado*

Percentual de revisões concluídas em até 3 dias do previsto.

Deliberadamente última e exibida em tamanho pequeno. Diz se o sistema está
sendo usado, não se está funcionando. Um mês de aderência 100% com M-1 parada
significa que você é disciplinado e o método está errado.

### 7.1 O que explica as métricas: o banco de erros

Taxa de acerto é um agregado que esconde o mecanismo. **Erros recorrentes
classificados por causa explicam muito mais do que 68%.**

Alguém em 68% por *desatenção* precisa de intervenção completamente diferente
de alguém em 68% por *falta de conhecimento* — e a métrica não distingue os
dois. A classificação de causa distingue.

Duas consequências:

1. O banco de erros deixa de ser funcionalidade lateral e passa a ser a
   **camada explicativa** das quatro métricas.
2. O erro guarda a **confiança que você tinha ao errar**. Erro cometido com
   certeza, quando corrigido, é corrigido de forma mais durável — é o efeito de
   hipercorreção, e "eu tinha certeza" é o achado mais valioso que o sistema
   pode registrar.

E é aqui que mora a **interrogação elaborativa**, sem campo novo: descrever por
que errou, no momento imediatamente após errar, *é* elaboração — no timing de
maior valor pedagógico que existe. O campo de descrição do erro não é anotação
livre: é o instrumento.

### 7.2 Contexto derivado: questões por hora

`questoes_feitas ÷ tempo` já é calculável do que se registra. 25 questões a 68%
em 20 minutos e em 2 horas são estados diferentes — e tempo por questão caindo
com acerto estável é sinal de automatização, que importa numa prova
cronometrada.

Fica como **contexto**, nunca como objetivo. Não é medida de carga cognitiva —
esse é outro construto, e o sistema não o mede.

---

## 8. O que o sistema deliberadamente não mede

Cada item foi considerado e recusado, com motivo.

### 8.1 Horas como progresso

Horas continuam registradas — dão denominador a §7.2 e dizem se você
compareceu. Mas **não sobem ao topo e não geram meta em destaque.**

Hora é entrada; a tese é sobre saída. Indicador grande de "240 min/dia" ensina
a otimizar a coisa errada, e o usuário obedece a indicadores grandes.

### 8.2 Sequência de dias (*streak*)

**Fora.** Não é corte por preguiça; é remoção por dano.

Sequência é mecânica de retenção de produto — existe para trazer o usuário de
volta amanhã. Este sistema não precisa de usuário ativo diário; precisa de um
usuário aprovado. E aversão à perda produz cumprimento simbólico: vinte minutos
às 23h50 para não quebrar a sequência entram no banco como sessão e contaminam
M-1 e M-4.

A sequência não mede aprendizado e ainda corrompe o que mede.

Substituto para a terceira dor: **intenção de implementação** — ao encerrar,
registrar *quando* e *o quê* será a próxima sessão. Planos de "quando-onde-como"
têm efeito substancialmente maior sobre execução do que qualquer contador de
dias, e olham para frente em vez de pressionar para trás.

### 8.3 Cobertura do edital como progresso

Cobertura mede material **visto** — a ilusão de fluência promovida a indicador.

Continua existindo, porque saber o que nunca foi tocado é legítimo, mas passa a
ser **ponderada por recuperação bem-sucedida**: um assunto só conta como
coberto depois de recuperado com sucesso ao menos uma vez. Lido e nunca testado
conta como *tocado*.

### 8.4 Confiança de 1 a 5 por lote

**Recusada**, pelo princípio da §9.1.

M-3 já registra a previsão **antes** do lote, que é o instrumento prospectivo e
mais forte. Uma nota de confiança depois de responder mede quase o mesmo
construto, de forma retrospectiva e mais fraca. Dois campos, um construto, e o
segundo é o pior dos dois.

Confiança fica onde ela não é redundante e é diagnóstica: **no erro** (§7.1).

### 8.5 Também fora

Estimativa de probabilidade de aprovação. Humor, energia ou "qualidade da
sessão". Medalhas, XP, níveis. Comparação com outros usuários. Cronograma
gerado automaticamente. Algoritmo adaptativo tipo SM-2/FSRS — §3.2 mostra que
compra pouco. Editor de conteúdo, anexo, upload. Notificação por e-mail ou push.
Campo estruturado de "insight" ou "observação elaborada" — §7.1 mostra que o
banco de erros já faz isso, no momento certo.

---

## 9. Princípios de projeto

### 9.1 Economia cognitiva

> **Valor da informação > custo de registrá-la.**

Toda informação solicitada ao usuário tem que justificar o próprio custo. Campo
que não muda decisão nem alimenta métrica é dívida cobrada em atrito, todo dia,
para sempre.

É o critério que decide inclusão de campo neste projeto, e é ele que recusa
§8.4.

### 9.2 O atrito é existencial

Registrar um lote ou uma revisão em **≤ 20 segundos, ≤ 5 campos visíveis**. Não
é meta de usabilidade: é condição de existência dos dados. Acima disso o
registro não acontece, o dado não existe, e nenhuma métrica deste documento
pode ser calculada.

### 9.3 Nunca mentir com número verdadeiro

Nenhuma tela afirma mais do que o n sustenta. As regras de §7 (M-1) são
vinculantes, não sugestões de estilo.

### 9.4 Que decisão isto muda?

Toda tela, gráfico e indicador tem que sobreviver a essa pergunta. O que não
sobrevive não entra.

---

## 10. Riscos ao produto

| Risco | Por que é grave | Mitigação |
|---|---|---|
| **Atrito de registro** | Mata o produto inteiro (§9.2) | ≤ 20 s, ≤ 5 campos. Requisito |
| **Ruído lido como progresso** | O sistema mente com números verdadeiros e perde a confiança de vez | Regras de n de M-1 |
| **Especificação infinita** | 20 documentos e o sistema não cadastra uma disciplina. Documento nunca falha em teste | Nenhum documento novo sem mudança de código |
| **Volume insuficiente** | 2.600 questões/ano é pouco para M-1. Se cair, M-1 vira decorativa | M-2 e M-3 não dependem de volume de questões |
| **Excesso de assuntos** | 300 assuntos = 600 revisões/ano só de manutenção | Intervalo de manutenção configurável; revisar vários irmãos num registro |

---

## 11. O que este documento decide

Vinculante para todos os demais:

1. O evento central é uma **tentativa de recuperação com resultado**.
2. A recuperação acontece **fora**; o sistema registra e serializa.
3. Há **duas formas** de recuperar: lote de questões (objetivo) e recuperação
   livre. A segunda é o caminho principal, por aritmética.
4. Recuperação livre exige **produção antes do julgamento** (§5.2.1). O sistema
   nunca pergunta se você acha que lembra.
5. Toda recuperação produz **`SUCESSO` · `PARCIAL` · `FALHA`**. O tipo muda como
   se apura, nunca o que significa.
6. **Desempenho não muda o intervalo; muda o caminho.** Falha volta para
   estudo.
7. As quatro métricas da §7 são o critério de sucesso.
8. Prova é **global-trimestral** ou **disciplina-anual**. Assunto é sinal.
9. Percentual sem denominador é proibido em qualquer tela.
10. Assunto obedece à **regra de granularidade** de §6.1.
11. **Sessão é 1:1 com assunto**, uma estratégia por sessão. Registro em lote é
    interface; o domínio não tem n:n.
12. Não existe "aprendido": existe consolidado → **manutenção perpétua**.
13. Revisão é sugerida, nunca obrigatória.
14. **Cadastrado ≠ em aprendizagem.** Existe **frente de estudo** com teto, e
    assunto novo só é sugerido quando há vaga. O algoritmo pergunta primeiro se
    cabe, depois o quê.
15. **Simulado não é sessão**: evento por disciplina, alimenta só M-1.
16. O banco de erros é a **camada explicativa**, não funcionalidade lateral.
17. *Streak* fora; horas rebaixadas; cobertura ponderada; confiança por lote
    recusada.
18. Recomendação desce de prioridade, e passa a ter **tipo** (leia / resolva /
    revise).
19. Vale o princípio da **economia cognitiva** (§9.1).

## 12. O que fica em aberto

As cinco perguntas que esta seção trazia foram **todas respondidas** por
`01_DOMINIO.md`, e ficam registradas aqui só para rastreabilidade:

| Pergunta | Onde foi respondida |
|---|---|
| Limiares de `SUCESSO`/`PARCIAL`/`FALHA` | `01_DOMINIO` §4.2 — 80% / 60–79% / <60%, parametrizados |
| O que `PARCIAL` faz com a escada | §5.1 — repete o degrau |
| Onde vive a previsão de M-3 | §3.3 — atributo da sessão de recuperação |
| Teoria e questões: mesma entidade? | §3.3 — uma entidade, quatro tipos |
| `FALHA` em manutenção desfaz consolidação? | §5.4 — sim; volta à escada e à fila de estudo |

**Nada deste documento está pendente.** O que resta em aberto é calibragem, e
está em `01_DOMINIO` §12.

---

## 13. Changelog

| Versão | Data | Mudança |
|---|---|---|
| 1.6.0 | 2026-08-31 | §7 M-3: nota explícita de que a granularidade do par previsto×real da variante subjetiva é decisão de tela (`02_JORNADAS §4.1`), não deste documento — fecha uma contradição achada em auditoria (`/agents/mentor.md`): a frase solta "vai conseguir reconstruir? / conseguiu?" tinha sido lida como binária no código (`previsaoReconstrucao: Boolean`), enquanto `02_JORNADAS` já desenhava três botões e o próprio `resultado` de `RECUPERACAO` já é `SUCESSO`/`PARCIAL`/`FALHA` desde a Sprint 3 (D-07 exige isso de qualquer sessão que cumpre revisão). Não é mudança da métrica — é a mesma métrica, granularidade explicitada. Código corrigido na mesma sessão: `previsaoReconstrucao` passa a `ResultadoSessao` |
| ~~1.3.0–1.5.0~~ | — | Bump de versão sem changelog correspondente, achado nesta revisão — não reconstruído aqui (fora do escopo do que foi pedido); registrado para não se perder |
| 1.2.0 | 2026-08-19 | Segunda revisão do usuário. **Regra da produção obrigatória** (§5.2.1): recuperação livre exige produzir antes de julgar — o sistema nunca pergunta se você acha que lembra. **Resultado unificado** `SUCESSO`/`PARCIAL`/`FALHA` (§6.3): o tipo muda a apuração, não o significado; consolidação passa a depender do resultado e não do limiar. **Definição de sessão** (§6.2): 1:1 com assunto, uma estratégia por sessão, turno fora do modelo, registro em lote é interface. **Simulado** (§6.6): evento por disciplina, alimenta só M-1, implementação adiada. M-3 ganha duas variantes reportadas separadamente |
| 1.1.0 | 2026-08-19 | Revisão do usuário. Interleaving como 4º pilar, com a correção de que domínios distintos não o produzem. Feedback vira princípio (§3.5) e gera "desempenho muda o caminho". Calibração passa a registrar sinal. Granularidade de assunto vira regra. Consolidado e manutenção definidos. Banco de erros promovido a camada explicativa, com confiança no erro. Adotado o princípio da economia cognitiva. **Recusadas:** confiança 1–5 por lote (redundante com M-3) e campo estruturado de elaboração (já é o banco de erros). **Correção principal:** a aritmética de 50 questões/semana × 200 assuntos derruba "toda revisão é lote de questões" — recuperação livre passa a ser o caminho principal, e M-1 tem seus níveis de prova corrigidos |
| 1.0.0 | 2026-08-18 | Criado. Primeiro documento da especificação reordenada |
