# Verificação por prova e gabarito — especificação conceitual (rascunho)

| | |
|---|---|
| Versão | 0.1.0 |
| Data | 2026-10-17 |
| Status | **Rascunho — não vigente.** Nada daqui é implementado até os requisitos (§9) serem decididos e as seções migrarem para os documentos donos (§11) |
| Origem | Pedido do usuário, 2026-10-17: "subir uma prova e saber se, com o material montado, seria possível responder as questões e gabaritar o concurso. É o nosso objetivo final" |
| Decisões já tomadas | Mora no planejamento; o SGE não guarda enunciado nem gabarito (`00_PRODUTO` §5.1 intacto); a IA julga questão a questão; entrada pelo mesmo gesto do edital, com **carregar prova** e **carregar gabarito** separados |

---

## 1. Para que serve

Hoje o sistema sabe **quanto** material existe por assunto (91 de 97 prontos, 15 896 min)
e **o que não existe** (6 lacunas, com motivo). Não sabe se o que existe **ensina o
suficiente**. O assunto "Relação do texto com seu contexto histórico" está "pronto" com
2 páginas soltas; o validador o aceitou porque a faixa é válida, não porque ensina.

A verificação por prova troca a pergunta. Em vez de "tem PDF sobre o tema?", pergunta
**"com este material, eu responderia esta questão?"** — usando questões reais da banca,
que são a medida mais honesta do que a prova vai cobrar.

**Como saberemos que funcionou.** Dois testes, antes de confiar no número:

1. **Ensaio com prova conhecida:** uma prova anterior da FCC (a banca do SEFAZ SC),
   analisada contra o material atual. O resultado precisa apontar como furos os assuntos
   que já sabemos furados (Morfossintaxe, Flexão, SC) e não apontar os sólidos.
2. **Conferência de amostra:** o usuário confere ~20 vereditos à mão. Se o julgamento da IA
   discorda do usuário em mais de 1 a cada 5, o critério (§5) é ajustado antes de a
   funcionalidade valer.

---

## 2. O que é e o que não é

**É:** um relatório de **cobertura do material sobre uma prova**, por questão, por assunto
e por disciplina, apontando onde o material do concurso-alvo não sustenta a resposta.

**Não é:**

- **Um banco de questões.** O SGE não hospeda enunciado, alternativa nem gabarito
  (`00_PRODUTO` §5.1). Guarda só o **resultado** por questão (§7).
- **Um corretor.** Não diz que o usuário acertaria; diz que o material **permite** acertar.
  O usuário continua resolvendo questões na plataforma externa (`00_PRODUTO` §5.1).
- **Uma garantia de aprovação.** "Gabaritar" é o horizonte, e o relatório mede a distância
  do material até ele, com a incerteza de um julgamento de IA (§5.3).
- **Um gerador de material.** Achou furo, o caminho é o que já existe: refazer o material
  do assunto (D-66) ou indexar apostila nova. Nada é inventado (D-65).

---

## 3. Glossário

| Termo | Significa |
|---|---|
| **Prova** | O PDF de uma prova de concurso (de qualquer ano, qualquer banca) |
| **Gabarito** | O PDF (ou texto) com a resposta oficial de cada questão; pode chegar depois da prova |
| **Questão avaliável** | Questão com enunciado em texto, que a IA consegue ler e julgar |
| **Atribuição** | O assunto do concurso-alvo ao qual a questão pertence |
| **Sustentação** | A página (ou faixa) do material do assunto que ensina o necessário para a questão |
| **Veredito** | `RESPONDIVEL` · `PARCIAL` · `NAO_RESPONDIVEL` (§5.2) |
| **Furo** | Assunto com questões `PARCIAL` ou `NAO_RESPONDIVEL`, ou questão sem assunto onde atribuir |
| **Relatório** | O retrato datado do resultado de uma análise de prova |

---

## 4. A jornada (JV-1)

Rara — algumas vezes por concurso —, como a J-5: o orçamento é de **atenção**, não de
segundos. O usuário só carrega o PDF e confere o resultado; o resto é do sistema.

1. Em **Concursos**, no concurso-alvo, toca **"Verificar com uma prova"**.
2. Escolhe **"Carregar prova"** (PDF) e, se tiver, **"Carregar gabarito"**. O gabarito pode
   ser enviado **na mesma hora ou depois**; sem ele a análise roda mesmo assim (§5.4).
