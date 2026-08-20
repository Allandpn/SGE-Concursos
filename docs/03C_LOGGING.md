# 03C — LOGGING & AUDIT

Log em arquivo e trilha de auditoria em tabela.

| Campo | Valor |
|---|---|
| Versão do documento | **2.0.0** |
| Status | **Congelado** |
| Data | 2026-08-15 |

---

## 0. Duas coisas diferentes, dois lugares

A v1 misturava log e auditoria numa aba só, porque só havia planilha. A v2
separa, e a separação é o ponto central deste documento:

| | **Log** | **Auditoria** |
|---|---|---|
| Pergunta que responde | "o que quebrou?" | "o que mudou nos dados?" |
| Destino | arquivo, via Logback | tabela `auditoria` |
| Formato | linha de texto | linha com `jsonb` |
| Consulta | `grep`, `tail` | SQL |
| Retenção | 7 arquivos rotacionados | 12 meses |
| Se falhar | perde-se uma linha | **derruba a operação** |

A última linha é a diferença de natureza. Log é diagnóstico: perder uma linha é
ruim, não é grave. Auditoria é **dado de domínio**: uma escrita sem trilha é
histórico incompleto, e por isso a gravação participa da transação (§4.2).

---

## 1. Log

### 1.1 Níveis

Os padrão do SLF4J. Não inventamos nenhum — o `AUDIT` da v1 virou tabela.

| Nível | Quando usar | Exemplos |
|---|---|---|
| `ERROR` | Falha que impediu a operação | exceção não tratada; falha de conexão com o banco |
| `WARN` | Anomalia contornada; o sistema seguiu | parâmetro ausente ou inválido; soma de pesos ≠ 1; `nivel-maximo` incoerente; **constraint violada em uso normal** |
| `INFO` | Marcos de fluxo | subida da aplicação; escada suprimida por INV-007; escada encerrada no nível máximo |
| `DEBUG` | Detalhe de desenvolvimento | SQL gerado; tempo por etapa |
| `TRACE` | Não usado | — |

Nível controlado por `LOG_LEVEL` (`03A_CONFIGURATION.md` §7.2). Default `INFO`.

> **`WARN` de constraint violada é sinal de bug.** O Service deveria ter validado
> antes e devolvido mensagem específica. Se a constraint pegou, o caminho normal
> falhou — e este `WARN` existe para você descobrir.

### 1.2 O que nunca é logado

| Nunca | Motivo |
|---|---|
| Conteúdo de `observacoes`, `descricao` de erro, `concursos` | Texto pessoal. Log é diagnóstico de operação, não cópia dos dados |
| Corpo completo de requisição | Contém os campos acima. Logue **ids** |
| Senha do banco, variáveis de ambiente | Óbvio, e mesmo assim vaza em `logger.info("config: {}", env)` |
| Stack trace em `WARN` ou `INFO` | Ruído. Stack é de `ERROR` |
| Qualquer coisa dentro de laço sobre entidades | 200 assuntos viram 200 linhas. Logue o agregado |
| SQL em produção | `show-sql` fica desligado; para diagnóstico, suba `LOG_LEVEL` temporariamente |

### 1.3 Formato

Logback com padrão fixo, incluindo o `requestId` vindo do MDC:

```
2026-08-15 17:32:07.412  INFO [k2m9x4qp] SessaoService     : sessao criada id=901 assuntoId=42
2026-08-15 17:32:11.887  WARN [p8w1n6rt] ParametroService  : parametro ausente, usando default chave=revisao.nivel-maximo default=6
2026-08-15 17:33:02.104 ERROR [a7f3k91z] GlobalExceptionHan: falha inesperada em POST /api/sessoes
java.lang.NullPointerException: …
```

```xml
<pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level [%X{requestId}] %-18logger{0} : %msg%n</pattern>
```

Regras de mensagem:

- pares `chave=valor`, separados por espaço, sem aspas;
- apenas ids, números, enums e datas — **nunca** texto livre do usuário;
- no máximo 6 pares;
- **parâmetros do SLF4J, sempre**: `log.info("sessao criada id={}", id)`, nunca
  concatenação. Com concatenação, a string é montada mesmo quando o nível está
  desligado.

