# Prova e gabarito — especificação conceitual (rascunho)

| | |
|---|---|
| Versão | 0.3.0 |
| Data | 2026-10-18 |
| Status | **Rascunho — não vigente.** Nada daqui é implementado até os requisitos serem aprovados e as seções migrarem para os documentos donos (§11) |
| Origem | Pedido do usuário, 2026-10-17: "subir uma prova e saber se, com o material montado, seria possível responder as questões e gabaritar o concurso. É o nosso objetivo final". Revisado em 2026-10-18 com as respostas do usuário aos pontos em aberto |
| Decisões já tomadas | Gabarito obrigatório · o SGE hospeda a prova e o usuário a responde no sistema · a nota alimenta disciplina e global, assunto só informa · reanálise incremental por assunto · só o concurso-alvo · IA julga questão a questão · entrada pelo mesmo gesto do edital (§10) |

**Mudanças da v0.2.0.** Amostra real de 4 bancas (§5.6): o gabarito vem em arquivo multi-cargo e multi-tipo, em layouts diferentes; uma prova veio em imagem pura; questão fora do edital deixa de ser furo. Regras P-17 e P-18; pontos abertos I, J e K.

**Mudanças da v0.1.0 (já na v0.2.0).** O gabarito deixou de ser opcional. O SGE passa a guardar a prova
(enunciado, alternativas, gabarito) e o usuário a responde no sistema. O critério do julgamento
passou a ser a alternativa do gabarito. A reanálise passou a ser incremental. Isso revisa
`00_PRODUTO` §5.1 e §6.7 (ver §11).

---

## 1. Para que serve

Hoje o sistema sabe **quanto** material existe por assunto (91 de 97 prontos, 15 896 min) e **o
que não existe** (6 lacunas, com motivo). Não sabe se o que existe **ensina o suficiente**. O
assunto "Relação do texto com seu contexto histórico" está "pronto" com 2 páginas soltas; o
validador o aceitou porque a faixa é válida, não porque ensina.

A funcionalidade responde a pergunta do objetivo final — **"com este material, eu acertaria
esta prova?"** — por dois caminhos que se conferem:

| Face | Pergunta | Natureza |
|---|---|---|
| **Cobertura** | O material sustenta a alternativa correta de cada questão? | **Previsão**, feita pela IA, antes de estudar |
| **Resolução** | O usuário acertou a questão? | **Medida**, feita pelo usuário, depois de estudar |

A previsão é barata e vem primeiro; a medida é a verdade. Comparar as duas **por assunto**
mostra onde a IA errou o julgamento e calibra o critério (§5.3). Se o usuário tira boa nota
numa prova que a cobertura previa como furada, o julgamento está severo demais; se tira nota
ruim onde a cobertura previa sólida, o material ensina menos do que parece. **O critério de
sucesso do sistema é a nota do usuário** — é o que ele pediu.

**Como saberemos que funcionou.**

1. **Ensaio com prova conhecida:** uma prova anterior da FCC (banca do SEFAZ SC) analisada
   contra o material atual. Os assuntos já sabidamente furados (Morfossintaxe, Flexão, SC)
   precisam aparecer como furos; os sólidos, não.
2. **Conferência de amostra:** o usuário confere ~20 vereditos à mão. Se discordar da IA em
   mais de 1 a cada 5, o critério (§5) é ajustado antes de a funcionalidade valer.
3. **A nota real**, quando o usuário resolver a prova depois de estudar, contra a cobertura
   prevista (§6).

---

## 2. O que é e o que não é

**É:**

- Um relatório de **cobertura do material sobre uma prova**, por questão, por assunto e por
  disciplina, apontando onde o material do concurso-alvo não sustenta a resposta correta.
- Uma **prova hospedada** que o usuário resolve no próprio sistema, que a corrige pelo
  gabarito e registra a nota.

**Não é:**

- **Um banco aberto de questões.** A prova é hospedada para **este fim** (verificar e
  resolver), não para ser buscada, filtrada ou exportada em massa (P-06).
