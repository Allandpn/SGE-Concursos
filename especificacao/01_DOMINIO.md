# 01 — DOMÍNIO

Os conceitos, os eventos e as regras do sistema. **Sem tecnologia.**
**Precedência 2.** Subordinado apenas a `00_PRODUTO.md`.

| Campo | Valor |
|---|---|
| Versão | 1.15.0 |
| Data | 2026-09-01 |
| Status | Vigente |
| Documento anterior | `00_PRODUTO.md` |
| Documento seguinte | `02_JORNADAS.md` |

---

## 0. Escopo

Este documento define **o que existe** no domínio e **como as coisas se
comportam**. Não menciona tabela, coluna, tipo de dado, endpoint, classe ou
linguagem — nada disso é decisão de domínio, e antecipá-la aqui contamina a
modelagem com conveniência de implementação.

Ele fecha as cinco questões que `00_PRODUTO.md` §12 deixou abertas, e as fecha
com aritmética onde a intuição não bastava.

**Toda regra daqui é rastreável a uma linha de `00_PRODUTO.md`.** Onde não for,
é erro deste documento.

---

## 1. Glossário

Uma frase cada. Se um termo precisar de duas, é porque está mal definido.

| Termo | É |
|---|---|
| **Disciplina** | Agrupamento de assuntos, com peso no edital |
| **Assunto** | A unidade de estudo, na granularidade de `00_PRODUTO` §6.1 |
| **Sessão** | Uma estratégia aplicada a um assunto, uma vez, produzindo métricas coerentes |
| **Recuperação** | Uma sessão que produz **resultado** — tentativa de evocar, com desfecho |
| **Revisão** | Um **compromisso agendado** de recuperar um assunto numa data |
| **Escada** | A sequência de níveis por que um assunto passa até consolidar |
| **Frente de estudo** | O conjunto de assuntos que estão sendo aprendidos agora (§6) |
| **Manutenção** | O regime perpétuo depois da escada |
| **Erro** | Um engano registrado, com causa e com a confiança que havia ao cometê-lo |
| **Simulado** | Prova completa, cronometrada, medida por disciplina |

Note a separação que a v1 não tinha: **revisão é compromisso, recuperação é
evento.** A revisão agenda; a sessão de recuperação cumpre. Confundir os dois
foi o que fez a especificação antiga registrar data sem resultado.

---

## 2. O evento central

> **Uma tentativa de recuperação, com resultado.**

Tudo gira em torno dele. Um assunto avança porque recuperações tiveram sucesso;
regride porque falharam; consolida porque acumulou sucessos. Sessão de teoria
não move nada sozinha — ela apenas habilita a próxima recuperação.

Todo evento de recuperação carrega, obrigatoriamente:

| Componente | Por quê |
|---|---|
| O assunto | Atribuição (`00_PRODUTO` §6.1) |
| A previsão, feita **antes** | M-3 (`00_PRODUTO` §7) |
| A produção, feita **antes** do julgamento | Regra da produção obrigatória (§5.2.1) |
| O resultado, em três níveis | §4 deste documento |
| Quando aconteceu | Tudo o mais depende de tempo |

---

## 3. As entidades

Seis. Um sétimo conceito — parâmetro de negócio — é configuração, não domínio,
e aparece só onde uma regra depende dele.

### 3.1 Disciplina

Agrupa assuntos. Tem nome, peso no edital e um estado ativo/arquivado.

É o **nível em que a medição funciona** (`00_PRODUTO` §7 M-1). Essa é a razão de
ela existir como entidade, e não apenas como rótulo do assunto.

O **nome é único**, mesmo depois de arquivada — arquivar não libera o nome
para reuso (D-47). Mesma semântica que o nome do assunto já tem dentro da
disciplina (§3.2, D-41 trata a ordem; a unicidade de nome do assunto está em
`ux_assunto_j1_nome_por_disciplina`).

### 3.2 Assunto

A unidade. Pertence a uma disciplina. Tem nome, peso no edital, dificuldade
percebida, **ordem dentro da disciplina** e estado ativo/arquivado.

A **ordem** é a prioridade no backlog: quando uma vaga abre na frente, entra o
assunto de menor ordem ainda não iniciado daquela disciplina. É o que faz
*Modelagem Conceitual* entrar antes de *Modelagem Lógica* sem ninguém precisar
decidir na hora.

A ordem é **única entre os assuntos ativos da mesma disciplina** (D-48) — sem
isso, "o assunto de menor ordem" deixaria de ter resposta única quando dois
empatassem, e a vaga na frente passaria a depender de acidente de
armazenamento, não da regra. Ao contrário do nome (D-47), arquivar **libera**
o número: ordem não é identidade permanente do assunto, é só a disputa pela
próxima vaga — perde sentido fora do backlog.

O assunto **não guarda a própria fase**. Ver §8.

### 3.2.1 Granularidade — o critério é o toque de 8 minutos

**Regra** (`00_PRODUTO` §6.1): o assunto cabe numa recuperação de ~8 minutos e
comporta a frase *"eu sei isto"* sobre o todo.

Assunto grande demais não é revisado: é **amostrado**. Uma recuperação de 8
minutos sobre "Linguagem SQL (DDL, DML, DQL, DCL, TCL)" cobre um pedaço
diferente a cada vez, e o resultado `SUCESSO` significa "acertei a fatia que me
veio à cabeça hoje" — que não é o que a escada supõe ao subir de nível.

#### O custo de dividir é menor do que parece

O medo natural é que granularidade fina exploda o sistema. A conta diz que não,
porque **dividir não cria conteúdo novo — só o rotula melhor.** As horas de
teoria são as mesmas; o que cresce é o número de recuperações, e cada uma custa
8 minutos.

| Assuntos | Horas totais | Meses (limite horas) | Frente necessária | Recup./dia | Min/dia em revisão | % do dia |
|---|---|---|---|---|---|---|
| 171 | 552 | 9,4 | 120 | 5,5 | 44 | 38% |
| 220 | 612 | 10,4 | 139 | 6,4 | 51 | 44% |
| **260** | **661** | **11,3** | **152** | **7,0** | **56** | **48%** |
| 300 | 710 | 12,1 | 163 | 7,5 | 60 | 52% |
| 340 | 759 | 12,9 | 173 | 7,9 | 64 | 55% |

Dobrar a granularidade custa **cerca de três meses** e leva a revisão de 38%
para 52% do dia.

E metade do dia em recuperação **não é desperdício** — é a atividade de maior
valor comprovado (`00_PRODUTO` §3.1). A intuição de que "estudar conteúdo novo"
é a parte produtiva é exatamente a ilusão que este documento inteiro combate.
O que a granularidade grossa economiza em tempo, ela paga em retenção que
ninguém mediu.

#### Dividir por peso, e depois por evidência

Não é preciso acertar a granularidade de antemão.

1. **Comece dividindo por peso.** Assunto de peso alto merece granularidade
   fina, porque vai receber a escada inteira e cada degrau precisa acertar o
   alvo. Peso baixo pode ficar grosso: uma escada curta e rasa sobre um bloco
   largo é uma troca honesta.
2. **Depois deixe o sistema apontar.** Assunto que devolve `PARCIAL`
   repetidamente é o sintoma clássico de assunto grande demais: você sabe uma
   parte e erra outra, sempre, e a escada nunca sobe.

> **Regra:** após N resultados `PARCIAL` seguidos no mesmo assunto (partida:
> **4**), o sistema sugere dividi-lo. Não divide sozinho — sugere, e explica o
> porquê.

É diagnóstico barato e é o próprio dado do usuário corrigindo uma decisão que
ele tomou antes de ter dado nenhum.

### 3.2.2 Dividir um assunto é migração de dados, não edição de tela

A §3.2.1 prevê que assuntos serão divididos. Falta dizer **o que acontece com o
histórico**, porque `Sessão`, `Revisão` e `Erro` apontam para **um** assunto: se
"Normalização" vira "1FN a 3FN" e "BCNF", para onde vão as sessões antigas?

> **Regra da divisão.** O assunto original é **arquivado**; nascem N assuntos
> novos. **O histórico permanece no arquivado** — nenhuma sessão, revisão ou
> erro é reapontado.