3. O sistema lê a prova e mostra o que achou **antes de gastar IA no julgamento**: nome da
   prova (corrigível), banca, ano, **quantas questões leu**, quantas não consegue avaliar
   (imagem, tabela) e, se há gabarito, quantas respostas casaram com questões. É o ponto
   de conferência, como a proposta do edital (D-62): erro de leitura aqui custa um toque.
4. Confirma. A análise roda em segundo plano — **a tela pode ser fechada**; ela continua e
   fica guardada, como a análise de edital. Acompanha em Concursos: "Prova X: 34 de 60
   questões julgadas".
5. **Relatório** (§6): o número que importa no topo, os assuntos furados, e cada questão.
6. Do relatório, o usuário vai direto ao assunto furado: ver o motivo, **refazer o
   material** ou aceitar. Quando o material mudar (D-66), **"Analisar de novo"** repete só
   o julgamento, sem reler a prova.

Falhas que o usuário vê, e o que fazem:

| Falha | O que acontece |
|---|---|
| PDF é imagem (sem texto) | Recusa na hora: "envie a versão com texto" — como o edital |
| Leu poucas questões (<50% do esperado) | Mostra e avisa; o usuário decide seguir ou trocar o PDF |
| IA indisponível / cota da assinatura esgotada | A análise **pausa** e retoma sozinha (ADR-045). Nunca vira veredito |
| Gabarito não casa com a prova (nº de questões diferente) | Recusa o gabarito, mantém a prova; diz a diferença |

---

## 5. O julgamento

### 5.1 Três etapas, cada uma com o que a IA faz e o que o sistema decide

| Etapa | A IA | O sistema |
|---|---|---|
| **Leitura** | Separa as questões do PDF: número, enunciado, alternativas | Valida: números sequenciais, sem repetição, alternativas presentes. O que não passa é "não avaliável", declarado |
| **Atribuição** | Escolhe, dentre os assuntos do concurso-alvo, a qual pertence cada questão; ou "nenhum" | Só aceita assunto que existe na cobertura (como D-63). "Nenhum" vira furo "sem assunto no edital" |
| **Julgamento** | Lê a questão e o texto dos segmentos do assunto; diz o veredito e **cita a página** | Só aceita citação que existe no segmento. Sem página válida, o veredito cai para `NAO_RESPONDIVEL` |

### 5.2 O veredito

| Veredito | Quer dizer | Exige |
|---|---|---|
| `RESPONDIVEL` | O material do assunto ensina o que a questão pede, de modo suficiente para decidir a alternativa | Página de sustentação válida |
| `PARCIAL` | Ensina parte; falta um elemento que a questão exige (exceção, valor, conceito vizinho) | Página do que há **e** nota do que falta |
| `NAO_RESPONDIVEL` | O material do assunto não traz o necessário | Nota do que falta |

Regra de ouro, como D-65: **na dúvida, o veredito é o pior dos dois.** Prefere-se acusar
furo falso (custo: o usuário confere) a esconder furo real (custo: a prova).

### 5.3 O limite honesto

O veredito é **um julgamento de IA sobre texto**, não uma medição. Duas consequências
vinculantes:

- O relatório mostra sempre o **denominador** (princípio 9 do `00_PRODUTO`): "42 de 58
  questões avaliáveis", nunca só "72%".
- Cada questão no relatório mostra a **sustentação** (página). O usuário pode abrir o PDF
  e discordar com um toque; o número nunca é uma caixa-preta.

### 5.4 Com e sem gabarito

| | Sem gabarito | Com gabarito |
|---|---|---|
| Pergunta | O material cobre o assunto da questão? | O material **leva à alternativa correta**? |
| Veredito | `RESPONDIVEL` / `PARCIAL` / `NAO_RESPONDIVEL` | Os mesmos, mais `leva_a_resposta`: `SIM` / `NAO` / `INDETERMINADO` |
| Custo | Uma chamada por questão | A mesma chamada (a resposta oficial entra no pedido) |

O gabarito **refina**, não substitui: uma questão `RESPONDIVEL` cujo material leva à
alternativa errada é o furo mais perigoso (o material **ensina errado ou incompleto**), e só
aparece com gabarito. Questão **anulada** ou sem gabarito sai do denominador, declarada.

---

## 6. O relatório

De cima para baixo, o que o usuário precisa para decidir:

1. **O número:** "Material sustenta **42 de 58** questões avaliáveis · 9 parciais · 7 não",
   da prova X, analisada em 17/10/2026 contra o material daquela data.
2. **Por disciplina:** a mesma contagem, na ordem do edital.
3. **Assuntos furados:** os assuntos com `PARCIAL`/`NAO_RESPONDIVEL`, do mais furado ao
   menos, cada um com quantas questões da prova caem nele — e o que faltou. Mais os
   **assuntos sem nenhuma questão** na prova (informação, não furo).
4. **Questões sem assunto no edital:** a prova cobra algo que o edital não lista.
5. **Cada questão:** número, assunto atribuído, veredito, página de sustentação, e (com
   gabarito) `leva_a_resposta`. Sem enunciado (§7).

O que **não** faz nesta fase: reordenar o turno, criar revisão ou mudar peso por causa do
relatório (§10, pergunta 7).

---

## 7. O que o SGE guarda e o que não guarda

Conceitual; coluna e índice são do documento de banco da sprint, depois.

| Guarda | Não guarda |
|---|---|
| A **análise de prova**: concurso, nome, banca, ano, estado, quando | Enunciado, alternativas, texto da prova |
| Por questão: número, assunto atribuído, veredito, página de sustentação, nota do que falta, `leva_a_resposta` | O gabarito oficial (guarda só se casou) |
| Retratos antigos: reanalisar gera um **relatório novo**, o anterior fica (D-18) | Qualquer coisa que reconstitua a prova |

O veredito é **fato registrado**, não derivado (D-16 vale para o que se calcula dos
segmentos; aqui há um julgamento externo, como a lacuna de D-65). O percentual e as
contagens **são derivados** do veredito, nunca persistidos.

**Onde o enunciado vive durante o trabalho:** no diretório de trabalho do planejamento, que
é do worker. O SGE nunca o recebe. Quando a análise termina, o planejamento descarta o
texto (pergunta 3).

---

## 8. Regras propostas

Numeração provisória `P-xx`, para não colidir com as D-xx; ao migrar para o
`01_DOMINIO` §10 viram D-67 em diante. Cada uma diz como se garante (§4 do
`03_INVARIANTES`).

| Id | Regra | Natureza provável |
|---|---|---|
| P-01 | A IA lê e julga; o **sistema** valida, conta e calcula. Contagem e percentual saem de regra fixa, nunca da IA (como D-61) | Política no serviço |
| P-02 | **Nada é inventado:** veredito `RESPONDIVEL` exige página de sustentação que exista no segmento do assunto; citação inexistente rebaixa para `NAO_RESPONDIVEL` | Validação no serviço + teste |
| P-03 | **Na dúvida, o pior veredito** | Instrução à IA + teste de calibragem |
| P-04 | Questão não avaliável (imagem, tabela, anulada) sai do denominador **e é declarada** no relatório | Serviço |
| P-05 | **Denominador sempre** exibido junto de qualquer percentual | Interface + teste |
| P-06 | O SGE **não persiste** enunciado, alternativa nem gabarito (`00_PRODUTO` §5.1) | Estrutura: o contrato da rota não tem o campo; teste estrutural |
| P-07 | **Nada é apagado:** reanalisar cria relatório novo; o antigo permanece (D-18) | Persistência (sem caminho de remoção) |
| P-08 | A atribuição só aceita assunto da **cobertura do concurso-alvo** (como D-63); "nenhum" é resposta válida e vira furo | Serviço |
| P-09 | **Indisponibilidade da IA não é veredito:** a análise pausa e retoma (ADR-045); nunca grava `NAO_RESPONDIVEL` por falha | Serviço + teste |
| P-10 | O relatório é um **retrato datado** contra o material vigente naquela data; mudar o material não altera relatórios antigos | Persistência |
| P-11 | A prova é sempre avaliada contra a cobertura do **concurso-alvo** | Serviço |

P-06 é a que mais custa se esquecida: é ela que impede esta funcionalidade de virar,
por acidente, o banco de questões que o produto decidiu não ser.

---

## 9. Requisitos não funcionais

