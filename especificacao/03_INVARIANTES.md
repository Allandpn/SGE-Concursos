# 03 — INVARIANTES E GARANTIA

Quem garante cada regra, em que camada, e como se sabe que quebrou.
**Precedência 4.** Subordinado a `00_PRODUTO`, `01_DOMINIO` e `02_JORNADAS`.

| Campo | Valor |
|---|---|
| Versão | 1.17.0 |
| Data | 2026-10-27 |
| Status | Vigente |
| Documento anterior | `02_JORNADAS.md` |

---

## 0. Por que este documento existe separado

`01_DOMINIO` §10 lista as 97 regras e é **deliberadamente livre de
tecnologia** (§0 daquele documento). Mas *"esta regra é garantida por uma
restrição de banco"* é afirmação tecnológica — não cabe lá.

Este é o documento-ponte: pega cada regra de domínio e responde três perguntas
que o domínio não pode responder sozinho.

1. **Que natureza ela tem?** Nem toda regra é invariante.
2. **Quem a garante?** Banco, serviço, interface — ou ninguém, e aí é política.
3. **Como se sabe que quebrou?** Regra sem teste é intenção.

**Ele não reescreve nenhuma regra.** Cita `D-xx` e acrescenta colunas. Se o
texto de uma regra precisar mudar, muda em `01_DOMINIO` — aqui só o
enforcement.

---

## 1. As quatro naturezas

Tratar tudo como "invariante" é o erro que faz gente tentar impor na
persistência coisas que a persistência não tem como saber.

| Natureza | O que é | Quem responde |
|---|---|---|
| **Invariante** | Afirmação que tem de ser verdadeira sobre os dados, sempre, escreva quem escrever | Persistência |
| **Derivação** | Valor calculado a partir de outros, nunca guardado | Serviço ou consulta de leitura |
| **Comportamento** | O que o sistema faz numa situação | Serviço + teste |
| **Política** | Uma postura. Não é mecanicamente verificável | Revisão humana |

### 1.1 Derivação é natureza própria, e tem riscos próprios

Frente de estudo, fase do assunto, dias sem tocar um assunto, cobertura, horas
acumuladas: nada disso é invariante, comportamento ou política. São **valores
derivados**, e a categoria existe porque os riscos deles são diferentes de tudo
o mais:

| Risco | O que acontece |
|---|---|
| **Divergência** | Duas partes do sistema derivam a mesma coisa de formas ligeiramente diferentes, e ninguém nota |
| **Persistir por conveniência** | Alguém guarda o resultado "para não recalcular", e a cópia envelhece |
| **Custo** | Derivação em cima de milhares de linhas fica cara e alguém a "otimiza" guardando |

As três terminam no mesmo lugar: **um valor guardado que diverge da fonte.** É
por isso que a regra é `D-16` — derivável não se persiste — e por isso a
derivação precisa de dono único: **um lugar só calcula, todo mundo lê de lá.**

Guardar uma derivação é decisão de cache, e cache tem custo próprio. Se algum
dia for necessário, é decisão que se justifica por escrito, não otimização
silenciosa.

---

## 2. Onde cada regra mora

> **Cada regra mora na camada mais baixa que consiga garanti-la *sem
> distorção*.**

Não é "quanto mais baixo, melhor" — é "quanto mais baixo **couber**". A escada
inteira, com seus três roteamentos e o nível-alvo por peso, poderia ser
espremida em SQL. Ficaria pior: ilegível, sem teste decente e impossível de
mudar. Ela **pertence** ao serviço, e isso não é concessão.

| Camada | Alcance | Quando é o lugar certo |
|---|---|---|
| **Persistência** | Todo caminho de escrita, para sempre | A regra é uma afirmação sobre a forma do dado, sem depender de histórico nem de parâmetro |
| **Serviço** | O caminho da aplicação | A regra depende de contexto, série histórica, cálculo ou parâmetro configurável |
| **Interface** | O caminho de quem clica | Conforto e sequência. **Nunca é garantia** — é conveniência |

Duas assimetrias que vale gravar:

- **Se a interface é a única a impedir algo, esse algo vai acontecer.** Toda
  proteção de tela precisa de par no serviço ou na persistência.
- **Regra que depende de parâmetro não desce.** O teto da frente é
  configurável; virar restrição fixa faria toda linha existente virar inválida
  no dia em que o valor mudasse.

### 2.1 "Persistência", não "banco"

Este documento diz **quem garante**, nunca **como**. `D-05 é garantida pela
persistência` — se é índice único parcial, restrição de exclusão ou outra coisa
é assunto do documento de persistência.

A razão **não** é portabilidade: ADR-020 fixa PostgreSQL e não há migração no
horizonte. Fingir que estamos abstraindo para trocar de banco seria cerimônia.

A razão é **acoplamento de documento**: misturar as duas coisas obriga a mexer
aqui toda vez que a implementação muda, e faz esta tabela — que é o mapa
conceitual — envelhecer por motivo errado.

---

## 3. As 97 regras, classificadas

`P` = persistência · `S` = serviço · `I` = interface · `H` = revisão humana