- **Uma plataforma de questões.** O usuário continua resolvendo volume na plataforma externa
  (`00_PRODUTO` §5.1, primeira parte); aqui entra a prova inteira que ele trouxe, uma por vez.
- **Uma fonte de revisão.** A nota **não gera revisão, não mexe na escada e não muda o turno**
  (P-12). É medida, não gatilho.
- **Uma garantia de aprovação.** "Gabaritar" é o horizonte; o relatório mede a distância com a
  incerteza de um julgamento de IA (§5.3).
- **Um gerador de material.** Achou furo, o caminho é o que já existe: refazer o material do
  assunto (D-66) ou indexar apostila nova. Nada é inventado (D-65).
- **Discursiva ou estudo de caso.** Só prova objetiva (múltipla escolha e certo/errado).

---

## 3. Glossário

| Termo | Significa |
|---|---|
| **Prova** | A prova objetiva de um concurso (qualquer banca, qualquer ano), hospedada no SGE |
| **Questão** | Enunciado, alternativas e a resposta do gabarito; pertence a uma prova |
| **Gabarito** | A resposta oficial de cada questão. **Obrigatório** para julgar e para corrigir |
| **Tipo da prova** | A versão impressa (Tipo 1, 2...), quando a banca embaralha alternativas. O gabarito é do tipo (§5.5) |
| **Questão avaliável** | Questão com enunciado em texto e gabarito válido; fora disso, sai do denominador (P-04) |
| **Atribuição** | O assunto do concurso-alvo ao qual a questão pertence |
| **Sustentação** | A página do material do assunto que ensina o necessário para justificar a alternativa do gabarito |
| **Veredito** | `RESPONDIVEL` · `PARCIAL` · `NAO_RESPONDIVEL` (§5.2) |
| **Furo** | Assunto com questões `PARCIAL` ou `NAO_RESPONDIVEL`, ou questão sem assunto no edital |
| **Resolução** | O usuário responde a prova no sistema; a correção sai do gabarito |
| **Nota** | Acertos sobre questões avaliáveis, por disciplina e global |
| **Relatório** | O retrato datado de cobertura e (quando houver) de nota |

---

## 4. As jornadas

### JV-1 · Carregar a prova e verificar o material

Rara — algumas vezes por concurso —, como a J-5: o orçamento é de **atenção**, não de
segundos. O usuário carrega os PDFs e confere; o resto é do sistema.

1. Em **Concursos**, no concurso-alvo, toca **"Verificar com uma prova"**.
2. Escolhe **"Carregar prova"** (PDF) e **"Carregar gabarito"** (PDF com texto, ou texto
   colado) e o sistema **descobre o cargo e o tipo** pela capa da prova e **recorta o gabarito certo** (§5.6); se não tiver certeza, pergunta. As duas entradas ficam na mesma tela;
   enquanto faltar o gabarito, a prova fica **"aguardando gabarito"**: o sistema **lê** a
   prova, mas **não julga nem deixa resolver** (P-15).
3. O sistema lê a prova e mostra o que achou **antes de gastar IA no julgamento**: nome
   (corrigível), banca, ano, **quantas questões leu**, quantas não consegue avaliar (imagem,
   tabela, anulada) e **quantas respostas do gabarito casaram** com questões. É o ponto de
   conferência, como a proposta do edital (D-62): erro de leitura aqui custa um toque.
4. Revisa só o que vem **duvidoso**: questões que a IA atribuiu com baixa confiança ou que
   ficaram **sem assunto no edital**. As demais atribuições o usuário não precisa ver.
5. Confirma. A análise roda em segundo plano — **a tela pode ser fechada**; ela continua e fica
   guardada, como a análise de edital. Acompanha em Concursos: "Prova X: 34 de 60 questões
   julgadas".
6. **Relatório** (§6): o número que importa no topo, os assuntos furados, e cada questão.
7. Do relatório, vai ao assunto furado: ver o motivo, **refazer o material** ou aceitar. Depois
   que o material mudar (D-66), **"Analisar de novo"** julga só as questões dos assuntos que
   mudaram (P-13); a prova **não é relida**.

Falhas que o usuário vê, e o que fazem:

| Falha | O que acontece |
|---|---|
| PDF é imagem (sem texto) | **Não recusa mais** (§5.6): a prova pode ser lida por OCR; se o OCR não estiver disponível, recusa com o motivo |
| Leu poucas questões (<50% do esperado) | Mostra e avisa; o usuário decide seguir ou trocar o PDF |
| Gabarito não casa (nº de questões diferente) | Recusa o gabarito, mantém a prova; diz a diferença |
| Gabarito de outro tipo da prova | Não há como o sistema ter certeza; ver §5.5 |
| IA indisponível / cota da assinatura esgotada | A análise **pausa** e retoma sozinha (ADR-045). Nunca vira veredito (P-09) |

### JV-2 · Resolver a prova

Uma prova inteira é um bloco de horas, como o simulado do `00_PRODUTO` §6.7; o usuário a faz
uma vez, de propósito, e o sistema não a mistura com o estudo diário.

1. Em Concursos, na prova já verificada (ou só lida, com gabarito), toca **"Resolver"**.
2. Uma questão por tela, uma alternativa por toque; pode **marcar para revisar**, **pular** e
   **sair e voltar** — o progresso fica guardado, e a mesma tentativa conta uma vez (P-14).
   Cronômetro opcional, com o tempo da prova.
3. Toca **"Entregar"**. O sistema corrige **pelo gabarito** (nunca pela IA) e mostra a **nota**:
   acertos de questões avaliáveis, **por disciplina e global**, sempre com denominador.
4. O relatório ganha a **nota real** ao lado da cobertura (§6). Cada erro mostra a questão, a
   alternativa marcada, a correta e — se houve cobertura — a **página que a sustentava**: o
   caminho do erro até o ponto do material, em um toque.

Orçamento: ≤ 1 s entre uma questão e a seguinte (é só navegação local); entregar e corrigir em
≤ 3 s.

---

## 5. O julgamento

### 5.1 Três etapas, cada uma com o que a IA faz e o que o sistema decide

| Etapa | A IA | O sistema |
|---|---|---|
| **Leitura** | Separa as questões do PDF: número, enunciado, alternativas; lê o gabarito | Valida: números sequenciais, sem repetição, alternativas presentes, gabarito casando. O que não passa é "não avaliável", declarado |
| **Atribuição** | Escolhe, dentre os assuntos do concurso-alvo, a qual pertence cada questão, ou "nenhum", com um grau de confiança | Só aceita assunto que existe na cobertura (como D-63). Baixa confiança vai para a revisão do usuário; "nenhum" é **fora do edital**, declarado e fora do denominador (P-08) |
| **Julgamento** | Lê a questão, a alternativa do gabarito e **trechos** do material do assunto (escolhidos por busca no índice, com o sumário); diz o veredito e **cita a página** | Só aceita citação que existe no segmento. Sem página válida, o veredito cai para `NAO_RESPONDIVEL` |

### 5.2 O veredito — sobre a alternativa do gabarito

O que importa é a **alternativa correta**: as erradas podem usar fatos que não estão em PDF
nenhum, e isso **não é furo**. O critério é se o material **sustenta a alternativa do gabarito**
— e, por isso, o enunciado e a resposta oficial entram no pedido à IA.

| Veredito | Quer dizer | Exige |
|---|---|---|
| `RESPONDIVEL` | O material do assunto ensina o necessário para justificar a alternativa do gabarito | Página de sustentação válida |
| `PARCIAL` | Ensina parte; falta um elemento que a justificativa exige (exceção, valor, conceito vizinho) | Página do que há **e** nota do que falta |
| `NAO_RESPONDIVEL` | O material do assunto não traz o necessário | Nota do que falta |

Casos que mudam o que "sustentar" quer dizer:

- **"Marque a incorreta"** (ou "errada"): a alternativa do gabarito é a **falsa**. O material
  sustenta se permite **reconhecê-la como falsa**; não se exige que ensine as outras.
- **Certo/errado:** a resposta do gabarito é `C` ou `E`; o material sustenta se permite
  decidir o item nesse sentido.
- **Cálculo:** o material precisa ensinar o **método**, não só o conceito.

