# Revisão do edital e material por casamento — especificação conceitual (rascunho)

| | |
|---|---|
| Versão | 0.1.0 |
| Data | 2026-10-24 |
| Status | **Rascunho para aprovação do usuário.** Nada daqui está em `01_DOMINIO`, `02_JORNADAS`, `03_INVARIANTES` ou `docs/00A_ADR.md` ainda; a migração (como foi feita com `ESPECIFICACAO-PROVA-GABARITO.md`) só acontece depois de os pontos da §9 serem decididos |
| Origem | Pedido do usuário (2026-10-22/23): "do edital ao estudo pronto", com revisão manual no SGE como parte central. Pedidos do planejamento em `docs/requisitos-planejamento-blocos-de-conteudo.md` §17 e §18; desenho de origem, no repositório do planejamento: `docs/especificacao-revisao-edital-sge.md` (0.3.0) e `docs/proposta-material-por-casamento.md` (0.4.0) |
| Decisões já tomadas | A IA propõe, as regras conferem, **a pessoa resolve** · decisão aprovada vale antes da IA · um único usuário · árvore de referência = QConcursos (TEC é outra funcionalidade) · questões fora dos segmentos · material já criado nunca é apagado |

---

## 1. Para que serve

Hoje o edital entra por uma análise de IA dentro do SGE (ADR-043): assuntos, disciplinas, peso, correspondência com o que
existe. O material vem depois, de uma busca por palavras no acervo (ADR-044). Duas fraquezas foram medidas no planejamento:

1. **O assunto do edital é grosso.** "Banco de dados: modelagem, normalização, SQL" é um assunto só, e a IA de material
   precisa adivinhar o que ensina cada pedaço. O planejamento passou a quebrar o assunto em **partes** (texto literal do
   edital, conferido por regra) e a ligar cada parte a um **nó da árvore de assuntos do QConcursos**, que é também o filtro
   das questões. Com o nó como pivô, o casamento com os temas das apostilas ficou verificável (84% de veredito igual entre
   duas IAs; 92% quanto a servir ou não).
2. **A IA erra, e trocar de modelo só troca o erro.** A qualidade tem de vir de a pessoa resolver cada divergência com pouco
   esforço e de o sistema guardar o que ela decidiu.

Esta especificação diz **o que o SGE precisa ter** para receber isso: a tela de revisão, o que ele guarda, o que muda no
fluxo de edital novo (J-5) e no material (§3.10), e o novo material de consulta "Questões deste assunto".

## 2. O que já existe e não muda

- `Concurso` com cobertura e histórico, ativação aplica a cobertura (D-58, D-60); peso por regra (D-61); nada gravado sem
  confirmação (D-62); chave externa do assunto e identificador do concurso (D-64).
- `Segmento` (§3.7): ~60 min, preso ao assunto, **nunca apagado** (D-18); refazer arquiva e não apaga (D-66).
- O worker do material roda no mesmo servidor, fala com o SGE por `/internal/**` com `INTERNAL_TOKEN` e usa a IA
  configurada em Ajustes (ADR-044, ADR-045). O contrato novo usa o mesmo canal.
- A escada, as sessões, as revisões e os erros **não dependem** de partes nem de nós: seguem o assunto. Trocar um nó nunca
  os toca.

## 3. Conceitos novos

| Termo | É |
|---|---|
| **Parte** | Pedaço de um assunto do edital, em texto literal (o que vem separado por vírgula ou ponto e vírgula). Pertence a um assunto; tem `chave` estável |
| **Nó** | Posição na árvore de assuntos do QConcursos. Referido só por id; o caminho se lê da árvore |
| **Ligação** | Parte → até 3 nós, com **natureza** (`exato`, `mais amplo`, `parcial`) e **origem** (`ia`, `refino`, `decisão`). Parte sem nó e sem natureza é "a árvore não tem" |
| **Decisão** | O que o usuário resolveu numa divergência. Chave: parte + contexto (sem a disciplina). Escopo: este edital (padrão) ou todos |
| **Revisão do edital** | O estado de conferência de um edital: pendências abertas, decididas, aplicadas de decisão anterior; pode ser concluída com pendências que não bloqueiam |
| **Material de consulta** | Páginas das apostilas que **não são leitura obrigatória**: questões comentadas, lista, questionário e gabarito das aulas do assunto, juntadas num PDF "Questões deste assunto", sem entrar na escada |

A parte **não é** assunto: o assunto continua sendo a unidade de estudo, de escada e de peso (§3.2). A parte só existe para
conferir a cobertura e ligar ao nó.

## 4. O fluxo (revisa J-5, passos 3 a 7)

