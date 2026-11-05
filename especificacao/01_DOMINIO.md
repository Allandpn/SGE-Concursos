# 01 — DOMÍNIO

Os conceitos, os eventos e as regras do sistema. **Sem tecnologia.**
**Precedência 2.** Subordinado apenas a `00_PRODUTO.md`.

| Campo | Valor |
|---|---|
| Versão | 1.45.0 |
| Data | 2026-11-05 |
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
| **Parte** | Pedaço de um assunto do edital, em texto literal; serve para conferir a cobertura e ligar ao nó (§3.12) |
| **Nó** | Posição na árvore de assuntos do QConcursos, referida só por id (§3.12) |
| **Conferência do edital** | O estado em que a pessoa resolve as divergências da leitura do edital, antes de ele virar assuntos (§3.12) |
| **Decisão** | O que a pessoa resolveu numa divergência; vale antes da IA em qualquer reprocessamento (§3.12) |
| **Material de consulta** | Páginas das apostilas que não são leitura obrigatória, como as questões do assunto (§3.12) |
| **Pomodoro** | Cronômetro de foco em fases (foco, pausa curta, pausa longa) que mede o tempo real de estudo (§3.15) |
| **Fase** | Um trecho do Pomodoro, com início gravado e fim; só a de foco conta como tempo de estudo (§3.15) |
| **Passkey** | A credencial do titular no login: biometria ou PIN do aparelho, sem senha (§3.16) |
| **Sessão (de acesso)** | O que mantém a pessoa dentro depois de entrar: 30 dias, revogável (§3.16). Não é a sessão de estudo |
| **Leitor** | Onde o material (o PDF de um bloco) abre dentro do app, para ler e marcar; nunca na recuperação (§3.17) |
| **Marcação** | Destaque, nota de trecho ou nota de página feita no leitor, guardada à parte do PDF (§3.17) |
| **Pergunta de recuperação** | Pista preparada pela IA a partir de um bloco lido, mostrada sem o material na recuperação, com um gabarito em pontos (§3.18) |
| **Ponto do gabarito** | O que devia ter vindo na resposta: principal ou detalhe, com a página de origem (§3.18) |
| **PDF próprio** | PDF que a pessoa adiciona a um assunto, cortado em blocos pelo próprio sistema (§3.19) |

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

Oito, mais a análise de edital (§3.9), que é um processo e não estado de
estudo. Um outro conceito — parâmetro de negócio — é configuração, não
domínio, e aparece só onde uma regra depende dele.

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

Pode carregar, opcionalmente, uma **chave externa**: um identificador dado por
um sistema de planejamento de estudos externo ao SGE, que o SGE guarda mas
nunca interpreta — existe só para esse sistema conseguir apontar, mais tarde,
um segmento de material (§3.7) para o assunto certo, sem depender do `id` que
o SGE decide na importação (`02_JORNADAS` J-1). Quando presente, é **única
entre todos os assuntos** (D-52) — do contrário, a importação de segmentos não
teria resposta única de para qual assunto apontar.

Um assunto que nasceu da conferência de um edital (§3.12) tem **partes**: os pedaços do texto do edital em que ele se
divide, cada uma com a sua ligação a um nó da árvore do QConcursos. A parte não é unidade de estudo: a escada, as sessões,
o peso e a ordem seguem o assunto, nunca a parte nem o nó.

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

**O que conta como resultado aqui:** o resultado de cada sessão de
`QUESTOES`, `FLASHCARDS` ou `RECUPERACAO` do assunto, em ordem de data; lote
abaixo do mínimo (§4.3) não conta, porque não chega a classificar. N é
parâmetro. A sugestão aparece no balanço semanal (`02_JORNADAS` J-3), com o
número de parciais seguidos e o motivo.

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

Cinco tipos. Cada um existe porque alimenta uma combinação diferente de
métricas — tipo que não se distingue nessa matriz não deve existir:

| Tipo | Tempo | Contagem de acertos | Previsão | Resultado | Alimenta M-1 | Cumpre revisão |
|---|---|---|---|---|---|---|
| **ESTUDO** | sim | não | não | **não** | não | **não** |
| **QUESTOES** | sim | sim | sim | derivado da contagem | **sim** | sim |
| **FLASHCARDS** | sim | sim | sim | derivado da contagem | **não** | sim |
| **RECUPERACAO** | sim | não | sim | declarado após produção | não | sim |
| **DISCURSIVA** | sim | não (nota 0–100) | sim | derivado da nota (D-134) | não | sim |

Três consequências que essa matriz decide:

1. **`ESTUDO` não é recuperação.** Ler não produz resultado, não cumpre revisão
   e não consolida nada. É o que habilita a recuperação seguinte.
2. **`FLASHCARDS` não alimenta M-1.** Cartão é material próprio, calibrado pela
   sua percepção de dificuldade, não pela banca. Misturar 90% em flashcards com
   62% em questões de banca infla a métrica que sustenta a tese inteira. Por
   isso é tipo próprio e não um rótulo de `QUESTOES`.
3. **`RECUPERACAO` é o caminho principal**, por aritmética (`00_PRODUTO` §5.3).
4. **`DISCURSIVA` é recuperação escrita** (2026-11-01, pedido do usuário): escrever uma resposta discursiva ou um estudo de caso
   sobre o assunto, de memória, e dar a nota (a própria ou a de um corretor). Distingue-se de `RECUPERACAO` porque o resultado
   sai de uma nota e não de uma âncora declarada, e de `QUESTOES` porque não é acerto de banca: não entra na M-1 (D-134).

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

Quando o simulado é uma **prova hospedada** (§3.11), é resolvido no sistema e a nota entra em M-1
por disciplina e global (D-78); o desempenho por assunto só informa.

**Registrar um simulado feito fora (D-148).** Em Registrar, a pílula **Simulado**: data, duração, formato e, para cada
disciplina do edital do concurso-alvo (mais "outra disciplina", entre as ativas), **certas**, **total** e, opcional, **em branco**.
Disciplina deixada sem total não entra. Os brancos não mudam a M-1 (acerto é acerto), mas mudam a nota em pontos com errada anula
certa (§3.20): erradas = total − certas − brancos.

**Corrigir (D-149).** Simulado registrado errado é **arquivado**, nunca editado nem apagado (D-18): sai da M-1, da nota em pontos e da
contagem do simulado da semana (D-144); a pessoa registra o certo.

### 3.7 Segmento

Um pedaço do material de estudo de um assunto — granularidade de **leitura**,
não de recuperação. Não confundir com o bloco de conteúdo do turno (§7.1): um
é unidade de *tempo* na agenda; este é unidade de *material*.

Pertence a um assunto, em sequência ordenada. A ordem é **única dentro do
assunto** (D-50, mesmo padrão de D-48). Carrega uma referência ao material
(apostila, página, link) que o sistema **guarda, nunca abre** — mesma
natureza que a referência de material já tinha antes de virar sequência
(`02_JORNADAS` §1.1).

Nasce só por importação (`02_JORNADAS` J-1) — o SGE não cria segmento por
conta própria. Existe porque um sistema externo de planejamento de estudos
fatia o material antes de ele chegar aqui (`docs/00A_ADR.md` ADR-037).

**Sessão de `ESTUDO` pode apontar, opcionalmente, para um segmento do mesmo
assunto** (D-51) — os outros três tipos continuam apontando só para o assunto
(D-01 intacto). Generalizar fragmentaria a escada por segmento em vez de por
assunto, competindo com o mecanismo que já existe para granularidade fina
(§3.2.1, dividir assunto).

**Quando a escada de um assunto com segmentos começa (D-54).** Recuperar um
assunto do qual só se leu uma parte mede o que não foi estudado, e a escada
regride por um motivo falso. Por isso, num assunto com segmentos, a primeira
revisão só é agendada quando **todos os segmentos** tiverem pelo menos uma
sessão de `ESTUDO`, e é agendada a partir da data da sessão que completou a
leitura. Uma sessão de `ESTUDO` **sem segmento** num assunto que tem segmentos
significa "estudei o assunto inteiro" e também inicia a escada — é a saída
para quem estudou por outro material. Assunto sem segmentos continua como
sempre: o primeiro estudo inicia a escada. Uma recuperação registrada antes
de terminar a leitura também inicia a escada, porque foi o usuário quem
decidiu se testar.

Enquanto a leitura não termina, o assunto está **em estudo** (§8): já tem
sessão, ainda não tem revisão. É o turno quem leva a leitura até o fim
(D-55, §7.4).

Carrega, **obrigatoriamente**, uma **chave externa** própria — mesma natureza
que a chave externa do assunto (§3.2), mas aqui sem ser opcional: como
segmento só nasce por importação, todo segmento tem, por origem, um
identificador dado pelo sistema de planejamento. É **única entre todos os
segmentos** (D-53). Existe para a reimportação corrigir um segmento já
existente (o link do material mudou) sem depender de `ordem` como
identidade — se o material for reordenado depois de importado, a posição de
um segmento pode mudar sem que o segmento deixe de ser "o mesmo", e uma
reimportação por posição confundiria conteúdo antigo com novo.

### 3.8 Concurso

O concurso para o qual o usuário está estudando — o **concurso-alvo** —
e os que já foram alvo antes. Tem nome, cargo, banca, data da prova (se o
edital já a fixou), a origem (lido por IA no próprio sistema, ou importado
do sistema de planejamento) e o **período em que foi alvo**.

Todo concurso tem um **identificador** estável, dado pelo sistema quando o
concurso nasce. É por ele — nunca pelo nome — que o material preparado para
o concurso o reencontra (D-64).

**No máximo um concurso é o alvo por vez** (D-58). Ativar outro encerra o
período do anterior, que fica no histórico; nada é apagado (D-18).

Cada concurso guarda **a cobertura do seu edital**: quais assuntos ele cobre,
e para cada um o peso, a disciplina e a ordem que aquele edital deu. A
cobertura é o que o edital *disse*; o assunto continua sendo um só, com um
só peso vigente — o do concurso ativo.

**Ativar um concurso aplica a cobertura dele à base** (D-60): os assuntos
cobertos recebem o peso, a disciplina e a ordem do edital; os assuntos
ativos dessas disciplinas que o edital não cita passam para depois, na
ordem em que já estavam; nada mais muda. Escada, sessões, revisões e erros
nunca são tocados — é por isso que trocar de edital não custa o estudo já
feito: o que é comum segue de onde estava. Assunto fora do edital continua
como está até o usuário decidir arquivá-lo.

Reativar um concurso antigo é o mesmo gesto: a cobertura dele é aplicada de
novo.

**Por que existe agora, e não antes.** A base de estudo era uma só e o
edital só entrava como CSV; o concurso era configuração do script de
importação. Passou a ser entidade quando três coisas precisaram dele ao
mesmo tempo: mostrar para que prova se estuda e quanto falta, guardar o
histórico de editais, e dar destino à análise de edital (§3.9)
(`docs/00A_ADR.md` ADR-042).

### 3.9 A análise de edital

> **Revisada na v1.27.0 (ADR-047).** O SGE continua lendo o cabeçalho do edital (nome, banca, data da prova e cargos). A
> separação em disciplinas, assuntos e partes, e a ligação aos nós, passam ao sistema de planejamento e à **conferência do
> edital** (§3.12). As três regras abaixo (D-61, D-62, D-63) seguem valendo; "a confirmação" de D-62 é a **conclusão da
> conferência** (D-92).

Um edital novo pode entrar pelo próprio sistema: o usuário envia o PDF, e
uma IA lê o texto em duas etapas — primeiro identifica o concurso e os
cargos; depois, para o cargo escolhido, separa as disciplinas e os assuntos
do conteúdo programático, na ordem do edital, e aponta qual assunto já
existente na base é o mesmo conteúdo (`docs/00A_ADR.md` ADR-043).

O resultado é uma **proposta**, não um cadastro. Três regras impedem que
um erro de leitura vire meses de estudo no lugar errado:

- **A IA extrai, o sistema decide** (D-61). A IA informa quantas questões o
  edital dá a cada disciplina; o **peso** sai dessa contagem por regra fixa:
  ordenadas as disciplinas com contagem declarada da maior para a menor, a
  posição de cada uma é quantas têm mais questões que ela; posição no
  primeiro terço é `ALTO`, no segundo `MEDIO`, no último `BAIXO` — empate
  recebe o mesmo peso. Disciplina sem contagem no edital fica `MEDIO`,
  marcada como **peso não determinado**. O peso de cada assunto é o da sua
  disciplina. A IA nunca estima peso.
- **Nada é gravado sem confirmação** (D-62). O usuário revisa a proposta
  inteira — pode tirar assuntos, trocar o peso de uma disciplina, desfazer
  uma correspondência, corrigir o nome do concurso e a data da prova — e só
  a confirmação cria disciplinas e assuntos e ativa o concurso (§3.8).
- **Correspondência só com o que existe** (D-63). Se a IA aponta um assunto
  que não existe ou está arquivado, a correspondência é descartada e o
  assunto entra como novo.

Na confirmação, cada assunto que ainda não tem **chave externa** recebe
uma, e o concurso recebe o seu identificador (D-64). A partir daí, concurso
e assunto são reconhecidos por essas chaves: renomear um assunto nunca faz o
material dele cair em outro, nem cria um segundo concurso. O material vem
depois, sem pedir nada (§3.10).

### 3.10 Material preparado pelo sistema

Depois da confirmação, o sistema de planejamento — que roda no mesmo
servidor — prepara o material de cada assunto do concurso-alvo que ainda não
tem: procura no acervo de apostilas, uma IA escolhe as páginas que ensinam
cada parte do assunto, e o material é fatiado em segmentos (§3.7). O usuário
não faz nada (`docs/00A_ADR.md` ADR-044).

Duas garantias:

- **Nada é inventado** (D-65). Se o acervo não ensina o assunto, ou a seleção
  da IA não passa na validação do fatiamento (página que não existe, parte do
  assunto sem página e sem motivo), o assunto fica marcado **sem material no
  acervo**, com o motivo. Estuda-se igual: assunto sem segmentos começa a
  escada no primeiro estudo (D-54).
- **O estudo nunca espera o material.** Os assuntos entram no backlog na
  confirmação; o material chega quando fica pronto, e o turno passa a sugerir
  os segmentos (D-55) a partir daí.

O estado do material de um concurso — prontos, sem material no acervo, a
preparar — é derivado (D-16) dos segmentos de cada assunto, com uma exceção:
a marca **sem material no acervo** é um fato registrado (o acervo não ensina
aquilo), e não se deriva de nada que o sistema guarde. Ela some quando o
assunto ganha segmentos.

**Material por casamento.** Quando o assunto tem partes ligadas a nós (§3.12), o planejamento casa cada parte com os temas
das apostilas pelo mesmo nó, uma IA confirma se o tema ensina, e a escolha do que entra é regra: por padrão todo tema que
ensina (*completo*), ou o conjunto mais curto que cobre (*compacto*). O que ficou sem tema tem uma segunda chance pela
busca por palavras, só para teoria. Resumo e roteiro de revisão entram depois da teoria; **questões e gabarito não entram
nos segmentos** e viram material de consulta (§3.12). Não há limite de leitura por assunto, só o tempo estimado.

**Refazer o material** (D-66). A preparação pode errar a mão — trazer páginas
do tema vizinho, ou marcar sem material o que o acervo ensina. O usuário pode
pedir que o material de um assunto, ou de todo o concurso-alvo, seja preparado
de novo. Nada é apagado (D-18): os segmentos atuais são **arquivados** e a marca
sem material é **descartada**; o assunto volta a "a preparar", e o sistema
prepara outra vez. Segmento arquivado sai do turno, da contagem de material e
da conta de leitura completa (D-54), mas continua no histórico: a sessão que o
estudou segue apontando para ele. Quem já tinha começado a ler um assunto
refeito recomeça a leitura pelos segmentos novos; a escada, se já começou, não
é tocada.