| O que acontece | Por quê |
|---|---|
| Original arquivado | Cancela as revisões pendentes dele (D-17) |
| Histórico fica com o original | Reapontar seria **inventar** de qual metade veio cada resultado passado. Corromperia M-2 com um palpite |
| Novos nascem **no nível do original** | Você não desaprendeu ao renomear. Começar do nível 1 jogaria fora meses de escada real |
| Cada novo pode ter o nível baixado na hora | Só o usuário sabe qual metade estava quebrada |
| Ocupam vagas na frente | São N assuntos onde havia 1: o teto por disciplina pode estourar e o sistema avisa |

**Por que nascer no nível do original, e não no 1.** A divisão foi disparada por
`PARCIAL` repetido — alguma parte está quebrada. Mas o mecanismo que descobre
*qual* já existe: a metade ruim vai dar `FALHA` ou `PARCIAL` na primeira
recuperação e regride sozinha (§5.1). **Desempenho corrige; suposição não
precisa.** Rebaixar tudo por precaução seria o sistema decidindo o que ele pode
medir.

**O custo honesto:** M-2 dos assuntos novos começa vazia, e a série do arquivado
congela. A continuidade se perde na divisão. É preço de ter começado com a
granularidade errada, e é menor que o preço de um histórico com atribuição
inventada.

**A importação por arquivo não divide assunto** (`02_JORNADAS` J-1). É operação
de tela, com confirmação, porque envolve arquivar, criar N e mexer na frente
numa transação só.

### 3.3 Sessão

Uma estratégia, um assunto, uma vez. Relação com assunto: **1:1**
(`00_PRODUTO` §6.2).

Quatro tipos. Cada um existe porque alimenta uma combinação diferente de
métricas — tipo que não se distingue nessa matriz não deve existir:

| Tipo | Tempo | Contagem de acertos | Previsão | Resultado | Alimenta M-1 | Cumpre revisão |
|---|---|---|---|---|---|---|
| **ESTUDO** | sim | não | não | **não** | não | **não** |
| **QUESTOES** | sim | sim | sim | derivado da contagem | **sim** | sim |
| **FLASHCARDS** | sim | sim | sim | derivado da contagem | **não** | sim |
| **RECUPERACAO** | sim | não | sim | declarado após produção | não | sim |

Três consequências que essa matriz decide:

1. **`ESTUDO` não é recuperação.** Ler não produz resultado, não cumpre revisão
   e não consolida nada. É o que habilita a recuperação seguinte.
2. **`FLASHCARDS` não alimenta M-1.** Cartão é material próprio, calibrado pela
   sua percepção de dificuldade, não pela banca. Misturar 90% em flashcards com
   62% em questões de banca infla a métrica que sustenta a tese inteira. Por
   isso é tipo próprio e não um rótulo de `QUESTOES`.
3. **`RECUPERACAO` é o caminho principal**, por aritmética (`00_PRODUTO` §5.3).

Toda sessão tem, opcionalmente, a **próxima sessão pretendida** — quando e o
quê. É o substituto do *streak* (`00_PRODUTO` §8.2): intenção de implementação,
olhando para frente, em vez de contador pressionando para trás.

### 3.3.1 Registrar duas vezes conta uma

O registro acontece pela rede, e rede falha na metade. A resposta se perde, o
usuário acha que não gravou e clica de novo — e o sistema, se for ingênuo,
grava **duas** recuperações do mesmo assunto, sobe a escada dois degraus e
contamina a série de retenção com um evento que não aconteceu.

> **Uma tentativa de registro é identificada pelo cliente e é única.** Reenviar
> a mesma tentativa devolve sucesso sem gravar de novo.

A identificação é **da tentativa, não do conteúdo**. Recuperar o mesmo assunto
duas vezes no mesmo dia é uso legítimo e tem de continuar possível — o que não
pode acontecer duas vezes é *o mesmo envio*.

Isso não é detalhe de transporte: é a diferença entre o histórico refletir o que
você fez e refletir o que a sua conexão fez. E é barato — a identificação nasce
no cliente, antes do primeiro envio.

A mecânica fica em `03_INVARIANTES` e no documento de persistência.

### 3.4 Revisão

**São dois processos, e separá-los é o que destrava tudo:**

```
Agendamento          decidir que um assunto deve ser recuperado, e quando
   ↓                 → produz uma Revisão
Execução             recuperar de fato, com resultado
                     → produz uma Sessão de recuperação
```

Eles têm gatilhos, momentos e donos diferentes. O agendamento é automático e
olha para o futuro; a execução é humana e produz o dado. A especificação antiga
fundia os dois numa entidade só — por isso ela conseguia registrar *quando* uma
revisão foi feita e era incapaz de registrar *como foi*.

A revisão é o lado do agendamento: um compromisso de recuperar um assunto a
partir de uma data.

| Tem | Papel |
|---|---|
| O assunto | O que recuperar |
| O nível | Onde está na escada; determina o intervalo |
| A data prevista | Quando |
| A sessão de origem | Qual estudo iniciou esta escada |
| A sessão que a cumpriu | Preenchida ao ser cumprida; **é onde vive o resultado** |
| A situação | Pendente, cumprida ou cancelada |

**Arquivar não é definitivo (D-49).** Reativar disciplina ou assunto existe
como operação simétrica a arquivar (D-17). Ela **nunca reescreve** a revisão
`CANCELADA` — histórico não se apaga aqui também, mesmo princípio de D-18 —
mas cria uma revisão `PENDENTE` **nova**, herdando o nível da última revisão
do assunto, com a data recalculada a partir de hoje pelo mesmo intervalo que
já regeria essa pendente (D-08, ou o intervalo de manutenção se o assunto já
estava consolidado). Sem revisão nenhuma antes de arquivar, reativar não cria
nada — volta exatamente como estava. Reativar uma disciplina não toca em
nenhum assunto que já esteja arquivado por conta própria — só restaura o que
o arquivamento *daquela* disciplina havia cancelado.

**A revisão não guarda resultado.** Ela aponta para a sessão de recuperação que
a cumpriu, e o resultado vive lá. Isso mantém um só lugar de verdade e faz uma
recuperação espontânea — feita sem revisão agendada — valer exatamente igual.

**Um assunto tem no máximo uma revisão pendente por vez.** Duas escadas
paralelas no mesmo assunto não significam nada e produzem revisão duplicada.

### 3.5 Erro

Um engano registrado. Pertence a um assunto; opcionalmente a uma sessão
(D-46).

| Tem | Papel |
|---|---|
| A descrição | **É a interrogação elaborativa** (`00_PRODUTO` §7.1), no momento de maior valor |
| A causa | Falta de conhecimento, esquecimento, interpretação, desatenção, pegadinha, chute, gestão de tempo |
| A **confiança que havia ao errar** | Efeito de hipercorreção: errar com certeza é o achado mais valioso do sistema |
| Resolvido ou aberto | Fecha o ciclo |

O erro é a **camada explicativa** das métricas, não funcionalidade lateral.
68% por desatenção e 68% por falta de conhecimento pedem intervenções opostas, e
só a causa distingue.

### 3.6 Simulado

Prova completa e cronometrada. Medido **por disciplina**, nunca por assunto
(`00_PRODUTO` §6.7).

Não é sessão, não gera revisão, não consolida nada. Alimenta **apenas M-1** — e
é a entrada mais pura que M-1 tem, porque é mista, cronometrada e é o melhor
preditor isolado de desempenho na prova.

Desenho decidido; implementação adiada.

---

## 4. O resultado da recuperação

Três níveis, iguais para todo tipo (`00_PRODUTO` §6.3):

```
SUCESSO · PARCIAL · FALHA
```

### 4.1 Como cada tipo apura

| Tipo | Apuração |
|---|---|
| `QUESTOES`, `FLASHCARDS` | Percentual de acerto, contra dois limiares |
| `RECUPERACAO` | Declarado após a produção, com as âncoras abaixo |

#### As âncoras da recuperação livre

"Tudo / parte / nada" é escala ruim: *parte* vai de 10% a 90%, e quem faltou
pouco não sabe se marca *tudo* ou *parte*. Quantidade lembrada é justamente o
que ninguém consegue estimar — é a mesma ilusão da §3.4 de `00_PRODUTO`, agora
disfarçada de régua.

A escala é ancorada em **estrutura × detalhe**, que é distinção respondível:

| Resultado | Rótulo | Âncora |
|---|---|---|
| `SUCESSO` | **Reconstruí** | Cheguei aos pontos principais sem consultar. Faltou detalhe, não estrutura |
| `PARCIAL` | **Faltou um pedaço** | A estrutura veio, mas um bloco inteiro não. Sei o que faltou |
| `FALHA` | **Não veio** | Não reconstruí a estrutura. Preciso reler antes de tentar de novo |