| #     | Natureza | Onde mora | Como se sabe que quebrou                                                                              |
|-------|---|---|-------------------------------------------------------------------------------------------------------|
| D-01  | Invariante | **P** | Sessão sem assunto ou com dois tipos é recusada pela escrita                                          |
| D-02  | Invariante | **P** | Sessão `ESTUDO` com resultado preenchido é recusada                                                   |
| D-03  | Comportamento | S + I | Teste: resultado enviado sem a produção declarada → recusado                                          |
| D-04a | Invariante | **P** | Resultado sem previsão é recusado                                                                     |
| D-04b | Comportamento | S | Teste: alterar previsão de sessão fechada → exceção de domínio                                        |
| D-05  | **Invariante crítica** | **P** | Duas revisões pendentes do mesmo assunto → a segunda escrita falha                                    |
| D-06  | Invariante | **P** | Revisão cumprida sem apontar sessão é recusada                                                        |
| D-07  | Comportamento | S | Teste por resultado: sobe / repete / regride + volta à fila                                           |
| D-08  | Comportamento | S | Teste: o intervalo do nível N é sempre o mesmo, qualquer que seja o histórico                         |
| D-09  | Comportamento | S | Teste: lote abaixo do mínimo grava sessão e **não** cumpre revisão                                    |
| D-10  | Comportamento | S | Teste: consolida só com escada concluída **e** dois `SUCESSO`                                         |
| D-11  | Comportamento | S | Teste: `FALHA` em manutenção → volta à escada e à fila de estudo                                      |
| D-12  | Derivação | S | Teste: M-1 calculada com e sem flashcards dá o mesmo número                                           |
| D-13  | Invariante | **P** | Resultado de simulado apontando assunto é recusado                                                    |
| D-14  | **Política** | **H** | Revisão de cada tela: existe algum caminho que bloqueia?                                              |
| D-15  | Derivação | S | Teste: fila com 40 vencidas e teto 8 devolve 8, as mais urgentes                                      |
| D-16  | Derivação | S + **P** | A fase é calculada; **e não existe coluna de fase** (§4.2)                                            |
| D-17  | Comportamento | S | Teste: arquivar assunto cancela as revisões pendentes na mesma transação                              |
| D-18  | Invariante | **P** | Não existe caminho de remoção física                                                                  |
| D-19  | Derivação | S | Teste: a frente calculada bate com a definição; um só lugar a calcula                                 |
| D-20  | Comportamento | S + I | Teste: frente cheia → nenhuma sugestão nova; abertura manual continua                                 |
| D-21  | Comportamento | S | Teste: consolidar abre vaga e a próxima sugestão já a usa                                             |
| D-22  | Política | **H** | Postura da recomendação. Não verificável por teste                                                    |
| D-23  | Comportamento | S | Teste: peso alto → alvo 6; médio → 4; baixo → 3                                                       |
| D-24  | Comportamento | **S** | Teste: arquivar disciplina com assunto na frente é recusado; sugestão nunca puxa de disciplina inativa |
| D-25  | Comportamento | S | Teste: vaga preenchida pela menor ordem do backlog da mesma disciplina                                |
| D-26  | Política | **H** | Regra de dimensionamento, não de escrita                                                              |
| D-27  | Comportamento | S | Teste: a recomendação devolve plano, não item                                                         |
| D-28  | Derivação | S + **P** | O plano é recalculado; **e não existe entidade de turno** (§4.2)                                      |
| D-29  | Comportamento | S + I | Teste: alterar o parâmetro muda a duração sugerida sem reinício; conteúdo e questões gravam o tempo declarado, não o de tela |
| D-30  | Política | **H** | Regra sobre o que **não** vira parâmetro                                                              |
| D-31  | Comportamento | S | Teste: teto abaixo da taxa da frente → aviso emitido                                                  |
| D-32  | Política | **H** | Padrão de apresentação. Sem parâmetro, sem teste                                                      |
| D-33  | Política | **H** | Nenhum código sabe se um assunto é grande demais                                                      |
| D-34  | Política | **H** | Idem                                                                                                  |
| D-35  | Comportamento | S | Teste: 4 `PARCIAL` seguidos → sugestão; nunca divisão automática                                      |
| D-36  | Invariante | **P** | Sessão `QUESTOES` sem formato é recusada                                                              |
| D-37  | Comportamento | S + I | Teste: os três valores válidos; rótulos ancorados na tela                                             |
| D-38  | Comportamento | S | Teste: cumprir fora da janela é recusado como cumprimento                                             |
| D-39  | Comportamento | S | Teste: fora da janela a sessão grava e a revisão continua pendente                                    |
| D-40  | Comportamento | S | Teste: pedir além do teto devolve mais itens                                                          |
| D-41  | Invariante | **P** | Assunto sem ordem é recusado                                                                          |
| D-42  | Comportamento | S | Teste: dividir arquiva o original; nenhuma sessão muda de assunto                                     |
| D-43  | Comportamento | S | Teste: novos nascem no nível do original                                                              |
| D-44  | Comportamento | S | Teste: importação com estrutura de divisão é recusada                                                 |
| D-45  | **Invariante** | **P** | Reenviar a mesma tentativa → segunda escrita recusada, resposta de sucesso                            |
| D-46  | Invariante | **P** | Erro apontando para assunto ou sessão inexistente é recusado pela escrita                             |
| D-47  | **Invariante** | **P** | Cadastrar disciplina com nome duplicado é recusada                                                    |
| D-48  | **Invariante** | **P** | Dois assuntos ativos da mesma disciplina com a mesma ordem → a segunda escrita falha                  |
| D-49  | Comportamento | S | Teste: reativar cria pendente nova no nível da última; a `CANCELADA` nunca é reescrita                |
| D-50  | **Invariante** | **P** | Dois segmentos **ativos** do mesmo assunto com a mesma ordem → a segunda escrita falha (índice único parcial, `WHERE arquivado_em IS NULL`) |
| D-51  | **Invariante** | **P** | Sessão apontando para um segmento de outro assunto é recusada pela escrita; e sessão fora de `ESTUDO` com segmento preenchido também |
| D-52  | **Invariante** | **P** | Cadastrar assunto com chave externa duplicada é recusada                                              |
| D-53  | **Invariante** | **P** | Segmento sem chave externa, ou com chave externa duplicada, é recusado pela escrita                   |
| D-54  | Comportamento | S | Teste: estudar 2 de 3 segmentos não agenda revisão; o 3º agenda a partir da data dele; `ESTUDO` sem segmento agenda |
| D-55  | Comportamento | S | Teste: com assunto em estudo e segmento não lido, o plano sugere o próximo segmento dele, mesmo com a frente no teto |
| D-56  | Comportamento | S | Teste: represado acima do teto diário → nenhum assunto novo no plano; leitura em curso continua sugerida |
| D-57  | Comportamento | S | Teste: com uma disciplina mais cheia na frente, o assunto novo vem da menos cheia, ainda que ela venha depois na ordem |
| D-58  | Invariante | **P** | Segundo concurso ativo é recusado pela escrita (`ux_concurso_d58_um_ativo`) |
| D-59  | Comportamento | S | Teste: ativar outro concurso fecha o período do anterior, que continua listado no histórico |
| D-60  | Comportamento | S | Teste: ativar aplica peso, disciplina e ordem da cobertura; escada e sessões do assunto em comum ficam intactas; assunto fora do edital vai para depois dos cobertos |
| D-61  | Comportamento | S | Teste: tercis pelo número de questões, empate com o mesmo peso; disciplina sem contagem → `MEDIO` não determinado |
| D-62  | Comportamento | S + I | Teste: até a confirmação, nenhuma disciplina, assunto ou concurso é criado pela análise |
| D-63  | Comportamento | S | Teste: correspondência com id inexistente ou arquivado vira assunto novo |
| D-64  | Comportamento | S | Teste: confirmar gera identificador do concurso e chave de todo assunto novo; o material entregue pelo identificador não cria segundo concurso |
| D-65  | Comportamento | S | Teste: lacuna registrada aparece no estado do material; some quando o assunto ganha segmento |
| D-66  | Comportamento | S | Teste: refazer arquiva os segmentos (o assunto volta a "a preparar", o turno não os sugere) e descarta a lacuna; a sessão antiga continua com o segmento; segmento novo com a mesma ordem é aceito |
| D-67  | Política | S | Teste: a contagem e a nota saem de regra fixa; resposta da IA com contagem própria é ignorada |
| D-68  | Comportamento | S | Teste: `RESPONDIVEL` com página que não existe no segmento do assunto é rebaixado para `NAO_RESPONDIVEL` |
| D-69  | Política | S + H | Instrução à IA; conferência humana de ~20 vereditos antes de a funcionalidade valer (`docs/ESPECIFICACAO-PROVA-GABARITO.md` §1) |
| D-70  | Comportamento | S | Teste: questão anulada, sem gabarito, fora do edital ou sem texto sai do denominador e aparece declarada |
| D-71  | Comportamento | S + I | Teste: nenhuma resposta de relatório ou nota traz percentual sem o denominador |
| D-72  | Invariante | S | Teste estrutural: nenhuma rota lista, busca ou exporta questões fora do contexto de uma prova |
| D-73  | Invariante | **P** | Reanálise cria registro novo e o anterior permanece; resposta e entrega não têm caminho de remoção |
| D-74  | Comportamento | S | Teste: atribuição a assunto fora da cobertura do alvo é descartada; "nenhum" vira fora do edital e não conta como furo |
| D-75  | Comportamento | S | Teste: IA indisponível pausa a análise e não grava veredito algum |
| D-76  | Comportamento | S + P | Teste: mudar o material não altera vereditos antigos; o novo é registrado com data |
| D-77  | Comportamento | S | Teste: a atribuição só enxerga a cobertura do concurso-alvo |
| D-78  | Comportamento | S | Teste de ausência: entregar a prova não cria sessão, revisão nem muda o turno; a nota entra em M-1 só por disciplina e global |
| D-79  | Derivação | S | Teste com data congelada: assunto com segmento criado ou arquivado depois do veredito é o único reaberto |
| D-80  | Invariante | **P** + S | Mesmo identificador de tentativa duas vezes → uma tentativa; correção sai do gabarito, não da IA |
| D-81  | Comportamento | S | Teste: prova sem gabarito é lida e não é julgada nem resolvida |
| D-82  | Comportamento | S | Teste: gabarito com número de respostas diferente do de questões é recusado |
| D-83  | Comportamento | S | Teste com os arquivos reais (FGV, IDECAN, FCC, CEBRASPE): o recorte do gabarito é o do cargo e tipo da prova e é mostrado |
| D-84  | Derivação | S | Teste: definitivo que altera ou anula uma resposta recalcula a nota e devolve as questões afetadas ao julgamento |
| D-85  | Comportamento | S + I | Teste: questão lida por OCR leva a marca; texto duvidoso torna a questão não avaliável |
| D-86  | Comportamento | S | Teste: reprocessar um edital com decisão da pessoa não altera a decisão; a ligação nova que discorda aparece como informação e não como pendência |
| D-87  | Comportamento | S | Teste: decisão de escopo "edital" não vale em outro edital; de escopo "todos" vale; sem a confirmação extra não grava "todos"; a decisão é aplicada antes da IA |
| D-88  | Comportamento | S | Teste com os casos reais de edital: o texto do assunto é literal; o que a regra recusa chega à fila e nenhum texto é reescrito |
| D-89  | Invariante | **P** + S | Chave única por edital no banco; teste: a mesma entrega duas vezes não duplica; chave que sumiu fica na fila e não é apagada; `edital-revisao/2` é recusado |
| D-90  | Comportamento | S + I | Teste: atualizar a árvore só ocorre por pedido; nó removido, renomeado ou movido devolve a parte à fila; nada muda sem o gesto |
| D-91  | Comportamento | S + I | Teste: concluir é recusado com divergência de conteúdo aberta e aceito com parte sem nó; o aviso aparece no painel e no plano |
| D-92  | Comportamento | S | Teste: antes da conclusão não existe assunto novo nem concurso ativo; concluir cria assuntos com chave e ativa o concurso |
| D-93  | Comportamento | S | Teste de ausência: trocar nó ou fonte não remove segmento nem altera escada, sessões, revisões e erros; o material novo entra como sugestão; o que muda é mostrado antes de aplicar |
| D-94  | Derivação | S | Teste: o material de consulta não conta para a leitura completa (D-54) nem aparece no turno |
| D-95  | Comportamento | S | Teste: trocar o modelo reprocessa só o que não tem decisão humana; a tela do reprocessamento lista só as pendências novas e o contador de decisões preservadas |
| D-96  | Comportamento | S | Teste: sem inscrição não envia; com inscrição envia exatamente nos dois eventos, uma vez por edital e rodada; nenhum outro caminho envia; inscrição rejeitada pelo serviço (404/410) é removida |
| D-97  | Comportamento | S + I | Teste: o PDF anexado a uma parte com lacuna gera segmentos do assunto sem apagar nem alterar os que já existem; PDF inválido é recusado; a lacuna continua declarada; o estado do anexo (preparando, pronto, falhou) vem do que o planejamento entregou |

