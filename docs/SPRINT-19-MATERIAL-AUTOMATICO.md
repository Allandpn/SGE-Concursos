# SPRINT 19 — Material preparado pelo sistema

| Campo | Valor |
|---|---|
| Versão | 1.0.0 |
| Data | 2026-10-17 |
| Status | Vigente — em andamento |
| Especificação conceitual | `01_DOMINIO.md` v1.24.0 (§3.8, §3.9, §3.10, D-64, D-65), `02_JORNADAS.md` v1.11.0 (J-5), `03_INVARIANTES.md` v1.12.0 |
| ADRs | ADR-044 (substitui ADR-037), ADR-043, ADR-042 |

Pedido do usuário (2026-10-17): "subir o edital, escolher o cargo, e o sistema faz
todo o resto". O estudo já começava sozinho depois da Sprint 18; faltava o material
de leitura, que dependia de uma pessoa no PC. Antecipada em relação ao uso real
(17.5–17.6), como a Sprint 18.

Três fases: **A** (este repositório), **B** (repositório do planejamento — o worker),
**C** (deploy no Pi 4B 4 GB). Este documento cobre A e o contrato que B consome.

---

## 1. Banco — `V13__material_automatico.sql`

| Mudança | Detalhe |
|---|---|
| `concurso.slug` preenchido | Linhas com `slug` nulo recebem `'concurso-' \|\| id`; depois `SET NOT NULL`. Todo concurso tem identificador (D-64) |
| Chave dos assuntos já confirmados | Assunto ativo sem `chave_externa`, coberto por um concurso de origem `ANALISE_IA`, recebe `gen_random_uuid()::text` — D-64 aplicada a quem já foi confirmado antes dela |
| `configuracao_ia` (nova) | `id SMALLINT PRIMARY KEY CHECK (id = 1)`, `chave TEXT`, `modelo VARCHAR(150)`, `atualizado_em TIMESTAMPTZ NOT NULL`. Uma linha só; ausente = vale o ambiente |
| `lacuna_material` (nova) | `assunto_id BIGINT PRIMARY KEY` (FK `assunto`), `motivo TEXT NOT NULL`, `registrada_em TIMESTAMPTZ NOT NULL`. Um registro por assunto, regravado se o planejamento tentar de novo (D-65) |

A lacuna não é apagada quando o material chega: o estado é derivado e segmento vence
lacuna (§5). Nada a apagar (D-18).

## 2. Identidade na confirmação (D-64)

- `Concurso` passa a exigir `slug` no construtor. Na confirmação da análise:
  `slug = slugificar(nome)`; se já existir, sufixo `-2`, `-3`… O slug não muda quando o
  nome do concurso é corrigido depois.
- Todo assunto dos itens da cobertura sem `chaveExterna` recebe `UUID.randomUUID()`
  antes de `ativarComCobertura`. Assunto em comum que já tinha chave mantém a dele.
- `registrarDoPlanejamento` continua achando pelo slug — agora o concurso da análise
  também tem um, então o `concurso.json` do mesmo concurso não cria outro.
- A adoção por nome na importação (Sprint 18) continua para assunto **sem** chave (caminho
  legado); assunto com chave só é reconhecido pela chave.

## 3. IA em Ajustes (ADR-044)

| Endpoint | Corpo / resposta |
|---|---|
| `GET /api/ia/configuracao` | `{disponivel, modelo, chaveFinal, origem}` — `chaveFinal` são os 4 últimos caracteres (nunca a chave); `origem` = `AJUSTES` ou `AMBIENTE` |
| `PUT /api/ia/configuracao` | `{chave?, modelo?}` — campo ausente ou vazio mantém o atual. Chave com menos de 8 caracteres: `422 CHAVE_INVALIDA`. Responde como o `GET` |
| `GET /api/ia/modelos` | Lista do OpenRouter (`GET {url-base}/models`, pública): `[{id, nome, precoEntradaPorMilhao, precoSaidaPorMilhao}]`, ordenada por nome. Falha de rede: `503 IA_INDISPONIVEL`. Sem cache (ADR-029) — a tela é aberta raramente |

`ConfiguracaoIa` passa a ler a linha de `configuracao_ia` a cada uso; campo nulo cai no
valor do ambiente (`OPENROUTER_API_KEY`, `OPENROUTER_MODEL`). A análise de edital e o
proxy usam a mesma configuração.