### 3.11 Prova hospedada e verificação do material

O objetivo do sistema é o usuário gabaritar a prova. Duas perguntas o medem: **o material que o
sistema montou sustenta a resposta certa de cada questão?** (a **cobertura**, uma previsão) e
**o usuário acertou?** (a **resolução**, a medida). Ambas partem de uma **prova**: a prova
objetiva de um concurso, de qualquer banca e ano, que o usuário traz em PDF com o **gabarito**.
Ela é avaliada contra a cobertura do concurso-alvo (D-77) e hospedada com um fim só: verificar e
resolver (D-72; `00_PRODUTO` §5.1.1, ADR-046).

| Termo | Significa |
|---|---|
| **Prova** | A prova objetiva (múltipla escolha ou certo/errado); a parte discursiva é ignorada e declarada |
| **Questão** | Enunciado, alternativas e a resposta do gabarito; pertence a uma prova |
| **Gabarito** | A resposta oficial de cada questão; do **cargo e tipo** da prova |
| **Questão avaliável** | Com texto utilizável e gabarito válido; as demais saem do denominador (D-70) |
| **Atribuição** | O assunto do concurso-alvo ao qual a questão pertence, ou "fora do edital" |
| **Sustentação** | A página do material do assunto que justifica a alternativa do gabarito |
| **Veredito** | `RESPONDIVEL` · `PARCIAL` · `NAO_RESPONDIVEL` |
| **Furo** | Assunto com questões `PARCIAL` ou `NAO_RESPONDIVEL` |

**Entrada.** Prova e gabarito entram como o edital (§3.9): o usuário carrega os PDFs, o sistema
lê e mostra o que achou — questões lidas, não avaliáveis, gabarito casado com cargo e tipo — e só
então roda. Quem lê o PDF é o planejamento (por OCR quando é imagem) e entrega as questões ao SGE,
que guarda questões, alternativas, gabarito e a imagem das páginas. **Sem gabarito a prova só é
lida**, não julgada nem resolvida (D-81). O gabarito é do cargo e do tipo: arquivo de vários
cargos é recortado pelo sistema e o recorte é mostrado (D-83); número de respostas diferente do
de questões é recusado (D-82); o **preliminar** vale como provisório e o definitivo o substitui
(D-84).

**Julgamento, em três etapas.** *Leitura*: a IA separa as questões; o sistema valida a sequência,
as alternativas e o gabarito. *Atribuição*: a IA escolhe o assunto do concurso-alvo; o sistema só
aceita assunto da cobertura, e "nenhum" é **fora do edital** (D-74). *Julgamento*: a IA lê a
questão, a alternativa do gabarito e **trechos** do material do assunto (por busca no índice) e
cita a página; o sistema só aceita citação que existe (D-68). A IA lê e julga; o sistema valida,
conta e calcula (D-67).

**O veredito é sobre a alternativa do gabarito.** As alternativas erradas podem usar fatos que
nenhum PDF traz, e isso não é furo. `RESPONDIVEL`: o material ensina o necessário para justificar a
alternativa do gabarito. `PARCIAL`: ensina parte, e o que falta é dito. `NAO_RESPONDIVEL`: não
traz o necessário. Em "marque a incorreta", a alternativa do gabarito é a falsa e o material
sustenta se permite reconhecê-la como falsa; em certo/errado, se permite decidir o item; em
cálculo, se ensina o método. **Na dúvida, o pior veredito** (D-69), e a falha da IA nunca vira
veredito (D-75). O veredito é um **julgamento de IA**, não uma medição: o relatório mostra sempre
o denominador (D-71) e a página de sustentação.

**Registro.** O veredito é **fato registrado** (um julgamento externo, como a lacuna de D-65); a
nota e as contagens são **derivadas** (D-16). Nada se apaga: reanalisar cria um veredito novo e o
anterior permanece, como retrato datado do material daquele dia (D-73, D-76). **Reanálise
incremental:** um assunto mudou se tem segmento ativo criado ou arquivado depois do veredito
vigente das suas questões, e "Analisar de novo" julga só essas questões (D-79); a prova não é
relida.

**Resolução.** O usuário resolve a prova inteira no sistema, com ou sem cronômetro; o sistema
corrige **pelo gabarito, nunca pela IA**, e a mesma tentativa conta uma vez (D-80). A nota entra em
M-1 **por disciplina e global**; por assunto só informa, e **nenhuma revisão, sessão ou mudança de
turno nasce dela** (D-78). A cobertura prevista e a nota real lado a lado, por assunto, mostram
onde o julgamento da IA errou.

**Texto lido por OCR** leva a marca e deixa o usuário ver a imagem da página; na dúvida, a questão
fica não avaliável em vez de julgada sobre texto corrompido (D-85).

### 3.12 Conferência do edital e material de consulta

A leitura do edital pelo sistema erra: quebra um assunto de um jeito numa rodada e de outro na seguinte, liga uma parte ao
nó vizinho, não acha o nó. Trocar de modelo só troca o erro. Por isso a qualidade não é a da IA, e sim **a facilidade de a
pessoa resolver cada divergência e a garantia de que o que ela resolveu não se perde**. A IA propõe, as regras conferem, a
pessoa resolve (D-86).

**O que é lido.** Para o cargo escolhido, o sistema de planejamento organiza o conteúdo programático em disciplinas, assuntos
e **partes**, e liga cada parte a até três nós da árvore do QConcursos, com a **natureza** do vínculo: *exato*, *mais amplo*
ou *parcial*. Parte sem nó é a árvore que não tem aquilo, e é um estado válido. O texto de cada assunto é **literal do
edital** e a parte usa só palavras do assunto; o que a regra não aceita aparece na conferência, nunca é corrigido em
silêncio (D-88). A origem de cada ligação é guardada: `ia`, `refino` (uma segunda checagem) ou `decisão`.

**A conferência.** A pessoa vê as contagens (disciplinas, assuntos, partes, por natureza, sem nó, pendências abertas,
decididas por pessoa, aplicadas de decisão anterior) e uma fila só com o que pede decisão, na ordem de gravidade. Em cada
item pode aceitar (também todos os exatos de uma disciplina), trocar o nó por busca, ramos ou sugestão, declarar que a
árvore não tem, dividir, juntar ou editar a parte, descartar com motivo e comentar. O que entra na fila: linhas fora de
qualquer disciplina; trecho do edital sem assunto; assunto recusado pela regra; alerta de parte; parte mal dividida; parte
sem nó; vínculo mais amplo ou parcial; nó trocado pela segunda checagem; duas execuções que discordam; disciplina ligada à
disciplina errada; nó que sumiu ou mudou na árvore.

**Decisão** (D-87). Cada gesto da pessoa é uma decisão: parte e contexto (a chave de reaproveitamento, sem a disciplina),
nós, natureza, escopo, edital de origem, versão da árvore e data. O escopo é **este edital** (padrão) ou **todos os
editais**, e este pede confirmação extra que mostra quantas partes de outros editais mudariam. A decisão vale antes da IA e
do refino, pode ser vista, editada e revogada numa lista própria, e duas decisões em conflito para a mesma chave abrem uma
pendência. Reprocessar nunca sobrescreve decisão; se a IA nova discorda, isso é informação, não pendência (D-86).

**Identidade** (D-89). Assunto e parte têm **chave** estável, um hash do texto normalizado. Reprocessar o mesmo edital mantém
a chave do que não mudou. Chave que sumiu fica na fila como "não aparece mais no reprocessamento" e nunca é removida em
silêncio. A entrega do planejamento é idempotente, e um formato que o SGE não conhece é recusado. Ao reprocessar, a tela
mostra só as pendências novas e um contador do que a IA mudou mas a decisão preservou (D-95).

**A árvore.** O nó é guardado só por id, com a versão da árvore (D-90). A árvore do QConcursos vive no SGE, carregada de um
arquivo que o planejamento entrega, e **a pessoa decide quando atualizá-la**. Ao atualizar, o SGE compara com a versão
anterior (nós novos, removidos, renomeados e movidos) e devolve à fila as partes cujo nó sumiu ou mudou. Nada é trocado sem
ela ver.

**Concluir** (D-91, D-92). Bloqueiam a conclusão só as divergências que deixam o **conteúdo do edital** incerto: linhas fora
de disciplina não confirmadas, trecho sem assunto, assunto recusado pela regra e parte com alerta de divisão. Parte sem nó
ou com vínculo mais amplo **avisa**, no painel e no plano junto ao assunto afetado, até a pessoa resolver; o estudo e o
material seguem. A pessoa pode concluir com essas pendências e elas ficam registradas. É a conclusão que cria os assuntos
(com chave, D-64) e ativa o concurso (D-60); antes disso a conferência pode mudar o texto e a divisão sem migrar nada.

**Depois de concluída**, tudo continua possível: vincular uma parte sem nó, trocar o nó, acrescentar nós, trocar a fonte de
uma parte. Trocar o nó atualiza na hora o atalho para as questões do assunto. Se a troca muda o material sugerido, o sistema
**mostra o que entra e o que sai antes de aplicar**. Nada que já existe é tocado (D-93): segmento criado ou estudado nunca
é removido, o material novo entra como sugestão e o antigo fica marcado como ligado ao nó anterior; escada, sessões,
revisões e erros não dependem do nó. A troca é uma decisão como as outras e pode ser desfeita.

**Material de consulta** (D-94). As páginas de questões comentadas, lista, questionário e gabarito das aulas que ensinaram o
assunto são juntadas num PDF **"Questões deste assunto"**, com capa que diz de onde veio cada página. Não é segmento: não
tem escada, não entra no turno e não conta para a leitura completa do assunto (D-54). Aparece no detalhe do assunto ao lado
do atalho para as questões do QConcursos. O sistema só serve o arquivo; quem o monta é o planejamento.

**Execuções.** Cada execução da IA guarda modelo, versão do prompt e data, e a conferência mostra qual execução gerou a
proposta. Trocar o modelo reprocessa só o que não tem decisão humana (D-95). A taxa de aceitação por modelo e por tipo de
divergência, que sai do uso, serve para escolher entre modelos.

**O que o sistema não garante.** Que o nó escolhido seja o melhor, e que o acervo ensine toda parte: parte sem tema que sirva
é lacuna declarada, nunca material inventado (D-65).

### 3.13 Avisos de processo

O sistema espera por processos longos que não dependem da pessoa: a leitura do edital (minutos) e o preparo do material (horas).
Quem espera não fica olhando a tela, e a faixa em Hoje só ajuda quando o app está aberto. Por isso existe um **aviso de processo
concluído**, e só ele.

- **Dois eventos**, cada um no máximo **uma vez por edital e por rodada**: a conferência ficou pronta para a pessoa (leitura
  entregue) e o material do concurso ficou pronto (nada mais a preparar).
- **Opcional, desligado por padrão e por aparelho**: a pessoa liga em Ajustes, neste aparelho, e pode desligar quando quiser.
- **Nunca sobre o estudo**: sem lembrete, sem "você não abriu", sem atraso, sem sequência de dias (`00_PRODUTO` §8.2 e §8.5). O
  texto diz o que aconteceu, não o que falta.
- **Nunca é a única via**: o mesmo fato aparece na tela (faixa em Hoje, Concursos). Aviso perdido não perde informação.
- O aviso leva à tela do fato (conferência ou Concursos) e não carrega dado de estudo.

**O que o sistema não garante.** Que o aviso chegue: o serviço de push do navegador pode atrasar ou descartar.

### 3.14 PDF anexado a uma lacuna

O acervo não ensina tudo: a legislação específica de um edital (a lei orgânica do órgão, o estatuto dos servidores, resoluções)
costuma ficar de fora, e o sistema declara a lacuna em vez de inventar material (D-65). A pessoa pode **preencher a lacuna com um
PDF que ela mesma anexa** — o texto da lei, por exemplo.

- **O PDF vira segmentos do assunto** pelo mesmo caminho do material automático (segmentos de cerca de 60 minutos, ligados ao
  assunto, abertos em "Abrir material"). Para o turno e a escada não há diferença entre um segmento anexado e os outros.
- **A lacuna fica à vista**: a parte continua constando como lacuna do acervo, e a tela mostra ao lado o PDF anexado, o estado
  (preparando, pronto, falhou) e o tempo estimado. Anexar não apaga a declaração.
- **Nada que já existe é apagado** (D-18, D-93): os segmentos novos entram depois dos que o assunto já tem.
- **Um PDF por parte**; se o preparo falhar, a pessoa vê o motivo e pode anexar de novo. Anexo já pronto não se remove por aqui:
  o segmento existe, e segmento só se arquiva pelo gesto de sempre.
- O PDF é da pessoa e fica só no servidor; este é o **único** anexo que o produto aceita (`00_PRODUTO` §8.5).

**O que o sistema não garante.** Que o PDF anexado seja o texto certo, nem que ele tenha texto selecionável: um PDF escaneado vira
segmentos de imagens, abertos como são.

---

### 3.15 Pomodoro: o foco cronometrado

O tempo de uma sessão de conteúdo ou de questões é **declarado** (§7.7), e declarado de memória é o dado mais frouxo do
sistema. O Pomodoro é um **cronômetro de foco** que mede esse tempo de verdade. Não é método de estudo, não é meta e não
dirige o turno: só alimenta o registro e o que se aprende dele (D-29).

**A fase.** O Pomodoro corre em fases, de três espécies: **foco**, **pausa curta** e **pausa longa**. Cada fase tem início
gravado, fim (quando termina ou é encerrada) e os intervalos em que a pessoa pausou à mão. O relógio é calculado a partir do
início gravado, nunca de um contador no aparelho: fechar o app, trocar de tela ou de aparelho não o altera (ADR-050).

**O vínculo.** Ao iniciar, a fase de foco pode apontar para um assunto (e para um segmento dele). O vínculo é **opcional** e
pode ser feito depois, antes de registrar. Um foco sem assunto é válido e só entra no total do dia.

**O que conta como tempo de estudo (D-99).** Só a fase de **foco**, pelo tempo que de fato correu: pausa manual congela o
relógio, fase encerrada antes do fim conta o que correu até parar, e **pausa curta e longa nunca contam**. Foco que venceu sem
a pessoa encerrar conta só a duração programada, nunca o tempo depois do vencimento: voltar duas horas depois não infla o dado.

**Encadeamento (D-102).** Nenhuma fase começa sozinha. Ao fim do foco o sistema propõe a pausa, ao fim da pausa propõe o
próximo foco, e cada uma espera um toque. Contar foco com a pessoa longe do aparelho falsearia o dado que o Pomodoro existe
para dar.

**Alimenta o registro (D-100, D-101).** Ao registrar uma sessão de um assunto, o tempo dos focos ainda não usados para ele
**pré-preenche** o campo de tempo (`ESTUDO`, `QUESTOES`, `FLASHCARDS`); a pessoa confirma ou corrige, e o que fica gravado é o
declarado (§7.7). Cada fase de foco serve a **uma** sessão no máximo; o que não foi usado continua disponível. Em
`RECUPERACAO` o tempo da sessão continua sendo o da tela (a reconstrução acontece entre a previsão e o resultado): o foco que
correu ao lado é guardado, entra nas consultas abaixo e **não** substitui o tempo da recuperação.

**Um só ativo (D-98).** Há no máximo uma fase em curso, em qualquer aparelho. Iniciar outra pede encerrar a atual. É a mesma
ideia de D-05: a unicidade mora na persistência, nunca numa verificação prévia.

