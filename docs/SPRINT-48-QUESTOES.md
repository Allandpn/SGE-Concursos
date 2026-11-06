# Sprint 48 — Um botão "Questões"

| | |
|---|---|
| Versão | 1.1.0 |
| Data | 2026-11-06 |
| Subordinado a | `04_FRONTEND` v1.30.0 §8K; `02_JORNADAS` v1.40.0 (J-17) |
| Origem | Revisão da estrutura pedida pelo usuário (árvore de telas), decidida numa rodada; wireframe aprovado na conversa |
| Modo de trabalho | Implementação direta (`AGENTS.md`) |

## 0. Escopo

**Entra:** um botão "Questões" em Hoje (Agora e Depois), Quer adiantar?, detalhe do assunto, Diagnóstico e Peso pela banca; a folha
única com o filtro, o vínculo, a previsão, Abrir e Já fiz.

**Fica de fora:** o endereço, a saída e a volta, a captura, o vínculo pela árvore (regras e servidor sem mudança).

## 1. Tela

| Peça | O quê |
|---|---|
| `qc.abrirQuestoes({assuntoId, nome})` | abre a folha e carrega o atalho do assunto; `antesDeIr` continua aceito |
| `#qc-questoes` | no lugar de `#qc-antes`: `#qc-questoes-filtro` (detalhe, de onde vem o filtro, trocar/tirar/ligar o assunto) ou `#qc-questoes-sem` (Ligar); a previsão; `#qc-questoes-abrir` (`qc.ir`); `#qc-questoes-ja-fiz` (`qc.jaFiz` → lote rápido); Cancelar |
| `.qc-questoes` | o botão nos sete lugares; nas listas, "sem assunto do QC" quando falta o vínculo |
| Detalhe do assunto | `#assunto-qc`: o botão e o resumo ("10 · FGV · ligado por você") |

`CASCA_VERSAO` → `casca-2026-11-06-2`.

## 2. Testes

| Teste | Prova |
|---|---|
| Chromium (`e2e-48`) | Hoje com um botão por recuperação, sem os links antigos, com o aviso curto; a folha da Crase (filtro, disciplina inteira, ligar o assunto); Abrir preso sem previsão; abrir a aba e guardar a saída com a previsão; Já fiz abre o lote; sem vínculo, Ligar e Já fiz, sem Abrir; o detalhe com o resumo e a mesma folha; tema escuro |
| Regressão | 36, 37 e 45 pela folha nova; 39, 44, 46, 47 |

## 3. Itens e ordem

| # | Item | Situação |
|---|---|---|
| 48.0 | Especificação e wireframe aprovados | feito |
| 48.1 | A folha "Questões" e o botão nos sete lugares; `CASCA_VERSAO` | feito |
| 48.2 | Chromium e regressão | feito |
| 48.3 | Deploy pelo Guardian | — |

## Changelog

| Versão | Data | Mudança |
|---|---|---|
| 1.1.0 | 2026-11-06 | Itens 48.1 e 48.2 feitos. Chromium: 15 passos e a regressão |
| 1.0.0 | 2026-11-06 | Criado, com o wireframe aprovado pelo usuário |
