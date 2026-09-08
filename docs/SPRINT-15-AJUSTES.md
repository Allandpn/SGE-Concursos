# SPRINT 15 — AJUSTES

Documento técnico da Sprint 15: expõe os parâmetros de configuração já
existentes (`Parametro`, `01_DOMINIO.md §3` — "sétimo conceito... é
configuração, não domínio") por API, só para leitura/edição pela tela
Ajustes (`docs/04_FRONTEND.md §7E`). Nasce contra a especificação vigente
nesta data.

| Campo | Valor |
|---|---|
| Versão | 1.0.0 |
| Data | 2026-09-07 |
| Status | Vigente |
| Subordinado a | `especificacao/01_DOMINIO.md §3` (parâmetro não é entidade de domínio), `§7.5` ("o que é ajustável, e o que só parece ser" — a lista fechada de 18 chaves é exatamente o que existe na tabela `parametro` hoje, nada a mais) |

---

## 0. Escopo

Nenhum parâmetro novo. Nenhuma migração. Os 18 já existem, inseridos pelas
Sprints 3/4/5 (`V4`/`V5`/`V6`) e lidos direto pelos serviços (`SessaoService`,
`RevisaoService`, `FrenteService`, `TurnoService`). Esta sprint só abre uma
via de leitura/escrita por API — o valor efetivo continua sendo lido do
mesmo jeito de sempre (`parametroRepository.findById(chave)`), sem cache,
sem camada nova.

**Fora de escopo, decisão explícita do usuário ao abrir**: faixa de valor
por chave (ex.: limiar entre 0 e 100, dias positivos inteiros). A validação
desta sprint é uma só, igual pras 18: **número positivo** (`> 0`). Não é
limite técnico — é escolha consciente de confiar no usuário além do básico,
registrada aqui pra não parecer descuido depois.

---

## 1. Por que sem D-xx novo

`Parametro` já está fora da numeração de regras de domínio por definição
(`01_DOMINIO §3`: "é configuração, não domínio"). A validação de "número
positivo" no `PATCH` é o mesmo tipo de checagem eager que `ErroService`
já faz pra `descricao`/`causa`/`confianca` — erro plausível do cliente, não
regra de negócio numerada.

---

## 2. Endpoints

| Método | Caminho | Corpo | Resposta |
|---|---|---|---|
| `GET` | `/api/parametros` | — | `List<ParametroResponse>` — as 18 chaves, `chave`/`valor`/`descricao`/`atualizadoEm` |
| `PATCH` | `/api/parametros/{chave}` | `{ "valor": "..." }` | `ParametroResponse` atualizado |

- `PATCH` com `chave` inexistente → `404`, `PARAMETRO_INEXISTENTE`.
- `PATCH` com `valor` vazio, não numérico, ou `≤ 0` → `422`,
  `VALOR_INVALIDO`, campo `valor`.
- Sem `POST`/`DELETE` — a lista de chaves é fechada (as 18 já inseridas),
  criar ou remover uma não é uma operação desta tela.

---

## 3. Testes

- `ParametroErroDominioTest` — `valor` vazio/não numérico/`≤0` devolve
  `422`/`VALOR_INVALIDO`; chave inexistente devolve `404`.
- `ParametroAtualizarTest` — caminho feliz: atualiza e o valor novo é o que
  volta no `GET` seguinte.
- `ParametroListagemTest` — `GET` devolve as 18 chaves.

---

## 4. Changelog

| Versão | Data | Mudança |
|---|---|---|
| 1.0.0 | 2026-09-07 | Criado. `GET`/`PATCH /api/parametros` sobre a tabela já existente, sem parâmetro novo nem migração. Validação única (número positivo) pras 18 chaves — decisão explícita do usuário, sem faixa por chave nesta sprint |