"Faltou pouco" é `SUCESSO`, não `PARCIAL` — era exatamente a dúvida que a escala
antiga produzia. E `PARCIAL` ganha um sentido operacional: **você sabe qual
bloco faltou**, o que é o gatilho natural para registrar um erro.

Três níveis e não quatro porque são três ações na escada (§5.1). Nível que não
muda o que o sistema faz não deve existir.

### 4.2 Os limiares

Parâmetros de negócio. Valores de partida:

| Resultado | Percentual |
|---|---|
| `SUCESSO` | ≥ 80% |
| `PARCIAL` | 60% a 79% |
| `FALHA` | < 60% |

### 4.2.1 O formato da banca não é detalhe — é escala

O usuário mira **as duas famílias de banca**: múltipla escolha (FGV, FCC) e
certo/errado (Cebraspe). Elas têm **chões diferentes**: o acaso rende 20–25% na
primeira e **50%** na segunda.

Isso quebra duas coisas ao mesmo tempo:

1. **Os limiares.** 60% é desempenho fraco numa e é praticamente chute na outra.
2. **A M-1.** Ela agrega acerto entre lotes. Se metade for certo/errado e metade
   múltipla escolha, o percentual agregado **não significa nada** — mudar a
   mistura de lotes parece mudança de desempenho. É exatamente o defeito que fez
   `FLASHCARDS` virar tipo próprio (§3.3).

> **Toda sessão de `QUESTOES` registra o formato.** Um toque, dois valores.

| Formato | Acaso | `SUCESSO` | `PARCIAL` | `FALHA` |
|---|---|---|---|---|
| Múltipla escolha (4–5 alt.) | 20–25% | ≥ 80% | 60–79% | < 60% |
| Certo/errado | 50% | ≥ 90% | 75–89% | < 75% |

E **M-1 é reportada por formato, nunca somada.** Duas séries, lado a lado.

Poderia haver uma normalização — acerto acima do acaso — que permitisse juntar
as duas. Recusada: produziria um número que ninguém sabe ler, e a §9.3 de
`00_PRODUTO` proíbe tela que afirma mais do que o leitor consegue interpretar.

**Advertência histórica:** os valores de múltipla escolha supõem 4 ou 5
alternativas. É por isso
que são parâmetros, e é por isso que o padrão vem documentado com a suposição
que o justifica.

### 4.3 Lote mínimo

Um lote pequeno demais não classifica: com 3 questões, cada uma vale 33 pontos.

> Abaixo do mínimo parametrizado (partida: **5 questões**), a sessão é
> registrada e conta tempo, mas **não cumpre revisão** e não move a escada.

---

### 4.4 Onde vive o modelo de aprendizagem

As seções §5, §6 e §7 formam **um modelo só**, ainda que os títulos pareçam
tratar de coisas diferentes. Este é o mapa, para não ser preciso remontá-lo a
cada leitura:

| Conceito | Onde | Responde |
|---|---|---|
| **Estratégia de recuperação** | §4, §5.1 | Como uma tentativa vira resultado, e o que o resultado faz |
| **Capacidade de aprendizagem** | §5.3, §7.2 | Quanto cabe num dia e numa semana |
| **Frente de estudo** | §6.1–§6.3 | O que está sendo aprendido agora, e por que esse tamanho |
| **Critério de abertura** | §6.7 | Quando um assunto novo pode entrar |
| **Critério de saída** | §5.5, §6.4 | Quando um assunto consolida e sai da frente |
| **Plano de turno** | §7.3, §7.4 | Como isso vira o dia de hoje |

**Não existe documento separado para isto**, e é deliberado: cada conceito já
tem um dono aqui. Um documento-ponte que os reunisse seria a mesma regra escrita
em dois lugares — o único defeito que esta documentação foi desenhada para
impedir.

---

## 5. A escada e o roteamento por resultado

### 5.1 A regra

`00_PRODUTO` §3.2 proíbe aperfeiçoar o agendador; §3.5 exige que desempenho
importe. As duas convivem assim:

> **Os intervalos são fixos. O que o desempenho move é a sua posição na
> escada.**

| Resultado | Efeito na escada | Efeito na fila de estudo |
|---|---|---|
| `SUCESSO` | Sobe um nível | — |
| `PARCIAL` | **Repete o mesmo nível** | — |
| `FALHA` | **Regride um nível** (piso: 1) | O assunto **volta para a fila de estudo** |

Isso não é algoritmo adaptativo. Não há fator de facilidade, não há intervalo
calculado: há uma escada fixa e três movimentos discretos sobre ela. A
diferença importa, porque é o que mantém o sistema explicável.

`FALHA` faz as duas coisas ao mesmo tempo — regride **e** roteia para estudo.
Isso é o que `00_PRODUTO` §3.5 exige: recuperação malsucedida sem correção é
desgaste, então o sistema pede a correção, não só o próximo intervalo.

### 5.2 Por que regride um nível, e não recomeça do zero

Foi simulado antes de ser decidido. Com 20.000 execuções por cenário, contando
quantas recuperações um assunto precisa até consolidar:

| Desempenho típico | Regride 1 nível | **Recomeça do nível 1** |
|---|---|---|
| bom — 70/25/05 | 9,1 recuperações | 10,2 |
| médio — 60/30/10 | 11,6 | 15,1 |
| fraco — 50/30/20 | 17,7 | 32,6 |
| ruim — 40/30/30 | 35,4 | **92,0** |

Recomeçar do zero **diverge**: quanto pior o desempenho, mais desproporcional a
punição, e o assunto entra num ciclo que consome o dia inteiro justamente de
quem já está com dificuldade. Regredir um nível é proporcional e estável.

### 5.3 A carga que isso gera — e o freio

Com ~200 assuntos e desempenho médio, distribuídos em 24 meses — números
substituídos pelos reais na §6.3, mas mantidos aqui porque a **forma** da conta
é o que importa:

| | Recuperações por dia | Tempo/dia (5 min cada) |
|---|---|---|
| Escada | 3,2 | |
| Manutenção | 1,1 | |
| **Total** | **4,3** | **~21 min** |

Sensibilidade — e é aqui que mora o risco:

| Desempenho | Total/dia | Tempo/dia |
|---|---|---|
| bom | 3,6 | 18 min |
| médio | 4,3 | 21 min |
| fraco | 6,0 | 30 min |
| ruim | 10,8 | 54 min |

**A carga de revisão cresce quando o desempenho cai** — exatamente quando o
usuário tem menos margem. Sem freio, o sistema vira fonte de pavor e é
abandonado, que é o único modo de falha que mata o produto de vez.

> **Advertência sobre esta conta.** Ela supõe os assuntos entrando de forma
> distribuída ao longo dos 24 meses. Se todos forem abertos nos primeiros meses,
> as revisões se concentram e o pico é muito pior. Essa distribuição não
> acontece sozinha — é a §6 que a impõe.

Dois freios, ambos obrigatórios:

1. **Teto diário de recuperações**, parametrizado. O que exceder rola para o dia
   seguinte, priorizado por urgência. O sistema nunca apresenta uma lista
   impossível.
2. **Alerta de represamento.** Quando o atraso acumulado passa do limiar, o
   sistema diz o que precisa ser dito: *pare de abrir assunto novo até drenar*.
   É a única situação em que ele opina sobre estratégia — e opina porque a
   aritmética é inequívoca.

Nenhum dos dois bloqueia nada (`00_PRODUTO` §6.6).

### 5.4 Antecipar: o que pode e o que a escada não aceita

Num dia de folga o usuário quer adiantar. São **três coisas diferentes**, e só
uma tem contraindicação:

| Adiantar… | Efeito | Regra |
|---|---|---|
| **Drenar o represado** — revisões já vencidas | Puro ganho | Livre. O teto é do que é *sugerido*, não do que é *permitido* |
| **Conteúdo novo** — mais blocos de teoria ou questões | Puro ganho | Livre, respeitado o teto da frente (§6.7) |
| **Revisão futura**, antes da data | **Encurta o espaçamento** | Janela de tolerância, abaixo |

Só a terceira cobra preço: fazer aos 20 dias uma revisão marcada para 90 é abrir
mão do intervalo que produz a retenção — e, pior, permitiria "consolidar" a
escada inteira numa semana, esvaziando o mecanismo.