## 4. Rotas internas (só para o planejamento)

Exigem o cabeçalho `X-Internal-Token` igual a `estudos.interno.token`
(`INTERNAL_TOKEN` no `.env`). Sem token configurado no servidor: `503 INTERNO_DESLIGADO`;
token errado ou ausente: `401 TOKEN_INVALIDO`. Checado por um filtro em `/internal/**`.

| Endpoint | Corpo / resposta |
|---|---|
| `POST /internal/ia/completar` | `{sistema, usuario}` → `{conteudo, modelo}`, via `ClienteOpenRouter`. IA sem chave: `503 IA_INDISPONIVEL`; erro do OpenRouter: `503 IA_FALHOU` com a mensagem dele |
| `POST /internal/material/lacunas` | `{chaveExterna, motivo}` → `204`. Assunto inexistente: `404 ASSUNTO_INEXISTENTE`. Motivo vazio: `422 MOTIVO_OBRIGATORIO` |

## 5. Estado do material (derivado, D-16)

Por assunto: **pronto** se tem segmento; senão **sem material no acervo** se tem lacuna;
senão **a preparar**.

- `PlanejamentoConcursoResponse.Assunto` ganha `segmentos` (quantidade) e `lacuna`
  (motivo ou nulo) — o worker pula quem já tem segmento ou lacuna. `chaveExterna` passa a
  vir sempre preenchida.
- `ConcursoResponse` ganha `material: {prontos, semMaterial, aPreparar}` para o
  concurso-alvo (nulo nos do histórico — evita contar o histórico inteiro a cada tela).
- `GET /api/assuntos/{id}/lacuna` → `{motivo, registradaEm}` ou `404` — mostrado no
  detalhe do assunto quando não há segmento.

## 6. Material servido pelo SGE

`/material/**` → diretório `estudos.material.dir` (`MATERIAL_DIR`, padrão
`/dados/material`), por resource handler do Spring (sem listagem de diretório; caminho
fora do diretório não resolve). O `arquivo` do segmento passa a ser o caminho
`/material/blocos/<assunto>/segmento-NN.pdf`. Na SPA, "Abrir material" aceita link
`http(s)` (legado) ou caminho que começa com `/material/`; o `sw.js` não intercepta
`/material/**`.

## 7. Telas

- **Ajustes → IA:** chave (campo senha, "definida ••••1234" quando há), modelo
  (seletor com a lista do OpenRouter, mostrando preço por milhão de tokens), salvar.
- **Concursos:** no alvo, "Material: N de M prontos · K sem material no acervo · R a
  preparar".
- **Assuntos → detalhe:** sem segmento e com lacuna, "Sem material no acervo: <motivo>".

## 8. Testes

| Teste | Prova |
|---|---|
| `AnaliseEditalTest.confirmar_d64_slugEChaves_eConcursoJsonDoMesmoSlugNaoDuplica` | D-64 e a regressão do concurso duplicado |
| `AnaliseEditalTest.confirmar_d64_nomeRepetidoGanhaSufixoNoSlug` | D-64 |
| `MaterialAutomaticoTest.semToken_recusa` / `tokenCerto_completaPeloOpenRouter` / `foraDeInternal_naoPedeToken` | §4 |
| `MaterialAutomaticoTest.lacunaAparece_segmentoVence` / `lacunaSemMotivo_ou_assuntoInexistente_recusa` | D-65 |
| `MaterialAutomaticoTest.serveDoDiretorio_naoSaiDele` | §6 |
| `MaterialAutomaticoTest.semAjuste_valeOAmbiente_eAChaveNuncaVolta` / `cadastrarChaveEModelo_passaAValerSemReiniciar` / `chaveCurta_recusa` / `modelos_ordenadosComPrecoPorMilhao` | §3 |
| `ConcursoTest.d58_segundoAtivoRecusadoPelaEscrita` | agora com `slug` — a V13 o torna obrigatório |

## 9. Fora desta fase

O worker (Fase B, repositório do planejamento) e o compose/volumes/limites no Pi
(Fase C, adendo `03E_DEPLOYMENT.md` §8C) — escritos quando cada fase começar.

## Changelog

| Versão | Data | Mudança |
|---|---|---|
| 1.0.0 | 2026-10-17 | Criado (Fase A) |
