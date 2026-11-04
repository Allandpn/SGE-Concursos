# Sprint 38 — Os próximos 7 dias e o ensaio

| | |
|---|---|
| Versão | 1.1.0 |
| Data | 2026-11-04 |
| Subordinado a | `01_DOMINIO` v1.41.0 §3.25, D-157 a D-159 (e D-121, D-143, D-144, D-155); `02_JORNADAS` v1.31.0 J-18; `03_INVARIANTES` v1.29.0; ADR-059 |
| Origem | Pedido do usuário (2026-11-04), decidido em duas rodadas; wireframe aprovado na conversa |
| Modo de trabalho | Implementação direta (`AGENTS.md`) |

## 0. Escopo

**Entra:** a coluna (computador) e o bloco recolhido (celular) dos próximos 7 dias em Hoje; a prévia do material no leitor; o ensaio
de recuperação, questões, discursiva e flashcards a partir dos próximos dias e do detalhe do assunto, com "Ver o que aconteceria".

**Fica de fora:** qualquer escrita vinda da prévia ou do ensaio; marcas de "ensaiada"; antecipar pela coluna; o bloco de leitura de
dias futuros.

## 1. Banco

Nada. A visão é consulta (D-16) e o ensaio é desfeito (ADR-059).

## 2. Servidor

| Peça | O quê |
|---|---|
| `ProximosDiasService.proximos()` | De amanhã a hoje + 7: as revisões `PENDENTE` com `data_prevista` no intervalo, de assuntos ativos de disciplinas ativas, por dia, com nível e `manutencao` (fase `CONSOLIDADO`, `FrenteService.fasesDe`). Na reta final, as de `FrenteService.assuntosComManutencaoEscondida()` saem dos dias e somam `esperandoProva`. `simulado` no sábado e no domingo com a reta final ativa (sem simulado desde a segunda, na semana corrente); `prova` no dia da prova. `teto` = `teto_diario_recuperacoes`; `acimaDoTeto` quando passa. `ritmoSemanal` pela mesma conta do Diagnóstico (D-139, com D-155) |
| `GET /api/turno/proximos-dias` | `{teto, ritmoSemanal, esperandoProva, dataProva, dias: [{data, total, acimaDoTeto, simulado, prova, revisoes: [{assuntoId, nome, nivel, manutencao}]}]}` |
| `EnsaioService.ensaiar(request)` | Numa transação marcada para desfazer: guarda a revisão pendente de antes, chama `SessaoService.gravar` (o registro real, com `tentativaId` novo e sem focos), lê o resultado e a pendente de depois e a fase, e desfaz. `efeito`: `CUMPRE` (a pendente de antes foi cumprida), `ABRE_ESCADA` (não havia pendente e passou a haver), `SEM_MUDANCA` com `motivo` `FORA_DA_JANELA`, `LOTE_PEQUENO`, `NUNCA_LIDO` (D-155) ou `LEITURA` (estudo sem escada nova) |
| `POST /api/sessoes/ensaio` | O corpo de `POST /api/sessoes`; devolve `{resultado, percentual, efeito, motivo, proxima: {nivel, dataPrevista} \| null, consolidaria}`. Erros de validação, os mesmos do registro |

## 3. Tela

- **Hoje:** o conteúdo vai para uma coluna esquerda; à direita (`lg`), `aside#proximos-dias`. No celular, o mesmo bloco no fim, recolhido,
  com o estado lembrado no aparelho (`hoje.proximos`). Em Hoje, `main` passa a `lg:max-w-5xl`.
- **Tocar num assunto:** um painel com os blocos para **Ver o material** e **Ensaiar…** (aviso e o tipo).
- **Prévia (D-158):** `#/ler/{id}?previa=1`: faixa "Prévia"; sem "Terminei", sem a barra de marcar, sem guardar a página.
- **Ensaio (D-159):** `Alpine.store('ensaio')` guarda o assunto e o tipo; Recuperar e Registrar abrem em ensaio, com a faixa "Ensaio · nada é
  gravado", sem focos e sem "pergunta ruim", e trocam Registrar por **Ver o que aconteceria**. Sair da tela encerra o ensaio.
- **Detalhe do assunto:** "Ensaiar…".
- `CASCA_VERSAO` sobe.

## 4. Testes

| Teste | Prova |
|---|---|
| `ProximosDiasTest` | revisões no dia previsto, só de amanhã a +7; contagem e acima do teto; manutenção marcada; reta final: as de médio e baixo à parte, simulado no fim de semana, o dia da prova (relógio congelado) (D-157) |
| `EnsaioTest` | recuperação e questões: o efeito e a próxima revisão iguais aos do registro real feito em seguida; o banco igual depois do ensaio (sessões, revisões, `sessao_pergunta`, `pergunta_preparo`); D-155 como `NUNCA_LIDO`; lote pequeno (D-159) |
| `AcessoRotasTest` (já existe) | as rotas novas nascem fechadas |
| Chromium | a coluna no computador e o bloco no celular; a prévia sem marcar e sem mudar a página; ensaio de recuperação e de questões sem gravar; "Ensaiar" no detalhe |

## 5. Itens e ordem

| # | Item | Situação |
|---|---|---|
| 38.0 | Especificação e wireframe aprovados | feito |
| 38.1 | `ProximosDiasService`, `EnsaioService` e as rotas | feito |
| 38.2 | Telas (Hoje, painel, prévia, ensaio, detalhe); `CASCA_VERSAO` | feito |
| 38.3 | Testes e Chromium | feito |
| 38.4 | Deploy pelo Guardian | — |

## 6. Decisões e alternativas

| Escolhido | Recusado | O que quebraria |
|---|---|---|
| O ensaio pelo registro real desfeito (ADR-059) | Um simulador da escada | Duas cópias das regras; a primeira mudança de limiar ou janela as separaria |
| Ensaio e prévia como estado da tela | Rotas novas | Cada tela de registro teria uma gêmea |
| O estado do bloco recolhido no aparelho | No servidor | É preferência de tela, não domínio |

## Changelog

| Versão | Data | Mudança |
|---|---|---|
| 1.1.0 | 2026-11-04 | Itens 38.1 a 38.3 feitos. Ao conferir no Chromium, o ensaio de uma revisão de amanhã feito hoje respondia, com razão, "fora da janela"; a partir dos próximos dias o ensaio passa a usar a **data prevista** da revisão (o detalhe ensaia hoje), e o texto começa com "No dia previsto (qui 05/11):" — D-159 precisado em `01_DOMINIO`. O painel do assunto compara a resposta pelo número do pedido (o store do Alpine embrulha o objeto). 5 testes novos (`ProximosDiasTest`, `EnsaioTest`, este sem `@Transactional` para ver o banco igual depois do ensaio). Chromium: 17 passos — a coluna à direita no computador, amanhã com 2 acima do teto de 1; a prévia sem "Terminei", sem gravar página, nota ou destaque; o ensaio de recuperação (nível 2 em 08/11) e de questões (60%, repete o nível 1) sem gravar; sair encerra o ensaio; o nunca lido pelo detalhe ("a escada não começaria"); o bloco recolhido no celular que lembra o estado |
| 1.0.0 | 2026-11-04 | Criado, com o wireframe aprovado pelo usuário |