Regra de ouro, como D-65: **na dúvida, o veredito é o pior dos dois.** Prefere-se acusar furo
falso (custo: o usuário confere) a esconder furo real (custo: a prova).

### 5.3 O limite honesto

O veredito é **um julgamento de IA sobre texto**, não uma medição. Duas consequências
vinculantes:

- O relatório mostra sempre o **denominador** (princípio 9 do `00_PRODUTO`): "42 de 58
  questões avaliáveis", nunca só "72%".
- Cada questão mostra a **sustentação** (página). O usuário abre o PDF e discorda com um toque;
  o número nunca é uma caixa-preta.

A **nota real** (JV-2) é o que corrige o julgamento ao longo do tempo: onde cobertura e nota
discordam por assunto, o relatório mostra a discordância, e a conferência de amostra (§1)
fica mais barata do que antes de existir resolução.

### 5.4 Reanálise incremental

O material muda (D-66, indexação nova). Reler a prova não adianta — o texto é o mesmo —; o que
muda é **o julgamento dos assuntos cujo material mudou**.

- Um assunto **mudou** se tem segmento ativo **criado ou arquivado depois** do veredito vigente
  das suas questões. É **derivado** dos segmentos (D-16): nenhuma coluna nova, nenhum "sujo".
- **"Analisar de novo"** julga de novo **só** as questões desses assuntos, e cada uma ganha um
  veredito **novo**; o anterior permanece (D-18).
- A **leitura** da prova roda **uma vez**. O enunciado já está no SGE, então a reanálise nem
  toca o PDF.

Custo proporcional à mudança, e não à prova: refazer o material de um assunto reabre
algumas questões, não 60.

### 5.5 O tipo da prova (risco real)

Bancas como a FCC imprimem a prova em **tipos**, com alternativas embaralhadas e um gabarito
**por tipo**. Gabarito do tipo errado faz o julgamento inteiro apontar a alternativa errada,
**em silêncio**, e a correção da JV-2 marcar certo o que o usuário errou. Proteções propostas:

- O usuário informa o **tipo** na entrada; o gabarito é associado a ele.
- Na conferência (JV-1, passo 3), o sistema **sorteia 3 questões** e mostra enunciado, alternativa
  do gabarito e o veredito inicial, para o olho do usuário pegar uma troca grosseira.
- A reanálise não esconde o tipo: ele aparece no relatório.

Se o PDF da prova traz um único tipo (caso comum de provas já publicadas com tipo único), a
pergunta some. Ver §10.

### 5.6 O que as bancas entregam (amostra real, 2026-10-18)

Quatro bancas, seis arquivos úteis, avaliados em `provas e gabaritos/` do usuário. Nenhuma
entrega é igual à outra, e isso decide onde a IA entra e onde o sistema só valida.

| | CEBRASPE (UNEAL 2026) | FGV (Dataprev e MPES 2026) | IDECAN (SEFAZ-RR 2023) |
|---|---|---|---|
| **Prova** | Texto; itens **certo/errado**, ligados a um texto-base e a um comando ("Julgue os itens...") | Texto; **A–E**; nº da questão sozinho na linha; cabeçalho de disciplina entre as questões; a prova do MPES traz **também discursiva** | **Imagem pura**, sem camada de texto, duas colunas; A–E |
| **Gabarito** | 2 págs., grade de colunas fora de ordem; anulado = `X`; vários blocos (básicos, específicos) | **Um arquivo para todos os cargos**: Dataprev 22 págs., MPES 41 págs., **uma página por cargo e tipo**. Layout varia entre editais (Dataprev: linha de números e linha de letras; MPES: pares "n letra"). Anulada = `*`. Dataprev é **preliminar**; MPES, **definitivo** | Um arquivo, 6 cargos (`9001`–`9006`), grade `Q01..Q10` e letras; anulada = `*` |
| **Tipo** | Não aparece | Tipo 1 e Tipo 3, **respostas diferentes** (8 de 70 diferem no Dataprev) | Tipo A |
| **Identificar o par** | Pelo cargo no título | Capa da prova × cabeçalho da página do gabarito, com nomes grafados diferente ("DESENVOLVIMENTO DE SOFTWARE" × "Desenvolvimento de Software") | Código do cargo (`9006`) na capa da prova × bloco do gabarito |