**Contagem:** 22 invariantes · 8 derivações · 59 comportamentos · 9 políticas — 98 linhas para 97 regras.
D-04 virou duas regras de naturezas diferentes. D-46 acrescentada na Sprint 7
(01_DOMINIO v1.12.0). D-47 acrescentada ao revisar `DisciplinaService.criar`
(01_DOMINIO v1.13.0). D-48 acrescentada ao revisar `FrenteService.proximaVaga`
(01_DOMINIO v1.14.0). D-49 acrescentada ao decidir como reativar funciona
(01_DOMINIO v1.15.0) — linhas ausentes aqui até a revisão 1.6.0, achado em
auditoria (`/agents/mentor.md`). D-50/D-51/D-52 acrescentadas na abertura da
Sprint 10 (01_DOMINIO v1.17.0, entidade `Segmento` e `chaveExterna` de
`Assunto`) — mesma lacuna do tipo "regra órfã" (§9), desta vez encontrada
antes de o documento técnico da sprint ser escrito, não depois. D-53
acrescentada logo em seguida (01_DOMINIO v1.18.0, chave externa obrigatória
do próprio segmento) — desta vez sem lacuna: entrou em §3 no mesmo passo em
que nasceu em `01_DOMINIO`, antes até de `docs/SPRINT-10-SEGMENTO.md` ser
revisado. D-54/D-55 acrescentadas na Sprint 17 (01_DOMINIO v1.20.0, início da
escada e continuação da leitura em assunto com segmentos), no mesmo passo.
D-56/D-57 acrescentadas na Sprint 17 (01_DOMINIO v1.21.0, plano sob represamento
e rodízio entre disciplinas), no mesmo passo.

