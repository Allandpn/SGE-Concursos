# Redesenho do fluxo do app — jornada, atritos e proposta

| | |
|---|---|
| Versão | 0.7.0 (rascunho; passos 0, A, B, C e D feitos; E medido e não recomendado) |
| Data | 2026-10-27 |
| Situação | Não é especificação. Aprovado, vira mudança em `02_JORNADAS` (J-5, J-2 e §4) pelo processo de `especificacao/` |
| Base | `02_JORNADAS` v1.16.0, as telas de `static/index.html`, o ensaio real do MPPR (2026-10-24 a 26) e a análise do usuário de 2026-10-27 |

Marcação: **[visto]** = aconteceu no uso real ou está no código; **[hipótese]** = a confirmar.

## 1. A jornada de hoje

```
Hoje ──► (card do concurso) ──► Concursos ──► Edital ──► Conferência ──► Concluir ──► Hoje
  │      subir PDF · esperar leitura · escolher cargo · esperar ligação · decidir N itens
  └──► Recuperar / Registrar / Assuntos / Erros / Progresso / Ajustes
                     (barra de cima: só Assuntos · Progresso · Ajustes)
```

O fluxo é **linear**: o material só começa depois de **Concluir** (D-62, D-92: é a conclusão que cria os assuntos), e a conclusão espera a pessoa decidir tudo. **Medido no MPPR (2026-10-26):** a preparação levou **58 minutos** (concluir às 17:47, material entregue às 18:45); a espera pela pessoa decidir os últimos itens é que levou horas (aceites automáticos às 07:08, últimos itens às 17:40).

## 2. Onde atrapalha

| # | Atrito | Evidência |
|---|---|---|
| 1 | **Decisão demais na conferência.** 315 partes; só 14 pediam a pessoa de verdade | [visto] |
| 2 | **Vocabulário interno:** parte, nó, natureza do vínculo, mais amplo, parcial, exata | [visto] |
| 3 | **Beco sem saída:** Aceitar desabilitado sem explicar (corrigido; falta implantar) | [visto] |
| 4 | **Esperas sem rosto:** leitura (minutos) e material (cerca de uma hora no MPPR) sem etapa visível | [visto] |
| 5 | **Conferência escondida:** 4 toques de Hoje; Concursos fora da barra | [visto] |
| 6 | **Lacuna sem ação:** leis do MPPR, Node.js ficam "sem material" sem o que fazer | [visto] |
| 7 | **Dois endereços** (443 e 8443); "Sem conexão com o servidor" não diz qual falhou nem o que tentar | [visto] |
| 8 | **Ninguém avisa que o material chegou.** Quem espera uma hora ou mais não fica olhando Hoje | [hipótese] |
| 9 | **Avisos de IA** repetidos em Hoje, Concursos e Ajustes, mesmo sem nada a fazer na hora; contradiz "Hoje é a porta" | [visto no código] |
| 10 | **Revisão trava o material.** O que já é seguro espera as exceções para começar a ser preparado | [visto] |
| 11 | **Aceite automático sem rastro:** a pessoa não vê o que foi aceito sozinho nem desfaz item a item | proposta nova |
| 12 | **Edital retificado:** não há caminho para uma versão nova do PDF (o "Reprocessar" relê o mesmo arquivo) | [visto no código] |

## 3. Princípios

1. **Exceção, não lista.** O que a regra e a IA resolvem com segurança entra aceito; a pessoa vê só o que pede o olho dela. Decisão da pessoa vale antes de tudo (D-86/D-87).
2. **Confiança tem rastro.** Todo aceite automático fica visível, com o motivo em uma linha, e se desfaz item a item, antes e depois do Concluir.
3. **Uma pergunta por vez, em português do usuário**, e as parecidas vêm agrupadas.
4. **Todo estado tem rosto**: o que está acontecendo, quanto falta (contagem, não relógio), o que fazer. Espera longa avisa por notificação.
5. **Todo beco tem saída**: nada desabilitado sem explicação; toda lacuna tem ao menos uma ação.
6. **Hoje é a porta**: aparece ali o que pede a pessoa, em uma linha; o que não bloqueia o dia fica em Ajustes.
7. **Nada bloqueia sem necessidade**: "decidir depois" existe em toda pergunta.

## 4. Regra do que entra aceito

| Caso | Tratamento |
|---|---|
| Exata | aceita sozinha, com rastro |
| Mais ampla, nó **pequeno** (poucos filhos) | aceita sozinha, com rastro |
| Mais ampla, nó **largo** (ex.: Morfologia) | **exceção**: um nó largo infla o escopo sem a pessoa perceber |
| Parcial | **sempre exceção** |
| Sem nó | **exceção** |
| Alerta ou erro da regra (linha fora de disciplina, divisão) | **exceção**, e bloqueia o Concluir como hoje |

**"Nó pequeno" = até 3 subitens (`NO_LARGO_ACIMA_DE`), medido no MPPR:** dos 156 vínculos mais amplos, 147 são folha ou têm até 3 subitens e só 9 são nós largos (4 a 10 ou mais). Com a regra, a fila do MPPR cairia de cerca de 190 itens para **45** (2 sem nó, 9 largos, 34 parciais), e 270 entram aceitos com rastro. Nó que sumiu da árvore conta como largo.

## 5. Fluxo proposto do edital novo