### 1.4 Rotação

```yaml
logging:
  file:
    name: /var/log/estudos/aplicacao.log
  logback:
    rollingpolicy:
      max-file-size: 10MB
      max-history: 7
      total-size-cap: 100MB
```

Sete arquivos de 10 MB, teto de 100 MB. No Pi, disco cheio por log é uma forma
banal e evitável de derrubar o banco junto — daí o `total-size-cap`.

---

## 2. `requestId`

Gerado por `RequestIdFilter`, um `OncePerRequestFilter` com a ordem mais alta.

```java
String id = UUID.randomUUID().toString().substring(0, 8);
MDC.put("requestId", id);
response.setHeader("X-Request-Id", id);
try { chain.doFilter(request, response); }
finally { MDC.clear(); }          // obrigatório
```

- Presente em **todo** log da requisição, automaticamente — não é passado por
  parâmetro para lugar nenhum.
- No header `X-Request-Id` de **toda** resposta, inclusive as de sucesso.
- No campo `requestId` de todo `ProblemDetail` (ADR-026).
- Na coluna `auditoria.request_id`.

> **`MDC.clear()` no `finally` não é opcional.** O MDC é `ThreadLocal`, e o Tomcat
> reutiliza threads. Sem a limpeza, o id de uma requisição aparece nos logs da
> seguinte — e você passa uma tarde investigando um erro que nunca existiu.

Operações fora de requisição HTTP (migração, rotina de manutenção) usam o
`requestId` fixo `sistema`.

---

## 3. Como o `requestId` fecha o ciclo

1. Ocorre um erro. A interface exibe a mensagem e, em erros `500`, o
   `requestId` em texto pequeno (`04_FRONTEND.md` §10).
2. `grep k2m9x4qp /var/log/estudos/aplicacao.log` mostra **toda** a requisição,
   em ordem, incluindo o stack.
3. `SELECT * FROM auditoria WHERE request_id = 'k2m9x4qp'` mostra o que chegou a
   ser gravado antes da falha — que, com transação, deve ser **nada**.

O passo 3 é o teste mais direto de que o *rollback* funcionou.

---

## 4. Auditoria

### 4.1 O que gera trilha

**Toda escrita bem-sucedida em dado de domínio**, sem exceção. As 17 operações e
os campos obrigatórios de `detalhe` estão em `02A_ENUMS.md` §6.

**Não** geram: leituras, alteração de parâmetro por `psql`, rotina de retenção.

### 4.2 Contrato

```java
@Transactional(propagation = Propagation.MANDATORY)
public void registrar(OperacaoAuditoria operacao, Long entidadeId,
                      Map<String, Object> detalhe);
```

`MANDATORY` é a decisão central. Consequências:

- auditoria **sempre** participa da transação da operação auditada;
- se a operação sofrer *rollback*, a linha de auditoria some junto — que é o
  correto: não houve mudança a auditar;
- chamar `registrar` fora de transação lança na hora, em vez de gravar algo que
  não aconteceu.

**Diferença deliberada em relação ao log:** log falha em silêncio, auditoria não.
Se a gravação da trilha falhar, a operação inteira falha. É o que garante que
não existe escrita sem rastro.

### 4.3 Formato

```
id | ocorrido_em         | request_id | entidade | entidade_id | operacao          | detalhe
---+---------------------+------------+----------+-------------+-------------------+------------------------------------------
 1 | 2026-08-15 17:32:07 | k2m9x4qp   | SESSAO   |         901 | SESSAO_CRIADA     | {"assuntoId":42,"tipo":"TEORIA",
   |                     |            |          |             |                   |  "tempoMin":50,"revisaoCriadaId":124}
 2 | 2026-08-15 17:32:07 | k2m9x4qp   | REVISAO  |         124 | REVISAO_AGENDADA  | {"assuntoId":42,"nivel":1,
   |                     |            |          |             |                   |  "dataPrevista":"2026-08-16"}
```

`detalhe` é `jsonb`, e não texto formatado. É a diferença que permite:

```sql
-- Quantos cancelamentos foram por cascata, e quantos por decisão do usuário?
SELECT detalhe->>'motivo' AS motivo, count(*)
  FROM auditoria
 WHERE operacao = 'REVISAO_CANCELADA'
 GROUP BY 1;

-- O que aconteceu com a revisão 124?
SELECT ocorrido_em, operacao, detalhe
  FROM auditoria
 WHERE entidade = 'REVISAO' AND entidade_id = 124
 ORDER BY ocorrido_em;
```

Na v1, isso exigiria parsear string.

### 4.4 Alterações registram antes e depois

```json
{ "camposAlterados": { "tempoMin": { "de": 50, "para": 90 },
                       "tipo":     { "de": "TEORIA", "para": "AULA" } } }
```

Apenas os campos que **de fato** mudaram. Registrar o objeto inteiro em toda
edição infla a tabela e esconde a informação útil.

### 4.5 Por que auditar num sistema de um usuário só

Não é para saber **quem** fez — só existe um. É para saber **o que o sistema
fez**. As cascatas de INV-015, INV-016 e INV-017 alteram registros que o usuário
não tocou. Quando ele notar que 12 revisões sumiram, a trilha é o que mostra que
foi a desativação de uma disciplina, três dias antes — e o campo `motivo`
(`02A_ENUMS.md` §9) é o que distingue cascata de decisão manual.

### 4.6 Retenção

Rotina mensal apaga linhas com mais de **12 meses**. **Única exclusão física do
sistema** (ADR-011).

Estimativa: uso normal produz ~15 linhas/dia — cerca de 5 500 por ano, ~4 MB com
índices. A retenção existe por higiene, não por pressão de espaço.

---

## 5. Investigando um problema

Roteiro, na ordem:

| # | Situação | Ação |
|---|---|---|
| 1 | Usuário tem o `requestId` | `grep <id>` no log + `WHERE request_id = '<id>'` na auditoria |
| 2 | "Sumiu uma revisão" | `SELECT … FROM auditoria WHERE entidade='REVISAO' AND entidade_id=N ORDER BY ocorrido_em` — a última operação e o `motivo` respondem |
| 3 | "Os números do dashboard estão errados" | Procure `SESSAO_ARQUIVADA` na auditoria; sessão arquivada sai de tudo (INV-018) |
| 4 | Lentidão | `03D_PERFORMANCE.md` §7 |
| 5 | Nada disso ajudou | `LOG_LEVEL=DEBUG`, reproduzir, **voltar para `INFO`** |
| 6 | Suspeita de schema divergente | `verificarSchema()` (`02_DATABASE.md` §12) |

O passo 5 tem a advertência porque `DEBUG` liga o SQL do Hibernate e enche 10 MB
em minutos.

---

## 6. Testes obrigatórios

| Teste | Verifica |
|---|---|
| `requestId` no header | toda resposta traz `X-Request-Id`, inclusive `200` |
| `requestId` no `ProblemDetail` | erro `500` traz o mesmo id do header |
| MDC limpo | duas requisições em sequência na mesma thread têm ids diferentes |
| Auditoria sofre rollback | operação revertida não deixa linha em `auditoria` |
| `MANDATORY` | `registrar` fora de transação lança |
| Cascata auditada | `desativar` registra `assuntosArquivados` e `revisoesCanceladas` corretos |
| `motivo` de cancelamento | distingue `USUARIO` de `ASSUNTO_ARQUIVADO` |
| `camposAlterados` | só os campos que mudaram aparecem |
| Sem dado pessoal | nenhuma operação grava `observacoes` ou `descricao` em `detalhe` |

O último é um teste de conformidade e vale a pena: é o tipo de coisa que entra no
código por conveniência num dia de pressa.

---

## 7. Changelog

| Versão | Data | Mudança |
|---|---|---|
| 2.0.0 | 2026-08-15 | Log (arquivo, Logback, MDC) separado de auditoria (tabela, `jsonb`). Nível `AUDIT` da v1 removido. Auditoria passa a ser `MANDATORY` e transacional — falha derruba a operação |
| 1.0.0 | 2026-08-15 | Versão inicial |