### 3.1 As três reclassificações desta versão

| Regra | Era | Virou | Por quê |
|---|---|---|---|
| **D-24** | Invariante | **Comportamento** | "Assunto na frente" é derivado. Impor isso na persistência exigiria gatilho ou visão materializada — o primeiro é proibido (espalha o domínio), a segunda reintroduz divergência. Mora no serviço: recusar o arquivamento e nunca sugerir de disciplina inativa |
| **D-04** | Uma regra | **Duas** | A nulidade é forma do dado e desce; a **imutabilidade depois de fechada** exige comparar com o estado anterior, e isso é serviço |
| **D-16, D-19, D-28** | Invariante / Comportamento | **Derivação** | São exatamente a natureza nova da §1.1 |

---

## 4. As que a persistência garante

Sobrevivem a qualquer erro futuro de código. Enunciadas como afirmações sobre os
dados — **como** cada uma é implementada é do documento de persistência.

| # | Afirmação que nunca pode ser falsa |
|---|---|
| D-01 | Toda sessão aponta para exatamente um assunto e tem exatamente um tipo |
| D-02 | Sessão de tipo `ESTUDO` não tem resultado |
| D-04a | Sessão com resultado tem previsão |
| D-05 | **Existe no máximo uma revisão pendente por assunto** |
| D-06 | Revisão cumprida aponta para a sessão que a cumpriu |
| D-13 | Resultado de simulado aponta para disciplina, nunca para assunto |
| D-18 | Não existe caminho de remoção física |
| D-36 | Sessão de tipo `QUESTOES` tem formato de banca |
| D-41 | Todo assunto tem ordem dentro da disciplina |
| D-45 | Duas gravações da mesma tentativa de registro são impossíveis |
| D-16, D-28 | *(por ausência — §4.2)* Não existe atributo de fase nem entidade de turno |