**Parâmetros (D-103).** Duração do foco, da pausa curta, da pausa longa e quantos focos até a pausa longa são parâmetros
em Ajustes. O padrão é 25 / 5 / 15 / 4 — convenção, não achado (§7.7); a pessoa muda sem pedir nada a ninguém.

**O que se aprende (D-105).** Três consultas, todas derivadas (D-16), nada guardado:

1. **tempo de foco por assunto e por dia**;
2. **focos completos × encerrados antes do fim**, onde completo é o que correu até a duração programada;
3. **estimado × real do segmento**: a estimativa de leitura do segmento contra o foco medido nas sessões `ESTUDO` dele.

São informação, como M-2: não geram meta, ranking, sequência de dias nem destaque (`00_PRODUTO` §8.1 e §8.2). O total do dia
aparece em Progresso, pequeno; a barra do Pomodoro mostra só o relógio da fase em curso.

**O aviso de fim de fase (D-104).** Com o app aberto, o fim de uma fase sempre dá sinal na tela, com som e vibração se a pessoa
deixou ligados. **Web Push** é opcional, desligado por padrão e por aparelho, e só avisa o fim de uma fase que a própria pessoa
iniciou: nunca lembra de estudar, nunca cobra, nunca conta dias. O texto diz o que aconteceu ("o foco terminou"), não o que
falta. É a segunda exceção à recusa de push (`00_PRODUTO` §8.5), pedida pelo usuário em 2026-10-30.

**O que o sistema não garante.** Que o foco medido seja foco: o cronômetro mede o tempo com a fase em curso, não a atenção.
E que o push chegue na hora: o serviço do navegador pode atrasar ou descartar.

---

### 3.16 O acesso: login por passkey

Até a v1.30.0 a rede era a autenticação (ADR-015): quem alcançava a porta estava dentro. Para usar o app fora da rede de casa, o serviço passa a
ser alcançável pela internet (ADR-051) e a rede deixa de ser defesa. A **porta** do app é o login por passkey.

**Um titular.** O sistema tem uma pessoa só, com um ou mais passkeys (WebAuthn: biometria ou PIN do aparelho). Não há senha, não há
cadastro aberto, não há outro usuário (ADR-040 continua: multiusuário fica fora).

**Tudo exige sessão (D-106).** Toda rota do app, de dado ou de tela, fica fechada sem sessão válida: a de dado responde `401`, a de tela leva à porta. O que fica aberto é uma **lista fechada**:
a página e os arquivos da porta (entrar), as rotas do próprio login, e a verificação de saúde do contêiner. Rota nova nasce fechada.

**O passkey (D-107).** A credencial é um passkey do titular; o contador de assinaturas do passkey nunca regride (assinatura clonada é recusada). O passkey que não conta (sempre 0, caso dos passkeys sincronizados entre aparelhos) não tem contador a regredir.

**O primeiro passkey (D-108).** Só se cadastra o primeiro quando **não existe nenhum** e **só por um pedido que não veio da internet pública** (a
rede privada do Tailscale). Depois do primeiro, esse caminho fecha: cadastrar outro passkey exige já estar dentro (D-111) ou usar um código de
recuperação (D-110). Antes do primeiro passkey o app é inacessível de fora.

**A sessão (D-109).** Depois de entrar, a sessão dura **30 dias** no mesmo aparelho, renovada a cada uso. Vive num cookie que o JavaScript não lê,
só em HTTPS e só no mesmo site; o servidor guarda dela só um resumo irreversível. É revogável: **Sair** encerra a deste aparelho, **Sair de todos**
encerra todas. Pedido que muda dado só vale com a origem do próprio app.

**Recuperação por códigos (D-110).** No primeiro cadastro o sistema mostra **8 códigos de uso único**, uma vez só; guarda só o resumo deles. Um
código vale para entrar e cadastrar um passkey novo, e é consumido. Gerar códigos novos invalida os antigos. Quem perder todos os aparelhos e todos
os códigos fica sem acesso: o convite emitido pelo Guardian é um pedido futuro ao projeto dele (o Guardian ainda não emite convite para outros sistemas), não uma garantia desta versão.

**Gerenciar passkeys (D-111).** Dentro, a pessoa cadastra passkeys novos e remove os que não usa. **O último passkey não se remove.**

**Freio de tentativas (D-112).** Tentativa de entrar falha devagar e é limitada por origem e no total; código de recuperação tem limite mais
estrito. Falha demais bloqueia por um tempo, nunca para sempre, para que um atacante não consiga trancar o titular do lado de fora.

**O que não muda (D-113).** As rotas internas do planejamento (`/internal`) continuam com o token interno e **nunca** respondem a pedido vindo da
internet pública. O aviso de push, o Pomodoro e o resto do estudo são os mesmos; só passam a exigir sessão.

**O que o sistema não garante.** Que o aparelho do titular esteja limpo: quem controla o aparelho desbloqueado usa a sessão. E recuperar o
acesso sem passkey nem código: não há segundo caminho nesta versão.

### 3.17 O leitor: ler e marcar o material

Até a v1.31.0 o sistema não exibia o material: "Abrir material" levava o PDF para fora do app, e a separação de aparelhos fazia o papel de
fechar o material antes de recuperar (`02_JORNADAS` §1.1). Com o uso no celular e no tablet, essa separação não existe mais, e o PDF estava a um toque
de qualquer tela. A pedido do usuário (2026-11-01), o material passa a abrir **dentro do app**, num leitor com marcações. O lugar dele é a
**leitura**; a recuperação continua sem material.

**Onde o leitor abre (D-114).** A partir de um bloco (segmento), no plano de Hoje, no detalhe do assunto e no reestudo. **A tela de recuperação
nunca oferece material, leitor nem marcações**, nas duas etapas: a produção vem antes do material (`00_PRODUTO` §5.2.1). O sistema não consegue impedir
a pessoa de abrir o leitor por outro caminho no meio de uma recuperação; consegue não oferecer o atalho, que é o que a regra da produção obrigatória
pede.

**O PDF nunca é alterado (D-115).** A marcação é guardada à parte, presa a **um bloco e uma página**. Há três tipos:

| Tipo | O que é | Precisa de texto selecionável |
|---|---|---|
| **Destaque** | Um trecho do texto, marcado com uma cor | sim |
| **Nota de trecho** | Comentário escrito ligado a um destaque; aparece ao tocar nele | sim (vem do destaque) |
| **Nota de página** | Comentário numa posição da página, sem trecho | não: é o que serve à página escaneada |

**As cores têm significado (D-116).** Três, fixas: **importante**, **dúvida** (não entendi) e **decorar**. O significado é dado que o sistema
usa, não enfeite: é com ele que as perguntas de recuperação e o reestudo vão priorizar o que a pessoa marcou (§12). Cor livre não existe.

**Nada se apaga (D-117).** Editar uma nota ou trocar a cor grava uma versão nova; remover tira a marcação da vista. As versões e as marcações
removidas ficam no histórico (D-18). Quando o mesmo destaque é editado em dois aparelhos sem rede, **vale a edição feita por último** (a hora em que
foi feita no aparelho), e a outra fica no histórico, à vista de quem procurar. Marcações novas nunca conflitam: somam-se.

**Cada mudança conta uma vez (D-118).** Toda criação, edição ou remoção de marcação leva um identificador gerado no aparelho, no momento do gesto. A
mesma mudança enviada duas vezes (a rede caiu, o aparelho tentou de novo) grava uma vez, como o registro de sessão (D-45).

**Ler sem internet (D-119).** Ficam guardados no aparelho os blocos do **plano de Hoje** (inclusive os de reestudo), baixados sozinhos quando há rede,
e os que a pessoa **escolher** com um toque. Os mais antigos saem quando o espaço guardado passa de um limite. Sem rede, o leitor abre esses blocos e
permite **só ler e marcar**; registrar sessão, Pomodoro e o resto do app pedem rede. As marcações feitas sem rede sobem quando ela volta, e uma
marcação que ainda não subiu nunca sai do aparelho por falta de espaço.

**Terminar não é chegar ao fim (D-120).** Chegar à última página não registra nada: folhear até o fim não é ler. O botão **"Terminei"** (na última
página, ou a qualquer hora) abre o registro de `ESTUDO` já com o assunto e o bloco, e o tempo dos focos livres do assunto (D-100); a pessoa confirma,
como sempre. A escada e a leitura completa (D-54) seguem a sessão registrada, não o leitor.

**Retomar de onde parou (D-121).** O leitor guarda a última página aberta de cada bloco e reabre nela, em qualquer aparelho. É conveniência, não
dado de estudo: não entra em métrica nem decide nada.

**O que não muda.** O bloco e o PDF são os mesmos (o leitor lê o arquivo que o app já serve); refazer o material cria blocos novos e as marcações
continuam presas aos blocos antigos, que nunca são apagados (D-93). As oito telas do estudo continuam; o leitor é uma tela nova que serve à leitura
(`02_JORNADAS` J-10).

**O que o sistema não garante.** Que a pessoa não abra o material no meio da recuperação por outro caminho. Que o aparelho guarde os blocos baixados
para sempre: o navegador pode liberar espaço, e então o bloco volta a pedir rede. Destaque em página escaneada: sem texto selecionável, só nota de
página.

### 3.18 Perguntas de recuperação

A recuperação livre (`00_PRODUTO` §5.2) pede para reconstruir o assunto "de memória", e o único apoio era o nome dele. A pedido do
usuário (2026-11-01), a IA prepara **perguntas** a partir do que foi lido, e um **gabarito** para conferir depois da tentativa. As
perguntas são pistas de evocação, não material: nenhuma traz o texto do bloco, e nenhuma é resumo para ler (ler antes de tentar é
reler, a ilusão que §3.4 de `00_PRODUTO` combate).

**Quando são preparadas (D-122).** Quando o estudo de um bloco é registrado (sessão `ESTUDO` com o segmento), a IA lê o texto daquele
bloco e as marcações feitas nele (§3.17) e prepara um banco de cerca de dez perguntas. Os blocos já lidos antes desta regra são
preparados aos poucos, em segundo plano, os dos assuntos com recuperação mais próxima primeiro. A IA indisponível nunca vira pergunta
nem resultado: a preparação pausa e retoma (como D-75). Bloco sem texto (PDF escaneado) não tem perguntas.