1. Em **Concursos → Novo edital**, o usuário sobe o PDF e escolhe o cargo (como hoje). **O PDF passa a ser guardado** no volume
   compartilhado com o worker enquanto a revisão não termina (hoje o ADR-043 não o guarda) — ver ponto A.
2. O worker do planejamento pega o edital, extrai o texto, organiza em disciplinas, quebra em assuntos e partes, liga as partes
   aos nós, e **entrega a revisão** ao SGE. Falha em qualquer etapa volta o edital a "pendente" com a etapa e o motivo.
3. **Tela de conferência.** Cabeçalho com contagens (disciplinas · assuntos · partes · exatas/mais amplas/parciais · sem nó ·
   pendências abertas · decididas por pessoa · aplicadas de decisão anterior). Duas visões: por disciplina e **fila de revisão**
   (só o que pede decisão, por gravidade).
4. O usuário resolve: aceitar (também "aceitar todos os exatos da disciplina"), trocar o nó (busca, navegação por ramos,
   sugestões), "a árvore não tem", dividir, juntar ou editar parte, descartar com motivo, comentar. Cada gesto vira decisão.
5. **Concluir.** Bloqueia só o que deixa o conteúdo do edital incerto: linhas fora de disciplina não confirmadas, trecho sem
   assunto, assunto recusado pela conferência, parte com alerta de divisão. Parte sem nó ou com vínculo "mais amplo" **avisa**
   (painel e plano), não bloqueia.
6. Ao concluir, vale o que J-5 já faz na confirmação: nascem os assuntos (com chave, D-64), o concurso vira alvo (D-60).
7. O material é preparado por casamento (§5) e acompanhado em **Concursos → Ver por assunto**, que ganha situação
   `completo`/`parcial`/`sem material`/`recusado`, tempo estimado e lacunas por parte.
8. **Depois de concluído**, tudo continua possível: vincular parte sem nó, trocar nó, acrescentar nós, trocar a fonte de uma
   parte. Trocar nó atualiza o atalho de questões na hora; se muda o material sugerido, **mostra o que entra e o que sai antes
   de aplicar**; segmento já criado ou estudado nunca sai — o novo entra como sugestão, o antigo fica "ligado ao nó anterior".

## 5. Material por casamento

Para cada assunto, o planejamento casa as partes (pelos nós) com os temas do catálogo de aulas, uma IA confirma "ensina /
ensina em parte / não serve", e a **seleção é regra** (sem IA): modo **completo** (padrão) leva todo candidato que serve;
**compacto** leva o conjunto mais curto que cobre. Cada assunto sai como um manifesto validado pelo fatiador (arquivo existe,
página existe, nenhuma página repetida, toda parte coberta ou lacuna declarada com motivo) e vira segmentos de ~60 min.

- Fixação (resumo, mapa mental) entra **depois** da teoria; questões e gabarito **não** entram nos segmentos.
- Sem limite de leitura por assunto: só **tempo estimado** (mediana medida: 4,3 a 5,9 h por assunto completo).
- **Segunda chance.** Parte que ficou sem candidato volta à busca por palavras da Sprint 19 (só teoria, sem páginas já usadas).
  Medido no MPPR: completos 101 → 127, parciais 16 → 7, sem material 21 → 4. Escolha da segunda chance é marcada como tal.
- Não inventa (D-65): parte sem tema que sirva é lacuna com motivo.
- O usuário pode **trocar a fonte** de uma parte; é decisão presa à chave, como as de nó.

## 6. Material de consulta: "Questões deste assunto"

Um PDF por assunto, com capa de rastreio (arquivo e páginas de origem) e as páginas de questões, comentários e gabarito das
aulas que ensinaram o assunto. **Não é segmento**: não tem escada, não conta para "leitura completa" (D-54), não entra no turno.
Aparece no detalhe do assunto como "Questões deste assunto (consulta)" ao lado do atalho para as questões do QConcursos.
O SGE só serve o arquivo e mostra o tamanho em páginas; o conteúdo é montado pelo planejamento.

## 7. Regras propostas (numeração a definir na migração; a última vigente é D-85)