### 4.1 A crítica é a D-05

Duas revisões pendentes do mesmo assunto significam duas escadas paralelas: o
assunto sobe dois degraus por ciclo, consolida cedo, e a retenção passa a medir
algo que não aconteceu. E é **silencioso** — nada na tela denuncia.

É a única com concorrência real: uma revisão sendo cumprida enquanto outra é
agendada. Não pode depender de o serviço verificar antes de escrever — entre a
verificação e a escrita cabe a outra transação.

> **D-05 é garantida por unicidade na persistência, restrita às pendentes.**
> Nunca por verificação prévia no serviço.

**D-45 é a mesma família, por outro caminho:** ali a duplicata não vem de duas
escadas, vem do mesmo clique repetido depois de um tempo esgotado. As duas se
resolvem por unicidade, não por leitura anterior.

### 4.2 A ausência só é garantia quando é testada

D-16 e D-28 se apoiam em **coisas que não existem**: não há coluna de fase, não
há entidade de turno. O que não existe não pode ser violado.

Mas nada impede alguém de **criar** depois, com a melhor das intenções — e aí a
garantia evapora sem aviso.

> **Ausência não é garantia. Ausência + teste estrutural é.**

E o teste precisa cobrir **os dois caminhos**, porque eles são independentes:

| Caminho | O que verifica |
|---|---|
| Estrutura do código | Nenhuma entidade mapeia atributo de fase; nenhuma classe representa turno |
| Estrutura do banco | Nenhuma tabela ou coluna com esse papel existe no schema |

Verificar só o código deixa aberta a migração que cria a coluna direto. Verificar
só o banco deixa aberto o campo calculado e guardado em memória entre
requisições. **Os dois, ou nenhum.**

---

## 5. O que não desce para a persistência, e por quê

| Regra | Por que fica acima |
|---|---|
| D-07, D-10, D-11, D-43 | Dependem de **histórico** — os dois últimos resultados, o nível anterior. Restrição olha o dado, não a série |
| D-08, D-23, D-29, D-38 | Dependem de **parâmetro configurável**. Restrição fixa invalidaria linhas antigas quando o valor mudasse |
| D-15, D-19, D-20, D-25, D-27, D-56, D-57 | São sobre o que é **sugerido**, não sobre o que está gravado. Não há dado errado a impedir |
| D-24 | "Na frente" é derivado; impor exigiria gatilho ou visão materializada (§3.1) |
| D-03 | **Nenhuma camada consegue.** Ver §6 |

**Regras que dependem de histórico têm um risco a mais:** duas transações
simultâneas leem o mesmo estado, aplicam a regra e gravam por cima. A proteção
disso é de persistência (controle de versão na escrita), mas a **regra** é de
serviço. Está anotado em §9 para o documento seguinte.

## 6. A regra que ninguém consegue garantir

D-03 exige produzir antes de julgar. **Nenhum software verifica isso.** O
sistema não sabe se você fechou o material e reconstruiu de memória, ou se
olhou a resposta e clicou "Reconstruí".

O que ele pode fazer é o que `02_JORNADAS` §4.1 especifica: **não oferecer o
atalho.** O resultado não existe na primeira etapa — não escondido, não
desabilitado: ausente. Quem quiser trapacear precisa querer, em dois passos.

É honestidade estrutural, não impedimento. E vale registrar por escrito, porque
uma regra que parece imposta e não é acaba tratada como garantia por quem lê
depressa.

**A mesma família inclui:** a previsão feita de verdade antes (D-04 garante a
ordem de gravação, não a sinceridade) e o tempo de sessão informado
honestamente. O sistema é de um usuário só, e o único prejudicado por dado falso
é quem o digitou. Isso não torna as regras dispensáveis — torna-as **acordo**, e
acordo se escreve.

---

## 7. Como cada natureza é testada

| Natureza | Teste | Quando roda |
|---|---|---|
| Invariante | Escrita inválida **é recusada** — e recusada pela restrição certa, identificada pelo nome | A cada build |
| Derivação | O valor derivado bate com o recalculado do zero; **e o teste estrutural de ausência** (§4.2) | A cada build |
| Comportamento | Entrada conhecida → saída esperada, com data congelada quando houver prazo | A cada build |
| Política | Lista de conferência revisada a olho quando entra tela ou fluxo novo | A cada tela nova |

Duas exigências que valem mais que as outras:

1. **Testar a invariante pelo nome da restrição violada**, não só pelo fato de
   ter falhado. Um teste que aceita "falhou de alguma forma" passa quando a
   escrita quebrou por outro motivo, e some no dia em que a restrição certa for
   removida por engano.
2. **Regra de data se testa com data congelada.** A janela da D-38, os
   intervalos, o represamento da J-4: todos dependem de "hoje". Teste que usa o
   relógio real falha em datas específicas e passa nas outras.

