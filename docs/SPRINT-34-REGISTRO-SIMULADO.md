# Sprint 34 — Registro de simulado

| | |
|---|---|
| Versão | 1.1.0 |
| Data | 2026-11-02 |
| Subordinado a | `01_DOMINIO` v1.39.0 §3.6, §3.20, D-148, D-149; `02_JORNADAS` v1.28.0 J-16; `03_INVARIANTES` v1.27.0 |
| Origem | Pedido do usuário (2026-11-02); wireframe aprovado na conversa |
| Modo de trabalho | Implementação direta (`AGENTS.md`) |

## 0. Escopo

**Entra:** a pílula Simulado do Registrar, com as disciplinas do alvo, "outra disciplina", certas, total e em branco; a nota logo
depois; arquivar e a lista de simulados em Progresso; o cartão do simulado da semana abrindo a mesma tela.

**Fica de fora:** editar simulado; formato por disciplina no mesmo simulado; brancos na M-1; brancos no simulado que vem da prova
hospedada (a nota em pontos dela já sai da tentativa, D-136).

## 1. Banco — `V30__simulado_brancos_e_arquivo.sql`

| Peça | O quê |
|---|---|
| `resultado_simulado.questoes_em_branco` | INT, nulo = não informado |
| `ck_resultado_simulado_d148_brancos` | nulo, ou ≥ 0 e certas + brancos ≤ total (D-148) |
| `simulado.arquivado_em` | TIMESTAMPTZ, nulo = vale (D-149) |

## 2. Servidor

| Peça | O quê |
|---|---|
| `ResultadoSimuladoRequest` | ganha `questoesEmBranco` (o construtor de quatro campos continua, para a prova hospedada) |
| `SimuladoService.registrar` | recusa brancos negativos ou acima de total − certas (`BRANCOS_INVALIDOS`) antes de gravar |
| `SimuladoService.arquivar` + `POST /api/simulados/{id}/arquivar` | marca `arquivado_em` (idempotente); 204; 404 inexistente |
| `GET /api/simulados` | devolve todos, com `arquivadoEm` e `questoesEmBranco`; a tela separa |
| Leitores | M-1 (`ResultadoSimuladoRepository.listarAcerto`), pontos (`ReguaService.pontosDosSimulados`) e simulado da semana (`TurnoService`) ignoram arquivados; os pontos usam erradas = total − certas − brancos |

## 3. Tela

- **Registrar:** o Registrar é por assunto (`#/registrar/{tipo}/{assuntoId}`) e o simulado não tem assunto. A pílula **Simulado** leva
  a `#/registrar/simulado`, uma tela com o mesmo cabeçalho e as mesmas pílulas (as outras quatro esmaecidas, porque pedem assunto).
  As linhas vêm da régua do alvo (as disciplinas do edital, em ordem alfabética; o edital não ordena as disciplinas entre si) e "outra disciplina" abre um seletor das ativas. Depois de
  registrar: acertos e, com régua, os pontos do simulado com o corte.
- **Hoje:** o cartão do simulado da semana leva a `#/registrar/simulado` (o formulário da Sprint 21 sai).
- **Progresso:** "Simulados" com "Registrar simulado", cada um com data, acertos, pontos (se houver) e **Arquivar** (confirmação);
  "Arquivados (N)" recolhido.
- `CASCA_VERSAO` sobe.

## 4. Testes

| Teste | Prova |
|---|---|
| `SimuladoBrancosTest` | brancos acima do total recusados no serviço e no banco; pontos com errada anula certa descontam só total − certas − brancos; arquivar tira da M-1, dos pontos e da lista de pontos, sem apagar; idempotente (D-148, D-149) |
| `AcessoRotasTest` (já existe) | a rota nova nasce fechada |
| Chromium | Registrar · Simulado com brancos, linha inválida recusada, nota em pontos; arquivar em Progresso |

## 5. Itens e ordem

| # | Item | Situação |
|---|---|---|
| 34.0 | Especificação e wireframe aprovados | feito |
| 34.1 | `V30`; brancos, arquivar, leitores | feito |
| 34.2 | Telas (Registrar, Hoje, Progresso); `CASCA_VERSAO` | feito |
| 34.3 | Testes e Chromium | feito |
| 34.4 | Deploy pelo Guardian | — |

## 6. Decisões e alternativas

| Escolhido | Recusado | O que quebraria |
|---|---|---|
| Rota própria `#/registrar/simulado`, com as mesmas pílulas | Simulado dentro de `#/registrar/{tipo}/{assuntoId}` | O simulado não tem assunto; inventar um assunto ou deixar a rota sem id mudaria o contrato das outras quatro |
| As linhas pela régua do alvo | Uma rota nova de "disciplinas do alvo" | A régua já devolve exatamente as disciplinas do edital do alvo |
| Um formato para o simulado inteiro | Formato por linha | O wireframe aprovado; simulado misto é raro e o registro por disciplina continua aceitando formatos diferentes pela API |

## Changelog

| Versão | Data | Mudança |
|---|---|---|
| 1.1.0 | 2026-11-02 | Itens 34.1 a 34.3 feitos. `V30`; brancos validados no serviço e no banco; `POST /api/simulados/{id}/arquivar`; M-1, pontos e simulado da semana ignoram arquivados; os pontos descontam total − certas − brancos; a tela `#/registrar/simulado` (pílula no Registrar, cartão da reta final e Progresso apontam para ela; o formulário da Sprint 21 em Hoje saiu); a lista de simulados em Progresso com arquivar. 3 testes novos (`SimuladoBrancosTest`). J-16 corrigida: as disciplinas vêm em ordem alfabética (o edital não as ordena). Chromium: linha com brancos demais recusada na tela e nada gravado; 3 disciplinas (uma de fora do edital) gravadas; "Acertos 26 de 39", "16 de 39 pontos · corte 10"; arquivar em Progresso tirou o simulado e manteve as 3 linhas no banco |
| 1.0.0 | 2026-11-02 | Criado, com o wireframe aprovado pelo usuário |
