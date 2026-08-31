# SPRINT 4 — ESCADA E REVISÃO

O documento de serviço da Sprint 4: a entidade `Revisao`, o agendamento e
cumprimento de revisão, o roteamento por resultado (D-07/D-10/D-11), a janela
de tolerância e o fechamento da pendência D-17 deixada pela Sprint 2. Nasce
agora porque a Sprint 4 é "escada e revisão" (`PROGRESSO.md` §1).

| Campo | Valor |
|---|---|
| Versão | 1.0.0 |
| Data | 2026-08-30 |
| Status | Vigente |
| Subordinado a | `especificacao/00_PRODUTO.md` §6.4; `especificacao/01_DOMINIO.md` §3.4, §5, §6.7; `especificacao/03_INVARIANTES.md` §11.1; `docs/00A_ADR.md` (ADR-032, escrita nesta sprint); `docs/SPRINT-3-SESSAO.md` (Sessão já existe, não muda) |

---

## 0. Escopo e decisões tomadas com o usuário

1. **Intervalos da escada (dias), parâmetro, valor de partida** — a
   especificação não lista os 6 valores, só ancora nível 1 = 1 e nível 6 = 90
   (`01_DOMINIO` §5.4, D-08). Progressão escolhida com o usuário:

   | Nível | 1 | 2 | 3 | 4 | 5 | 6 |
   |---|---|---|---|---|---|---|
   | Dias | 1 | 3 | 7 | 15 | 30 | 90 |

2. **Roteamento completo, confirmado com o usuário** (§3 detalha):
   agendamento a partir de `ESTUDO`, cumprimento por recuperação (com ou sem
   pendente — recuperação espontânea, §3.3), D-07/D-10/D-11, D-17.
3. **Frente de estudo fora de escopo** (Sprint 5). `D-24` (backlog) é regra
   derivada, sem tabela própria — nada aqui persiste "fase" ou "frente".
4. **`ADR-032` escrita nesta sprint** — era "pendente de redação", marcada
   para "quando a sprint que implementa o roteamento da escada começar"
   (`docs/00A_ADR.md`).

---

## 1. Migração `V5` — coluna de versão e parâmetros

Sem tabela nova — `revisao` já existe (Sprint 1). Dois ajustes:

```sql
ALTER TABLE revisao ADD COLUMN versao BIGINT NOT NULL DEFAULT 0;
```

E os parâmetros que o serviço consome (valores de partida):

| Chave | Valor | De onde vem |
|---|---|---|
| `intervalo_nivel_1` .. `intervalo_nivel_6` | `1, 3, 7, 15, 30, 90` | §0.1 |
| `intervalo_manutencao_dias` | `150` | `01_DOMINIO` §5.5/§6.4 diz "120–180", meio do intervalo escolhido como valor único determinístico — mesmo raciocínio de D-08 (intervalo fixo, não sorteado) |
| `janela_tolerancia_percentual` | `20` | `01_DOMINIO` §5.4 |

**Nível-alvo por peso (D-23) não é parâmetro de banco** — é mapeamento fixo no
código (`TipoPeso.ALTO → 6, MEDIO → 4, BAIXO → 3`, `01_DOMINIO` §6.7): a
especificação apresenta como decisão fechada (D-23), não como valor a
sintonizar em runtime, ao contrário dos limiares e intervalos acima.

---

## 2. Entidade JPA

### 2.1 `Revisao`

Mapeia o schema que já existe + a coluna nova.

| Campo | Tipo | Nota |
|---|---|---|
| `id` | `Long` | idem demais entidades |
| `assunto` | `Assunto` | `@ManyToOne(LAZY)` |
| `nivel` | `Integer` | posição na escada desta revisão específica; `nivel-alvo` congelado quando consolidado (§3.4) |
| `dataPrevista` | `LocalDate` | |
| `sessaoOrigem` | `Sessao` | `@ManyToOne(LAZY)`, nullable — sessão que agendou esta revisão |
| `sessaoCumpriu` | `Sessao` | `@ManyToOne(LAZY)`, nullable — preenchida ao cumprir (D-06) |
| `situacao` | `SituacaoRevisao` (enum: `PENDENTE`, `CUMPRIDA`, `CANCELADA`) | |
| `versao` | `Long` | `@Version` — ADR-032, protege contra tentativas *diferentes* colidindo na mesma pendente |
| `criadoEm`, `atualizadoEm` | `Instant` | idem |

`SituacaoRevisao` em `shared.enums`, mesmo padrão dos enums da Sprint 3.

---

## 3. `RevisaoService` — o roteamento

Chamado por `SessaoService.gravar` (mesma transação, depois do `save` da
sessão) — Sessão continua sendo o dono do evento; `RevisaoService` interpreta
o efeito dele na escada. Nenhum endpoint cria `Revisao` diretamente.

### 3.1 `ESTUDO` agenda a primeira pendente

**Com pré-checagem — a única exceção ao "nunca checar antes" de D-05/ADR-031
neste sistema, e por um motivo técnico, não de gosto.** Insere `Revisao`
nível 1 só se não existir pendente. Achado implementando: tentar inserir
direto e capturar a violação de `ux_revisao_d05_pendente_por_assunto` (o
padrão usado em toda parte) corrompe a sessão do Hibernate pro resto da
transação — e essa é a **mesma transação** que está gravando a `Sessao`
(`AssertionFailure: has a null identifier`, o mesmo defeito que o D-45 da
Sprint 3 encontrou). Lá a saída foi isolar numa transação nova; aqui não dá,
porque a `Revisao` referencia a `Sessao` que ainda não commitou — as duas
*têm* que estar na mesma transação. A restrição continua existindo no banco
como rede de segurança contra corrida real (rara: sistema de um usuário só).