---

## 8. A lista de conferência das políticas

As 7 políticas não têm teste. Têm esta lista, revisada a cada tela nova:

- [ ] **D-14** — existe algum caminho que impede o usuário de fazer o que quer?
- [ ] **D-22** — a tela sugere que todo assunto precisa consolidar?
- [ ] **D-30** — apareceu parâmetro novo para algo que é consequência?
- [ ] **D-26** — algum lugar trata recuperação como bloco longo?
- [ ] **D-32** — a ordem sugerida virou ordem imposta?
- [ ] **D-33/D-34** — a granularidade dos assuntos ainda passa no teste dos 8 minutos?
- [ ] Nenhum percentual aparece sem denominador (`00_PRODUTO` §7)
- [ ] Nenhuma seta de tendência abaixo de `n = 100`
- [ ] Nenhum texto cobra, repreende ou lamenta (`02_JORNADAS` J-4)

Os três últimos não são D-xx: são regras de `00_PRODUTO` que só se manifestam na
tela, e por isso só aqui podem ser conferidas.

---

## 9. Rastreabilidade: o código cita a regra, não o contrário

Uma tabela `D-05 → ServicoDeRevisao.agendar() → uk_revisao_pendente` seria útil
por três semanas e mentira depois. Classe renomeada, método extraído, arquivo
movido — e a tabela vira ficção que ninguém confere.

> **A citação mora no código.** Cada ponto que implementa uma regra a menciona
> pelo identificador; a restrição na persistência leva o identificador no nome.
> A cobertura se apura varrendo o código, não mantendo uma lista.

| O que se pergunta | Como se responde |
|---|---|
| Onde D-07 é implementada? | Varredura por `D-07` no código |
| Alguma regra ficou sem implementação? | Varredura das 95 contra o resultado |
| Esta restrição corresponde a quê? | O identificador está no nome dela |

### 9.1 Citação como metadado, não como comentário

Varrer texto atrás de `D-05` é frágil: uma substituição em massa desatenta apaga
a citação e nada acusa. A forma melhor é a citação ser **um elemento da
linguagem** — algo que o compilador enxerga e que um teste consegue coletar por
reflexão.

Com isso, um único teste responde a pergunta que importa: **alguma das 97 regras
ficou órfã?** Se uma refatoração levou embora a última citação de D-07, o build
quebra na hora, não seis meses depois.

Duas ressalvas que o teste precisa conhecer, ou vira falha permanente:

1. **Nem toda regra vive em código.** As garantidas pela persistência moram numa
   restrição, e restrição não recebe anotação — o identificador vai **no nome
   dela**. A cobertura se apura nas duas fontes, código e schema. É o mesmo
   padrão de dois caminhos da §4.2.
2. **As 7 políticas não têm implementação, por definição.** Elas entram na
   varredura como esperadamente ausentes; sem isso, o teste acusa sete órfãs
   para sempre e alguém o desliga.

O princípio: **referência mantida à mão apodrece**; referência que vive junto do
código se move com ele.

---

## 10. Princípios arquiteturais

Cinco. Se este documento inteiro se perdesse, estes cinco reconstituiriam a
maior parte dele.

1. **Toda informação derivável não é persistida.** Guardar derivação é decisão
   de cache, justificada por escrito — nunca otimização silenciosa.
2. **Toda invariante mora na camada mais baixa que a comporte sem distorção.**
   Não é "quanto mais baixo, melhor": a escada pertence ao serviço, e isso está
   certo.
3. **Nenhuma política desce para a persistência.** Postura não vira restrição.
4. **Todo comportamento relevante tem teste automatizado**, com data congelada
   sempre que depender de prazo.
5. **Nenhuma interface é responsável por integridade.** Se a tela é a única a
   impedir algo, esse algo vai acontecer.

---

## 11. Decisões de garantia

Quatro fechadas nesta versão, duas ainda abertas. O **como** de cada uma é do
documento de persistência; aqui fica a decisão e o motivo.

### 11.1 Fechadas

#### D-45 — a chave é da tentativa, não do conteúdo

Uma restrição sobre `assunto + data + resultado` seria **falso positivo**:
recuperar o mesmo assunto duas vezes no mesmo dia é uso legítimo, e o sistema o
recusaria. A persistência não tem como distinguir clique repetido de segunda
tentativa real — a informação não está no conteúdo.

> **A tentativa carrega um identificador próprio, gerado pelo cliente, e a
> unicidade recai só sobre ele.**

Dois detalhes que decidem se funciona:

| Detalhe | Por quê |
|---|---|
| Gerado **ao abrir a tela**, não ao enviar | Gerado no envio, o duplo clique produz dois identificadores e a proteção evapora |
| Reenvio devolve **sucesso com o resultado original** | Devolver erro faria o cliente exibir falha para algo que gravou. O usuário não pode saber que houve reenvio |

Abrir a tela duas vezes de propósito gera dois identificadores e duas sessões —
que é exatamente o certo.

#### D-18 — sem filtro global de leitura

O filtro automático de arquivados quebraria D-42, que **exige** ler o assunto
arquivado para mostrar o histórico da divisão. A decisão:

| Aspecto | Como |
|---|---|
| Impedir remoção física | A operação de remoção é substituída por marcação, no mapeamento |
| Leitura | **Sem filtro mágico.** Consultas explícitas |

