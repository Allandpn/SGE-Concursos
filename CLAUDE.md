# Instruções para o Claude neste repositório

Sistema pessoal de gestão de estudos para concursos.
Java 21 · Spring Boot · PostgreSQL · Docker no Raspberry Pi · Tailscale.

---

## A regra que vale mais que todas as outras

**`especificacao/` é a fonte única de verdade. O código é consequência dela.**

- Se algo **não está** na especificação, **não implemente**.
- Se está, implemente **exatamente como está**.
- Achou que a especificação está errada? **Corrija o documento primeiro**,
  incremente a versão, registre no changelog — e só então mexa no código.
- Contradição entre documentos é **defeito de documentação**: reporte, não
  contorne.

---

## A segunda regra: a documentação técnica é escrita por sprint

A especificação **conceitual** está completa. A **técnica** não, e é
deliberado.

- Não escreva documento técnico de sprint futura.
- Não implemente nada de sprint futura.
- Documento técnico nasce quando a sprint começa, contra a especificação
  conceitual vigente naquele momento.

O motivo: a especificação conceitual foi revisada oito vezes e melhorou em
todas. Escrever agora as nove reescritas técnicas seria produzir retrabalho para
a próxima revisão.

---

## Onde o projeto está

**Leia `PROGRESSO.md` primeiro, sempre.** Ele tem o mapa das 8 sprints, a sprint
corrente detalhada item a item, e a *Definition of Done* dela.

Se você não sabe qual é o próximo passo, a resposta está lá — não a invente.
E ao concluir um item, **atualize a tabela do `PROGRESSO.md` na mesma sessão**.

---

## Os documentos

### Conceituais — completos, precedência nesta ordem

| Documento | Responde |
|---|---|
| `especificacao/00_PRODUTO.md` | Para que serve, e como saberemos que funcionou |
| `especificacao/01_DOMINIO.md` | O que existe e como se comporta. **As 46 regras D-xx** |
| `especificacao/02_JORNADAS.md` | Como é usado, e quanto pode custar em segundos |
| `especificacao/03_INVARIANTES.md` | Quem garante cada regra e como se testa |

### Técnicos — escritos sprint a sprint

O documento técnico de uma sprint nasce **quando ela começa**, contra a
especificação conceitual vigente naquele momento. O da Sprint 1 é
`docs/SPRINT-1-BANCO.md`.

Herdados da v2 e ainda válidos:

| Documento | Situação |
|---|---|
| `docs/00A_ADR.md` | 19 ADRs vigentes, **nenhum revogado**. Faltam ADR-031 a 033 |
| `docs/03C_LOGGING.md` | Intacto |
| `docs/03E_DEPLOYMENT.md` | Intacto |
| `docs/09_CODE_STYLE.md` | Intacto |

Tudo o mais é escrito quando a sprint que precisa dele começar.

---

## Cada tipo de artefato tem um dono

| Definir… | Dono |
|---|---|
| tese, métrica, não-objetivo | `00_PRODUTO.md` |
| entidade, regra de domínio, parâmetro conceitual | `01_DOMINIO.md` |
| jornada, tela, orçamento de tempo | `02_JORNADAS.md` |
| quem garante uma regra, e como se testa | `03_INVARIANTES.md` |
| decisão de arquitetura | `docs/00A_ADR.md` |
| coluna, restrição, índice | documento de banco da sprint |
| endpoint | documento de API da sprint |
| estilo de código | `docs/09_CODE_STYLE.md` |

Definir a mesma coisa em dois lugares é o único erro que esta documentação foi
desenhada para impedir.

---

## As regras D-xx

`01_DOMINIO.md` §10 tem as 46. `03_INVARIANTES.md` §3 diz de cada uma: que
natureza tem, em que camada mora, como se sabe que quebrou.

**Todo código que implementa uma regra a cita pelo identificador**, e o
identificador vai no nome da restrição correspondente. É assim que se apura se
alguma regra ficou órfã (`03_INVARIANTES` §9).

As que mais custam se forem esquecidas:

| Regra | O que é |
|---|---|
| **D-05** | No máximo **uma** revisão pendente por assunto. Unicidade na persistência, **nunca** verificação prévia no serviço |
| **D-45** | A mesma tentativa de registro conta uma vez. Identificador vem do cliente, gerado ao abrir a tela |
| **D-16** | A fase do assunto é **derivada**. Não existe coluna de fase, e o teste estrutural precisa provar isso |
| **D-07** | `SUCESSO` sobe · `PARCIAL` repete · `FALHA` regride **e volta para a fila de estudo** |
| **D-18** | Nada é apagado. Mas **sem filtro global de leitura** — quebraria D-42 |

---

## Os cinco princípios

De `03_INVARIANTES` §10. Se tudo o mais se perdesse, estes reconstituiriam o
projeto:

1. Toda informação derivável **não é persistida**.
2. Toda invariante mora na camada mais baixa que a comporte **sem distorção** —
   não é "quanto mais baixo, melhor".
3. Nenhuma política desce para a persistência.
4. Todo comportamento relevante tem teste, com **data congelada** quando
   depender de prazo.
5. Nenhuma interface é responsável por integridade.

---

## Modo de trabalho: mentoria

O dono do projeto está aprendendo Spring Boot em nível de produção, com 3 a 5
horas por semana. Já construiu uma API REST na faculdade, faz o curso do Nélio
Alves e trabalha diariamente com a API do consignado da Dataprev.

**Fundamentos não são o gargalo.** Não explique `@Service` nem injeção de
dependência a menos que ele pergunte. Ritmo rápido; ele sinaliza quando
precisar desacelerar.

- **Não escreva o código dele.** Mostre o molde só onde a mentoria disser, e
  exija que ele reescreva de memória depois.
- **Para cada decisão, cubra três coisas:** o que o tutorial faria, o que este
  projeto faz e por quê, e o que quebra em produção com a alternativa.
- **Nenhum item se dá por concluído sem ele explicar o porquê.** O modo de falha
  dele não é travar — é seguir adiante com meia compreensão.
- Pergunta de verdade: *"o que quebra se a gente remover isto?"*

---

## Como o projeto chegou aqui

A especificação anterior partia de um modelo **sem resultado de recuperação**, e
nove dos vinte documentos técnicos dela não sobreviveram. O código foi
descartado — nada estava em produção, então recriar o schema já correto saiu
mais limpo que corrigi-lo por migração.

Isso explica por que a especificação conceitual é densa e a técnica é magra: a
primeira foi revisada oito vezes; a segunda nasce por sprint, de propósito.

**Em que ponto estamos: `PROGRESSO.md`.**

---

## Três coisas que parecem erro e não são

Herdadas da tentativa anterior, e continuam valendo:

1. **`BIND_ADDRESS: 0.0.0.0` no compose** com `server.address: 127.0.0.1` no
   `application.yml`. Dentro do contêiner o bind precisa ser `0.0.0.0`; o
   confinamento vem do `127.0.0.1:8080:8080` do host. Não "corrija" nenhum.
2. **`unaccent_imutavel`**, wrapper de função que já existe. `unaccent()` é
   `STABLE`; índice por expressão exige `IMMUTABLE`.
3. **Lacunas na numeração de ids.** A sequência avança mesmo em inserção
   recusada. Nada depende de ids contíguos.

---

## Ao terminar qualquer tarefa

- [ ] a regra implementada está **citada** no código pelo identificador;
- [ ] teste correspondente escrito e passando;
- [ ] nada de sprint futura implementado;
- [ ] item marcado no `PROGRESSO.md`, se concluiu algum;
- [ ] documentação atualizada **se** algo divergiu — documento primeiro.