> **Janela de tolerância:** a revisão só é cumprida a partir de
> `data prevista − 20% do intervalo do nível`. Nível 6 (90 dias) aceita 18 dias
> de antecipação; nível 1 (1 dia), nenhum.

Fora da janela, o usuário **ainda pode estudar o assunto** — a sessão é
registrada, conta tempo, conta em M-1 se for lote de questões. Ela só **não
cumpre a revisão** nem move a escada. A distinção é a mesma do lote mínimo
(§4.3): o esforço vale, o crédito não.

O teto diário (§5.3) nunca impede: ele governa o tamanho da lista **oferecida**.
Pedir mais é sempre possível, e num dia de folga é a coisa certa a fazer.

### 5.5 Manutenção

Assunto consolidado é recuperado a cada 120–180 dias, indefinidamente
(`00_PRODUTO` §6.4). Parâmetro.

| Resultado na manutenção | Efeito |
|---|---|
| `SUCESSO` | Continua consolidado; agenda a próxima manutenção |
| `PARCIAL` | Continua consolidado; **antecipa** a próxima para metade do intervalo |
| `FALHA` | **Sai de consolidado**, volta à escada no último nível, e vai para a fila de estudo |

Consolidado não é porta de mão única. Ao longo de 24 meses, tratá-lo como
permanente seria a própria ilusão de fluência codificada no modelo.

---

## 6. A frente de estudo

### 6.1 O buraco que ela tapa

Nada, até aqui, limita **quantos assuntos estão na escada ao mesmo tempo**.

O sistema convida ao comportamento clássico do concurseiro: abrir vinte
disciplinas na primeira semana. Cada assunto aberto gera uma revisão no dia
seguinte, outra em uma semana, outra em quinze dias — e revisões só param
quando o assunto consolida, o que leva mais de um ano. Abrir cedo demais cria
**dívida permanente**, e o cálculo de carga da §5.3 escondia isso, porque supôs
os assuntos distribuídos uniformemente ao longo do horizonte. Essa distribuição não
acontece por acidente: ou é imposta por uma regra, ou não acontece.

> **Frente de estudo** é o conjunto de assuntos que estão sendo *aprendidos
> agora* — os que recebem teoria, questões e recuperação.

Formalmente, é o conjunto `{em estudo} ∪ {em escada}` da §8. **Não é entidade
nova e não guarda estado**: é nome para um conjunto que já é derivável, mais uma
regra sobre o **tamanho** dele. O custo de modelagem é um parâmetro.

Assunto consolidado **sai da frente automaticamente** e fica só no ciclo de
manutenção. É isso que abre vaga.

### 6.2 Backlog — a frente tem uma fila de entrada

Um assunto cadastrado que ainda não entrou na frente está no **backlog**. Não é
descuido nem esquecimento: é fila, e o sistema a administra.

O mesmo padrão vale um nível acima. Uma **disciplina** cadastrada e ainda não
ativa também está no backlog — e é assim que se representa a estratégia real do
usuário sem inventar conceito novo:

| | Disciplinas |
|---|---|
| **Núcleo inicial** (ativas) | Redes · Banco de Dados · Engenharia de Software · Segurança da Informação · Desenvolvimento · Português · Raciocínio Lógico · Gestão e Governança |
| **Expansão** (backlog) | Direito Tributário · Contabilidade · Economia · Finanças Públicas · o que o próximo edital trouxer |

Duas consequências práticas:

- **Edital novo não bagunça nada.** Saiu LGPD? Entra no backlog. A frente atual
  não muda, e o sistema decide quando há vaga.
- **Nada de estado novo.** Backlog de assunto é "sem sessão e fora da frente";
  backlog de disciplina é "cadastrada e não ativa". Derivado, os dois.

#### Um retrato, para não restar dúvida

Como o núcleo se distribui por volta do quinto mês, com frente de 100:

```
Assuntos cadastrados no núcleo        171
  ├── Frente de estudo                100   recebem teoria, questões, recuperação
  ├── Consolidados                     45   saíram da frente; só manutenção
  └── Backlog                          26   aguardando vaga

Disciplinas de expansão            backlog   Tributário, Contabilidade, Economia…
```

Quando um assunto consolida, ele sai da frente e **uma vaga é preenchida do
backlog da mesma disciplina**, automaticamente. Os 171 nunca estão todos
recebendo estudo novo ao mesmo tempo — e é exatamente isso que o sistema existe
para garantir.

A estratégia de esgotar um núcleo antes de expandir está certa, e o mecanismo
que a sustenta é **transferência**: conhecimento prévio no domínio é o preditor
mais forte de aprendizagem nova naquele domínio. Normalização em Banco de
Dados, modelagem em Engenharia de Software e ORM em Desenvolvimento se
sustentam mutuamente; Redes e Direito Tributário não. Estudar um núcleo coerente
rende mais que a mesma carga espalhada — e isso é transferência, não
interleaving (que é discriminação entre coisas confundíveis) nem carga
cognitiva.

### 6.3 O tamanho da frente não é escolha — é consequência

Esta é a parte contraintuitiva, e ela sobrevive a qualquer opinião.

A frente é uma fila com entrada e saída. Em regime estacionário vale a relação
mais antiga da teoria de filas: **tamanho da fila = taxa de entrada × tempo de
permanência.** O tempo de permanência é quanto um assunto leva para consolidar,
e isso já está determinado pela escada e pelo roteamento da §5.

Simulado, 30.000 execuções por cenário:

| Desempenho | Dias até consolidar | Recuperações |
|---|---|---|
| escada perfeita (teórico) | 203 | 6,0 |
| bom — 70/25/05 | **347** | 9,6 |
| médio — 60/30/10 | **443** | 12,5 |
| fraco — 50/30/20 | **617** | 19,2 |

Com o núcleo real do usuário — **171 assuntos** — e escada completa até o nível
6, o resultado é desconfortável:

| Frente | Meses para cobrir os 171 | Carga/dia |
|---|---|---|
| 50 | **50,3** | 1,4 |
| 60 | **41,9** | 1,7 |
| 75 | **33,5** | 2,1 |

Uma frente de 60 leva **3 anos e meio** só para o núcleo inicial — antes de
abrir a primeira disciplina fiscal. A carga diária é baixíssima (1,7), o que
denuncia o problema: não falta esforço, **sobra espera**. O gargalo não é você;
é a escada.

### 6.4 A saída: a escada não precisa ser a mesma para todo assunto

`00_PRODUTO` §6.4 define consolidação, mas nunca disse que **todo** assunto
precisa consolidar. Aquilo foi suposição, e é ela que está apertando a conta.

Quanto custa parar em cada nível (desempenho médio):

| Sobe até | Dias | Recuperações | Manutenção adequada |
|---|---|---|---|
| 3 | 52 | 6,4 | 60–90 dias |
| **4** | **118** | **8,5** | 90–120 dias |
| 5 | 251 | 10,5 | 120 dias |
| 6 | 447 | 12,5 | 120–180 dias |

Parar no nível 4 custa **um quarto do tempo** do nível 6 e entrega 30 dias de
intervalo — que, com manutenção a cada 90–120 dias, é retenção perfeitamente
respeitável para assunto de peso médio.

> **O nível-alvo da escada é função do peso do assunto no edital, não uma
> constante do sistema.**

Isso não viola `00_PRODUTO` §3.2. Os **intervalos continuam fixos**; o que muda
é **quantos degraus** aquele assunto merece. Não é agendador adaptativo — é
priorização, que é decisão estratégica e não cálculo.

Com uma distribuição plausível de pesos, tudo se encaixa:

| Configuração | Permanência média | Frente 60: meses | Carga/dia |
|---|---|---|---|
| Tudo nível 6 | 447 d | 41,9 | 1,7 |
| **30% n6 · 45% n4 · 25% n3** | **200 d** | **18,8** | **2,7** |
| 20% n6 · 50% n4 · 30% n3 | 164 d | 15,4 | 3,2 |
| Tudo nível 4 | 118 d | 11,1 | 4,3 |

**A linha do meio é a recomendação:** frente de 60, escada por peso, núcleo de
171 assuntos coberto em ~19 meses, a 2,7 recuperações por dia. Sobra folga para
desempenho pior e para a expansão fiscal depois.

### 6.5 O orçamento de horas — o outro limite

A fila diz quanto tempo leva. Ela não diz se cabe no seu calendário. Esse é um
segundo limite, independente:

| Teoria por assunto | Escada até | Teoria | Recuperação | **Total do núcleo** |
|---|---|---|---|---|
| 2 h | nível 4 | 342 h | 194 h | **536 h** |
| 3 h | nível 4 | 513 h | 194 h | **707 h** |
| 3 h | nível 6 | 513 h | 285 h | **798 h** |

### 6.6 O dimensionamento real

O usuário estuda **13 h 30 por semana** — 1 h 30 de segunda a sexta, 3 h no
sábado e 3 h no domingo. Média de **116 minutos por dia**.

Com escada por peso na distribuição 30/45/25, o núcleo custa **551 h**, o que as
horas cobrem em **9,4 meses**. Esse é o piso: nenhuma frente entrega mais rápido
que isso.

| Frente | Fila (meses) | Gargalo | Carga/dia | Min/dia em revisão | % do dia |
|---|---|---|---|---|---|
| 60 | 18,8 | fila | 2,7 | 22 | 19% |
| 75 | 15,0 | fila | 3,4 | 27 | 24% |
| 90 | 12,5 | fila | 4,1 | 33 | 29% |
| **100** | **11,3** | fila | **4,6** | **37** | **32%** |
| 120 | 9,4 | horas | 5,5 | 44 | 38% |

**Frente de 60 é pequena demais para 13 h 30 por semana.** Ela entrega em 18,8
meses o que as horas entregariam em 9,4: metade do tempo seria passada esperando
revisão amadurecer, com o dia sobrando. O gargalo seria artificial.

> **Recomendação: frente de 100, teto de 12 por disciplina.**
> Núcleo em ~11 meses, 4,6 recuperações/dia, 37 minutos — **um terço do dia de
> estudo em revisão, dois terços em conteúdo novo.** É a proporção que a
> literatura de recuperação espaçada consideraria saudável, e ela caiu da conta
> em vez de ter sido escolhida.

### 6.6.1 Por que 100 não é tanto quanto parece

Cem assuntos na frente soa como cem pratos girando. Não é. **A frente mede
throughput; o que se sente no dia é a carga diária** — e ela é 4,6.

Um assunto no nível 5 é tocado uma vez a cada 60 dias. Ele está na frente, mas
não está na sua cabeça. Os que realmente ocupam atenção são os de nível 1 e 2 e
os que acabaram de receber teoria — um subconjunto pequeno e móvel.

O desconforto com o tamanho da frente é intuição enganosa. O número a vigiar é
**carga/dia**, e é ele que tem teto (§5.3).

### 6.7 As regras

1. **Teto global da frente**, parametrizado. Partida: **100** (§6.6).
2. **Teto por disciplina**, parametrizado. Partida: **12**. Resolve dois
   problemas de uma vez — impede que uma disciplina monopolize a frente, e é o
   que faz o núcleo avançar em **ondas** dentro de cada disciplina em vez de
   abrir os 28 assuntos de Redes de uma vez.
3. **Nível-alvo da escada derivado do peso do assunto** (§6.4), sem campo
   próprio: peso alto → nível 6, peso médio → nível 4, peso baixo → nível 3. O
   usuário classifica peso uma vez; o alvo sai sozinho.
4. **Frente cheia → o sistema não sugere assunto novo.** Passa a recomendar
   apenas o que já está na frente, até abrir vaga.
5. **Consolidar abre vaga**, e a vaga é preenchida do backlog da mesma
   disciplina, respeitando o teto dela.
6. **Não bloqueia** (`00_PRODUTO` §6.6). O usuário abre o assunto que quiser; o
   sistema informa que a frente está cheia e para de sugerir. Recomendação, não
   catraca.

O algoritmo passa a responder **duas** perguntas, e a segunda é a que quase
nenhum aplicativo de estudo faz:

```
1. Qual assunto estudar agora?
2. Existe espaço para abrir um assunto novo?
```

Isso torna o sistema um **orientador de estratégia**, e não um registrador de
sessões — e é o único ponto, junto com o alerta de represamento da §5.3, em que
ele opina sobre como estudar. Opina porque a aritmética é inequívoca.

### 6.8 Uma ressalva sobre a justificativa

A frente de estudo é corroborada por **mastery learning** — dominar o conjunto
atual antes de ampliar o escopo é literalmente a tese de Bloom.

Já **carga cognitiva** (Sweller) não sustenta este conceito: aquela teoria trata
do limite da memória de trabalho *dentro de uma tarefa*, em minutos, não de
quantos tópicos manter em aberto ao longo de meses. Invocá-la aqui seria
empréstimo indevido.

Vale registrar que **o argumento forte é aritmético, não cognitivo.** As §6.3 a
§6.5 se sustentam sozinhas, sem citar literatura nenhuma. A ciência corrobora; a
fila decide.

---

## 7. O turno: quantos assuntos por dia

A pergunta "quantos assuntos consigo estudar num turno de 1 h 30?" parece de
interface e é de domínio: ela determina o que o algoritmo de recomendação
precisa devolver.

E ela tem resposta aritmética. O que faltava era perceber que **nem toda sessão
tem o mesmo tamanho.**

### 7.1 O erro de tratar sessão como bloco uniforme

Raciocinar em "blocos de 45 minutos" quebra na primeira conta. A §6.6 exige
**4,6 recuperações por dia**. Se cada uma fosse um bloco de 45 minutos, o dia
precisaria de 3 h 30 só de revisão — três vezes o que existe.

A saída não é revisar menos. É que os dois tipos de sessão têm naturezas
diferentes:

| | Duração | Quantidade | O que é |
|---|---|---|---|
| **Recuperação** | ~8 min | muitas | Fechar o material, reconstruir, registrar |
| **Conteúdo** (teoria, lote de questões) | 45–75 min | poucas | Aprender ou praticar de verdade |

Recuperação livre não é bloco de estudo: é **toque**. Reconstruir de memória os
pontos de `JOIN` leva minutos, não uma hora.

### 7.2 O orçamento semanal

Com 13 h 30 (810 min), front de 100 e menos de 50 questões por semana:

| | Minutos/semana | % | Equivale a |
|---|---|---|---|
| Recuperação | 258 | 32% | 32 recuperações de ~8 min |
| Questões | 75 | 9% | ~50 questões |
| **Teoria** | **477** | **59%** | ~8 h |

As 8 h semanais de teoria cobrem as 342 h do núcleo em **~10 meses** — coerente
com a fila da §6.6. Os dois limites fecham.

### 7.3 A forma real do turno

**Turno de 1 h 30, segunda a sexta:**

```
Fila de recuperação    ~37 min    4 a 5 assuntos, ~8 min cada
Um bloco de conteúdo   ~53 min    UM assunto: teoria ou lote de questões
```

**Turno de 3 h, sábado e domingo:**

```
Fila de recuperação    ~37 min    4 a 5 assuntos
Dois blocos            ~72 min cada   DOIS assuntos
```

> **Você toca 6 a 7 assuntos por dia, mas estuda 1 (semana) ou 2 (fim de
> semana).** Os outros 4 a 5 são toques de recuperação de oito minutos, que não
> disputam a atenção do bloco de conteúdo.

Isso dissolve a preocupação que originou esta seção. A intuição de "só dá para
fazer dois assuntos no turno de 1 h 30" estava certa **sobre o conteúdo** e não
enxergava a fila de recuperação, que é barata e cabe folgada.

### 7.4 O que o algoritmo devolve

Consequência direta: o algoritmo não recomenda *assunto*. Recomenda **sessão** —
e, melhor, o **plano do turno inteiro**:

```
Hoje · 1 h 30

Fila de recuperação (37 min)
  · JOIN · Normalização · TCP/IP · Crase · Criptografia simétrica

Bloco de conteúdo (53 min)
  · Engenharia de Software → Padrões de projeto → TEORIA
```

O plano é **derivado e efêmero**. Não se guarda, não vira entidade: é recalculado
a cada pedido, a partir da fila de revisões vencidas, da frente e dos tetos. Se
o usuário fizer outra coisa, nada quebra — ele nunca prometeu nada
(`00_PRODUTO` §6.6).

### 7.5 O que é ajustável, e o que só parece ser

A tentação natural é criar um parâmetro "quantas revisões por dia". **Ele já
existe — é o teto diário da §5.3 — e não é o que parece.**

O teto é **válvula de segurança**, não seletor de carga. Baixá-lo não faz o
sistema pedir menos: faz o excedente rolar para amanhã. Teto abaixo da taxa
natural da frente é escolher acumular atraso todo dia, indefinidamente.

