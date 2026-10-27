# Sprint 27 — Avisos de processo concluído (Web Push)

| | |
|---|---|
| Versão | 1.1.0 |
| Data | 2026-10-27 |
| Subordinado a | `00_PRODUTO` v1.9.0 §8.5; `01_DOMINIO` v1.28.0 §3.13, D-96; `02_JORNADAS` v1.17.0 §2.3; `03_INVARIANTES` v1.16.0; ADR-048 |
| Origem | `docs/REDESENHO-FLUXO.md` (passo C) |
| Modo de trabalho | Implementação direta (`AGENTS.md`) |

## 0. Escopo

**Entra:** dois avisos de processo concluído (conferência pronta, material pronto), por Web Push, opcionais e por aparelho; a tela
em Ajustes para ligar, desligar e testar; o tratamento do push no service worker.

**Fica de fora:** qualquer aviso sobre o estudo (D-96); o app Android nativo; e-mail; "decidir depois" por aviso; histórico de
avisos enviados.

## 1. O desenho em uma página

```
Ajustes → "Ligar avisos neste aparelho" ─► permissão do navegador ─► pushManager.subscribe(chave pública VAPID)
        └─► POST /api/push/inscricoes {endpoint, keys}                    (um registro por aparelho)

Entrega da leitura do edital  ─► análise vira EM_CONFERENCIA ─► AvisoDeProcesso.conferenciaPronta(análise)
Entrega do material           ─► nada mais a preparar?       ─► AvisoDeProcesso.materialPronto(análise)
   └─ cada um: se a marca da análise está vazia ─► grava a marca ─► (depois do commit) envia a todas as inscrições
Refazer o material / Reprocessar ─► zera a marca correspondente (nova rodada)
```

## 2. Banco — `V20__push.sql`

| Mudança | Para quê |
|---|---|
| `push_config` (uma linha: `chave_publica`, `chave_privada`) | a chave VAPID, gerada na primeira necessidade; a privada nunca sai do servidor |
| `push_inscricao` (`id`, `endpoint` único, `p256dh`, `auth`, `criada_em`, `ultimo_envio_em`) | um aparelho inscrito |
| `analise_edital.aviso_conferencia_em`, `aviso_material_em` | a marca da rodada: o aviso já saiu (D-96, "uma vez por edital e rodada") |

## 3. Rotas (`/api/push`)

| Rota | O que faz |
|---|---|
| `GET /chave` | a chave pública VAPID (base64url), gerando o par se ainda não existe |
| `POST /inscricoes` | `{endpoint, keys: {p256dh, auth}}`; idempotente pelo endpoint; recusa endereço fora da lista de serviços de push (`422`) |
| `DELETE /inscricoes` | `{endpoint}`; `204` mesmo se não existia |
| `POST /teste` | manda um aviso de teste a todas as inscrições; devolve `{enviados, removidos, falhas}` |

## 4. Envio

- Cifra `aes128gcm` (RFC 8291) e cabeçalho VAPID (RFC 8292, JWT ES256 com `aud` = origem do endpoint, validade de 12 h) em
  `PushCripto`; só JCA e `java.net.http`.
- Lista fechada de serviços de push (FCM, Mozilla, Windows, Apple), `https`, porta 443: o endereço vem do navegador.
- Resposta `404` ou `410` do serviço remove a inscrição; qualquer outra falha só conta. Envio **depois do commit** e fora da
  transação: o aviso nunca desfaz nem atrasa a entrega.
- Carga: `{titulo, corpo, url}`; o service worker mostra e, ao tocar, abre `url`.

## 5. Tela e service worker

- **Ajustes → Avisos neste aparelho**: estado (desligado, ligado, bloqueado pelo navegador, sem suporte), botões Ligar, Desligar e
  "Enviar aviso de teste", e a frase "Só dois avisos: edital pronto para conferir e material pronto. Nunca sobre estudo."
- `sw.js` ganha `push` (mostra a notificação) e `notificationclick` (foca ou abre a URL). `CASCA_VERSAO` sobe.

## 6. Testes

| Garante | Teste |
|---|---|
| Cifra correta | decifra como o navegador (RFC 8291) o que `PushCripto` cifrou; JWT VAPID verifica com a chave pública |
| D-96, lista fechada | endpoint fora da lista é recusado; o envio não vai a ele |
| D-96, só dois eventos | sem inscrição não envia; conferência pronta e material pronto enviam uma vez por rodada; entregar de novo não repete; refazer/reprocessar libera nova rodada; nada mais envia |
| Inscrição ruim | `404`/`410` do serviço remove a inscrição; falha de rede mantém |
| Rotas | chave estável entre chamadas; inscrever é idempotente; teste devolve contagens |

## 7. Itens e ordem

| # | Item | Estado |
|---|---|---|
| 27.0 | Este documento, ADR-048 e a especificação | feito |
| 27.1 | V20, `PushCripto`, `PushService`, rotas | feito |
| 27.2 | Os dois eventos e as marcas de rodada | feito |
| 27.3 | Ajustes e service worker | feito |
| 27.4 | Pi: implantar, ligar no celular e mandar o aviso de teste | a fazer |

## 8. Riscos

| Risco | Tratamento |
|---|---|
| Cifra errada, que só se vê no aparelho | teste que decifra; o botão "aviso de teste" mostra de ponta a ponta |
| Serviço de push descarta o aviso | o fato aparece na tela (D-96) |
| Inscrição velha pesar no envio | 404/410 remove |

## Changelog

| Versão | Data | Mudança |
|---|---|---|
| 1.1.0 | 2026-10-27 | Itens 27.0 a 27.3 feitos. O envio garante a chave VAPID antes de enviar; a mesma entrega duas vezes não reavisa (D-89); falta o item 27.4 (o aviso de teste no celular) |
| 1.0.0 | 2026-10-27 | Primeira versão |
