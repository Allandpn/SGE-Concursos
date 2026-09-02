# SPRINT 3 — SESSÃO

O documento de serviço da Sprint 3: a entidade `Sessao`, os parâmetros de
apuração de resultado, o serviço de registro (com a idempotência de D-45) e o
endpoint REST. Nasce agora porque a Sprint 3 é "registrar sessão" (`PROGRESSO.md`
§1) — nada aqui antecipa a escada/roteamento por resultado (Sprint 4).

| Campo | Valor |
|---|---|
| Versão | 1.3.0 |
| Data | 2026-09-01 |
| Status | Vigente |
| Subordinado a | `especificacao/01_DOMINIO.md` §2, §3.3, §3.3.1, §4; `especificacao/03_INVARIANTES.md` §11.1 (D-45); `docs/00A_ADR.md` (ADR-031); `docs/09_CODE_STYLE.md`; `docs/SPRINT-1-BANCO.md` (schema já existe, não muda) |

---

## 0. Escopo

Decidido com o usuário antes de escrever este documento:

1. **Sessão nasce standalone.** Nenhuma sessão registrada nesta sprint "cumpre"
   uma revisão pendente, mesmo sendo `QUESTOES`/`FLASHCARDS`/`RECUPERACAO` — a
   coluna `revisao.sessao_cumpriu_id` só é escrita a partir da Sprint 4, quando
   `RevisaoService` existir. **Pendência documentada, não escondida** — mesmo
   tratamento que `docs/SPRINT-2-CADASTRO.md` §7 deu a D-17.
2. **Lote mínimo (`01_DOMINIO` §4.3) não tem efeito nesta sprint.** O único
   efeito da regra é "não cumpre revisão e não move a escada" — e nenhuma das
   duas coisas existe ainda. Fica para a Sprint 4, junto do resto do
   roteamento por resultado.
3. **Só API**, mesma decisão da Sprint 2 (`docs/SPRINT-2-CADASTRO.md` §0.1):
   tela de registro depende de telas que ainda não existem.

Fora de escopo, de propósito: `Revisao` (entidade, service, agendamento — Sprint
4), `Erro` (Sprint 7), `Simulado` (Sprint 8), qualquer coisa de frente/backlog
(Sprint 5).

---

## 1. Entidade JPA

Mapeia o schema que já existe (`V1__tabelas.sql`, `V3__restricoes.sql`) —
nenhuma migração de tabela nova nesta sprint. Uma migração pequena entra só
para popular `parametro` (§2).

### 1.1 `Sessao`

| Campo | Tipo | Nota |
|---|---|---|
| `id` | `Long` | idem `Assunto`/`Disciplina` |
| `assunto` | `Assunto` | `@ManyToOne(fetch = LAZY)`, mesmo padrão de `Assunto.disciplina` — D-01 |
| `tipo` | `TipoSessao` (enum: `ESTUDO`, `QUESTOES`, `FLASHCARDS`, `RECUPERACAO`) | `@Enumerated(STRING)` |
| `data` | `LocalDate` | `NOT NULL` |
| `tempoMinutos` | `Integer` | `NOT NULL`, `> 0` (`ck_sessao_tempo_minutos`) |
| `resultado` | `ResultadoSessao` (enum: `SUCESSO`, `PARCIAL`, `FALHA`) | nullable — `ESTUDO` nunca tem (D-02); `QUESTOES`/`FLASHCARDS` **calculado pelo serviço** (§3.2); `RECUPERACAO` **declarado pelo cliente** |
| `questoesCorretas`, `questoesTotal` | `Integer` | só `QUESTOES`/`FLASHCARDS` |
| `formato` | `FormatoBanca` (enum: `MULTIPLA_ESCOLHA`, `CERTO_ERRADO`) | só `QUESTOES` (D-36) — `FLASHCARDS` não tem banca |
| `previsaoPercentual` | `Short` | só `QUESTOES`/`FLASHCARDS`, 0..100 |
| `previsaoReconstrucao` | `ResultadoSessao` | só `RECUPERACAO`; mesma escala de `resultado` (`00_PRODUTO` §7 v1.6.0) |
| `tentativaId` | `UUID` | `NOT NULL`, identifica a tentativa de registro (D-45) — **nunca gerado pelo servidor** |
| `ativo` | `boolean` | D-18, igual `Assunto`/`Disciplina` |
| `criadoEm`, `atualizadoEm` | `Instant` | idem |

Sem coluna de fase, sem `revisao_id` — a ligação com `Revisao` é sempre no
sentido `Revisao → Sessao` (`revisao.sessao_cumpriu_id`), nunca o inverso; essa
sprint não escreve nela (§0.1).

### 1.2 Enums novos

