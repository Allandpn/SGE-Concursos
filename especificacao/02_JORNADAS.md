# 02 — JORNADAS

Como o sistema é usado, momento a momento. **Precedência 3.**
Subordinado a `00_PRODUTO.md` e `01_DOMINIO.md`.

| Campo | Valor |
|---|---|
| Versão | 1.2.1 |
| Data | 2026-08-19 |
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
  5. Declara a próxima sessão pretendida (opcional).

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
| `disciplina` | sim | Criada se não existir |
| `assunto` | sim | Único dentro da disciplina, comparado sem acento e sem caixa |
| `peso` | não | `ALTO` · `MEDIO` · `BAIXO`. Ausente → `MEDIO` |
| `ordem` | não | Inteiro. Ausente → ordem de aparição no arquivo |

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
| Linha com `id` conhecido | Atualiza nome, peso e ordem daquele registro |
| Linha com `id` inexistente | **Recusa o arquivo inteiro** — sinal de arquivo corrompido ou de outra base |

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

### J-2 · O turno de estudo — a jornada principal

É a §2 acima. O que ela exige do sistema:

| Momento | O que a tela precisa dar | Orçamento |
|---|---|---|
| Abrir | O plano do turno, pronto, sem cliques | < 2 s de carga |
| Cada recuperação | Duas etapas, sem campo de resultado na primeira | **≤ 15 s** de registro |
| Bloco de conteúdo | Assunto já preenchido pelo plano; tempo e tipo | **≤ 20 s** |
| Lote de questões | Assunto, formato, feitas, certas, previsão | **≤ 25 s** |
| Encerrar | Próxima sessão pretendida — opcional, um toque para pular | ≤ 10 s |

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

## 4. As telas

Sete. Cada uma justificada por uma jornada — tela sem jornada não entra.

| Tela | Serve a | Existe porque |
|---|---|---|
| **Hoje** | J-2, J-4 | A porta de entrada. O plano do turno como lista, e o gatilho de puxar mais (§4.3) |
| **Recuperar** | J-2 | As duas etapas da §5.2.1. É a tela mais usada do sistema |
| **Registrar sessão** | J-2 | Conteúdo, questões ou flashcards |
| **Assuntos** | J-1, J-3 | Cadastro em lote, peso, frente × backlog |
| **Erros** | J-2, J-3 | Registro com causa e confiança; consulta por assunto |
| **Progresso** | J-3 | As quatro métricas, com as regras de n de `00_PRODUTO` §7 |
| **Ajustes** | J-1, J-3 | Tetos, limiares, intervalos |

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

## 5. Os orçamentos de tempo

Requisitos, não metas. Medidos com cronômetro, no aparelho real.

| Operação | Orçamento | Campos visíveis |
|---|---|---|
| Registrar recuperação | 15 s | 2 (previsão, resultado) |
| Registrar bloco de conteúdo | 20 s | 3 (assunto, tipo, tempo) |
| Registrar lote de questões | 25 s | 5 (assunto, formato, feitas, certas, previsão) |
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
| J-1 Primeiro uso | Assuntos, Ajustes | D-19, D-24, D-33, D-34 |
| J-2 Turno de estudo | Hoje, Recuperar, Registrar, Erros | D-01 a D-09, D-26 a D-29, D-36 |
| J-3 Balanço semanal | Progresso, Assuntos | M-1 a M-4, D-35 |
| J-4 Retorno | Hoje | D-15, D-20, §5.1 |

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
| 1.2.1 | 2026-08-19 | Correção: o enum de peso do CSV estava no feminino (`ALTA`/`MEDIA`/`BAIXA`), divergindo de `01_DOMINIO` D-23. *Peso* é masculino — `ALTO`/`MEDIO`/`BAIXO`. Divergência encontrada ao derivar o schema da Sprint 1 |
| 1.2.0 | 2026-08-19 | Segunda revisão. **Coluna `id` no CSV** e ciclo exportar–editar–importar: sem isso, corrigir um typo de disciplina duplicava a base inteira. Renomear passa a ser exigência de interface. J-3: **ordem das métricas invertida** — M-1 é lenta demais para liderar tela semanal; M-2 e M-3 assumem, e semanal × trimestral viram telas distintas. Nova §4.3, lugar do **puxar mais**, no fim da lista e nunca no topo. Nova §5.1: orçamento é tempo do usuário, transição otimista, falha não destrutiva, fila offline deliberadamente fora |
| 1.1.0 | 2026-08-19 | Revisão do usuário. §2.1: o turno vira **lista clicável**, não sequência — a ordem é apresentação, nunca imposição. J-1: cadastro em massa vira **importação CSV** com layout definido, validação tudo-ou-nada e reimportação idempotente. J-4 ganha **o outro lado** — puxar mais num dia de folga, com as três modalidades e o preço de cada uma. §4.1: botões passam a usar as âncoras de estrutura × detalhe. Nova §4.2: **registrar erro é ação global**, nunca etapa de fluxo |
| 1.0.0 | 2026-08-19 | Criado. Contexto físico (tablet estuda, computador registra) e por que a separação de aparelhos protege a regra da produção obrigatória. Quatro jornadas, com **J-4, o retorno depois de sumir**, tratada como a que decide a sobrevivência do sistema. Sete telas, cada uma justificada por jornada. Orçamentos de tempo como requisito. Web de computador primeiro, celular depois |