| Botão | É botão de verdade? | O que faz |
|---|---|---|
| **Tamanho da frente** | **sim — é o único** | Define vazão **e** carga diária |
| Nível-alvo por peso | sim | Já decidido: D-23 |
| Teto diário | válvula | Abaixo da taxa natural → represamento perpétuo |
| Quantas revisões por dia | **não** | É *consequência* dos dois primeiros |
| Agrupar ou espalhar no turno | preferência | Zero efeito sobre a carga |

> **Regra de coerência:** se o teto diário ficar abaixo da taxa implicada pela
> frente, o sistema **avisa**. Dois parâmetros que se contradizem em silêncio
> produzem um atraso que ninguém escolheu.

**Quem quiser menos revisão por dia mexe na frente, não no teto.** O painel:

| Frente | Recup./dia | Min/dia em revisão | Meses p/ cobrir o núcleo | Sobra p/ conteúdo |
|---|---|---|---|---|
| 50 | 2,3 | 18 | 22,5 | 97 min |
| 65 | 3,0 | 24 | 17,3 | 92 min |
| 80 | 3,7 | 29 | 14,1 | 86 min |
| **100** | **4,6** | **37** | **11,2** | **79 min** |
| 120 | 5,5 | 44 | 9,4 | 72 min |

Menos revisão por dia é sempre a mesma troca: **mais meses até cobrir o
núcleo.** Não existe configuração que dê as duas coisas.

*(Piso: 9,4 meses. É o limite das horas — nenhuma frente entrega antes disso.)*

### 7.6 Agrupar ou espalhar as recuperações

Preferência do usuário, **sem parâmetro**.

Padrão: **fila de recuperação primeiro**, antes do bloco de conteúdo. Dois
motivos, e o segundo é o que decide:

1. Limpa a fila enquanto ela é obrigação, não sobra do dia.
2. **Recuperação com a atenção fresca produz sinal mais limpo.** O resultado
   alimenta M-2 e o roteamento da §5.1; medir depois de 50 minutos de teoria
   mistura esquecimento com fadiga, e o sistema não sabe distinguir os dois.

Num turno longo, pôr a fila **entre** os dois blocos também é bom: a pausa vira
produtiva. Mas isso é escolha de tela, não de domínio.

Não vira parâmetro por economia cognitiva (`00_PRODUTO` §9.1): não há
consequência mensurável, e o sistema não fiscaliza ordem nenhuma (D-14).

### 7.7 Sobre a duração dos blocos — o que não é ciência

O bloco de 45–55 minutos é **convenção**, não achado. Ele vem de período
escolar e de tradição de cursinho, não de estudo controlado. A literatura
sustenta com firmeza o **espaçamento** e sustenta que atenção sustentada degrada
com tempo em tarefa — mas **não** estabelece um ótimo de 50 minutos, e o
Pomodoro de 25 é igualmente arbitrário.

Consequência de projeto: **não cravar duração no domínio.** Os blocos são
sugestão parametrizada, e o próprio sistema mede a duração real — toda sessão
registra tempo. Depois de alguns meses o dado dele vale mais que qualquer
convenção.

### 7.8 Uma correção sobre interleaving — a terceira

Alternar Redes e Português dentro do turno **não é interleaving**, pelo mesmo
motivo da §3.3 de `00_PRODUTO`: interleaving exige material **confundível**, e
essas duas disciplinas não se confundem.

O ganho de alternar existe e é real — variedade, menos fadiga, espaçamento
dentro do dia. Só não é o mecanismo de discriminação, e o crédito precisa ir
para o lugar certo.

O interleaving de verdade acontece **dentro de um bloco**: um lote misto de
`INNER` / `LEFT` / `CROSS JOIN`, ou de crase antes de pronome / topônimo /
masculino. Isso é escolha de lote na plataforma externa, não arranjo de turno.

A confusão é compreensível: a divulgação popular de técnicas de estudo descreve
interleaving como "misture suas matérias", e é exatamente essa formulação que a
revisão sistemática contradiz.

---

## 8. A fase do assunto é derivada

O assunto **não guarda** em que ponto do processo está. A fase é calculada:

| Fase | Como se reconhece |
|---|---|
| **Backlog** | Cadastrado, sem sessão, fora da frente (§6.2) |
| **Em estudo** | Tem sessão, sem revisão pendente, sem escada concluída |
| **Em escada** | Tem revisão pendente com nível abaixo do máximo |
| **Consolidado** | Escada concluída **e** os dois últimos resultados `SUCESSO` |
| **Em manutenção** | Consolidado com revisão pendente de manutenção |

**A frente de estudo é `{em estudo} ∪ {em escada}`** (§6). Também derivada:
nenhum assunto carrega uma marca de "estou na frente".

O único estado realmente guardado é **ativo ou arquivado** — decisão do usuário,
que nada deriva.

Motivo: estado derivado que se persiste diverge do que o gera, e a divergência
só aparece quando alguém confia nela. A regra é antiga no projeto e sobrevive
intacta à reordenação.

---

## 9. Relações

```
Disciplina 1 ──── N Assunto
Assunto    1 ──── N Sessão            (1:1 no sentido sessão → assunto)
Assunto    1 ──── N Revisão           (no máximo 1 pendente por vez)
Assunto    1 ──── N Erro
Revisão    N ──── 1 Sessão (origem)   qual estudo iniciou a escada
Revisão    1 ──── 1 Sessão (cumpriu)  onde vive o resultado
Sessão     1 ──── N Erro              opcional
Disciplina 1 ──── N ResultadoSimulado
```

**Não existe relação n:n em lugar nenhum.** Registrar quatro assuntos irmãos de
uma vez grava quatro sessões (`00_PRODUTO` §6.2) — é tela, não modelo.

---

## 10. Regras de domínio

Numeradas para serem citadas. Cada uma aponta para sua origem.