E o remendo para o risco que isso cria — esquecer o filtro numa consulta e
vazar arquivado para M-1: **a consulta sem filtro não existe com nome neutro.**
O acesso padrão devolve ativos; quem quiser arquivados pede por um nome que diz
isso em voz alta. Assim esquecer exige escrever algo que se lê como intenção,
não como descuido.

Continua valendo o teste estrutural: nenhum caminho de remoção física, em
código nem em migração.

#### Regras que dependem de histórico — controle de versão na escrita

D-07, D-10 e D-11 leem a série antes de decidir. Duas transações simultâneas
leem o mesmo estado e gravam por cima.

> **A revisão carrega um número de versão; a segunda gravação concorrente é
> recusada.**

Basta versionar a **revisão**: como D-05 garante no máximo uma pendente por
assunto, só existe uma revisão em disputa por vez. Versionar o assunto seria
mais amplo e mais caro sem cobrir nada a mais.

**Isto não é redundante com D-45, e confundir os dois deixa um buraco:**

| Proteção | Contra o quê |
|---|---|
| Identificador de tentativa (D-45) | **A mesma** tentativa reenviada — rede ruim, botão clicado duas vezes |
| Versão na escrita | **Tentativas diferentes** colidindo — duas abas, dois aparelhos |

Implementar só uma delas e achar-se coberto é o erro provável.

#### Carimbo de criação e alteração

As entidades principais carregam quando foram criadas e alteradas pela última
vez. É barato, ajuda a depurar alteração fora do fluxo, e **não substitui regra
nenhuma** — é metadado, não garantia.

### 11.2 Abertas

| # | Decisão | Por que ainda não fechou |
|---|---|---|
| 1 | Como **frente** e **fase** são consultadas sem uma consulta por assunto | Derivação cruzando assunto, sessão e revisão sobre centenas de linhas. Se virar visão de leitura, **visão simples** — materializada é cache e reintroduz a divergência que D-16 combate |
| 2 | Formato exato da citação de regra no código | Ver §9.1 |

---

## 12. Changelog