O que isso obriga:

- **Gabarito é multi-cargo e multi-tipo** (P-17). O sistema precisa recortar a página certa
  pela capa da prova, normalizando acento e caixa, e mostrar o recorte na conferência, porque
  escolher o cargo errado dá um gabarito plausível e errado.
- **O layout do gabarito muda até dentro da mesma banca.** A extração por regra fixa quebra no
  edital seguinte; por isso a **IA lê o gabarito** e o **sistema valida**: número de respostas
  igual ao de questões, só letras válidas, anuladas reconhecidas (`*`, `X`).
- **Anulada e preliminar existem de verdade** (P-04, P-18). 14 questões anuladas só no gabarito
  do IDECAN.
- **PDF em imagem é caso real, não exceção:** a prova do IDECAN, entregue pela própria banca,
  não tem texto. Recusar na hora (como o edital) descartaria uma prova legítima. É preciso OCR
  ou leitura por visão (ponto I). **No Pi não há OCR hoje**, nem no worker.
- **CEBRASPE liga o item ao texto-base:** a questão carrega o texto e o comando, senão o item
  "O trecho acima..." não é julgável. A leitura precisa anexá-los (ponto J).
- **A prova traz blocos que o edital do alvo não tem:** legislação do próprio órgão (MPES,
  CETIF), português e raciocínio lógico de outro cargo. Daí "fora do edital" (P-08).