| # | Regra | Origem |
|---|---|---|
| D-01 | Sessão pertence a exatamente um assunto e aplica exatamente uma estratégia | `00P` §6.2 |
| D-02 | Só `QUESTOES`, `FLASHCARDS` e `RECUPERACAO` produzem resultado | §3.3 |
| D-03 | `RECUPERACAO` exige produção declarada antes de o resultado ser oferecido | `00P` §5.2.1 |
| D-04 | A previsão é registrada antes do resultado e não é editável depois | `00P` §7 M-3 |
| D-05 | Um assunto tem no máximo uma revisão pendente | §3.4 |
| D-06 | O resultado vive na sessão; a revisão aponta para ela | §3.4 |
| D-07 | `SUCESSO` sobe, `PARCIAL` repete, `FALHA` regride e roteia para estudo | §5.1 |
| D-08 | Intervalo de um nível nunca é recalculado | `00P` §3.2 |
| D-09 | Lote abaixo do mínimo não cumpre revisão nem move a escada | §4.3 |
| D-10 | Consolidado exige escada concluída e dois `SUCESSO` seguidos | `00P` §6.4 |
| D-11 | `FALHA` em manutenção desfaz a consolidação | §5.5 |
| D-12 | `FLASHCARDS` nunca entra em M-1 | §3.3 |
| D-13 | Simulado mede disciplina, nunca assunto; não gera revisão | `00P` §6.6 |
| D-14 | Nenhuma recuperação é obrigatória; o sistema sugere e registra o desvio | `00P` §6.5 |
| D-15 | A fila diária respeita o teto; o excedente rola por urgência | §5.3 |
| D-16 | A fase do assunto é derivada, nunca guardada | §8 |
| D-17 | Arquivar assunto ou disciplina cancela as revisões pendentes dependentes | §3.1, §3.2 |
| D-18 | Nada é apagado; arquivar é o único descarte | tradição do projeto |
| D-19 | A frente de estudo é `{em estudo} ∪ {em escada}`, derivada, com teto global e teto por disciplina | §6.1, §6.4 |
| D-20 | Frente cheia: o sistema deixa de sugerir assunto novo, sem bloquear a abertura manual | §6.7 |
| D-21 | Consolidar sai da frente e abre vaga automaticamente | §6.1 |
| D-22 | Consolidar não é obrigação de todo assunto; a frente é priorizada por peso no edital | §6.4 |
| D-23 | O **nível-alvo** da escada é função do peso do assunto, não constante do sistema. Os intervalos continuam fixos | §6.4 |
| D-24 | Assunto fora da frente e sem sessão está no **backlog**; disciplina não ativa também | §6.2 |
| D-25 | Vaga aberta por consolidação é preenchida do backlog da mesma disciplina, respeitando o teto dela | §6.7 |
| D-26 | Recuperação é **toque** (~8 min), conteúdo é **bloco** (45–75 min). Tratar tudo como bloco estoura o dia | §7.1 |
| D-27 | O algoritmo devolve **plano de turno** — fila de recuperação + 1 ou 2 blocos —, não "próximo assunto" | §7.4 |
| D-28 | O plano de turno é derivado e efêmero: nunca persistido | §7.4 |
| D-29 | Duração de bloco é parâmetro, não constante do domínio; o sistema mede a real | §7.7 |
| D-30 | Revisões/dia **não é parâmetro**: é consequência da frente. O teto diário é válvula, não seletor | §7.5 |
| D-31 | Teto diário abaixo da taxa implicada pela frente → o sistema avisa | §7.5 |
| D-32 | Ordem das recuperações no turno é preferência, sem parâmetro. Padrão: fila primeiro, com atenção fresca | §7.6 |
| D-33 | Granularidade do assunto: cabe numa recuperação de ~8 min e comporta "eu sei isto" sobre o todo | §3.2.1 |
| D-34 | Peso alto → granularidade fina; peso baixo pode ficar grosso | §3.2.1 |
| D-35 | Após N `PARCIAL` seguidos (partida: 4), o sistema **sugere** dividir o assunto. Nunca divide sozinho | §3.2.1 |
| D-36 | Sessão de `QUESTOES` registra o **formato da banca**; limiares e M-1 são por formato, nunca somados | §4.2.1 |
| D-37 | Resultado da recuperação livre é ancorado em **estrutura × detalhe**, não em quantidade lembrada | §4.1 |
| D-38 | Revisão só é cumprida dentro da janela: a partir de `prevista − 20% do intervalo` | §5.4 |
| D-39 | Fora da janela a sessão é registrada e conta, mas não cumpre revisão nem move a escada | §5.4 |
| D-40 | O teto diário limita o que é **oferecido**, nunca o que é permitido. Pedir mais é sempre possível | §5.4 |
| D-41 | Assunto tem **ordem** dentro da disciplina; a vaga na frente é preenchida pela menor ordem do backlog | §3.2 |
| D-42 | Dividir assunto **arquiva** o original e cria N novos; o histórico **permanece no arquivado**, nunca é reapontado | §3.2.2 |
| D-43 | Os assuntos nascidos de uma divisão começam **no nível do original**; o usuário pode baixar cada um | §3.2.2 |
| D-44 | Divisão é operação de tela, transacional. Importação por arquivo não divide | §3.2.2 |
| D-45 | Registrar a mesma sessão duas vezes conta **uma**. Repetir o envio nunca move a escada duas vezes | §3.3.1 |
| D-46 | Todo erro aponta para um assunto existente; se apontar para uma sessão, ela também precisa existir | §3.5 |
| D-47 | Nome de disciplina é único; arquivar não libera o nome para reuso | §3.1 |
| D-48 | Ordem do assunto é única entre os ativos da mesma disciplina; arquivar libera o número | §3.2 |
| D-49 | Reativar cria uma revisão pendente nova, no nível da última; nunca restaura a cancelada | §3.4 |

---

## 11. Decisões deste documento

Com a alternativa recusada, para não serem reabertas por engano.

| Decisão | Alternativa recusada | Por quê |
|---|---|---|
| Uma entidade Sessão com quatro tipos | Entidades separadas por tipo | "O que fiz neste assunto" é a consulta mais frequente e não deve juntar fontes. Os tipos não se misturam: eles discriminam |
| Resultado na sessão, não na revisão | Resultado na revisão | Faz recuperação espontânea valer igual à agendada, e mantém um só lugar de verdade |
| `FLASHCARDS` como tipo próprio | Rótulo dentro de `QUESTOES` | Cartão próprio infla M-1; a separação protege a métrica que sustenta a tese |
| `FALHA` regride um nível | Recomeçar a escada | Simulação: recomeçar diverge (92 recuperações/assunto no cenário ruim) |
| Fase derivada | Fase guardada no assunto | Estado derivado persistido diverge, e a divergência só aparece quando confiam nela |
| Teto diário com transbordo | Mostrar tudo o que venceu | A carga cresce quando o desempenho cai; lista impossível faz abandonar o sistema |
| Intenção de próxima sessão como atributo | Entidade "plano" | Dois campos opcionais não justificam entidade (economia cognitiva) |
| Frente de estudo como conjunto derivado | Entidade ou marca no assunto | Já é derivável de `{em estudo} ∪ {em escada}`; marcar seria estado duplicado que diverge (§7) |
| Teto global **e** por disciplina | Só um dos dois | Carga e balanceamento são problemas diferentes: o global impede a dívida, o por disciplina impede o monopólio |
| Frente dimensionada pela fila | Número escolhido por intuição | O tamanho é forçado por `assuntos × permanência ÷ horizonte`. Frente de 60 com escada completa cobre 171 assuntos em 42 meses, não em 24 |
| Nível-alvo variável por peso | Escada igual para todo assunto | É o que reconcilia a fila com o orçamento de horas (§6.5). Escada igual em tudo faz esperar 42 meses o que as horas entregariam em 15 |
| Backlog como fase derivada, em assunto e disciplina | Lista separada, ou marca de "planejado" | É o mesmo conjunto que já existia sob o nome errado ("não iniciado"). O nome mudou o comportamento do algoritmo, não o modelo |

---

## 12. O que fica em aberto

Para `02_JORNADAS.md` e para o documento de regras:

- ~~O valor de partida do **teto diário** de recuperações. A simulação diz que
  5 cobre o cenário médio e sufoca o fraco; 8 cobre o fraco e nunca aperta o
  bom.~~ **Fechado na Sprint 5** (`docs/SPRINT-5-FRENTE.md` §0): **8** —
  nunca aperta quem vai bem, ainda cobre quem vai mal.
- ~~O limiar de **represamento** que dispara o alerta de parar conteúdo
  novo.~~ **Fechado na Sprint 5**: `represado > teto diário` — dispara quando
  o acumulado sozinho já não cabe num único dia. Deriva do teto diário em vez
  de introduzir um segundo parâmetro solto sem âncora nenhuma na
  especificação.
- Se a **manutenção** de vários assuntos irmãos deve ser agendada junta de
  propósito, para viabilizar lote misto — é o mecanismo de interleaving de
  `00_PRODUTO` §3.3, e ninguém decidiu se é acaso ou desenho.
- Se **erro aberto** deve influenciar a ordem da fila. Hoje não influencia, e a
  §3.5 sugere que talvez devesse.
- ~~Quantos níveis tem a escada, e se os intervalos de partida (`1, 7, 15, 30,
  60, 90`) continuam adequados a um horizonte de 24 meses.~~ **Fechado na
  Sprint 4** (`docs/SPRINT-4-ESCADA.md` §0.1): 6 níveis, intervalos `1, 3, 7,
  15, 30, 90` — progressão diferente da citada aqui (que nenhum outro trecho
  deste documento chegou a fixar como decisão, só como pergunta), escolhida
  com o usuário por ancorar os dois pontos que o §5.4 já fixava (nível 1 = 1
  dia, nível 6 = 90 dias) com crescimento mais suave entre eles.
- **A distribuição de pesos dos 171 assuntos.** A §6.4 supõe 30/45/25 entre os
  níveis-alvo 6/4/3, e todo o dimensionamento da §6.6 depende disso. Precisa
  virar classificação real, disciplina por disciplina. **É a única tarefa manual
  que o plano exige do usuário**, e ela pode ser feita aos poucos: assunto sem
  peso classificado assume o nível 4.
- Quanto tempo dura de fato uma recuperação livre. A §6.6 supõe **8 minutos** de
  média; se forem 15, a carga de 4,6/dia consome 69 min em vez de 37, e a frente
  de 100 fica apertada. É o número mais frágil de toda a conta, e o próprio
  sistema vai medi-lo nos primeiros meses.
- Se as 2 h de teoria por assunto se confirmam. Também será medido.

---

## 13. Changelog

