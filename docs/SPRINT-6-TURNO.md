# SPRINT 6 — PLANO DE TURNO

O documento de serviço da Sprint 6: o plano do turno de hoje — fila de
recuperação e bloco de conteúdo. Nasce agora porque a Sprint 6 é "plano de
turno" (`PROGRESSO.md` §1).

| Campo | Valor |
|---|---|
| Versão | 1.0.0 |
| Data | 2026-08-30 |
| Status | Vigente |
| Subordinado a | `especificacao/00_PRODUTO.md` §3.3, §6.6; `especificacao/01_DOMINIO.md` §7; `especificacao/02_JORNADAS.md` J-2/J-4; `docs/SPRINT-5-FRENTE.md` (`FrenteService` já existe, não muda) |

---

## 0. Escopo e decisão tomada com o usuário

> **O plano é derivado e efêmero. Não se guarda, não vira entidade: é
> recalculado a cada pedido.** (`01_DOMINIO` §7.4)

Confirmado com o usuário antes de escrever este documento:

- **Fila de recuperação**: revisões `PENDENTE` vencidas (`dataPrevista` ≤
  hoje), da mais atrasada para a menos, cortada no teto diário (§5.3,
  parâmetro já existente da Sprint 5). O que excede simplesmente não entra
  nesta lista — "rola pro dia seguinte" não precisa de mecanismo: a próxima
  chamada do plano, no dia seguinte, já traz o resto.
- **Bloco de conteúdo**: só quando há vaga — reaproveita a lógica de
  `FrenteService` (Sprint 5), sem duplicar. Sem vaga em nenhuma disciplina, o
  plano não sugere nada aqui: `00_PRODUTO` §3.3 e `01_DOMINIO` §7.6 são
  explícitos que o sistema **não organiza o seu dia** além de abrir a
  frente.
- **"Puxar mais" não é endpoint novo.** Registrar a sessão sugerida e chamar
  o plano de novo já traz a próxima sugestão, porque nada é guardado entre
  as chamadas — é a mesma lógica que faz "puxar mais" funcionar de graça.
- **Não fixa "1 bloco em dia de semana, 2 no fim de semana"** (`01_DOMINIO`
  §7.3): são horas de disponibilidade real do usuário, não uma regra que o
  sistema rastreia ou impõe. Nenhuma regra `D-xx` liga dia da semana a
  contagem de bloco — a §7.3 é ilustração de orçamento de tempo, não
  especificação de comportamento.

Fora de escopo: qualquer coisa de tela (a ordem fila-antes-de-conteúdo de
`01_DOMINIO` §7.6 é preferência de interface, "sem parâmetro" — este
documento só devolve os dois conjuntos, a ordem de apresentação é da tela que
ainda não existe).

---

## 1. `FrenteService` ganha uma consulta

`proximaSugestaoDeConteudo()`: se a frente já está no teto global, devolve
vazio (§6.7 regra 4 de `01_DOMINIO`, mesma regra recomendação-não-catraca do
resto do sistema). Senão, percorre as disciplinas ativas e devolve a primeira
`proximaVaga` não vazia — mesmo mecanismo de vaga por disciplina que a Sprint
5 já tem, só decidindo *qual* disciplina perguntar primeiro. Sem prioridade
explícita entre disciplinas na especificação: ordem estável (por id) é o
suficiente, e é o que faz o núcleo avançar em ondas dentro de cada disciplina
antes de passar à próxima (`01_DOMINIO` §6.7 regra 2).

---

## 2. `TurnoService.plano()`

```
fila       = revisões PENDENTE com dataPrevista <= hoje,
             ordenadas por dataPrevista crescente (mais atrasada primeiro),
             cortadas em teto_diario_recuperacoes
blocoConteudo = FrenteService.proximaSugestaoDeConteudo() — Assunto ou nada
```

Cada item da fila leva `assuntoId`, `nome`, `nivel` e `diasAtraso`
(`hoje − dataPrevista`) — o suficiente para a tela montar "JOIN · 3 dias" sem
outra chamada.

---

## 3. Endpoint REST

Só leitura.

| Método | Caminho | Retorno |
|---|---|---|
| `GET` | `/api/turno/plano` | `200`, `TurnoPlanoResponse` |

---

## 4. Testes

- Fila vencida ordenada da mais atrasada pra menos, cortada no teto diário —
  mais vencidas que o teto não aparecem todas.
- Sem revisão vencida: fila vazia.
- Bloco de conteúdo sugerido quando há vaga; vazio quando a frente está no
  teto global.

---

## 5. Changelog

| Versão | Data | Mudança |
|---|---|---|
| 1.0.0 | 2026-08-30 | Criado. Desenho confirmado com o usuário: sem endpoint dedicado pra "puxar mais" (emerge de recalcular o plano), sem regra de 1×2 blocos por dia da semana (não é regra de domínio, é ilustração de orçamento) |