| Versão | Data | Mudança |
|---|---|---|
| 1.17.0 | 2026-10-27 | **D-97** com `01_DOMINIO.md` v1.29.0 (PDF anexado a uma lacuna). Um comportamento. Contagem: 98 linhas para 97 regras |
| 1.16.0 | 2026-10-27 | **D-96** com `01_DOMINIO.md` v1.28.0 (avisos de processo). Um comportamento. Contagem: 97 linhas para 96 regras |
| 1.15.0 | 2026-10-24 | **D-86 a D-95** com `01_DOMINIO.md` v1.27.0 (conferência do edital e material de consulta). Uma invariante (D-89), uma derivação (D-94), oito comportamentos. Contagem: 96 linhas para 95 regras |
| 1.14.0 | 2026-10-18 | **D-67 a D-85** com `01_DOMINIO.md` v1.26.0 (prova hospedada e verificação do material). Três invariantes (D-72, D-73, D-80), duas derivações (D-79, D-84), duas políticas (D-67, D-69), doze comportamentos. Contagem: 86 linhas para 85 regras |
| 1.13.0 | 2026-10-17 | **D-66** com `01_DOMINIO.md` v1.25.0 (refazer material). **D-50** passa a índice único parcial sobre os segmentos ativos. Contagem: 67 linhas para 66 regras |
| 1.12.0 | 2026-10-17 | **D-64 e D-65** com `01_DOMINIO.md` v1.24.0 (identidade dada na confirmação; material preparado pelo sistema). As duas Comportamento/`S`. Contagem: 66 linhas para 65 regras |
| 1.11.0 | 2026-10-15 | **D-58 a D-63 adicionadas à tabela de §3** com `01_DOMINIO.md` v1.23.0 (concurso-alvo e análise de edital). D-58 é Invariante/`P` (índice único parcial: um ativo); as demais são Comportamento — D-62 também na interface, porque é a tela de revisão que obriga a confirmar. Contagem: 64 linhas para 63 regras |
| 1.10.0 | 2026-10-14 | **D-56 e D-57 adicionadas à tabela de §3** no mesmo passo de `01_DOMINIO.md` v1.21.0 — ambas Comportamento/`S`, sobre o que é sugerido (§5: nada gravado a impedir). D-29 passa a `S + I`: o parâmetro de duração existe no serviço, e a declaração do tempo mora na tela de registro. Contagem: 58 linhas para 57 regras |
| 1.9.0 | 2026-10-14 | **D-54 e D-55 adicionadas à tabela de §3**, no mesmo passo em que nasceram em `01_DOMINIO.md` v1.20.0. As duas são Comportamento/`S`: dependem de contar segmentos estudados e de ordenar assuntos em estudo, nada que a persistência expresse sem distorção. Contagem: 56 linhas para 55 regras; §0, §3 e §9 atualizados |
| 1.8.0 | 2026-09-06 | **D-53 adicionada à tabela de §3** — nova em `01_DOMINIO.md` v1.18.0 (chave externa obrigatória do próprio `Segmento`, para reimportação identificar um segmento já existente sem depender de `ordem`). Invariante, `P`, mesmo molde de D-52 — a diferença é que aqui a chave é obrigatória (`Segmento` só nasce por importação, nunca manualmente), não opcional. Diferente de D-50/51/52, esta entrou em §3 **no mesmo passo** em que nasceu em `01_DOMINIO`, sem lacuna — a "regra órfã" da revisão anterior não se repetiu. Contagem de §3 corrigida para 17 invariantes / 54 linhas / 53 regras |
| 1.7.0 | 2026-09-06 | **D-50, D-51 e D-52 adicionadas à tabela de §3** — novas em `01_DOMINIO.md` v1.17.0 (entidade `Segmento`, §3.7, e `chaveExterna` de `Assunto`, §3.2, decididas na abertura da Sprint 10). As três são Invariante/`P`: D-50 (ordem do segmento única dentro do assunto) e D-52 (chave externa única entre assuntos) seguem o molde exato de D-47/D-48 — índice único, sem pré-checagem no serviço; D-51 (segmento pertence ao mesmo assunto da sessão que o referencia, e só sessão `ESTUDO` pode referenciar segmento) é caso novo — invariante entre duas tabelas, não uma coluna só, mecanismo de imposição a fechar no documento técnico da sprint (`docs/SPRINT-10-SEGMENTO.md`). Corrigida **antes** do documento técnico, não depois — mesma lacuna de "regra órfã" que a revisão 1.4.0-1.6.0 já tinha corrigido para D-47/48/49, desta vez pega antes de a sprint fechar. Contagem de §3 corrigida para 16 invariantes / 53 linhas / 52 regras. Achado no caminho: §0, §3 e §9.1 ainda diziam "46 regras" — número estava desatualizado desde D-47/48/49 (revisões 1.4.0-1.6.0 corrigiram a tabela, mas não o texto em prosa ao redor); corrigido para 52 nos quatro lugares |
| 1.6.0 | 2026-09-01 | **D-49 adicionada à tabela de §3** — nova em `01_DOMINIO.md` v1.15.0 (reativar cria revisão pendente nova, nunca restaura a cancelada). Comportamento, garantido por `S` (lógica de serviço, sem constraint — nada no banco impede reescrever uma `CANCELADA`, é disciplina de código). Contagem de §3 corrigida para 13 invariantes / 25 comportamentos / 50 linhas / 49 regras |
| 1.5.0 | 2026-09-01 | **D-48 adicionada à tabela de §3** — nova em `01_DOMINIO.md` v1.14.0 (ordem de assunto única entre ativos da mesma disciplina). Invariante, garantida por `P`, mas **parcial** (`WHERE ativo = true`), diferente de D-05/D-45/D-47: a unicidade só importa enquanto o assunto compete pela vaga, arquivar libera o número. Contagem de §3 corrigida para 13 invariantes / 49 linhas / 48 regras |
| 1.4.0 | 2026-09-01 | **D-47 adicionada à tabela de §3** — nova em `01_DOMINIO.md` v1.13.0 (nome de disciplina único). Invariante, garantida por `P` (mesma família de D-05/D-45: unicidade por índice, sem pré-checagem no serviço). Contagem de §3 corrigida para 12 invariantes / 48 linhas / 47 regras |
| 1.3.0 | 2026-08-31 | **D-46 adicionada à tabela de §3** — existia em `01_DOMINIO.md` desde v1.12.0 (Sprint 7), mas esta classificação nunca ganhou a linha correspondente. Invariante, garantida por `P` (mesma família de D-01/D-13, integridade referencial). Contagem de §3 e as três menções a "45 regras" (§0, §9) corrigidas para 46. Achado em auditoria (`/agents/mentor.md`) |
| 1.2.0 | 2026-08-19 | Quatro decisões de garantia fechadas (§11.1): **D-45** por identificador de tentativa gerado ao abrir a tela — restrição sobre conteúdo daria falso positivo em uso legítimo; **D-18** sem filtro global de leitura, porque quebraria D-42, com o acesso padrão devolvendo ativos e o irrestrito exigindo nome explícito; **controle de versão na revisão** para regras que dependem de histórico, registrado como **complementar e não redundante** com D-45; e carimbo de criação/alteração. §9.1: citação de regra como metadado da linguagem, com as duas ressalvas que impedem falha permanente do teste de cobertura |
| 1.1.0 | 2026-08-19 | Revisão do usuário. Nova natureza **Derivação** (§1.1) com riscos próprios. "Banco" vira **Persistência** — por acoplamento de documento, não por portabilidade. A hierarquia vira *"a camada mais baixa que comporte sem distorção"*: a escada pertence ao serviço e isso está certo. **D-24 rebaixada** para comportamento; **D-04 dividida** em duas naturezas; D-16/D-19/D-28 reclassificadas como derivação; **D-45** acrescentada. Ausência passa a exigir teste estrutural **nos dois caminhos** — código e schema. Nova §9 (rastreabilidade pelo código, não por tabela), §10 (cinco princípios) e §11 (decisões pendentes, com duas viradas ADR). Recusada a troca de "revisão humana" por "governança" |
| 1.0.0 | 2026-08-19 | Criado. Classifica as 44 regras de `01_DOMINIO` §10 em invariante, comportamento e política (12/26/6), atribui camada de garantia e teste a cada uma. Estabelece a hierarquia banco > serviço > interface e o princípio de empurrar para a camada mais baixa. Registra que **D-03 não é garantível por software nenhum** e o que se faz no lugar. Lista de conferência para as 8 políticas |