`TipoSessao`, `ResultadoSessao`, `FormatoBanca` em `shared.enums`, mesmo
pacote de `TipoPeso` (`09_CODE_STYLE` §1: "pacote técnico em inglês, mas
domínio em português" — os *valores* do enum são o vocabulário do domínio).

---

## 2. Parâmetros de negócio (`parametro`)

`01_DOMINIO` §4.2/§4.2.1: limiares são parâmetro, não constante de código —
tabela `parametro` nasceu vazia na Sprint 1 exatamente para isto. Chaves que
esta sprint insere (migração `V4__parametros_sessao.sql`, valores de partida):

| Chave | Valor | De onde vem |
|---|---|---|
| `limiar_sucesso_multipla_escolha` | `80` | §4.2.1 |
| `limiar_parcial_multipla_escolha` | `60` | §4.2.1 |
| `limiar_sucesso_certo_errado` | `90` | §4.2.1 |
| `limiar_parcial_certo_errado` | `75` | §4.2.1 |
| `limiar_sucesso_flashcards` | `80` | §4.2 (flashcards não tem banca, usa o limiar único de partida) |
| `limiar_parcial_flashcards` | `60` | idem |

`lote_minimo_questoes` (§4.3) **não entra nesta sprint** — só passa a ter
efeito quando existir "cumprir revisão"/"mover a escada" para desligar (§0.2).
Inserir a chave agora seria configuração sem nenhum código que a lê.

---

## 3. Serviço

### 3.1 `SessaoService.registrar`

Grava direto, deixa o banco recusar por restrição nomeada, traduz pelo nome —
mesmo gesto de `AssuntoService` (`docs/SPRINT-2-CADASTRO.md` §3.1), com uma
diferença: **uma das restrições não vira erro.**

| Restrição violada | Erro de domínio | HTTP |
|---|---|---|
| `fk_sessao_d01_assunto` | `ASSUNTO_INEXISTENTE` | 404 |
| `ck_sessao_d02_estudo_sem_resultado` | `ESTUDO_SEM_RESULTADO` | 422, campo `resultado` |
| `ck_sessao_d04a_previsao_por_tipo` | `PREVISAO_INVALIDA` | 422 |
| `ck_sessao_d36_questoes_tem_formato` | `FORMATO_OBRIGATORIO` | 422, campo `formato` |
| `ck_sessao_formato_por_tipo` | `FORMATO_NAO_APLICAVEL` | 422, campo `formato` — higiene, sem regra numerada (`docs/SPRINT-1-BANCO.md` §3.5) |
| `ux_sessao_d45_tentativa_unica` | **não é erro** — ver §3.3 | 201 (mesmo status do sucesso original) |

Qualquer outra restrição (`ck_sessao_tipo`, `ck_sessao_tempo_minutos`, etc. —
"higiene de dado", sem regra `D-xx` numerada) não tem tradução própria: sobe
como exceção crua, vira 500 com stack no log. Não é lacuna por descuido — são
formas de request tão claramente malformado que não merece um `codigo` de
domínio, e esconder atrás de um catch genérico é o defeito que `09_CODE_STYLE`
§3.2 proíbe.

**Duas validações eager, achadas ao implementar — sem restrição de banco
correspondente, então não dá pra esperar o `catch`:**

| Situação | Erro de domínio | HTTP |
|---|---|---|
| `QUESTOES`/`FLASHCARDS` sem `questoesCorretas`/`questoesTotal` | `QUESTOES_OBRIGATORIAS` | 422, campo `questoesTotal` — sem os dois, o serviço não tem o que apurar (§3.2), e `ck_sessao_questoes_por_tipo` (banco) só exige os dois **nulos** fora de QUESTOES/FLASHCARDS, nunca exige presença |
| `RECUPERACAO` sem `resultado` | `RESULTADO_OBRIGATORIO` | 422, campo `resultado` — sem `PATCH` nesta sprint (§4), uma sessão sem resultado nunca seria completável depois |

E `SESSAO_INEXISTENTE` (404) para `GET /api/sessoes/{id}` com `id` que não
existe — mesmo padrão de `ASSUNTO_INEXISTENTE`/`DISCIPLINA_INEXISTENTE` da
Sprint 2, não é violação de restrição, é `findById` vazio.

### 3.2 Cálculo de `resultado` — quando o serviço decide, não o cliente

`01_DOMINIO` §4.1: `QUESTOES`/`FLASHCARDS` apuram por **percentual de acerto
contra limiar**; `RECUPERACAO` é **declarado** pelo cliente após a produção
(as âncoras de §4.1 são texto de tela, não campo). Consequência direta na
API: o `resultado` do `SessaoRequest` só é usado (e obrigatório) para
`RECUPERACAO`. Para `QUESTOES`/`FLASHCARDS` o serviço **ignora** o que vier
nesse campo e calcula a partir de `questoesCorretas`/`questoesTotal` (e
`formato`, só para `QUESTOES`) contra os limiares de `parametro` (§2). Para
`ESTUDO` o campo fica sempre `null`.

Isto não é regra `D-xx` numerada — é política de aplicação (quinto princípio
de `03_INVARIANTES` §10: "nenhuma política desce para a persistência"), então
mora inteira no serviço, nunca em `CHECK`.

### 3.3 D-45 — reenvio devolve sucesso, não erro (ADR-031)

```java
try {
    return sessaoRepository.save(sessao);
} catch (DataIntegrityViolationException e) {
    if (nomeRestricao(e).contains("ux_sessao_d45_tentativa_unica")) {
        return sessaoRepository.findByTentativaId(request.tentativaId())
            .orElseThrow(); // não pode faltar: a unicidade acabou de confirmar que existe
    }
    throw traduzir(e); // as quatro da tabela de §3.1, senão relança crua
}
```

O **molde** desta sprint é este `if`: uma violação de restrição de unicidade
que, ao contrário de todas as outras do sistema até aqui, não vira
`ProblemDetail` — vira o mesmo corpo de sucesso que a tentativa original
devolveu. `GlobalExceptionHandler` nunca é acionado neste caminho.

---

## 4. Endpoints REST

| Método | Caminho | Corpo | Retorno |
|---|---|---|---|
| `POST` | `/api/sessoes` | `SessaoRequest` | `201`, `SessaoResponse` — igual no reenvio de D-45 |
| `GET` | `/api/sessoes/{id}` | — | `200`, `SessaoResponse` |
| `GET` | `/api/sessoes?assuntoId=` | — | `200`, lista de `SessaoResponse` |

Sem `PATCH`/arquivar: `01_DOMINIO` não prevê editar ou desfazer sessão
registrada — é evento, não cadastro. Sem exportação/importação (isso é só de
`Assunto`, J-1).

`SessaoRequest`: `assuntoId, tipo, data, tempoMinutos, questoesCorretas,
questoesTotal, formato, previsaoPercentual, previsaoReconstrucao, resultado,
tentativaId`. `SessaoResponse`
espelha a entidade (sem expor `Assunto`, só `assuntoId` — mesmo padrão de
`AssuntoResponse`).

---

## 5. Pendências explícitas

- **Vínculo com `Revisao`** (§0.1) — até a Sprint 4, uma sessão que "deveria"
  cumprir uma revisão pendente não faz isso. A revisão fica órfã, do mesmo
  jeito que arquivar deixa órfã hoje (D-17, `SPRINT-2-CADASTRO` §7).
- **Lote mínimo** (§0.2) — chave de parâmetro não inserida, regra não
  implementada, sem efeito nenhum enquanto a escada não existir.

---

## 6. Testes

Mesmo padrão de `docs/SPRINT-2-CADASTRO.md` §8 — `IntegracaoTestBase`
(`@SpringBootTest`, Testcontainers, chama o service ou o controller).

Cobertura mínima:
- Um teste por erro de domínio da tabela de §3.1 (status HTTP + `codigo`).
- D-45: reenviar o mesmo `tentativaId` duas vezes devolve **o mesmo** `id` de
  sessão nas duas respostas, e só existe **uma** linha na tabela.
- `QUESTOES`/`FLASHCARDS`: resultado calculado bate com o limiar parametrizado,
  nos dois formatos de banca.
- `RECUPERACAO`: resultado declarado pelo cliente é o que persiste.
- `ESTUDO`: resultado enviado pelo cliente é recusado (`ESTUDO_SEM_RESULTADO`, eager — nunca chega a tentar gravar).

---

## 7. Changelog

| Versão | Data | Mudança |
|---|---|---|
| 1.3.0 | 2026-09-01 | Nova `ck_sessao_formato_por_tipo` → `FORMATO_NAO_APLICAVEL` (`docs/SPRINT-1-BANCO.md` v1.5.0) — `ck_sessao_d36_questoes_tem_formato` sozinha aceitava formato preenchido fora de `QUESTOES`. Achado testando o fluxo manualmente |
| 1.2.0 | 2026-09-01 | `proximaSessaoData`/`proximaSessaoDescricao` removidos de `Sessao`, `SessaoRequest` e `SessaoResponse` — `00_PRODUTO` v1.7.0 e `01_DOMINIO` v1.16.0 tiram o conceito de "próxima sessão pretendida" (nunca era lido de volta por nada). Colunas removidas de `V1__tabelas.sql` — ainda não há banco persistente, editada em vez de nova migração |
| 1.1.0 | 2026-08-31 | `previsaoReconstrucao`: `Boolean` → `ResultadoSessao` (`docs/SPRINT-1-BANCO.md` v1.2.0, `00_PRODUTO` v1.6.0) — mesma escala de três vias do `resultado`, fecha contradição achada em auditoria entre `00_PRODUTO §7` e `02_JORNADAS §4.1` |
| 1.0.0 | 2026-08-30 | Criado. Escopo definido em conversa com o usuário: sessão nasce sem tocar em `Revisao` (pendência até a Sprint 4, mesmo tratamento de D-17), lote mínimo sem efeito nesta sprint, só API |