**Como é uma pergunta (D-123).** De um de quatro tipos — **evocação** ("quais são os mecanismos de…"), **completar** ("a ____ retoma
um termo já dito"), **porquê** ("por que a elipse é coesão e não coerência?") e **comparação** ("qual a diferença entre…") — e com
um **gabarito em pontos**: o que a resposta devia trazer. Cada ponto é **principal** (estrutura do assunto) ou **detalhe**, e diz a
página do bloco de onde veio. Trechos marcados como *dúvida* puxam perguntas de porquê e comparação; *decorar* puxa completar;
*importante* puxa evocação.

**Quais aparecem (D-124).** Cada recuperação mostra **3 a 5** perguntas do assunto, dos blocos já lidos, e muda a cada vez: primeiro as
nunca vistas, depois as que tiveram ponto principal faltando na última vez, e entre elas as de trechos marcados como *dúvida* ou
*decorar*. Sem pergunta disponível (IA fora, bloco não lido, assunto sem segmento), a tela é a recuperação livre de sempre: as
perguntas ajudam, não são condição.

**Primeiro tentar, depois conferir (D-125).** A etapa 1 mostra só as perguntas e a previsão; cada pergunta tem um campo de texto
**opcional** (responder de cabeça ou no papel também vale). O gabarito só aparece na etapa 2, depois de "Já tentei", ao lado do que a
pessoa digitou. O que foi digitado não é guardado.

**A conferência sugere, a pessoa decide (D-126).** Na etapa 2, a pessoa marca os pontos que vieram. O sistema **sugere** o resultado
pelas âncoras de §4.1, lidas nos pontos principais: todos vieram → **Reconstruí**; pelo menos metade → **Faltou um pedaço**; menos
que metade → **Não veio**. A sugestão vem pré-selecionada e a pessoa confirma ou troca; o resultado gravado é o dela. Os pontos
marcados ficam guardados com a sessão (fato registrado, D-18), e a previsão da etapa 1 continua intocada (M-3).

**Pergunta ruim (D-127).** Um toque em "pergunta ruim" tira a pergunta do banco: ela não volta, e os pontos dela não entram na
sugestão daquela recuperação. Nada se apaga: a pergunta fica marcada.

**O que faltou vira caminho (D-128).** Depois de um **Não veio**, o reestudo (§5.1) lista os pontos que não vieram naquela sessão,
cada um com o bloco e a página, e abre o leitor ali; o bloco inteiro continua a um toque. Depois de um **Faltou um pedaço**, os
pontos que faltaram aparecem na hora do resultado, com o mesmo atalho, sem criar reestudo: D-07 não muda. A lista é derivada dos
pontos guardados (D-16).

**Os pontos altos depois de conferir (D-172).** Depois do toque em **Conferir** (com ou sem perguntas, e no ensaio), a tela mostra
as marcações vigentes da pessoa nos blocos do assunto (§3.17): cada destaque com a cor, o trecho e a nota de trecho, e cada nota de
página, na ordem do bloco e da página, com o atalho para o leitor ali. Antes de conferir, nada do material aparece (D-125): a
recuperação continua medindo a memória, não a releitura. Marcação removida não aparece; bloco arquivado também não. A lista é derivada
(D-16) e não muda o resultado nem a sugestão.

**O que o sistema não garante.** Que a pergunta da IA esteja certa (por isso o gabarito diz a página e existe "pergunta ruim"); que a
pessoa tenha produzido antes de conferir (o sistema não oferece o atalho, como sempre); que a sugestão acerte a régua da pessoa: ela
é sugestão.

### 3.19 PDF próprio

Até aqui o material de um assunto vinha do acervo (pelo planejamento) ou do anexo de uma lacuna (§3.14). A pedido do usuário
(2026-11-01), a pessoa pode **adicionar os próprios PDFs** a um assunto do edital — uma apostila de outro curso, um resumo, a lei
seca —, e eles viram blocos como os outros.

**Adicionar (D-129).** No detalhe do assunto, **"Adicionar meu PDF"**: o arquivo (até 50 MB) e o tipo de leitura, que define o
ritmo — **apostila ou resumo** (cerca de 2 min por página) ou **lei seca** (cerca de 3 min por página). O sistema corta o PDF em
blocos de cerca de 60 minutos, como o material (§3.7), cortando no início de uma seção quando o PDF tem marcadores. Os blocos entram
**depois** dos que o assunto já tem, na ordem em que os PDFs foram enviados. Um assunto pode ter vários PDFs próprios.

**Bloco como os outros (D-130).** O bloco de um PDF próprio abre no leitor, aceita marcações, entra no turno e na leitura completa
(D-54) e ganha perguntas quando é lido (D-122). PDF **sem texto** (escaneado) é aceito, com aviso no envio: os blocos dele se leem e
se marcam com notas de página, e não têm perguntas.

**Assunto que já está na escada (D-131).** Se o assunto já entrou na escada, os blocos novos aparecem em Hoje como **leitura
sugerida** ("Continuar leitura", como em D-55), e a revisão já marcada **não muda**: a escada segue. Num assunto ainda em estudo, os
blocos novos fazem parte da leitura que falta, e a escada começa quando ela termina (D-54, sem mudança).

**Tirar (D-132).** **"Tirar este PDF"** arquiva de uma vez os blocos que vieram dele. Nada se apaga (D-18): as sessões, as marcações e
as perguntas desses blocos ficam no histórico. O arquivo enviado fica guardado no servidor.

**O que o sistema não garante.** Que o PDF seja do assunto certo; que o corte caia sempre no fim de uma ideia (sem marcadores, o corte
é pelo tempo); que um PDF escaneado tenha perguntas.

### 3.20 Nota de corte e pontuação do edital

A nota de prova e de simulado é contada em acertos (D-78, §3.6), e é isso que a M-1 usa. Para comparar com a **nota de corte** de
um concurso, é preciso a régua do edital, que raramente é "um ponto por questão". A pedido do usuário (2026-11-01):

**A régua (D-135).** No concurso-alvo, a pessoa informa: os **pontos por questão de cada disciplina** (padrão 1), se a prova é de
**errada anula certa** (o regime da CEBRASPE em certo/errado) e a **nota de corte**, em pontos. Tudo editável a qualquer momento.

**A nota em pontos (D-136).** Para cada prova resolvida e cada simulado, o sistema calcula, na hora, a nota em pontos pela régua
vigente: soma dos pontos das certas; com errada anula certa, menos os pontos das erradas (em branco não desconta); a global nunca
fica abaixo de zero. Aparece ao lado da nota de corte, com a diferença ("62 pontos · corte 70 · faltaram 8"). É **informação**:
nunca probabilidade de aprovação (`00_PRODUTO` §8.5), nunca meta, e **a M-1 continua em acertos** (§3.6). Nada disso é guardado
(D-16): mudar a régua muda a conta de todo o histórico.

**O que o sistema não garante.** Que a régua informada seja a do edital. Simulado registrado sem brancos (os de antes do campo, ou
deixado vazio) conta, com errada anula certa, o que não foi acerto como erro (D-148).

### 3.21 Diagnóstico do edital

Quando um edital novo chega, a pergunta é "quanto disso eu já sei, e o que cabe até a prova?". A pedido do usuário (2026-11-01),
cada concurso com cobertura tem um **diagnóstico**, calculado na hora e nunca guardado (D-16). Serve tanto ao alvo quanto a um
concurso que ainda não é alvo: comparar editais antes de trocar (D-60) não custa nada, porque abrir o diagnóstico não muda nada.

**A cobertura por fase (D-137).** Os assuntos ativos da cobertura do concurso, contados pela fase derivada (§8): **consolidado**
(inclui em manutenção), **em escada**, **em estudo** e **nunca visto** (backlog); por disciplina e no total. Duas faixas resumem:
**consolidado**, e **encaminhado** (consolidado + em escada) — assunto no primeiro nível da escada não finge estar pronto. À parte,
informativos: assuntos da cobertura que estão arquivados, e quantos assuntos ativos estão **fora** deste edital. O diagnóstico lê a
fase do assunto, que é um só (D-60): não separa de qual edital anterior veio o que já se sabe.

**O valor em pontos (D-138).** Ao lado das contagens, quanto cada faixa vale da prova. Cada disciplina vale os **pontos por questão**
da régua do concurso (D-135, padrão 1) vezes o **número de questões** que o edital deu a ela (o que a análise do edital leu, §3.9).
Se falta o número de questões de alguma disciplina, nenhuma o usa: cada uma vale só os seus pontos por questão — sem régua, todas
iguais —, para não comparar disciplina com contagem e disciplina sem. O valor da disciplina divide-se igualmente entre os assuntos dela na
cobertura. "62% dos pontos da prova já encaminhados" é informação, nunca probabilidade de aprovação (`00_PRODUTO` §8.5).

**O que cabe até a prova (D-139).** O **ritmo medido** é quantos assuntos tiveram a primeira sessão nas últimas 4 semanas, dividido
por 4 (assuntos novos por semana). Com a data da prova do concurso no futuro — ou com uma **data simulada** que a pessoa digita e que
não é gravada (edital sem data, ou prova já passada, usado como preparação) —, cabem `semanas até a data × ritmo` dos assuntos nunca
vistos. Os que não cabem são os últimos na ordem **peso (alto, médio, baixo), depois a ordem do edital**, e aparecem listados, com o
tempo estimado do material deles. Mostra também a data em que todos estariam vistos nesse ritmo. Sem ritmo medido (nenhum assunto
novo em 4 semanas), a pessoa digita um ritmo para simular, também não gravado. Sem data, só a cobertura e os pontos.

**O que o diagnóstico não faz.** Não muda peso, ordem, escada nem arquiva nada: os gestos continuam nas telas de sempre. Não
considera a carga de revisões (ela já está dentro do ritmo medido) nem o esquecimento dos consolidados até a data.

### 3.22 Reta final

Perto da prova, o estudo muda de regime: menos assunto novo, a manutenção concentrada no que mais vale, simulado toda semana. A
pedido do usuário (2026-10-17 e 2026-11-01), com uma condição que manda em todo o resto: **a reta final nunca entra sozinha**. O
usuário estuda editais de prova já passada ou em cima da hora como preparação para o próximo; a data perto não basta.

**Entrar (D-140).** Quando o concurso-alvo tem data de prova futura a até N semanas (parâmetro, padrão 8), Hoje **sugere**: "Faltam
8 semanas para a prova: entrar na reta final?", com *Entrar* e *Agora não*. *Agora não* cala a sugestão daquele concurso. Em
Concursos, o alvo com data futura tem "Entrar na reta final" a qualquer momento, com ou sem sugestão. Sem data futura, não há reta
final.

**Enquanto dura (D-141).** A reta final está ativa enquanto: a pessoa entrou e não saiu, o concurso é o alvo e a data da prova não
passou. Termina sozinha no dia seguinte à prova, ao trocar de alvo, ou por "Sair da reta final". Só se guardam a entrada e a saída;
"ativa" é derivado. Um concurso tem no máximo uma reta final aberta.

**O que muda no plano** (§7.4), e só isso:

- **Assunto novo só de peso alto (D-142).** O bloco de conteúdo continua o que está em estudo (D-55) e só abre do backlog assunto de
  peso alto. A escada dos assuntos em subida segue normal, em qualquer peso.
- **Manutenção (D-143).** A manutenção de consolidado de peso **médio e baixo** sai da fila de recuperação enquanto a reta final está
  ativa — nada é reescrito; ao terminar, ela volta vencida e o teto diário a espalha (§5.3). Ao entrar, a manutenção pendente dos
  consolidados de peso **alto** que venceria depois de *prova − 7 dias* é **antecipada**, distribuída por igual entre amanhã e
  *prova − 7 dias*, e a data anterior fica guardada. A última semana fica para questões e o simulado.
- **Simulado da semana (D-144).** No sábado e no domingo, se não há simulado registrado desde a segunda-feira daquela semana, o plano
  traz **"Simulado"** no lugar do segundo bloco de conteúdo. O registro é o de sempre (§3.6); a nota em pontos aparece pela régua
  (D-136).

**O que a reta final não faz.** Não muda peso, ordem, a escada nem o teto diário; não apaga revisão (D-18); não decide pela pessoa que
é hora de entrar.

### 3.23 Peso pela banca

O peso vem do edital, por disciplina (D-61): todos os assuntos de uma disciplina saem com o mesmo. Dentro dela, porém, a banca
cobra uns muito mais que outros. A pedido do usuário (2026-11-02), o sistema mede isso nas **provas hospedadas** (§3.11) e propõe
refinar o peso de cada assunto. O edital continua mandando; a banca refina, e só com o aceite da pessoa.

**A incidência (D-145).** Para o concurso-alvo, contam-se as questões **avaliáveis e com assunto** das provas hospedadas da **mesma
banca** (o nome sem acento e sem caixa; CESPE e Cebraspe são a mesma), de **qualquer concurso**, dos **últimos N anos** (parâmetro,
padrão 5; prova sem ano conta). A contagem é por assunto da cobertura, dentro de cada disciplina. Disciplina com menos de **2 provas**
ou **30 questões** (parâmetros) fica "sem amostra suficiente da banca" e não recebe proposta. A banca é a do concurso; sem ela (o
concurso importado do planejamento não a traz), a da prova hospedada mais recente do próprio concurso; sem nenhuma, não há proposta.

**A proposta (D-146).** Partindo do **peso do edital** de cada assunto: **sobe um degrau** (baixo → médio → alto) quem tem mais
questões que pelo menos dois terços dos outros assuntos da disciplina; **desce um degrau** quem tem menos que pelo menos dois terços.
Assunto que a banca nunca cobrou tem zero e entra na conta como os outros. Alto não sobe; baixo não desce. A proposta é calculada na
hora e nunca guardada; mostra, para cada assunto, o peso do edital, o proposto e "12 de 64 questões da banca na disciplina".

**Aceitar e desfazer (D-147).** Nada muda sem gesto: a pessoa aceita um a um ou "aceitar todos". O aceite vira um **ajuste**
(concurso, assunto, peso) e passa a ser o peso do assunto. O ajuste vale sempre que a cobertura do concurso é aplicada — ativar,
reativar, reimportar o edital, concluir a conferência de novo — até a pessoa **desfazer**, que volta ao peso do edital. Prova nova da
banca muda a proposta, nunca o peso: a diferença aparece como proposta nova. No máximo um ajuste vigente por assunto em cada concurso.

**O que muda com o peso.** O mesmo que o edital mudaria (D-23, D-60): o nível-alvo da escada, a ordem de abertura (D-41) e, na
reta final, o que é "peso alto" (§3.22). Subir para alto pede mais degraus antes de consolidar; a tela avisa.

**O que o sistema não garante.** Que as provas hospedadas representem a banca: duas provas são uma amostra pequena, e a atribuição
de cada questão a assunto (§3.11) pode errar. Por isso o mínimo de amostra, os números à vista e o aceite.

---

### 3.24 Questões no QConcursos

As questões de banca o usuário resolve no **QConcursos**, que não tem API de questões, não aceita ser aberto dentro de outra página e
não exporta o desempenho. A pedido do usuário (2026-11-04), o SGE faz o melhor que esse limite permite: **leva** a pessoa ao filtro
certo com um toque e, **na volta**, abre o registro já preenchido. O que se fez lá só entra no SGE pelo que a pessoa digita.

**O atalho (D-150).** "Resolver no QConcursos" leva à página de questões do QConcursos filtrada pelos **nós da árvore** que a
conferência do edital ligou ao assunto (§3.12): os vigentes das partes aceitas ou decididas, na análise mais recente que tem o assunto.
Nó de disciplina filtra a disciplina; nó de assunto filtra o assunto e a disciplina dele. Sem nó, o atalho filtra **a disciplina** — a
da análise, senão a disciplina da árvore com o mesmo nome (sem acento e sem caixa) — e avisa "filtro pela disciplina". Sem nenhuma das
duas, não há atalho. O filtro sempre tira as anuladas e as desatualizadas, fica nas **não resolvidas** pela pessoa, nos anos de
*ano atual − N* até o atual (N é o parâmetro da banca, D-145) e na **modalidade do alvo**: o formato que o lote rápido usaria
(D-133) — múltipla escolha ou certo/errado —, para que o percentual digitado na volta seja medido contra os limiares certos (D-36).
A página abre com o menor tamanho que o QConcursos aceita (5, 10, 15 ou 20) que caiba a sugestão de D-152.

**A banca (D-151).** O filtro leva a banca do **concurso-alvo**, procurada pelo nome (sem acento e sem caixa; CESPE = Cebraspe) no
**catálogo de bancas do QConcursos**. O catálogo nasce com as bancas conhecidas (FGV, CESPE/Cebraspe, FCC, VUNESP, Cesgranrio, IADES,
AOCP e outras) e **aprende pelos endereços**: quando a banca não está nele, a pessoa cola no concurso um endereço do QConcursos filtrado
por ela, e o número fica guardado para aquele nome, valendo para todo concurso da mesma banca. Sem alvo ou sem número, o atalho vai
sem banca e avisa "sem filtro de banca".

**Quantas (D-152).** O atalho sugere *máx(mínimo do lote, ⌊minutos do foco ÷ minutos por questão⌋)* questões: um foco de 25 min a
3 min por questão dá 8. "Minutos por questão" é parâmetro (padrão 3) e passa a ser também o tempo do lote rápido sem foco (D-133).

**A volta (D-153).** Tocar no atalho guarda **no aparelho** o assunto e a hora. Quando o SGE volta a ficar à vista, até 6 h depois,
o lote rápido do assunto (D-133) abre na tela em que a pessoa está, com o tempo igual aos minutos desde a saída (os focos livres
mandam, D-100). *Registrar* grava como qualquer lote e cumpre a revisão pendente; *Não fiz* descarta; fechar adia para a próxima
volta. Passadas 6 h, a saída é descartada sem abrir. Uma saída por aparelho; outra substitui a anterior. Nada vai ao servidor antes
de registrar.

**Onde (D-154).** Em Hoje, em cada recuperação (a fila e as antecipáveis); no detalhe do assunto; no Diagnóstico, nos que ficam de
fora; no Peso pela banca, nas propostas e nos ajustados.

**Questões antes de ler (D-155).** Fazer questões de um assunto **nunca lido** — sem sessão que não seja de questões e sem revisão —
conta nas métricas (M-1, M-2, M-3) e no tempo, mas **não abre a escada** nem tira o assunto do backlog: a fase, a frente, o ritmo e o
diagnóstico só passam a vê-lo quando a leitura começa. Antes, a sessão de questões sem revisão pendente abria a escada no nível 1
(recuperação espontânea, §3.3); isso continua valendo para o assunto já lido.

**Anotar os erros (D-156).** Depois de registrar o lote, "Anotar erros" abre, opcional, até *feitas − certas* linhas: o número da
questão no QConcursos (Q2318890), a descrição curta (a interrogação elaborativa, §3.5), a causa e a confiança. Cada linha vira um
**Erro** (§3.5) ligado ao assunto e à sessão, e o número vira link para a questão. Pular não perde nada do lote.

**O que o sistema não garante.** Que o QConcursos mantenha os nomes do endereço; que a pessoa esteja logada lá (sem login, "não
resolvidas" não filtra nada); nem que o número digitado seja o que ela fez. Os nomes dos parâmetros vêm de endereços copiados do
navegador; o catálogo guarda quais foram conferidos assim. Se o endereço mudar, só o atalho quebra; o registro continua.

---

### 3.25 Os próximos 7 dias e o ensaio

Hoje mostra só o turno de hoje. A pedido do usuário (2026-11-04), a tela ganha a **visão dos próximos 7 dias** e a possibilidade de
**ver e testar** o que vem pela frente sem bagunçar o fluxo: nada do que se faz nela grava sessão, revisão ou marcação.

**Os próximos 7 dias (D-157).** De amanhã até daqui a 7 dias, cada dia mostra as **revisões pendentes previstas** para ele (assunto,
nível, subida ou manutenção) e a contagem do dia contra o **teto diário**, com aviso quando passa. Na reta final, as manutenções de
peso médio e baixo não aparecem nos dias (esperam a prova, D-143) e são contadas à parte; o sábado e o domingo do simulado da semana
(D-144) e os dias até a prova aparecem marcados. Uma linha **estimada** diz quantos assuntos novos o ritmo medido (D-139) abre na
semana; é estimativa, porque o bloco de leitura de cada dia só é calculado no próprio dia. Tudo derivado, nada guardado (D-16).

**A prévia do material (D-158).** Tocar num assunto dos próximos dias abre os blocos dele no leitor **só para ler**: sem
"Terminei", sem destaques nem notas, sem guardar a última página (D-121) e sem sessão. Uma faixa diz que é prévia e que reler antes
da recuperação deixa o resultado melhor do que a memória está (`00_PRODUTO` §5.2.1).

**O ensaio (D-159).** Dos próximos 7 dias e do detalhe de qualquer assunto, "Ensaiar" abre a tela de **recuperação** (com as
perguntas e o gabarito, §3.18), de **questões**, de **discursiva** ou de **flashcards** em modo ensaio. Antes de abrir, a tela avisa
que ver as perguntas antes contamina a recuperação real. No lugar de Registrar, **"Ver o que aconteceria"** mostra o resultado e a
revisão seguinte (nível e data) calculados pela mesma regra do registro — a partir dos próximos dias, como se fosse feito **no dia
previsto** da revisão; do detalhe, hoje —, e nada é gravado: nem sessão, nem revisão, nem "pergunta
ruim". O ensaio não deixa marca.

**O que o sistema não garante.** Que a pessoa não se lembre, no dia, do que viu no ensaio. Por isso o aviso; a decisão é dela.

---

### 3.26 Tirar sessões da escada

A pedido do usuário (2026-11-05): sessões feitas só para ver a escada montar ficaram na escada real. Elas podem ser **tiradas** —
desativadas, nada apagado — e **postas de volta**.

**O que sai (D-160).** Uma sessão tirada deixa de existir para o resto do sistema: a escada, Hoje, os próximos dias, as métricas,
o diagnóstico, o ritmo, o histórico do assunto. Vão junto os **erros** anotados nela, as **perguntas** respondidas nela e o **tempo
de foco** ligado a ela. Nada é editado: a sessão sai inteira ou não sai.

**A escada refeita (D-161).** Tirar ou pôr de volta refaz a escada de cada assunto afetado: as revisões dele são montadas de novo,
na ordem em que as sessões foram feitas (data, depois registro), **só com as que continuam valendo**, pelas mesmas regras do
registro (§5) — como se as tiradas nunca tivessem existido. Se a revisão pendente refeita é a mesma de antes (mesma sessão de
origem, mesmo nível), ela guarda a data que tinha (a antecipação da reta final, D-143; a reativação, D-49). Sem nenhuma sessão,
o assunto volta a **nunca visto**. Num assunto ou disciplina arquivados, a pendente refeita fica cancelada (D-17).

**Pôr de volta (D-162).** A sessão tirada continua guardada e aparece riscada na lista; "Pôr de volta" a reativa e refaz a escada
de novo (D-161).

---

### 3.27 Reiniciar o progresso

A pedido do usuário (2026-11-05): recomeçar o progresso de **um concurso** ou de **tudo**, sem perder o cadastro e com volta.

**O que zera (D-163).** Reiniciar um concurso tira da escada (D-160) **todas as sessões ativas dos assuntos da cobertura dele** —
também os que ele divide com outro concurso, que a confirmação aponta. Reiniciar tudo tira todas as sessões ativas. A escada desses
assuntos é refeita (D-161): sem sessões, eles voltam a **nunca visto**; com as sessões saem os erros, as perguntas respondidas e o
foco ligados a elas. **Fica** todo o cadastro (concursos, editais, assuntos, material, perguntas preparadas, pesos, ajustes), e
também simulados, provas resolvidas, marcações, a última página lida, a reta final e os erros anotados sem sessão.

**Desfazer (D-164).** Nada é apagado: o reinício fica registrado (quando, de quê, quais sessões tirou). **Desfazer** põe de volta as
sessões dele que ainda estiverem tiradas e refaz a escada — as sessões feitas depois do reinício entram na mesma ordem, pela data.
Um reinício desfeito não se desfaz de novo; reiniciar outra vez cria outro. As sessões tiradas aparecem também em Sessões
registradas (§3.26), e lá podem voltar uma a uma.

**A confirmação (D-165).** Antes de reiniciar, a tela mostra o que vai zerar — quantas sessões, quantos assuntos e, num concurso, os
divididos com outros — e só reinicia depois de a pessoa **digitar REINICIAR**.

---

### 3.28 A captura do QConcursos

A pedido do usuário (2026-11-04 e 2026-11-05), depois do atalho (§3.24): um **userscript** no navegador (Tampermonkey no computador;
Firefox com Tampermonkey ou Violentmonkey no Android) percebe cada questão respondida no QConcursos e a envia ao SGE.

**A questão capturada (D-166).** Chega com o número Q, se acertou, a disciplina e os assuntos como o QConcursos os mostra (nomes e,
quando a página traz, os números), a banca, o ano, a alternativa marcada, o gabarito e quando foi respondida. Repetir o mesmo Q no
mesmo dia não duplica. **Ela não é sessão:** só vira sessão de questões (§3.3) quando é registrada — pelo lote do atalho ou pela
caixa em Hoje. Nada muda na escada até lá.

**O assunto (D-167).** Primeiro, o par já aprendido para aquele nome de assunto do QConcursos; senão, o nó da árvore vigente (§3.11)
com esse número ou nome, se ele estiver ligado pela conferência do edital a **um** assunto do SGE; senão a questão fica **sem
assunto**, na caixa. Escolher o assunto ali grava o par, e as próximas questões daquele assunto do QC já chegam com ele.

**O lote do atalho (D-168).** Saindo pelo atalho (§3.24), a **previsão é feita na saída**, antes de ver as questões (M-3). Na volta,
o lote vem **preenchido** com as capturadas daquele assunto desde a saída (feitas e certas), e pode ser corrigido. Registrar o lote
cria a sessão e marca as capturadas como registradas.

**A caixa em Hoje (D-169).** As capturadas ainda não registradas aparecem num cartão em Hoje, por assunto ("Crase · 8 questões"),
e as sem assunto à parte. **Registrar** pede a previsão **antes** de mostrar quantas foram certas — é a previsão possível depois do
fato, e a tela diz isso —, e cria a sessão de questões com a data da resolução mais recente; o tempo é o número de questões vezes o
parâmetro de minutos por questão, editável. **Descartar** tira da caixa sem apagar.

**A chave de envio (D-170).** Uma por navegador, criada em Ajustes com o nome do aparelho, mostrada **uma vez** e guardada só como
resumo (hash). Ela só **envia** questões e diagnósticos; não lê nada do SGE. Revogar corta na hora. Ajustes mostra quando cada uma
enviou pela última vez.

**O diagnóstico (D-171).** Quando o userscript não entende uma questão (o QConcursos mudou a página), ele guarda um **recorte** do
trecho dela e oferece "Enviar diagnóstico". O recorte vai para Ajustes, para corrigir a leitura; pode ser apagado.

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
| `DISCURSIVA` | A nota de 0 a 100 contra dois limiares: a partir do primeiro (padrão 70) `SUCESSO`; a partir do segundo (padrão 40) `PARCIAL`; abaixo, `FALHA` (D-134) |
| `RECUPERACAO` | Declarado após a produção, com as âncoras abaixo. Com perguntas (§3.18), a conferência do gabarito sugere a âncora e a pessoa decide (D-126) |

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

**Como se reconhece o assunto que voltou para a fila de estudo (reestudo).**
Derivado, sem atributo novo (D-16): o assunto está em **reestudo** quando a
revisão pendente dele nasceu de uma sessão com resultado `FALHA` e nenhuma
sessão `ESTUDO` desse assunto foi registrada depois dela. Registrar o estudo
de correção é o que tira o assunto do reestudo — a revisão pendente não muda,
a escada segue em paralelo. Reestudo **não é fase** (§8): é uma marca sobre
um assunto que continua na frente, e por isso não ocupa vaga nova nem conta
duas vezes no teto (§6.7). O plano do turno o oferece numa lista própria,
antes do bloco de conteúdo novo (§7.4) — corrigir vem antes de abrir.

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

**O plano do turno obedece ao próprio alerta (D-56).** Com o alerta ligado, o
bloco de conteúdo deixa de sugerir assunto novo do backlog — seria o sistema
dizendo "pare de abrir" e, na linha de baixo, abrindo. Continuar a leitura de
um assunto já em estudo (D-55) segue sendo sugerido: não abre vaga na frente,
e terminar o que começou é o que tira o assunto do meio do caminho. Abrir
assunto novo à mão continua livre; o plano só deixa de pedir.

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

**Quando o assunto está consolidado**, contando só as revisões cumpridas no
nível-alvo, em ordem: dois `SUCESSO` seguidos consolidam; antes de
consolidar, `PARCIAL` ou `FALHA` zeram a sequência ("seguidos" é sem nada no
meio); depois de consolidado, `SUCESSO` e `PARCIAL` mantêm, e só `FALHA`
desfaz (D-11). Reconsolidar depois de uma `FALHA` exige de novo dois
`SUCESSO` seguidos no alvo.

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

Quando há assunto em **reestudo** (§5.1), ele aparece numa lista própria
entre a fila de recuperação e o bloco de conteúdo novo — é estudo de
correção de assunto que já está na frente, não abertura de assunto novo.

**O bloco de conteúdo termina o que começou antes de abrir assunto novo
(D-55).** Se algum assunto está em estudo (§8) e tem segmento ainda não lido,
o bloco do turno é o **próximo segmento** dele, pela ordem — o assunto em
estudo há mais tempo primeiro. Só sem leitura pendente o bloco abre um assunto
do backlog (D-20, D-25), e então já indica o primeiro segmento dele, se houver.
Continuar não abre vaga nova na frente, então não depende do teto.

**Entre disciplinas, o assunto novo vai para a que tem menos assuntos na
frente (D-57).** Percorrer as disciplinas sempre na mesma ordem enche a
primeira até o teto dela antes de tocar a segunda — semanas seguidas de Redes
antes do primeiro assunto de Português. Escolher a disciplina com menos
assuntos na frente alterna sozinho, sem estado novo, e respeita tudo o que já
vale: dentro da disciplina, a menor ordem do backlog (D-41); o teto dela
(D-25); o teto global (D-20). Empate fica com a ordem estável das disciplinas.
O ganho é o da §7.8 — variedade e espaçamento dentro da semana, não
interleaving.

O plano é **derivado e efêmero**. Não se guarda, não vira entidade: é recalculado
a cada pedido, a partir da fila de revisões vencidas, da frente e dos tetos. Se
o usuário fizer outra coisa, nada quebra — ele nunca prometeu nada
(`00_PRODUTO` §6.6).

**Na reta final (§3.22)** o plano muda em três pontos e só neles: assunto novo só de peso alto (D-142), manutenção de médio e
baixo fora da fila (D-143) e o simulado da semana no lugar do segundo bloco do fim de semana (D-144).

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

**Como o tempo chega ao sistema.** Numa recuperação, o tempo é o da própria
tela: a reconstrução acontece com ela aberta, entre a previsão e o resultado.
Num bloco de conteúdo ou num lote de questões, **não** — a leitura e as
questões acontecem antes de abrir o registro, e tempo de tela gravaria um
minuto para uma hora de estudo, exatamente o dado que este parágrafo diz valer
mais que a convenção. Nesses tipos o tempo é **declarado** no registro. Em
conteúdo, o campo já vem preenchido com a estimativa do segmento ou, sem
segmento, com a duração de bloco parametrizada — confirmar custa zero, corrigir
custa um toque. Em questões e flashcards vem vazio: não há estimativa honesta
para um lote de tamanho livre.

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
| **Backlog** | Cadastrado, sem sessão, fora da frente (§6.2). Sessões só de questões, sem revisão, não contam (D-155) |
| **Em estudo** | Tem sessão e ainda nenhuma revisão: a leitura começou e não terminou (D-54) |
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
Assunto    1 ──── N Segmento          (ordenado)
Segmento   1 ──── N Sessão            (opcional; só sessão ESTUDO aponta)
Revisão    N ──── 1 Sessão (origem)   qual estudo iniciou a escada
Revisão    1 ──── 1 Sessão (cumpriu)  onde vive o resultado
Sessão     1 ──── N Erro              opcional
Disciplina 1 ──── N ResultadoSimulado
Concurso   N ──── N Assunto           cobertura do edital: peso, disciplina e ordem que ele deu
Assunto    1 ──── N Parte             só os nascidos da conferência de um edital (§3.12)
Parte      1 ──── N Nó                até 3, com natureza (os nós são da árvore do QConcursos, fora do estudo)
Assunto    1 ──── N Fase de foco      opcional (§3.15); o segmento também pode ser apontado
Fase de foco N ── 1 Sessão            a sessão que usou o tempo medido; no máximo uma por fase (D-101)
```

**A única relação n:n é a cobertura do edital** (§3.8) — e ela descreve um
documento externo, não estado de estudo. No estudo não há nenhuma: registrar
quatro assuntos irmãos de uma vez grava quatro sessões (`00_PRODUTO` §6.2) —
é tela, não modelo.

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
| D-50 | Ordem do segmento é única entre os segmentos ativos do assunto | §3.7 |
| D-51 | Segmento referenciado por uma sessão pertence ao mesmo assunto dessa sessão | §3.7 |
| D-52 | Chave externa do assunto, quando presente, é única entre todos os assuntos | §3.2 |
| D-53 | Todo segmento tem chave externa, obrigatória e única entre todos os segmentos | §3.7 |
| D-54 | Assunto com segmentos só entra na escada quando todos os segmentos foram estudados, ou com estudo do assunto inteiro (sem segmento); sem segmentos, no primeiro estudo | §3.7 |
| D-55 | O bloco de conteúdo do turno continua o assunto em estudo (próximo segmento não lido) antes de abrir assunto do backlog | §7.4 |
| D-56 | Com o alerta de represamento ligado, o plano não sugere assunto novo do backlog; continuar a leitura (D-55) segue sugerido | §5.3 |
| D-57 | Assunto novo sugerido vem da disciplina ativa com vaga que tem menos assuntos na frente; empate pela ordem estável das disciplinas | §7.4 |
| D-58 | No máximo um concurso é o alvo por vez | §3.8 |
| D-59 | Ativar um concurso encerra o período do anterior; concurso nunca é apagado | §3.8 |
| D-60 | Ativar um concurso aplica a cobertura dele (peso, disciplina e ordem do edital) e nunca toca escada, sessões, revisões nem erros; assunto fora do edital fica como está, depois dos cobertos | §3.8 |
| D-61 | O peso vindo da análise de edital sai do número de questões por regra fixa (tercis); sem contagem no edital, `MEDIO` com peso não determinado — nunca estimado pela IA | §3.9 |
| D-62 | Nada da análise de edital é gravado sem o usuário confirmar a proposta revisada | §3.9 |
| D-63 | Correspondência proposta pela análise só vale para assunto existente e ativo; senão o assunto entra como novo | §3.9 |
| D-64 | Na confirmação de uma análise de edital, o concurso recebe identificador e cada assunto sem chave externa recebe uma; daí em diante são reconhecidos por elas, nunca pelo nome | §3.8, §3.9 |
| D-65 | Assunto que o acervo não ensina fica marcado sem material, com o motivo; material nunca é inventado e o estudo nunca espera por ele | §3.10 |
| D-66 | Refazer o material arquiva os segmentos e descarta a marca sem material; nada é apagado, e sessões antigas continuam apontando para o segmento arquivado | §3.10 |
| D-67 | A IA lê e julga a prova; o sistema valida, conta e calcula. Contagem, nota e percentual saem de regra fixa, nunca da IA | §3.11 |
| D-68 | `RESPONDIVEL` exige página de sustentação da alternativa do gabarito que exista no segmento do assunto; citação inexistente rebaixa para `NAO_RESPONDIVEL` | §3.11 |
| D-69 | Na dúvida, o veredito é o pior dos dois | §3.11 |
| D-70 | Questão não avaliável (sem texto utilizável, anulada, sem gabarito, fora do edital) sai do denominador e é declarada | §3.11 |
| D-71 | Todo percentual ou nota da prova é exibido com o denominador | §3.11 |
| D-72 | A prova hospedada serve só à verificação e à resolução: nenhuma rota lista, busca ou exporta questões fora do contexto de uma prova | §3.11 |
| D-73 | Reanalisar cria veredito novo e o anterior permanece; resposta e entrega também ficam; nada é apagado | §3.11 |
| D-74 | A atribuição só aceita assunto da cobertura do concurso-alvo; "nenhum" é resposta válida (fora do edital) e não conta como furo do material | §3.11 |
| D-75 | Indisponibilidade da IA não é veredito: a análise pausa e retoma, nunca grava `NAO_RESPONDIVEL` por falha | §3.11 |
| D-76 | O veredito é um retrato datado contra o material daquele dia; mudar o material não altera vereditos antigos | §3.11 |
| D-77 | A prova é sempre avaliada contra a cobertura do concurso-alvo | §3.11 |
| D-78 | A nota entra em M-1 por disciplina e global; o desempenho por assunto só informa e não gera revisão, sessão nem mudança de turno | §3.11 |
| D-79 | Um assunto mudou se tem segmento ativo criado ou arquivado depois do veredito vigente das suas questões; a reanálise julga só essas questões, e é derivada dos segmentos | §3.11 |
| D-80 | A mesma tentativa de resolução conta uma vez (identificador gerado no cliente); a correção é do sistema, pelo gabarito, nunca da IA | §3.11 |
| D-81 | Sem gabarito, a prova não é julgada nem resolvida; a leitura segue | §3.11 |
| D-82 | O gabarito tem o tipo da prova; número de respostas diferente do de questões é recusado | §3.11 |
| D-83 | O gabarito é do cargo e do tipo da prova; arquivo de vários cargos é recortado pelo sistema e o recorte é mostrado ao usuário | §3.11 |
| D-84 | Gabarito preliminar vale como provisório; o definitivo, ao mudar ou anular uma resposta, recalcula a nota e devolve as questões afetadas ao julgamento | §3.11 |
| D-85 | Questão lida por OCR leva a marca e a imagem da página fica disponível; na dúvida fica não avaliável em vez de julgada sobre texto corrompido | §3.11 |
| D-86 | A IA propõe, as regras conferem, a pessoa resolve; nenhuma decisão da pessoa é sobrescrita por reprocessamento, e a divergência da IA nova é informação, não pendência | §3.12 |
| D-87 | A decisão vale antes da IA e do refino, em qualquer edital, pela chave parte + contexto; o escopo é este edital (padrão) ou todos, e "todos" pede confirmação extra com a contagem de partes de outros editais que mudariam | §3.12 |
| D-88 | O texto do assunto é literal do edital e a parte usa só palavras do assunto; o que a regra recusa aparece na conferência e nunca é corrigido em silêncio | §3.12 |
| D-89 | Assunto e parte têm chave estável (hash do texto normalizado); chave que sumiu na reprocessamento fica na fila e nunca é removida em silêncio; a entrega do planejamento é idempotente e formato desconhecido é recusado | §3.12 |
| D-90 | O nó é guardado só por id, com a versão da árvore; a árvore só é atualizada quando a pessoa decide, e a atualização devolve à fila as partes cujo nó sumiu ou mudou | §3.12 |
| D-91 | Concluir a conferência só é bloqueado por divergência que deixa o conteúdo do edital incerto; parte sem nó ou com vínculo mais amplo avisa no painel e no plano e não bloqueia | §3.12 |
| D-92 | Os assuntos do edital nascem na conclusão da conferência (com chave, D-64), que também ativa o concurso (D-60); antes disso nada é gravado como assunto (D-62) | §3.12 |
| D-93 | Trocar nó ou fonte de uma parte nunca apaga segmento nem toca escada, sessões, revisões e erros (D-18); o material novo entra como sugestão e o sistema mostra o que muda antes de aplicar | §3.12 |
| D-94 | O material de consulta ("Questões deste assunto") não é segmento: não tem escada, não entra no turno e não conta para a leitura completa do assunto | §3.12 |
| D-95 | Cada execução da IA guarda modelo, prompt e data; trocar o modelo reprocessa só o que não tem decisão humana, e a tela do reprocessamento mostra só as pendências novas e um contador do que a decisão preservou | §3.12 |
| D-96 | O sistema só avisa de processo concluído (conferência pronta, material pronto), no máximo uma vez por edital e rodada, por escolha da pessoa e por aparelho; nunca avisa de estudo, atraso ou sequência, e o mesmo fato sempre aparece na tela | §3.13 |
| D-97 | Uma lacuna do material se preenche com um PDF anexado pela pessoa, que vira segmentos do assunto pelo caminho do material automático; a lacuna continua declarada, nada que já existe é apagado e é o único anexo que o produto aceita | §3.14 |
| D-98 | Existe no máximo uma fase de Pomodoro em curso, em qualquer aparelho; iniciar outra pede encerrar a atual, e a unicidade mora na persistência | §3.15 |
| D-99 | Conta como tempo de estudo só a fase de foco, pelo tempo que correu sem as pausas manuais; pausa curta e longa nunca contam, e foco vencido sem encerramento conta só a duração programada | §3.15 |
| D-100 | O tempo dos focos ainda não usados pré-preenche o tempo da sessão de conteúdo, questões e flashcards; a pessoa confirma ou corrige, e o gravado é o declarado | §3.15, §7.7 |
| D-101 | Cada fase de foco serve a uma sessão no máximo; em recuperação o tempo da sessão continua o da tela e o foco ao lado não o substitui | §3.15 |
| D-102 | Nenhuma fase do Pomodoro começa sozinha: o sistema propõe a seguinte e espera um toque | §3.15 |
| D-103 | Durações do foco, da pausa curta e da pausa longa, e focos até a pausa longa, são parâmetros em Ajustes (padrão 25 / 5 / 15 / 4) | §3.15, §7.7 |
| D-104 | O aviso de fim de fase é sinal na tela com o app aberto; Web Push é opcional, desligado por padrão e por aparelho, só de fase iniciada pela pessoa, sem lembrete, cobrança nem sequência | §3.15 |
| D-105 | Tempo de foco por assunto e por dia, focos completos × encerrados e estimado × real do segmento são derivados, informação e nunca meta, ranking ou sequência | §3.15 |
| D-106 | Toda rota do app exige sessão válida: sem ela, a de dado responde 401 e a de tela leva à porta; as abertas são uma lista fechada (a porta, as rotas do login e a saúde), e rota nova nasce fechada | §3.16 |
| D-107 | A credencial é um passkey (WebAuthn) do único titular, sem senha nem cadastro aberto; o contador de assinaturas do passkey nunca regride (o que está sempre em 0 não conta) | §3.16 |
| D-108 | O primeiro passkey só se cadastra quando não existe nenhum e só por pedido que não veio da internet pública; depois dele esse caminho fecha | §3.16 |
| D-109 | A sessão dura 30 dias, renovada ao usar, em cookie inacessível ao JavaScript, só HTTPS e mesmo site; o servidor guarda só o resumo, e ela é revogável (sair, sair de todos) | §3.16 |
| D-110 | A recuperação é por 8 códigos de uso único, mostrados uma vez e guardados só como resumo; cada código vale para entrar e cadastrar um passkey novo e é consumido; gerar novos invalida os antigos | §3.16 |
| D-111 | Cadastrar passkey novo exige sessão (ou código de recuperação); o último passkey não se remove | §3.16 |
| D-112 | Tentativas de entrar são limitadas por origem e no total, com bloqueio temporário e nunca permanente; o código de recuperação tem limite mais estrito | §3.16 |
| D-113 | As rotas internas do planejamento mantêm o token interno e nunca respondem a pedido vindo da internet pública | §3.16 |
| D-114 | O material abre no leitor do app a partir do bloco, do assunto e do reestudo; a tela de recuperação nunca oferece material, leitor nem marcações | §3.17 |
| D-115 | A marcação nunca altera o PDF: é guardada à parte, presa a um bloco e uma página, e é destaque, nota de trecho (ligada a um destaque) ou nota de página | §3.17 |
| D-116 | O destaque tem uma de três cores de significado fixo (importante, dúvida, decorar), e o significado é dado que o sistema usa; não há cor livre | §3.17 |
| D-117 | Marcação não se apaga: editar grava versão nova e remover a tira da vista, e tudo fica no histórico; entre edições concorrentes vale a feita por último no aparelho | §3.17 |
| D-118 | Cada criação, edição ou remoção de marcação leva identificador gerado no aparelho e grava uma vez, chegue quantas vezes chegar | §3.17 |
| D-119 | Sem rede, o leitor abre só os blocos guardados no aparelho (os do plano de Hoje, baixados sozinhos, e os escolhidos) e só lê e marca; as marcações sobem quando a rede volta e a que não subiu nunca é descartada | §3.17 |
| D-120 | Chegar à última página não registra nada; "Terminei" abre o registro de ESTUDO preenchido com assunto, bloco e o tempo de foco livre, e a pessoa confirma | §3.17 |
| D-121 | O leitor reabre cada bloco na última página aberta, em qualquer aparelho; é conveniência e não entra em métrica | §3.17 |
| D-122 | As perguntas de um bloco são preparadas pela IA, do texto e das marcações dele, quando o estudo do bloco é registrado; os blocos já lidos, aos poucos, os de recuperação mais próxima primeiro; IA indisponível pausa e retoma, nunca vira pergunta nem resultado | §3.18 |
| D-123 | Pergunta é de evocação, completar, porquê ou comparação, nunca traz o texto do material, e tem gabarito em pontos, cada um principal ou detalhe e com a página de origem | §3.18 |
| D-124 | A recuperação mostra 3 a 5 perguntas do assunto, dos blocos lidos, variando a cada vez (nunca vistas, depois as com ponto principal faltando, priorizando dúvida e decorar); sem pergunta, é a recuperação livre | §3.18 |
| D-125 | O gabarito só aparece depois de declarada a tentativa; a resposta digitada é opcional e não é guardada | §3.18 |
| D-126 | A pessoa marca os pontos que vieram; o sistema sugere o resultado pelos pontos principais (todos → Reconstruí; metade ou mais → Faltou um pedaço; menos → Não veio) e a pessoa confirma ou troca; os pontos marcados ficam guardados com a sessão | §3.18, §4.1 |
| D-127 | "Pergunta ruim" tira a pergunta do banco e da sugestão daquela recuperação; a pergunta fica marcada, não apagada | §3.18 |
| D-128 | Depois de Não veio, o reestudo lista os pontos que não vieram, com bloco e página, e abre o leitor ali; depois de Faltou um pedaço, eles aparecem na hora, sem criar reestudo; a lista é derivada | §3.18, §5.1 |
| D-129 | A pessoa adiciona PDFs próprios a um assunto (até 50 MB cada, vários por assunto), dizendo o tipo de leitura (apostila ~2 min/página, lei seca ~3 min/página); o sistema corta em blocos de ~60 min, no início de seção quando há marcadores, e os põe depois dos blocos que o assunto já tem | §3.19 |
| D-130 | O bloco de PDF próprio é bloco como os outros (leitor, marcações, turno, D-54, perguntas); PDF sem texto é aceito com aviso e não tem perguntas | §3.19 |
| D-131 | Em assunto que já está na escada, os blocos próprios ainda não lidos aparecem em Hoje como leitura sugerida, sem mudar a revisão marcada | §3.19, §7.4 |
| D-132 | "Tirar este PDF" arquiva de uma vez os blocos que vieram dele; sessões, marcações e perguntas ficam no histórico | §3.19 |
| D-133 | Cada assunto da fila de recuperação em Hoje tem "Fiz questões": o lote abre com assunto e formato preenchidos (o da última sessão de questões do assunto, senão o do concurso-alvo) e o tempo pelos focos livres (D-100), senão os minutos por questão (D-152); a pessoa digita feitas e certas e toca a previsão em faixas de 10%; cumpre a revisão como qualquer lote | §3.3, §7.4 |
| D-134 | `DISCURSIVA` é tipo de sessão: tempo, nota 0–100, previsão em três níveis, resultado derivado da nota pelos limiares (parâmetros, padrão 70 e 40); não alimenta a M-1, cumpre revisão, e a previsão entra na variante subjetiva da M-3 | §3.3, §4.1 |
| D-135 | O concurso-alvo guarda a régua da prova: pontos por questão de cada disciplina (padrão 1), errada anula certa (sim/não) e a nota de corte em pontos; editáveis | §3.20 |
| D-136 | A nota em pontos de prova resolvida e de simulado é derivada da régua vigente, mostrada com a nota de corte e a diferença; é informação, nunca probabilidade nem meta, e a M-1 continua em acertos | §3.20 |
| D-137 | O diagnóstico de um concurso (alvo ou não) conta os assuntos ativos da cobertura pela fase derivada — consolidado (com manutenção), em escada, em estudo, nunca visto —, por disciplina e no total, com as faixas consolidado e encaminhado (consolidado + em escada); é derivado na leitura e abri-lo não muda nada | §3.21 |
| D-138 | No diagnóstico, cada disciplina vale pontos por questão da régua (padrão 1) × questões que o edital lhe deu (faltando o número de alguma, só os pontos por questão; sem régua, todas iguais), dividido igualmente entre os assuntos dela na cobertura; a faixa em pontos é informação, nunca probabilidade | §3.21 |
| D-139 | O que cabe até a data (a da prova, se futura, ou uma simulada não gravada) é semanas × ritmo medido (assuntos com a primeira sessão nas últimas 4 semanas ÷ 4, ou um ritmo digitado sem histórico) sobre os nunca vistos; os que não cabem são os últimos por peso e depois ordem do edital; nada é alterado | §3.21 |
| D-140 | A reta final só começa por gesto da pessoa: o sistema sugere quando o alvo tem data de prova futura a até N semanas (parâmetro, padrão 8), "agora não" cala a sugestão daquele concurso, e "Entrar na reta final" fica no alvo com data futura; sem data futura não há reta final | §3.22 |
| D-141 | A reta final está ativa enquanto houve entrada sem saída, o concurso é o alvo e a data da prova não passou; termina sozinha no dia seguinte à prova, ao trocar de alvo ou por "sair"; guarda-se só entrada e saída, no máximo uma aberta por concurso | §3.22 |
| D-142 | Na reta final, o bloco de conteúdo continua o que está em estudo e só abre assunto novo de peso alto; a escada segue normal em qualquer peso | §3.22, §7.4 |
| D-143 | Na reta final, a manutenção de consolidado de peso médio e baixo sai da fila sem ser reescrita; ao entrar, a manutenção pendente dos consolidados de peso alto que venceria depois de prova − 7 dias é antecipada e distribuída por igual entre amanhã e prova − 7 dias, guardando a data anterior | §3.22 |
| D-144 | Na reta final, no sábado e no domingo, sem simulado registrado desde a segunda-feira da semana, o plano traz "Simulado" no lugar do segundo bloco de conteúdo | §3.22 |
| D-145 | A incidência da banca conta as questões avaliáveis e com assunto das provas hospedadas da mesma banca (nome normalizado; CESPE = Cebraspe), de qualquer concurso, dos últimos N anos (parâmetro, padrão 5; sem ano conta), por assunto da cobertura dentro da disciplina; a banca é a do concurso, senão a da prova hospedada mais recente dele; com menos de 2 provas ou 30 questões na disciplina (parâmetros), ou sem banca, não há proposta | §3.23 |
| D-146 | A proposta parte do peso do edital: sobe um degrau quem tem mais questões que ao menos dois terços dos outros assuntos da disciplina, desce quem tem menos que ao menos dois terços; zero conta; alto não sobe e baixo não desce; derivada, nunca guardada | §3.23 |
| D-147 | O peso pela banca só entra por aceite da pessoa; o ajuste aceito vale em toda aplicação da cobertura (ativar, reativar, reimportar) até ser desfeito, que volta ao peso do edital; prova nova muda a proposta, nunca o peso; no máximo um ajuste vigente por assunto e concurso | §3.23 |
| D-148 | Simulado registrado à mão leva, por disciplina, certas, total e, opcional, em branco, com certas + brancos ≤ total; os brancos não entram na M-1 e, na nota em pontos com errada anula certa, erradas = total − certas − brancos | §3.6, §3.20 |
| D-149 | Simulado errado é arquivado, nunca editado nem apagado; arquivado sai da M-1, da nota em pontos e do simulado da semana | §3.6 |
| D-150 | O atalho "Resolver no QConcursos" filtra pelos nós vigentes das partes aceitas ou decididas da análise mais recente que tem o assunto (nó de assunto leva também a disciplina dele); sem nó, a disciplina da análise, senão a da árvore de mesmo nome, com aviso; sem nenhuma, não há atalho; sempre sem anuladas e desatualizadas, só não resolvidas, dos últimos N anos (D-145) e na modalidade do formato do lote (D-133); a página no menor tamanho aceito (5, 10, 15, 20) que caiba a sugestão | §3.24 |
| D-151 | A banca do atalho é a do concurso-alvo, achada pelo nome normalizado (CESPE = Cebraspe) no catálogo de bancas do QConcursos, que nasce com as conhecidas e aprende o número de um endereço filtrado pela banca colado pela pessoa, valendo para toda banca de mesmo nome; sem alvo ou sem número, vai sem banca, com aviso | §3.24 |
| D-152 | O atalho sugere máx(mínimo do lote, ⌊minutos do foco ÷ minutos por questão⌋) questões; minutos por questão é parâmetro (padrão 3) e é também o tempo do lote rápido sem foco | §3.24, §3.3 |
| D-153 | A saída para o QConcursos fica só no aparelho; ao voltar até 6 h depois, o lote rápido do assunto abre com o tempo desde a saída (os focos livres mandam); registrar ou "Não fiz" encerram, fechar adia; depois de 6 h é descartada sem abrir; uma por aparelho | §3.24 |
| D-154 | O atalho aparece em cada recuperação de Hoje, no detalhe do assunto, nos que ficam de fora do Diagnóstico e nas propostas e ajustados do Peso pela banca | §3.24 |
| D-155 | Sessão de questões em assunto nunca lido (sem sessão que não seja de questões e sem revisão) conta nas métricas, mas não cria revisão nem tira o assunto do backlog; fase, frente, ritmo e diagnóstico ignoram essas sessões até a leitura começar | §3.24, §3.3, §8 |
| D-156 | Depois do lote, "Anotar erros" (opcional) cria até feitas − certas erros do assunto ligados à sessão, cada um com o número da questão no QConcursos, a descrição, a causa e a confiança | §3.24, §3.5 |
| D-157 | A visão dos próximos 7 dias mostra, de amanhã a +7, as revisões pendentes previstas para cada dia (assunto, nível, subida ou manutenção) e a contagem contra o teto diário com aviso acima dele; na reta final, sem as manutenções de médio e baixo (contadas à parte), com o simulado do fim de semana e os dias até a prova; e uma estimativa de assuntos novos na semana pelo ritmo medido; derivada, nada guardado | §3.25 |
| D-158 | A prévia do material abre o bloco no leitor só para ler: sem "Terminei", sem marcações, sem guardar a última página e sem sessão, com a faixa de prévia | §3.25 |
| D-159 | O ensaio abre recuperação (com perguntas e gabarito), questões, discursiva ou flashcards a partir dos próximos 7 dias e do detalhe do assunto, depois do aviso; "Ver o que aconteceria" mostra o resultado e a revisão seguinte pela mesma regra do registro, no dia previsto da revisão (dos próximos dias) ou hoje (do detalhe); nada é gravado e não fica marca | §3.25 |
| D-160 | Uma sessão tirada da escada (desativada) sai de tudo — escada, Hoje, próximos dias, métricas, diagnóstico, ritmo, histórico — e leva junto os erros anotados nela, as perguntas respondidas nela e o foco ligado a ela; nada é apagado nem editado | §3.26 |
| D-161 | Tirar ou pôr de volta refaz a escada de cada assunto afetado: as revisões dele são remontadas na ordem das sessões (data, registro) só com as ativas, pelas regras do registro; a pendente refeita que é a mesma de antes (mesma origem e nível) guarda a data que tinha; sem sessões, o assunto volta a nunca visto; arquivado, a pendente fica cancelada | §3.26 |
| D-162 | A sessão tirada continua guardada e pode ser posta de volta, o que refaz a escada de novo (D-161) | §3.26 |
| D-163 | Reiniciar um concurso tira da escada (D-160) as sessões ativas dos assuntos da cobertura dele, divididos com outros inclusive; reiniciar tudo, todas as sessões ativas; a escada desses assuntos é refeita (D-161); o cadastro, simulados, provas, marcações, a última página, a reta final e os erros sem sessão ficam | §3.27 |
| D-164 | O reinício fica registrado com as sessões que tirou; desfazê-lo põe de volta as que ainda estiverem tiradas e refaz a escada; um reinício desfeito não se desfaz de novo | §3.27 |
| D-165 | Reiniciar mostra antes o que vai zerar (sessões, assuntos e, num concurso, os divididos) e só acontece depois de digitar REINICIAR | §3.27 |
| D-166 | A questão capturada (número Q, acerto, disciplina e assuntos do QC, banca, ano, alternativa, gabarito, quando) chega pela chave de envio; o mesmo Q no mesmo dia não duplica; ela só vira sessão de questões quando registrada (lote ou caixa) | §3.28 |
| D-167 | O assunto da capturada: o par aprendido pelo nome do assunto do QC; senão o nó da árvore vigente (número ou nome) ligado pela conferência a um único assunto do SGE; senão fica sem assunto na caixa; escolher ali grava o par | §3.28 |
| D-168 | No atalho, a previsão é feita na saída; na volta, o lote vem preenchido com as capturadas do assunto desde a saída, corrigível; registrar marca-as como registradas | §3.28 |
| D-169 | A caixa em Hoje junta as capturadas não registradas por assunto (e as sem assunto); registrar pede a previsão antes de mostrar o acerto e cria a sessão de questões com a data da resolução mais recente e o tempo por questão; descartar tira sem apagar | §3.28 |
| D-170 | A chave de envio é uma por navegador, mostrada uma vez, guardada só como hash, revogável; só envia questões e diagnósticos, não lê nada | §3.28 |
| D-171 | Questão que o userscript não entende vira um recorte de diagnóstico, enviado por gesto do usuário, visto e apagável em Ajustes | §3.28 |
| D-172 | Depois de conferir, a recuperação mostra as marcações vigentes do assunto (destaques com nota e notas de página), com atalho para o leitor; antes, nada do material | §3.18 |

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
| Frente de estudo como conjunto derivado | Entidade ou marca no assunto | Já é derivável de `{em estudo} ∪ {em escada}`; marcar seria estado duplicado que diverge (§7) |
| Teto global **e** por disciplina | Só um dos dois | Carga e balanceamento são problemas diferentes: o global impede a dívida, o por disciplina impede o monopólio |
| Frente dimensionada pela fila | Número escolhido por intuição | O tamanho é forçado por `assuntos × permanência ÷ horizonte`. Frente de 60 com escada completa cobre 171 assuntos em 42 meses, não em 24 |
| Nível-alvo variável por peso | Escada igual para todo assunto | É o que reconcilia a fila com o orçamento de horas (§6.5). Escada igual em tudo faz esperar 42 meses o que as horas entregariam em 15 |
| Backlog como fase derivada, em assunto e disciplina | Lista separada, ou marca de "planejado" | É o mesmo conjunto que já existia sob o nome errado ("não iniciado"). O nome mudou o comportamento do algoritmo, não o modelo |
| Segmento como entidade própria, referenciada por Sessão | Derivar "próximo pedaço" contando sessões de `ESTUDO` já registradas | Contagem quebra se o candidato estudar dois segmentos na mesma sentada ou fora de ordem; FK direta responde "existe segmento sem sessão apontando pra ele?" sem ambiguidade |
| Segmento só referenciado por sessão `ESTUDO` | Generalizar para os quatro tipos | Fragmentaria a escada por segmento em vez de por assunto; duplicaria o mecanismo que já existe para granularidade fina (§3.2.1) |
| Chave externa opcional em Assunto, gerada pelo sistema de planejamento | Sistema de planejamento referenciar o assunto pelo nome | Nome pode ser corrigido depois de importado — mesmo risco que a coluna `id` do J-1 já existe para evitar (`02_JORNADAS` §"Por que existe a coluna id"). Chave gerada fora do SGE não muda com a correção |
| Chave externa **obrigatória** em Segmento (diferente da de Assunto, que é opcional) | Reimportação identificada por `(assuntoId, ordem)` | Segmento só nasce por importação — não existe caminho manual sem a chave, então torná-la opcional só adiaria um `NOT NULL` que sempre valeria na prática. E `ordem` é posição, não identidade: reordenar depois de importado confundiria, em silêncio, o segmento antigo com um novo na mesma posição — acharia que uma sessão antiga aponta pro material certo quando o conteúdo já mudou |
| Concurso como entidade, com cobertura do edital | Concurso só como configuração do script (ADR-039) | Sem entidade não há histórico, nem dias até a prova, nem destino para a análise por IA. A cobertura guarda o que o edital disse sem duplicar o assunto |
| Peso único por assunto, o do concurso ativo | Peso por concurso | Peso por concurso mudaria o nível-alvo da escada e todas as métricas para servir a dois editais simultâneos, que ninguém pediu |
| Peso da análise calculado pelo sistema a partir do número de questões | Peso decidido pela IA | A IA inventaria um peso que o edital não declarou; contagem de questões é fato do edital, a regra de tercis é auditável |
| Conferência do edital pela pessoa, com decisão guardada | Melhorar o prompt da IA até acertar | Trocar modelo ou prompt troca o erro, não o elimina, e overfitting a casos conhecidos não generaliza. A decisão presa à chave faz a correção valer para sempre |
| Parte como pedaço literal do assunto, com nó da árvore do QConcursos como pivô | Casar o assunto inteiro com o acervo por palavras | O assunto é grosso; o nó dá um critério verificável (84% de veredito igual entre duas IAs) e é também o filtro das questões |
| Os assuntos nascem na conclusão da conferência | Criar na leitura e migrar a cada correção | A conferência pode mudar o texto e a divisão; criar antes obrigaria a migrar assunto com chave, sessão e segmento |
| Árvore do QConcursos no SGE, atualizada quando a pessoa decide | Árvore servida pelo planejamento a cada consulta, ou atualização automática | A tela precisa de busca rápida; e uma troca de nós sob os pés da pessoa mudaria o que ela já resolveu |
| Login por passkey de um titular, com a rede deixando de ser a defesa | Manter o app só na tailnet; senha; reaproveitar o login do Guardian | Só tailnet impede usar fora de casa sem instalar o Tailscale em cada aparelho; senha exposta à internet convida força bruta; acoplar ao Guardian amarra dois sistemas de projetos diferentes (ADR-048 já recusou) |
| Pomodoro como cronômetro que alimenta o registro, com início gravado no servidor | Pomodoro só no aparelho, ou Pomodoro que monta o turno em ciclos | Estado só no aparelho some ao limpar o navegador e não vale entre celular e tablet; montar o turno por ciclos mexeria em §7 por uma convenção que o próprio domínio diz arbitrária (§7.7) |
| Questões e gabarito como material de consulta, fora dos segmentos | Segmentos de questões na escada | Resolver questão é feito no QConcursos, ligado ao nó; misturar com leitura inflaria o tempo do assunto e a contagem de leitura (D-54) |
| Material dentro do app, num leitor com marcações, e nunca na recuperação | Continuar sem exibir o material (`02_JORNADAS` §1.1 até a v1.20.0) | A separação de aparelhos que fechava o material deixou de existir com o celular e o tablet; o link externo abria o PDF de qualquer tela, inclusive da recuperação. Dentro do app, o sistema escolhe onde o material aparece |
| Três cores de destaque com significado fixo | Cores livres | Cor sem significado não diz nada ao sistema; com significado, a dúvida marcada vira ponto fraco e o "decorar" vira pergunta (§12) |
| Perguntas e gabarito em pontos, sem resumo | Resumo da IA para revisar | Ler um resumo antes de tentar é reler: mede familiaridade, não evocação (`00_PRODUTO` §5.2.1). Pergunta sem texto preserva a produção |
| A conferência sugere, a pessoa decide | O sistema decide pela proporção de pontos | A âncora é estrutura × detalhe, e só a pessoa sabe se o que veio foi estrutura; decidir por ela tiraria o julgamento depois da tentativa, que é o que calibra a M-3 |
| Preparar quando o bloco é lido | Preparar o edital inteiro quando o material fica pronto | Centenas de chamadas da assinatura para blocos que talvez nunca sejam lidos; ao ler, a recuperação só vem no dia seguinte |
| O SGE corta o PDF próprio | O worker do planejamento corta, como no anexo de lacuna | Exigiria escrever no outro repositório e o deploy dos dois; o SGE já lê PDF (PDFBox), e o bloco fica pronto em segundos |
| Blocos novos de assunto na escada viram leitura sugerida | Assunto volta a "em estudo" | A escada mede retenção do que já foi lido; parar a escada por um PDF a mais regrediria por motivo falso (o mesmo raciocínio de D-54) |
| `DISCURSIVA` como tipo próprio | Rótulo dentro de `RECUPERACAO` ou de `QUESTOES` | Em `QUESTOES` a nota entraria na M-1, que mede acerto de banca; em `RECUPERACAO` o resultado seria âncora declarada e a nota se perderia |
| Nota em pontos derivada, M-1 em acertos | M-1 ponderada pela régua | Mudaria a tese e o histórico já registrado; a régua é do edital, a M-1 é da retenção |

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
- **Decidido com o usuário em 2026-11-01, a especificar quando a sprint de cada um começar** (nada disso vale como regra antes):
  - ~~Perguntas e gabarito da IA na recuperação, reestudo que aponta os trechos e `PARCIAL` que mostra o que faltou~~: **especificados em §3.18** (D-122 a D-128), v1.33.0.
  - ~~PDFs próprios~~: **especificados em §3.19** (D-129 a D-132), v1.34.0, cortados pelo próprio SGE.

---

## 13. Changelog

| Versão | Data | Mudança |
|---|---|---|
| 1.45.0 | 2026-11-05 | Pedido do usuário (Sprint 44), decidido numa rodada, com `02_JORNADAS` v1.37.0 e a nota em ADR-053. **D-172** (§3.18): os pontos altos (as marcações do assunto) aparecem na recuperação depois de conferir, nunca antes. 172 regras |
| 1.44.0 | 2026-11-05 | Pedido do usuário (Sprint 37), decidido em duas rodadas, com `02_JORNADAS` v1.36.0 e ADR-063. **§3.28** (a captura do QConcursos) e **D-166 a D-171**: a questão capturada, o assunto pelo par aprendido ou pela árvore, o lote preenchido com a previsão na saída, a caixa em Hoje, a chave de envio e o diagnóstico. 171 regras |
| 1.43.0 | 2026-11-05 | Pedido do usuário (Sprint 43), decidido em duas rodadas, com `02_JORNADAS` v1.35.0. **§3.27** (reiniciar o progresso) e **D-163 a D-165**: reiniciar um concurso ou tudo pela mesma desativação de sessões (D-160, D-161), registrado e com desfazer, e a confirmação digitada. 165 regras |
| 1.42.0 | 2026-11-05 | Pedido do usuário (Sprint 42), decidido numa rodada, com `02_JORNADAS` v1.34.0 e ADR-062. **§3.26** (tirar sessões da escada) e **D-160 a D-162**: a sessão desativada sai de tudo com o que está ligado a ela, a escada do assunto é refeita só com as ativas, e dá para pôr de volta. 162 regras |
| 1.41.0 | 2026-11-04 | Pedido do usuário (Sprint 38), decidido em duas rodadas, com `02_JORNADAS` v1.31.0 e ADR-059. **§3.25** (os próximos 7 dias e o ensaio) e **D-157 a D-159**: a visão derivada dos próximos dias, a prévia do material só para ler e o ensaio que mostra o que aconteceria sem gravar nada. 159 regras |
| 1.40.0 | 2026-11-04 | Pedido do usuário (Sprint 36), decidido em duas rodadas, com `02_JORNADAS` v1.30.0 e ADR-058. **§3.24** (questões no QConcursos) e **D-150 a D-154**: o atalho filtrado pelos nós do assunto (ou pela disciplina, com aviso), pela banca do alvo, sem anuladas e desatualizadas, só não resolvidas e dos últimos N anos; a sugestão de quantas pelo foco; a volta com o lote rápido aberto, guardada só no aparelho; onde o atalho aparece. **D-133**: o tempo sem foco passa a ser o parâmetro "minutos por questão" (padrão 3, antes 2 fixo). Numa segunda rodada: a modalidade do alvo no filtro, o catálogo de bancas que aprende pelos endereços colados, **D-155** (questões antes de ler só contam nas métricas; §8 ajustada) e **D-156** (anotar os erros do lote). 156 regras |
| 1.39.0 | 2026-11-02 | Pedido do usuário (tela de simulado), decidido numa rodada, com `02_JORNADAS` v1.28.0. **§3.6**: o registro à mão com brancos (D-148) e o arquivamento (D-149); sai "implementação adiada". **§3.20**: a limitação dos brancos passa a valer só para quem não os informa |
| 1.38.0 | 2026-11-02 | Pedido do usuário (Sprint 22), decidido em duas rodadas, com `02_JORNADAS` v1.27.0 e ADR-057. **§3.23** (peso pela banca) e **D-145 a D-147**: a incidência medida nas provas hospedadas da mesma banca (5 anos, mínimo de 2 provas e 30 questões por disciplina), a proposta de um degrau pelos terços dentro da disciplina, o aceite que sobrevive à reimportação até ser desfeito |
| 1.37.0 | 2026-11-01 | Pedido do usuário (Sprint 21), decidido em duas rodadas, com `02_JORNADAS` v1.26.0 e ADR-056. **§3.22** (reta final) e **D-140 a D-144**: entra só por gesto da pessoa, sugerida a 8 semanas (parâmetro); ativa derivada, termina sozinha depois da prova; assunto novo só de peso alto; manutenção de médio e baixo fora da fila sem reescrever, a de peso alto antecipada até prova − 7 dias; simulado no fim de semana. §7.4 aponta para lá |
| 1.36.0 | 2026-11-01 | Pedido do usuário (Sprint 20), decidido em duas rodadas, com `02_JORNADAS` v1.25.0. **§3.21** (diagnóstico do edital) e **D-137 a D-139**: cobertura por fase com as faixas consolidado e encaminhado, valor em pontos pela régua e pelas questões do edital, o que cabe até a data pelo ritmo medido (data e ritmo simulados não gravados). Nada guardado |
| 1.35.0 | 2026-11-01 | Pedido do usuário (Sprint 23), com `00_PRODUTO` v1.15.0, `02_JORNADAS` v1.24.0 e ADR-055. **§3.3**: quinto tipo de sessão, `DISCURSIVA` (D-134). **§3.20** (nota de corte e pontuação do edital) e **D-135, D-136**. **D-133**: "Fiz questões" na fila de Hoje. §4.1 ganha a apuração da discursiva. 136 regras |
| 1.34.0 | 2026-11-01 | Pedido do usuário, com `00_PRODUTO` v1.14.0, `02_JORNADAS` v1.23.0 e ADR-054. **§3.19** (PDF próprio) e **D-129 a D-132**: PDFs da pessoa ligados a um assunto, cortados em blocos pelo SGE pelo ritmo escolhido; bloco como os outros; em assunto na escada, leitura sugerida sem mexer na revisão; tirar arquiva os blocos. 132 regras |
| 1.33.0 | 2026-11-01 | Pedido do usuário, com `00_PRODUTO` v1.13.0, `02_JORNADAS` v1.22.0 e ADR-053. **§3.18** (perguntas de recuperação) e **D-122 a D-128**: a IA prepara perguntas de quatro tipos e um gabarito em pontos (principal ou detalhe, com página) quando o bloco é lido; 3 a 5 por recuperação, variando; gabarito só depois da tentativa; a pessoa marca os pontos, o sistema sugere o resultado e ela decide; pergunta ruim sai do banco; o que faltou abre o leitor na página (reestudo depois de Não veio, na hora depois de Faltou um pedaço). §4.1 e §12 atualizados. 128 regras |
| 1.32.1 | 2026-11-01 | Ao escrever a Sprint 30: **D-106** diz o que a tela faz sem sessão (leva à porta; só o dado responde 401), e **D-107** explicita o passkey que não conta (contador sempre 0, como os sincronizados), que de outro modo nunca entraria. Sem regra nova |
| 1.32.0 | 2026-11-01 | Pedido do usuário (testando a jornada no MPPR), com `00_PRODUTO` v1.12.0 e `02_JORNADAS` v1.21.0. **§3.17** (o leitor: ler e marcar o material) e **D-114 a D-121**, com ADR-052: o material abre dentro do app e nunca na recuperação; destaques em três cores com significado, notas de trecho e de página, que nunca alteram o PDF; nada se apaga; leitura sem rede dos blocos de Hoje e dos escolhidos; "Terminei" abre o registro. §12 registra o que foi decidido e ainda não especificado: perguntas e gabarito da IA na recuperação, reestudo que aponta os trechos, `PARCIAL` que mostra o que faltou e PDFs próprios. 121 regras |
| 1.31.0 | 2026-10-30 | Pedido do usuário: usar o app fora da rede de casa. **§3.16** (o acesso: login por passkey) e **D-106 a D-113**, com ADR-051 (substitui o ADR-015): toda rota exige sessão, um titular com passkey, primeiro cadastro só pela rede privada, sessão de 30 dias revogável, recuperação por 8 códigos de uso único, freio de tentativas e rotas internas fechadas à internet. 113 regras |
| 1.30.0 | 2026-10-30 | Pedido do usuário (testando o fluxo de estudo), com `00_PRODUTO` v1.11.0. **§3.15** (Pomodoro: o foco cronometrado) e **D-98 a D-105**: o tempo de foco medido pré-preenche o registro sem substituir o declarado (§7.7), um só ativo por vez, nenhuma fase começa sozinha, durações em Ajustes, aviso de fim de fase com push opcional, e três consultas derivadas. 105 regras |
| 1.29.0 | 2026-10-27 | Pedido do usuário, com `00_PRODUTO` v1.10.0. **§3.14** (PDF anexado a uma lacuna) e **D-97**: a lacuna do material se preenche com um PDF que a pessoa anexa, e ele vira segmentos |
| 1.28.0 | 2026-10-27 | Pedido do usuário, com `00_PRODUTO` v1.9.0. **§3.13** (avisos de processo) e **D-96**: o sistema avisa só de processo concluído, opcional e por aparelho, nunca do estudo |
| 1.27.0 | 2026-10-24 | Pedido do usuário ("do edital ao estudo pronto", com a revisão manual no SGE como parte central). **§3.12** (conferência do edital e material de consulta) e **D-86 a D-95**, migradas de `docs/ESPECIFICACAO-REVISAO-EDITAL.md` v0.1.0: a IA propõe e a pessoa resolve, a decisão é guardada presa à chave e vale antes da IA, os assuntos nascem na conclusão da conferência, a árvore do QConcursos é atualizada quando a pessoa decide, questões e gabarito viram material de consulta fora dos segmentos. **§1** ganha cinco termos; **§3.2** ganha partes; **§3.9** passa a ler só o cabeçalho (ADR-047); **§3.10** ganha o material por casamento; **§9** ganha as relações de parte e nó. "Revisão" continua sendo o compromisso da escada: o novo conceito se chama conferência. 95 regras |
| 1.26.0 | 2026-10-18 | Pedido do usuário: o objetivo final é gabaritar a prova. **§3.11** (prova hospedada e verificação do material) e **D-67 a D-85**, migradas de `docs/ESPECIFICACAO-PROVA-GABARITO.md` v0.4.0: o veredito é sobre a alternativa do gabarito, a nota entra em M-1 por disciplina e global, o desempenho por assunto só informa, a reanálise é incremental e derivada. **§3.6** remete à prova hospedada. Revisa `00_PRODUTO` §5.1 e §6.7 (v1.8.0). 85 regras |
| 1.25.0 | 2026-10-17 | Pedido do usuário, depois da primeira preparação real (SEFAZ SC 2026): parte das faixas trazia o tema vizinho. **D-66** (§3.10): refazer o material de um assunto ou do concurso arquiva segmentos e descarta a marca sem material, sem apagar nada. **D-50** passa a valer entre os segmentos ativos — os segmentos novos repetem as ordens dos arquivados. 66 regras |
| 1.24.0 | 2026-10-17 | Pedido do usuário: "subir o edital, escolher o cargo, e o sistema faz todo o resto". Nova **§3.10**: o material de leitura é preparado pelo sistema de planejamento rodando no mesmo servidor, sem toque humano (ADR-044). **D-64** (§3.8, §3.9): concurso e assunto confirmados ganham identificador e chave externa na confirmação — fim do casamento por nome e do concurso duplicado quando o material chegava (o concurso da análise nascia sem identificador e a importação criava outro). **D-65**: o que o acervo não ensina vira marca visível com motivo, nunca material inventado. 65 regras |
| 1.23.0 | 2026-10-15 | Pedido do usuário. Nova **§3.8, Concurso**: o concurso-alvo e o histórico, com a cobertura do edital (peso, disciplina e ordem que ele dá a cada assunto); ativar aplica a cobertura e nunca toca o estudo feito. Nova **§3.9, análise de edital**: IA lê o PDF e propõe disciplinas e assuntos para o cargo escolhido; peso calculado pelo sistema a partir do número de questões; nada gravado sem confirmação. §9 ganha a primeira relação n:n (cobertura). D-58 a D-63. 63 regras (`docs/00A_ADR.md` ADR-042 e ADR-043) |
| 1.22.0 | 2026-10-15 | §3.2.1: D-35 ganha definição operacional (quais resultados contam, lote abaixo do mínimo fora, N parametrizado, onde a sugestão aparece). A regra existia desde a v1.7.0 sem nenhum código — era regra órfã no sentido de `03_INVARIANTES` §9. Achado ao revisar o que falta antes do uso real |
| 1.21.0 | 2026-10-14 | Atritos 2, 3 e 4 da análise de jornada, decididos pelo usuário. (2) §7.7: tempo de bloco de conteúdo e de lote de questões é **declarado** no registro — tempo de tela gravava um minuto para uma hora de leitura e anulava o "o sistema mede a real" de D-29; conteúdo vem preenchido com a estimativa do segmento ou com a duração de bloco parametrizada. (3) Nova **D-56** (§5.3): com o alerta de represamento ligado, o plano para de sugerir assunto novo, mas continua a leitura em curso. (4) Nova **D-57** (§7.4): o assunto novo vem da disciplina com menos assuntos na frente, em vez de encher uma disciplina por vez. 57 regras |
| 1.20.0 | 2026-10-14 | Atrito 1 da análise de jornada: num assunto com vários segmentos, o primeiro `ESTUDO` agendava a revisão para o dia seguinte (recuperar um assunto lido em 20% fazia a escada regredir por motivo falso), e o turno nunca mais sugeria os segmentos seguintes, embora `docs/requisitos-planejamento-blocos-de-conteudo.md` §3 dissesse que o SGE decide qual segmento é o próximo. Nova **D-54** (§3.7): a escada começa quando a leitura termina. Nova **D-55** (§7.4): o bloco do turno continua o assunto em estudo antes de abrir outro. §8: "em estudo" passa a ter definição operacional (tem sessão, nenhuma revisão). 55 regras. Pedido pelo usuário |
| 1.19.0 | 2026-10-14 | Duas lacunas achadas em análise do código contra este documento (Sprint 16). (1) **§5.5**: explicitado quando o assunto está consolidado, como sequência de resultados no nível-alvo — `PARCIAL` em manutenção **mantém** consolidado (a tabela já dizia; a implementação o desconsolidava porque só olhava "os dois últimos"). (2) **§5.1**: definido como se reconhece, sem atributo novo, o assunto que `FALHA` devolveu à fila de estudo (*reestudo*) — D-07 e D-11 diziam "volta para a fila de estudo", mas nada dizia como a fila o reconhecia, e a implementação nunca o oferecia. §7.4 ganha a lista de reestudo no plano do turno. Nenhuma regra D-xx nova: as duas são leitura precisa de D-07, D-10 e D-11. Aprovado pelo usuário |
| 1.18.0 | 2026-09-06 | **§3.7 revisada**: `Segmento` ganha atributo **chave externa obrigatória** (diferente da de `Assunto`, que é opcional) — todo segmento nasce de importação, então sempre tem uma. Existe para a reimportação identificar um segmento já existente pela chave, não por `ordem`: reordenar o material depois de importado não pode fazer o SGE confundir um segmento antigo (já referenciado por sessões passadas) com um novo que ocupa a mesma posição. Nova **D-53** (§10) e linha de decisão em §11 explicando por que aqui a chave é obrigatória, ao contrário do caso de `Assunto`. Decidido com o usuário ao revisar o mecanismo de reimportação de `docs/SPRINT-10-SEGMENTO.md` (ainda não fechado) — acrescentado antes do documento técnico, seguindo a regra central do `CLAUDE.md` |
| 1.17.0 | 2026-09-05 | Nova **§3.7, entidade Segmento**: pedaço de material de leitura dentro de um assunto, ordenado, nascido só por importação de um sistema de planejamento externo (`docs/00A_ADR.md` ADR-037). Sessão de `ESTUDO` pode apontar, opcionalmente, para um segmento do mesmo assunto — os outros três tipos continuam apontando só para o assunto (D-01 intacto). `Assunto` ganha atributo opcional **chave externa** (§3.2), que o SGE guarda sem interpretar, para o sistema de planejamento referenciar o assunto certo ao importar segmentos sem depender do `id` que o SGE decide (evita o mesmo risco que a coluna `id` do J-1 já resolve para o próprio assunto). §9 ganha as duas relações novas. D-50 a D-52. Decidido com o usuário na abertura da Sprint 10 (referência de material por blocos), sequenciada antes da Sprint 9 (frontend pausado) |
| 1.16.0 | 2026-09-01 | §3.3: removida a **próxima sessão pretendida** — campo nunca lido de volta por nada, `00_PRODUTO` v1.7.0 retira a intenção de implementação como substituto da terceira dor (§8.2 de lá). §11: removida a linha de decisão correspondente ("Intenção de próxima sessão como atributo") — a alternativa que ela recusava (entidade "plano") também deixou de fazer sentido, o conceito inteiro saiu, não só a forma dele. Decisão do usuário, testando o fluxo de sessão |
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
