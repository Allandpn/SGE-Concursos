# Sprint 41 — Modo de teste

| | |
|---|---|
| Versão | 1.1.0 |
| Data | 2026-11-05 |
| Subordinado a | `docs/04_FRONTEND.md` v1.21.0 §8C; `02_JORNADAS` v1.33.0 J-19; ADR-061 |
| Origem | Pedido do usuário (2026-11-05), decidido em duas rodadas; wireframe aprovado na conversa |
| Modo de trabalho | Implementação direta (`AGENTS.md`) |

## 0. Escopo

**Entra:** o schema `teste` criado e migrado pelo app; a escolha do schema por requisição (cookie do aparelho); a cópia dos dados reais;
a data do teste (deslocamento em dias); as rotas do modo de teste; a faixa e o cartão em Ajustes; a tela pede "hoje" ao servidor.

**Fica de fora:** desativar sessões na escada real (Sprint 42); IA, avisos e envio de arquivo no teste.

## 1. Banco

Nenhuma migração nova. O schema `teste` recebe as mesmas migrações (Flyway, `schemas=teste`) na primeira entrada e a cada subida do app
em que ele já exista; a tabela `teste.modo_teste_estado` (deslocamento em dias, quando foi copiado) é criada fora do Flyway, só nele.

## 2. Servidor

| Peça | O quê |
|---|---|
| `ModoTeste` | O estado da requisição (`ThreadLocal`): ligado e o deslocamento em dias |
| `ModoTesteConfig` | Dois pools (real e `teste`, com `currentSchema=teste,public`) e o `DataSource` de roteamento, `@Primary`; sem a marca, o real |
| `RelogioDoTeste` | O `Clock` do app: o do sistema, mais o deslocamento quando a requisição é de teste |
| `ModoTesteFiltro` | Depois do filtro de acesso: com o cookie `modo_teste=1` e o schema pronto, liga o modo, exceto em `/api/acesso/**` e `/internal/**`. No teste, recusa (409 `MODO_TESTE_SEM_ARQUIVO`) envio de arquivo (multipart, exceto a importação de assuntos) e o reprocessamento (`/reprocessar`, `/refazer`, `/preparar`) |
| `ModoTesteService` | Preparar (cria e migra o schema); copiar (esvazia as tabelas do teste e copia do real na ordem das chaves estrangeiras, preservando os ids, com `OVERRIDING SYSTEM VALUE`, e acerta as identidades); deslocar a data. Não copia `passkey`, `sessao_acesso`, `codigo_recuperacao`, `push_config`, `push_inscricao`, `configuracao_ia` nem `flyway_schema_history`. Usa o pool real com nomes qualificados (`public.`, `teste.`) |
| `ServicoIa.completar` | No teste, recusa: "A IA não roda no modo de teste" |
| Rotas | `GET /api/modo-teste` → `{ativo, preparado, hoje, deslocamentoDias, copiadoEm}`; `POST /api/modo-teste/entrar` (prepara e, se nunca copiou, copia; grava o cookie), `/sair`, `/recomecar`, `/avancar` `{dias}`, `/data` `{data}`, `/hoje` |

## 3. Tela

- **A faixa** listrada fixa no topo, em todas as telas, com a data do teste, +1 dia, +7 dias e o menu (Ir para a data…, Hoje de verdade,
  Recomeçar, Sair). Cada gesto recarrega o app, para tudo ler a data nova.
- **Ajustes:** o cartão "Modo de teste" (entrar, com a cópia em andamento; ou recomeçar e sair).
- **A data:** `hojeISO()` passa a usar o "hoje" do servidor quando o modo está ligado; as datas humanas também.
- `CASCA_VERSAO` sobe.

## 4. Testes

| Teste | Prova |
|---|---|
| `ModoTesteTest` | entrar cria o schema e copia (ids e contagens iguais, sem as tabelas de acesso); com o cookie, uma sessão grava só no teste; sem ele, no real; avançar muda o "hoje" e o plano do teste; recomeçar refaz a cópia e zera a data; envio de arquivo recusado no teste; a IA recusada no teste |
| Chromium | entrar pela tela, a faixa em todas as telas, +1 dia muda Hoje, uma recuperação no teste não aparece no real, sair volta ao real, Recomeçar |

## 5. Itens e ordem

| # | Item | Situação |
|---|---|---|
| 41.0 | Especificação e wireframe aprovados | feito |
| 41.1 | Roteamento, relógio, filtro, cópia e rotas | feito |
| 41.2 | Faixa, Ajustes e a data do servidor na tela; `CASCA_VERSAO` | feito |
| 41.3 | Testes e Chromium | feito |
| 41.4 | Deploy pelo Guardian | — |

## Changelog

| Versão | Data | Mudança |
|---|---|---|
| 1.1.0 | 2026-11-05 | Itens 41.1 a 41.3 feitos. O pool real passa a ler `spring.datasource.hikari.*` (sem isso, os testes, que limitam o pool a 3, esgotaram as conexões do Postgres); o de teste segue o limite do real, até 4. O router espera o estado do modo de teste antes da primeira tela, e o cabeçalho de Hoje recalcula a data quando ele chega. No celular, a faixa diz "TESTE · domingo, 08/11" (no computador, "MODO DE TESTE"). `ModoTesteTest` (cópia, isolamento, data, recomeçar, arquivo e IA recusados); suíte local 476 de 478 (os 2 de esquema pedem Docker). Chromium: 14 passos — entrar por Ajustes, +1 dia três vezes até a revisão vencer, a recuperação gravada só no teste com a data do teste, outro aparelho no real, envio de arquivo recusado, ir para a data, hoje de verdade, recomeçar e sair; e o roteiro da Sprint 39 de novo (30) |
| 1.0.0 | 2026-11-05 | Criado, com o wireframe aprovado pelo usuário |