| Rascunho | Regra |
|---|---|
| R-1 | A IA propõe, a regra confere, a pessoa resolve. **Nenhuma decisão do usuário é sobrescrita por reprocessamento**; se a IA nova discorda, é informação, não pendência |
| R-2 | A decisão vale antes da IA e do refino, em qualquer edital, pela chave parte + contexto. Escopo "todos os editais" exige confirmação extra com a contagem de partes de outros editais que mudariam |
| R-3 | O texto de um assunto é **literal do edital**; a parte usa só palavras do assunto. O que a regra recusa aparece na revisão, nunca é corrigido em silêncio |
| R-4 | A `chave` da parte e do assunto é hash do texto normalizado; reprocessar mantém a chave do que não mudou. Chave que sumiu fica na fila "não aparece mais no reprocessamento", **nunca é removida em silêncio** |
| R-5 | A entrega da revisão é **idempotente**; formato desconhecido (`edital-revisao/N`) é recusado |
| R-6 | O nó é guardado por id, com a versão da árvore. Nó que sumiu ou mudou na atualização da árvore devolve a parte à fila |
| R-7 | Concluir bloqueia só conteúdo incerto; parte sem nó ou "mais amplo" avisa no painel e no plano até ser resolvida |
| R-8 | Trocar nó ou fonte nunca apaga segmento nem toca escada, sessões, revisões ou erros (D-18); o novo material entra como sugestão |
| R-9 | Material de consulta não é leitura obrigatória e não conta para a leitura completa do assunto |
| R-10 | Cada execução guarda modelo, versão do prompt e data; trocar o modelo reprocessa só o que não tem decisão humana |

## 8. Dados (resumo, para o documento técnico da sprint)

`edital_revisao` (estado, execução, árvore) · `parte` (chave, texto, contexto, herdado, ordem, assunto) · `ligacao` (parte, nós,
natureza, origem, execução) · `decisao` (chave, nós ou fonte, escopo, edital de origem, versão da árvore, data, ativa) ·
`evento_de_revisao` (histórico de cada gesto) · `execucao_ia` (modelo, prompt, data) · `material_assunto` (situação, minutos,
lacunas, consulta). Operações do worker em `/internal/**`: editais pendentes, dados do edital com as decisões, entregar,
falha, árvore do QConcursos. Nada disso é decidido aqui; é o que o documento da sprint terá de resolver.

## 9. Pontos para o usuário decidir

| # | Pergunta | Recomendação |
|---|---|---|
| A | A análise nova **substitui** a do ADR-043 (Java, OpenRouter) ou **convive** com ela? Substituir exige guardar o PDF e perder o caminho 100% dentro do SGE; conviver mantém duas leituras de edital | Substituir para cargo e assuntos; manter só a leitura de cabeçalho (nome, banca, data, cargos), que já funciona e é barata |
| B | **Quando nascem os assuntos**: na conclusão da revisão (R-7) ou antes, com o edital ainda em revisão? Hoje nascem na confirmação (D-62) | Na conclusão: a revisão pode mudar o texto e a divisão, e criar assunto antes obrigaria a migrar |
| C | A **árvore do QConcursos** vive no SGE (busca e navegação da tela) ou o worker serve a busca? | No SGE, carregada do arquivo `taxonomia/qconcursos-AAAA-MM-DD.json` que o planejamento entrega; o diff entre versões gera a fila "nó mudou" |
| D | O **material de consulta** é entidade própria ou arquivo servido junto do assunto? | Arquivo servido junto do assunto, sem entidade; guarda só caminho e páginas |
| E | Esta funcionalidade ocupa **uma sprint** ou duas? Proposta: 25 = recebimento, árvore, ligação e tela de revisão; 26 = decisões com escopo, material por casamento no worker e consulta | Duas, nessa ordem, porque 26 depende de 25 funcionar com dado real |
| F | Reprocessar um edital com decisões já tomadas: a tela mostra o diff entre execuções ou só as pendências novas? | Só as pendências novas, mais um contador "mudou na IA, decisão preservada" |

## 10. Fora do escopo

Leitura de PDF em imagem do edital (OCR do edital; a prova já tem, Sprint 24), árvore do TEC, uso das ligações para filtrar
questões **dentro** do SGE (hoje só o atalho para o QConcursos), avaliação objetiva com perguntas da IA e o SGE como
plataforma completa (visão de futuro, não desta funcionalidade).

## 11. Dados medidos que sustentam o desenho

| Medida | Resultado |
|---|---|
| Quebra do assunto em partes, 9 editais, conferência manual | 65 de 66 partes certas |
| Ligação das partes aos nós | ~95% (3 IAs + usuário) |
| Confirmação do material, 60 partes, outra IA às cegas | 84% de veredito igual; 92% quanto a servir ou não |
| MPPR cargo 402: 138 assuntos, 300 partes, 5 sem nó | antes da segunda chance 101 completos / 16 parciais / 21 sem material; depois 127 / 7 / 4; 0 recusados |
| Cobertura por assunto | ALESC 70% completos; SEFAZ-SP TIC 43% |
| Defeito do catálogo | 31 temas (28 fichas) com página além do PDF: ignorados e avisados; reler não resolve |
| Quebra varia entre rodadas | 199 vs 138 assuntos no mesmo edital: por isso a revisão existe e a decisão presa à chave |

## Changelog

- **0.1.0 (2026-10-24)** — primeiro rascunho, a partir dos pedidos §17 e §18 e das especificações do planejamento.