| Requisito | Meta proposta | Por quê |
|---|---|---|
| **Custo de IA** | ≤ ~1 chamada por questão no julgamento + 1 por lote de ~10 na atribuição; prova de 60 questões ≈ 70 chamadas | A cota da assinatura é o recurso escasso (`memória do projeto`: IA só na preparação) |
| **Tempo** | Prova de 60 questões em até ~30 min, sem o usuário esperar | Roda em segundo plano, como o material |
| **Memória no Pi** | Dentro do teto do worker (768 MB, `03E_DEPLOYMENT` §8C) | O Pi 4B tem 4 GB para tudo |
| **Retomada** | Reiniciar o worker no meio retoma de onde parou, sem repetir questão julgada | O worker já se reinicia (deploy) |
| **Idempotência** | Reenviar a mesma prova (mesmo hash) não duplica a análise | Como D-45 |
| **Tamanho** | PDF de prova até o mesmo limite do edital | Consistência |

---

## 10. Perguntas em aberto — para definir os requisitos

Cada uma com a recomendação, para decidir rápido.

| # | Pergunta | Recomendação |
|---|---|---|
| 1 | A prova é avaliada só contra o **concurso-alvo**, ou também contra o de histórico? | Só o alvo (P-11). Simples, e é o que importa para a prova que vem |
| 2 | Quais **bancas/anos** servem de prova de referência? Qualquer? | Qualquer; o usuário escolhe. Ideal: provas anteriores da banca do alvo (FCC) |
| 3 | O texto da prova (enunciado) fica **descartado** após a análise, ou o planejamento o guarda para reanalisar sem recarregar o PDF? | Guardar no **planejamento** (não no SGE), para "Analisar de novo" sem reenviar; apagar por ação do usuário |
| 4 | **Várias provas** juntas? (ex.: 3 anos da FCC e um relatório consolidado) | Fora da primeira entrega; um relatório por prova. Consolidado é Sprint seguinte |
| 5 | O relatório mostra **o trecho do enunciado** ao usuário, ou só número + assunto + página? | Só número + assunto + página: o usuário tem a prova em PDF. Mostrar enunciado é o primeiro passo para hospedá-lo |
| 6 | A IA julga com o **texto completo** dos segmentos do assunto ou com trechos? Segmentos podem ter 200+ páginas | Trechos escolhidos por busca no índice, mais o sumário; texto completo estoura contexto e cota |
| 7 | O resultado **influencia o turno** (reforçar assunto furado, subir prioridade)? | Não nesta fase. Primeiro mostrar o relatório e ver se o número é confiável; ligar ao turno é decisão separada |
| 8 | **Meta de "gabaritar"**: definir um limiar (ex.: ≥ 90%) que o relatório compara? | Não definir; mostrar o número e a distância à nota de corte quando a Sprint 23 existir |
| 9 | Prova **discursiva** ou de estudo de caso? | Fora: só objetiva (múltipla escolha, certo/errado) |
| 10 | O **gabarito** tem formatos variados (PDF da banca, tabela, texto colado). Quais aceitar? | PDF com texto e **texto colado**. Imagem não |
| 11 | Quem gera a atribuição e o julgamento é a **mesma IA/modelo** da preparação do material? | Sim, mesma assinatura; mas o modelo pode ser escolhido em Ajustes |
| 12 | Número da Sprint e posição no mapa | Antes da 20 (Diagnóstico de edital)? Depende de quanto o usuário valoriza o veredito diante da prova de 22/11/2026 |

---

## 11. Para onde cada parte vai quando for aprovada

| Parte | Dono | Documento |
|---|---|---|
| Para que serve, métrica, não-objetivo | `00_PRODUTO` | §7 (métrica), §11 (decisão vinculante) |
| Glossário e regras P-01 a P-11 | `01_DOMINIO` | §3 (nova subseção), §10 (D-67+) |
| Jornada JV-1 e falhas | `02_JORNADAS` | nova jornada, ao lado da J-5 |
| Quem garante P-xx e como se testa | `03_INVARIANTES` | §3 e §9 |
| Por que no planejamento, sem guardar enunciado | `docs/00A_ADR.md` | ADR novo |
| Tabelas, endpoints, formato do enunciado no worker | documento da sprint | nasce quando a sprint começar |

Nada de coluna, endpoint ou formato de arquivo entra aqui (segunda regra do projeto: a
documentação técnica nasce com a sprint).

---

## Changelog

| Versão | Data | Mudança |
|---|---|---|
| 0.1.0 | 2026-10-17 | Rascunho inicial, a partir do pedido do usuário. Decisões já tomadas: planejamento, sem guardar enunciado, IA por questão, prova e gabarito carregados separadamente |