```
1. Subir o PDF               (Concursos ► Novo edital)
2. Esperar                   faixa: lendo · ligando à árvore · pronto para revisar
3. Revisão rápida            só as exceções, agrupadas por tipo ("estes 8 são nó largo em Direito Administrativo: aceitar todos?"),
                             com "decidir depois"; aba "Aceitos sozinhos" com o motivo e o botão Desfazer
4. Preparo em paralelo       os assuntos já seguros entram na fila do material logo após a leitura; as exceções esperam a pessoa
5. Concluir                  quando as exceções acabarem (ou forem adiadas): "203 assuntos entram no seu estudo"
6. Material                  contagem em Hoje ("120 de 203 prontos") + notificação ao terminar
7. Lacunas                   cada uma com ação: anexar PDF · estudar por fora · ignorar
8. Retificação do edital     (ver §7)
```

**Dependência do passo 4 (grande):** hoje os assuntos só nascem na conclusão e o worker só pega conferência concluída. Entregar aos poucos exige **concluir por partes**: criar os assuntos aceitos antes do Concluir final e deixar o worker preparar assunto a assunto. Antes de prometer, medir o worker com itens chegando aos poucos e rever D-62/D-92 em `01_DOMINIO`. Se não valer, o passo 4 fica fora e o resto do fluxo continua de pé.

## 6. Ordem de trabalho

| Passo | O quê | Back-end? |
|---|---|---|
| **0** | Implantar a correção do Aceitar desabilitado | **feito** em 2026-10-27 (app reconstruído no Pi) |
| **A** | **Feito, falta implantar** (branch `redesenho-fluxo`). Revisão rápida: regra de §4, exceções agrupadas, "decidir depois", aba "Aceitos sozinhos" com Desfazer, **vocabulário em português do usuário** (inclusive no "abrir o resto") e Concursos na barra | pouco: o rastro e o desfazer usam as decisões que já existem; "aceito sozinho" precisa de uma marca |
| **B** | **Feito, falta implantar** (branch `redesenho-fluxo-b`). Faixa do material em Hoje ("Preparando o material: 120 de 203 assuntos", e "pronto" com "Entendi" guardado só no aparelho); avisos de IA em Hoje e em Concursos só enquanto o material espera por eles, em Ajustes sempre; lacuna com "vou estudar por fora", "ignorar" e "desfazer" (V19, `PUT /api/assuntos/{id}/lacunas`; a entrega nova do planejamento não apaga o destino); aviso de conexão que diz qual endereço falhou e sugere o outro no tailnet; os avisos do concurso passaram a usar a mesma regra de nó pequeno | pouco: uma tabela e uma rota |
| **C** | **Feito, falta implantar e testar no celular** (branch `redesenho-fluxo-c`; `docs/SPRINT-27-AVISOS-DE-PROCESSO.md`, ADR-048, D-96). Web Push próprio, sem biblioteca; dois avisos (edital lido, material pronto), opcionais por aparelho em Ajustes | sim: V20, rotas `/api/push`, marcas por rodada |
| **D** | **Feito, falta implantar** (branch `redesenho-fluxo-d`; `docs/SPRINT-28-PDF-ANEXADO-A-LACUNA.md`, ADR-049, D-97). Lacuna com "Anexar PDF": o SGE guarda, o worker corta em segmentos e importa, a tela mostra o estado | sim: V21, rotas, ciclo no worker |
| **E** | **Medido: não recomendado.** Preparo em paralelo à revisão pouparia no máximo os ~58 min de preparo depois do último "Concluir"; a espera longa do MPPR foi a pessoa (10 h entre os aceites automáticos e os últimos itens), e com o passo A restam 45 itens em vez de 190. O custo seria alto: concluir por partes (rever D-62/D-92) e um worker que hoje joga fora o trabalho quando o lote muda (`MaterialDoEdital.processar` apaga a pasta se a lista de assuntos muda; as etapas se identificam por posição). Reabrir só se um edital grande mostrar o preparo passando de várias horas | sim, e grande |
| **F** | Um só endereço (443 do Guardian) | fora do app |

## 7. Edital retificado (caso a registrar)

Edital de concurso costuma ser retificado. Hoje não há caminho: "Reprocessar" relê o mesmo PDF. **Decisão proposta para esta fase: por enquanto, sobe-se como um edital novo do mesmo concurso e as decisões da pessoa se aplicam pela chave estável** (a parte igual reaproveita a decisão, D-86/D-89). O que muda de verdade entra na fila como pendência nova. Fica anotado como lacuna conhecida até haver um caso real.

## 8. Respostas do usuário às perguntas da v0.1.0

1. Em frequência, Hoje pesa mais; mesmo assim, começar por A (só tela, dor aguda no MPPR).
2. Exata aceita sozinha; mais amplo conforme §4; parcial sempre exceção.
3. Lacuna: "anexar PDF" tende a ser usado nas leis (texto público); "estudar por fora" basta para Node.js.

## 9. O que muda em `02_JORNADAS` quando aprovado

- J-5, passo 5 a 8: revisão rápida, aceitos sozinhos com rastro, preparo em paralelo (se o passo E valer), lacunas com ação.
- J-2/§4.4: Hoje com a faixa do material e o aviso de pronto.
- §4: Concursos entra na barra.
- Novo item em §7 (o que fica em aberto): retificação do edital.
- `01_DOMINIO`: regra de §4 (o que entra aceito sozinho) e, se o passo E valer, a revisão de D-62/D-92.