- **O cabeçalho de disciplina da prova** ("Legislação e Código de Ética do MPES", "CONHECIMENTOS
  GERAIS EM TI") ajuda a atribuição: a IA o recebe como pista.
- **Discursiva na mesma prova:** ignorar (só objetiva) e declarar quantas ignorou.

---

## 6. O relatório

De cima para baixo, o que o usuário precisa para decidir:

1. **O número de cobertura:** "Material sustenta **42 de 58** questões avaliáveis · 9 parciais ·
   7 não", da prova X, analisada em 17/10/2026 contra o material daquela data.
2. **A nota real**, se resolvida: "Você acertou **38 de 58**". Ao lado do número de cobertura.
3. **Por disciplina:** cobertura e nota, na ordem do edital.
4. **Assuntos furados:** os com `PARCIAL`/`NAO_RESPONDIVEL`, do mais furado ao menos, cada um
   com quantas questões da prova caem nele e o que faltou. Mais os **assuntos sem nenhuma
   questão** na prova (informação, não furo).
5. **Onde cobertura e nota discordam**, por assunto: prevista sólida e errada (o material ensina
   menos do que parece) ou prevista furada e acertada (o julgamento está severo). Só quando há
   as duas medidas; **informação, não gatilho** (P-12).
6. **Questões fora do edital:** a prova cobra algo que o edital do alvo não lista (a legislação do órgão da prova, o bloco de outro cargo). **Não são furo do material** e saem do denominador (P-08), mas aparecem contadas.
7. **Cada questão:** número, assunto atribuído, veredito, página de sustentação e, se resolvida,
   o que o usuário marcou.

O desempenho **por assunto** é sempre informação. O que entra em M-1 é a nota por **disciplina**
e a global (P-12).

---

## 7. O que o SGE guarda

Conceitual; coluna e índice são do documento de banco da sprint, depois.

| Guarda | Não guarda |
|---|---|
| A **prova**: concurso-alvo, nome, banca, ano, tipo, estado, quando | Nada que reconstitua uma prova fora do contexto dela (P-06) |
| As **questões**: número, enunciado, alternativas, gabarito, se avaliável e por quê | Uma busca ou lista de questões fora de uma prova |
| A **atribuição** de cada questão a um assunto, e se o usuário a corrigiu | |
| O **veredito**: por questão, com página, nota do que falta e quando; um por rodada | |
| A **resolução**: respostas do usuário, tentativa, tempo, entrega | |
| A **nota**, derivada das respostas e do gabarito: **não** é persistida (D-16) | |

Veredito é **fato registrado** (um julgamento externo, como a lacuna de D-65); a nota e as
contagens do relatório são **derivadas** do veredito e das respostas, nunca persistidas.

**Planejamento e SGE.** O planejamento **lê o PDF** e entrega as questões ao SGE por uma rota
interna, como entrega os segmentos; o SGE guarda. Para julgar, o planejamento **pede as questões
ao SGE**, e o trecho do material vem do índice dele. O planejamento **não guarda enunciado**:
tudo que a reanálise precisa já está no SGE.

---

## 8. Regras propostas

Numeração provisória `P-xx`, para não colidir com as D-xx; ao migrar para o `01_DOMINIO` §10
viram D-67 em diante. Cada uma diz como se garante (`03_INVARIANTES` §4).

| Id | Regra | Natureza provável |
|---|---|---|
| P-01 | A IA lê e julga; o **sistema** valida, conta e calcula. Contagem e nota saem de regra fixa, nunca da IA (como D-61) | Política no serviço |
| P-02 | **Nada é inventado:** `RESPONDIVEL` exige página de sustentação **da alternativa do gabarito** que exista no segmento do assunto; citação inexistente rebaixa para `NAO_RESPONDIVEL` | Validação no serviço + teste |
| P-03 | **Na dúvida, o pior veredito** | Instrução à IA + teste de calibragem |
| P-04 | Questão não avaliável (imagem, tabela, anulada, sem gabarito, fora do edital) sai do denominador **e é declarada** | Serviço |
| P-05 | **Denominador sempre** exibido junto de qualquer percentual ou nota | Interface + teste |
| P-06 | A prova hospedada serve **só à verificação e à resolução**: nenhuma rota lista, busca ou exporta questões fora do contexto de uma prova. O SGE **não vira banco aberto de questões** | Estrutura: o contrato não tem a rota; teste estrutural |
| P-07 | **Nada é apagado:** reanalisar cria veredito novo e o anterior permanece; resposta e entrega também ficam (D-18) | Persistência (sem caminho de remoção) |
| P-08 | A atribuição só aceita assunto da **cobertura do concurso-alvo** (como D-63); "nenhum" é resposta válida: a questão é **fora do edital**, sai do denominador e é declarada, e **não conta como furo** do material | Serviço |
| P-09 | **Indisponibilidade da IA não é veredito:** a análise pausa e retoma (ADR-045); nunca grava `NAO_RESPONDIVEL` por falha | Serviço + teste |
| P-10 | O veredito é um **retrato datado** contra o material vigente naquela data; mudar o material não altera vereditos antigos, só motiva um novo | Persistência |
| P-11 | A prova é sempre avaliada contra a cobertura do **concurso-alvo** | Serviço |
| P-12 | A nota entra em **M-1 por disciplina e global**, como o simulado do `00_PRODUTO` §6.7; o desempenho por assunto **só informa**: não gera revisão, não toca a escada, não muda o turno | Serviço + teste de ausência (nenhuma sessão é criada) |
| P-13 | **Reanálise incremental:** um assunto mudou se tem segmento ativo criado ou arquivado depois do veredito vigente das suas questões; "Analisar de novo" julga só essas questões. Derivado dos segmentos (D-16), sem coluna nova | Derivação + teste com data congelada |
| P-14 | **A mesma tentativa de resolução conta uma vez** (identificador gerado no cliente ao abrir a tela, como D-45). A **correção é do sistema, pelo gabarito**, nunca da IA | Persistência (unicidade) + serviço |
| P-15 | **Sem gabarito, a prova não é julgada nem resolvida:** fica "aguardando gabarito"; a leitura segue | Serviço |
| P-16 | O gabarito tem o **tipo** da prova; gabarito de nº de questões diferente é recusado | Serviço |
| P-17 | O gabarito é do **cargo e do tipo** da prova; arquivo multi-cargo é **recortado pelo sistema** e o recorte (cargo, tipo, turno) é mostrado na conferência. Sem recorte único, o usuário escolhe | Serviço + teste com os arquivos reais |
| P-18 | **Gabarito preliminar vale como provisório:** o relatório marca "gabarito preliminar". Quando o definitivo chega e muda uma resposta ou anula uma questão, a nota se recalcula (derivada) e as questões afetadas voltam para julgamento (como P-13) | Derivação + teste |

P-06 é a que mais custa se esquecida: é ela que impede esta funcionalidade de virar, por
acidente, o banco de questões que o produto escolheu não ser.

---

## 9. Requisitos não funcionais

| Requisito | Meta proposta | Por quê |
|---|---|---|
| **Custo de IA** | Julgamento ≈ 1 chamada por questão; atribuição ≈ 1 por lote de ~10; prova de 60 questões ≈ 70 chamadas, uma vez. Reanálise: só o que mudou | A cota da assinatura é o recurso escasso |
| **Tempo** | Prova de 60 questões em até ~30 min, em segundo plano | Como o material |
| **Memória no Pi** | Dentro do teto do worker (768 MB, `03E_DEPLOYMENT` §8C) | O Pi 4B tem 4 GB para tudo |
| **Retomada** | Reiniciar o worker no meio retoma de onde parou, sem repetir questão julgada | O worker já se reinicia no deploy |
| **Idempotência** | Reenviar a mesma prova (mesmo hash) não duplica | Como D-45 |
| **Resolução** | Navegação local; resposta gravada ao tocar; sair e voltar não perde nada | Provas de 3 a 4 horas |
| **Tamanho** | PDF até o mesmo limite do edital | Consistência |

---

## 10. Pontos de requisito

### 10.1 Decididos

| # | Ponto | Decisão |
|---|---|---|
| 1 | Contra qual concurso | Só o **alvo** (P-11) |
| 2 | Bancas e anos | Qualquer; o usuário escolhe |
| 3 | Onde mora o texto | **No SGE** (mudou da v0.1.0): o planejamento lê e entrega |
| 4 | Várias provas juntas | Fora da primeira entrega: um relatório por prova; consolidado, sprint seguinte |
| 5 | Relatório mostra enunciado | **Sim, e o usuário resolve a prova** (mudou) |
| 6 | Texto completo ou trechos | **Trechos** por busca no índice, mais o sumário |
| 7 | Influencia o turno | **Não** nesta fase (P-12) |
| 8 | Limiar de "gabaritar" | Não definir; mostrar número e distância à nota de corte quando a Sprint 23 existir |
| 9 | Discursiva | Fora: só objetiva |
| 10 | Formatos de gabarito | PDF com texto e texto colado; imagem não |
| 11 | Qual IA | A mesma assinatura da preparação; modelo escolhido em Ajustes |
| 12 | Prazo | **Nenhum.** O usuário não fará a prova de 22/11/2026 e usa o sistema para começar a estudar em 2027 |
| 13 | Gabarito | **Obrigatório** (P-15) |
| 14 | Reanálise | **Incremental por assunto** (P-13) |
| 15 | O que a nota alimenta | Disciplina e global (M-1); assunto só informa (P-12) |

### 10.2 Abertos — para decidir antes de implementar

| # | Ponto | Recomendação |
|---|---|---|
| A | **Tipo/versão da prova** (§5.5): o usuário informa o tipo? Como evitar gabarito do tipo errado em silêncio? | Informar o tipo na entrada, mais o sorteio de 3 questões na conferência |
| B | **Revisão da atribuição:** só o duvidoso, ou todas? | Só o duvidoso (baixa confiança e "sem assunto"), para não pedir revisão de 100 questões |
| C | **Resolver antes de verificar:** o usuário pode resolver a prova sem a cobertura ter rodado? | Sim, desde que haja gabarito; a cobertura se junta depois |
| D | **Cronômetro:** obrigatório, opcional ou nenhum? | Opcional, com o tempo da prova; o resultado marca se foi cronometrado |
| E | **Resolver mais de uma vez** a mesma prova | Cada tentativa é um registro; a nota que entra em M-1 é a **primeira** (as seguintes já têm memória da prova), as outras ficam como informação |
| F | **Posição no mapa:** sprint própria ou fundida com a 23 (simulado no formato da prova, com nota de corte) | A resolução (JV-2) é o simulado da Sprint 23; a cobertura (JV-1) pode sair antes. Decidir depois de aprovados os requisitos |
| G | **Direitos da prova:** uso pessoal de provas públicas de banca, um usuário só | Registrar no ADR que o conteúdo é do usuário e não é redistribuído |
| I | **Prova em imagem (§5.6):** OCR no worker (instalar Tesseract com português: mais disco e memória no Pi), ou leitura por visão (o proxy de IA hoje só aceita texto) | OCR no worker, num estágio de **leitura** separado do julgamento, e conferência mais rígida (OCR erra): mostrar a página lida ao lado do texto extraído em 3 questões sorteadas. Medir tamanho e RAM antes de aprovar |
| J | **Texto-base e figuras:** itens do CEBRASPE dependem do texto; questões com imagem (gráfico, diagrama) não são avaliáveis | Anexar o texto-base à questão; imagem = não avaliável e declarada (P-04) |
| K | **Quantas provas bastam:** as 4 bancas da amostra são de TI, mas a banca do alvo (FCC) não está nela | Pedir uma prova da FCC para o ensaio da §1, porque o formato pode ser o mais diferente de todos |
| H | **Tempo e custo da primeira prova** | Medir no ensaio da FCC antes de fixar as metas da §9 |

---

## 11. Para onde cada parte vai quando for aprovada

| Parte | Dono | Documento |
|---|---|---|
| Para que serve, métrica, não-objetivo | `00_PRODUTO` | §7 (métrica), §11 (decisão vinculante) |
| **Revisões necessárias** | `00_PRODUTO` | **§5.1** ("o sistema não faz: hospeda enunciado, alternativa ou gabarito; corrige questão") e **§6.7** ("não há atribuição confiável por assunto"), mais as decisões vinculantes nº 2 ("a recuperação acontece fora") e nº 15 ("simulado não é sessão") |
| Glossário e regras P-01 a P-18 | `01_DOMINIO` | §3 (nova subseção), §10 (D-67+) |
| Jornadas JV-1 e JV-2, falhas, orçamento | `02_JORNADAS` | novas jornadas, ao lado da J-5 |
| Quem garante P-xx e como se testa | `03_INVARIANTES` | §3 e §9 |
| Por que hospedar a prova; por que o §6.7 muda; direitos | `docs/00A_ADR.md` | ADR novo |
| Tabelas, endpoints, formato do enunciado | documento da sprint | nasce quando a sprint começar |

**Sobreposição com o mapa.** A linha da **Sprint 23** ("simulado no formato da prova, com nota de
corte") cobre a resolução (JV-2). Quando os requisitos forem aprovados, ou a Sprint 23 absorve a
JV-2, ou esta funcionalidade ganha a sua e a 23 perde essa parte (ponto F).

Nada de coluna, endpoint ou formato de arquivo entra aqui (segunda regra do projeto: a
documentação técnica nasce com a sprint).

---

## Changelog

| Versão | Data | Mudança |
|---|---|---|
| 0.3.0 | 2026-10-18 | Amostra real de 4 bancas (§5.6): gabarito multi-cargo e multi-tipo em layouts diferentes (P-17), preliminar × definitivo (P-18), prova em imagem sem OCR (ponto I), texto-base do CEBRASPE (J), banca do alvo fora da amostra (K). Questão fora do edital deixa de ser furo e sai do denominador (P-04, P-08). PDF em imagem deixa de ser recusado na hora |
| 0.2.0 | 2026-10-18 | Respostas do usuário aos pontos em aberto. Gabarito obrigatório e critério sobre a alternativa do gabarito (§5.2). O SGE hospeda a prova e o usuário a resolve (JV-2); revisa `00_PRODUTO` §5.1 e §6.7. Nota alimenta disciplina e global, assunto só informa (P-12). Reanálise incremental derivada dos segmentos (P-13). Regras P-12 a P-16. Pontos decididos e abertos separados (§10) |
| 0.1.0 | 2026-10-17 | Rascunho inicial: prova só no planejamento, gabarito opcional, IA por questão |