| Versão | Data | Mudança |
|---|---|---|
| 1.15.0 | 2026-09-01 | **D-49**, nova: reativar disciplina/assunto é a operação simétrica de D-17, mas nunca restaura a revisão `CANCELADA` — cria uma `PENDENTE` nova, no nível da última, data recalculada a partir de hoje. Decidido para não precisar de um campo novo em `Revisao` só para registrar por que uma revisão foi cancelada (arquivamento do próprio assunto vs. da disciplina) — sem isso, reativar uma disciplina poderia ressuscitar a revisão de um assunto arquivado à parte, por outro motivo. Fechada com o usuário ao perceber que arquivar não tinha operação inversa |
| 1.14.0 | 2026-09-01 | **D-48**, nova: ordem do assunto é única entre os ativos da mesma disciplina; arquivar libera o número (ao contrário de D-47, que é permanente). Sem isso, "o assunto de menor ordem" (D-41, §3.2) não tinha resposta única em caso de empate — achado ao revisar `FrenteService.proximaVaga`, que desempatava por ordem de retorno do banco (não determinística, sem `ORDER BY`), em auditoria (`/agents/mentor.md`) |
| 1.13.0 | 2026-09-01 | **D-47**, nova: nome de disciplina é único, mesmo arquivada. Mesma semântica que `ux_assunto_j1_nome_por_disciplina` já dava ao assunto (D-41/§3.2), faltando para `Disciplina` desde que §3.1 foi escrita. Fechada com o usuário ao revisar `DisciplinaService.criar`, antes do índice único poder citar a regra |
| 1.12.0 | 2026-08-31 | **D-46**, nova: erro aponta para um assunto existente e, opcionalmente, para uma sessão existente — mesma integridade referencial que D-01 já dá a `sessao.assunto_id`, faltando para `Erro` desde que §3.5 foi escrita. Fechada com o usuário na abertura da Sprint 7, antes de `docs/SPRINT-7-METRICAS.md` poder citar a regra na restrição da migração |
| 1.11.2 | 2026-08-30 | §12: fechadas três das sete questões em aberto. (1) Intervalos da escada — Sprint 4 decidiu `1, 3, 7, 15, 30, 90` (não os `1, 7, 15, 30, 60, 90` citados ali como pergunta, nunca fixados como decisão em nenhum outro trecho); achado só depois de já ter perguntado ao usuário na Sprint 4 sem ter visto esta linha — a pesquisa da época não cobriu §12. (2) Teto diário de recuperações — Sprint 5 decidiu **8**. (3) Limiar de represamento — Sprint 5 decidiu `represado > teto diário`. Usuário confirmou os três valores |
| 1.11.1 | 2026-08-19 | §3.3.1: explicitado que a identificação é da **tentativa**, não do conteúdo — recuperar o mesmo assunto duas vezes no mesmo dia continua legítimo |
| 1.11.0 | 2026-08-19 | Nova §3.3.1 e **D-45**: registrar a mesma sessão duas vezes conta uma. Sem isso, um reenvio por falha de rede sobe a escada dois degraus e inventa um evento de retenção |
| 1.10.0 | 2026-08-19 | Nova §3.2.2: **dividir assunto é migração de dados** — original arquivado, histórico permanece nele, novos nascem no nível do original porque o desempenho corrige sozinho. Nova §4.4: **mapa do modelo de aprendizagem**, apontando onde cada um dos seis conceitos vive, em vez de criar documento-ponte que duplicaria regra. D-42 a D-44 |
| 1.9.0 | 2026-08-19 | Revisão do usuário. §4.1 troca a escala *tudo/parte/nada* por **âncoras de estrutura × detalhe** — quantidade lembrada é inestimável e reintroduzia a própria ilusão que M-3 combate. Nova §5.4, **antecipar**: drenar represado e adiantar conteúdo são livres; antecipar revisão futura tem **janela de tolerância** de 20% do intervalo, e fora dela a sessão conta mas não move a escada. Assunto ganha **ordem** como prioridade de backlog. D-37 a D-41 |
| 1.8.0 | 2026-08-19 | Nova §4.2.1: o usuário mira **as duas famílias de banca**, e elas têm chões diferentes (acaso 20–25% × 50%). Formato passa a ser atributo obrigatório da sessão de `QUESTOES`; limiares por formato (MC 80/60, CE 90/75) e **M-1 reportada por formato, nunca somada**. Normalização por acaso considerada e recusada. D-36 |
| 1.7.0 | 2026-08-19 | **Regra de granularidade refeita** (§3.2.1): o critério passa a ser o toque de 8 minutos, não a homogeneidade de 20 questões — que protegia uma medição que a `00_PRODUTO` §5.3 já declarou inexistente no nível de assunto. Quantificado o custo de dividir: dobrar a granularidade custa ~3 meses e leva a revisão de 38% para 52% do dia, o que **não é desperdício**. Estratégia em dois tempos: dividir por peso, depois deixar `PARCIAL` repetido apontar. D-33 a D-35 |
| 1.6.0 | 2026-08-19 | §7.5 separa **botão de verdade** de botão aparente: revisões/dia não é parâmetro, é consequência da frente; o teto diário é válvula e, abaixo da taxa natural, gera represamento perpétuo — daí a regra de coerência entre os dois. Painel frente → carga → meses. §7.6 resolve agrupar × espalhar sem parâmetro: fila primeiro, porque recuperação com atenção fresca dá sinal mais limpo para M-2 e para o roteamento. D-30 a D-32 |
| 1.5.0 | 2026-08-19 | Nova **§7, o turno**. Responde por aritmética quantos assuntos cabem por dia, e desfaz o erro de tratar toda sessão como bloco uniforme: recuperação é toque de ~8 min, conteúdo é bloco de 45–75 min. Orçamento semanal (32% recuperação, 9% questões, 59% teoria) e forma do turno: **6 a 7 assuntos tocados por dia, 1 ou 2 estudados**. Algoritmo passa a devolver **plano de turno** derivado, não assunto. Registrado que a duração de bloco é convenção e não achado — parametrizada, e o sistema mede a real. Terceira correção sobre interleaving |
| 1.4.0 | 2026-08-19 | Revisão do usuário. §3.4 passa a abrir com a separação **agendamento × execução**, que estava implícita. §6.2 ganha um retrato numérico do núcleo (171 = 100 frente + 45 consolidados + 26 backlog), que era o que faltava para o conceito ficar inequívoco à leitura |
| 1.3.0 | 2026-08-19 | Dimensionamento fechado com as **13 h 30 semanais** reais. Núcleo custa 551 h → 9,4 meses pelo limite de horas. Frente de 60 revelada pequena demais: entregaria em 18,8 meses o que as horas entregam em 9,4. **Recomendação passa a frente 100, teto 12 por disciplina, núcleo em ~11 meses a 4,6 recuperações/dia** — um terço do dia em revisão. Nível-alvo confirmado como derivado do peso, sem campo próprio. Nova §6.6.1 sobre por que o tamanho da frente engana e a carga diária é o número a vigiar |
| 1.2.0 | 2026-08-19 | Segunda revisão. **Backlog** (§6.2) como fase, em assunto e em disciplina — núcleo inicial × expansão, sem conceito novo. Contas refeitas com os **171 assuntos reais** do núcleo. Descoberto que escada completa em tudo pede 42 meses só para o núcleo: entra o **nível-alvo por peso** (§6.4), que derruba para ~19 meses a 2,7 recuperações/dia. Acrescentado o **orçamento de horas** (§6.5) como segundo limite independente, a ser lido junto com a fila. Tetos com valores de partida (frente 60, disciplina 8–10) e a regra de ondas. D-23 a D-25 |
| 1.1.0 | 2026-08-19 | Revisão do usuário. **Frente de estudo** (§6) entra como conceito central: conjunto derivado `{em estudo} ∪ {em escada}`, com teto global e por disciplina, e a regra de não sugerir assunto novo com a frente cheia. Dimensionada por teoria de filas — o tamanho é consequência de assuntos × tempo de consolidação ÷ horizonte, não escolha. Simulação do tempo de consolidação (347/443/617 dias) e das tabelas de entrega. Registrada a ressalva de que a conta da §5.3 supunha distribuição uniforme, que só existe se a §6 a impuser. Acrescentado que **consolidar não é obrigação de todo assunto**. Regras D-19 a D-22 |
| 1.0.0 | 2026-08-19 | Criado. Separa revisão (compromisso) de recuperação (evento). Define as 6 entidades, os 4 tipos de sessão e a matriz que os justifica. Fecha as 5 questões abertas de `00_PRODUTO` §12. Roteamento `SUCESSO`/`PARCIAL`/`FALHA` decidido por simulação. Introduz teto diário e alerta de represamento a partir da análise de carga |