### 3.2 Recuperação cumpre — ou agenda espontânea (§3.3 de `01_DOMINIO`)

`QUESTOES`/`FLASHCARDS`/`RECUPERACAO` busca a pendente do assunto
(`findByAssuntoIdAndSituacao`, D-05 garante no máximo uma):

- **Existe pendente, dentro da janela de tolerância** (§3.4): marca
  `CUMPRIDA` + `sessaoCumpriu`, aplica o roteamento (§3.3) a partir do nível
  dela, cria a próxima pendente.
- **Existe pendente, fora da janela** (adiantou demais): a sessão já foi
  gravada por `SessaoService` — **não mexe na revisão**. É a mesma distinção
  do lote mínimo (Sprint 3): o esforço vale, o crédito não.
- **Não existe pendente** — recuperação espontânea. Não há o que marcar
  `CUMPRIDA`; roteia a partir do nível 1 implícito e cria a pendente
  resultante direto, com `sessaoOrigem = esta sessão`.

### 3.3 Roteamento (D-07, D-10, D-11)

```
nível atual = nível da pendente cumprida (ou 1, se espontânea)
alvo        = nível-alvo do assunto (peso → 6/4/3, §0.1)
consolidado = nível atual == alvo E os dois últimos resultados de
              recuperação deste assunto (antes deste) foram SUCESSO
```

| Situação | Resultado | Próximo nível | Próximo intervalo |
|---|---|---|---|
| Não consolidado | `SUCESSO` | `min(nível atual + 1, alvo)` | escada (§0.1) |
| Não consolidado | `PARCIAL` | nível atual (repete) | escada |
| Não consolidado | `FALHA` | `max(nível atual − 1, 1)` | escada |
| **Consolidado** | `SUCESSO` | alvo (congelado) | manutenção (§1) |
| **Consolidado** | `PARCIAL` | alvo (congelado) | **metade** do intervalo de manutenção (`01_DOMINIO` §5.5: "antecipa a próxima para metade") |
| **Consolidado** (D-11) | `FALHA` | **alvo** (não regride abaixo — "volta à escada no último nível", `01_DOMINIO` §5.5) | escada, intervalo do nível alvo |

"Os dois últimos resultados" olha as duas últimas sessões que **cumpriram**
revisão deste assunto (`QUESTOES`/`FLASHCARDS`/`RECUPERACAO` com
`revisao.sessao_cumpriu_id` apontando pra elas), ordenadas por data — não
conta a sessão que está sendo processada agora nem sessões espontâneas sem
revisão associada.

### 3.4 D-17 — arquivar cancela a pendente

Pendência da Sprint 2 (`docs/SPRINT-2-CADASTRO.md` §7), fechada agora:
`AssuntoService.arquivar` e `DisciplinaService.arquivar` passam a cancelar
(`situacao = CANCELADA`) a revisão pendente do assunto (ou de todos os
assuntos da disciplina). Sem efeito sobre revisões já `CUMPRIDA` (D-18, nada
histórico é tocado).

### 3.5 Concorrência (ADR-032)

Update na pendente (marcar `CUMPRIDA`, ou qualquer escrita nela) passa pelo
`@Version`. Colisão vira `OptimisticLockingFailureException`, traduzida para
`ConflictException` — `codigo` `REVISAO_CONCORRENTE`, 409. Complementar a
D-45 (Sprint 3): D-45 protege contra a *mesma* tentativa reenviada; a versão
protege contra tentativas *diferentes* colidindo na mesma pendente (duas
abas, dois aparelhos).

---

## 4. Endpoints REST

Só leitura — nenhum endpoint cria/edita `Revisao` diretamente, ela nasce só
como efeito de registrar sessão (§3).

| Método | Caminho | Retorno |
|---|---|---|
| `GET` | `/api/revisoes/{id}` | `200`, `RevisaoResponse` |
| `GET` | `/api/revisoes?assuntoId=` | `200`, lista de `RevisaoResponse` |

---

## 5. Testes

Mesmo padrão das sprints anteriores (`IntegracaoTestBase`).

- `ESTUDO` cria a pendente nível 1; `ESTUDO` de novo com pendente já aberta
  não duplica (D-05 continua valendo pelo caminho de `Revisao` também).
- `SUCESSO` sobe nível e agenda a próxima com o intervalo certo; `PARCIAL`
  repete; `FALHA` regride (piso 1).
- Recuperação espontânea sem pendente cria a pendente roteada, sem
  `sessaoCumpriu`.
- Fora da janela de tolerância: sessão grava, revisão pendente não muda.
- Consolidação: nível-alvo + 2 `SUCESSO` seguidos agenda no intervalo de
  manutenção; `FALHA` consolidado volta pro nível-alvo (não abaixo).
- D-17: arquivar assunto cancela a pendente dele; arquivar disciplina cancela
  as pendentes de todos os assuntos dela.

---

## 6. Changelog

| Versão | Data | Mudança |
|---|---|---|
| 1.0.0 | 2026-08-30 | Criado. Escopo e roteamento completo (D-07/D-10/D-11/D-17) confirmados com o usuário antes da implementação — incluindo a progressão de intervalos da escada, que a especificação não lista numericamente |
