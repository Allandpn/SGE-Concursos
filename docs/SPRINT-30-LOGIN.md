# Sprint 30 — Login por passkey

| | |
|---|---|
| Versão | 1.1.0 |
| Data | 2026-11-01 |
| Subordinado a | `01_DOMINIO` v1.32.1 §3.16, D-106 a D-113; `02_JORNADAS` v1.21.0 J-9; `03_INVARIANTES` v1.20.1; ADR-051 |
| Origem | Pedido do usuário (2026-10-30): usar o app fora da rede de casa. ADR-051 aprovado em 2026-11-01 |
| Modo de trabalho | Implementação direta (`AGENTS.md`) |

## 0. Escopo

**Entra:** a porta (`entrar.html`), o cadastro do primeiro passkey pela rede privada, entrar com passkey, a sessão de 30 dias em cookie,
os 8 códigos de recuperação, Ajustes → Acesso (passkeys, sair, sair de todos, códigos), o freio de tentativas, o filtro que fecha toda rota
fora da lista aberta e a recusa de `/internal` a pedido vindo da internet pública.

**Fica de fora:** ligar o `tailscale funnel` no Pi e o atributo `funnel` na política da tailnet (operação de quem tem root e a conta do
Tailscale, ADR-051); o convite pelo Guardian; o app Android nativo (ADR-041), que passa a receber `401` e só volta a funcionar quando
ganhar login (não há uso dele registrado hoje); Spring Security.

## 1. O desenho em uma página

```
Qualquer pedido ─► SessaoAcessoFilter
   ├─ lista aberta (§3.1)? ─► segue
   ├─ /internal/** ─► TokenInternoFilter (token; pedido com marca de internet pública → 403, D-113)
   ├─ cookie eagora_sessao válido (resumo SHA-256 no banco, não expirado, não encerrado)? ─► renova se preciso ─► segue
   └─ sem sessão: /api/** e /material/** → 401 SESSAO_AUSENTE · tela (GET) → 302 /entrar.html            (D-106)

Porta: GET /api/acesso/estado ─► { primeiroAcesso } (só verdadeiro pela rede privada e sem passkey, D-108)
  primeiro:  POST /primeiro/opcoes ─► navigator.credentials.create ─► POST /primeiro ─► passkey + 8 códigos + sessão
  entrar:    POST /entrar/opcoes   ─► navigator.credentials.get    ─► POST /entrar   ─► sessão                   (D-107, D-112)
  recuperar: POST /recuperar {codigo} ─► código consumido + sessão ─► cadastra o passkey do aparelho (D-110)
```

## 2. Banco — `V23__acesso.sql`

```sql
CREATE TABLE passkey (
    id             BIGSERIAL PRIMARY KEY,
    credencial_id  BYTEA NOT NULL,
    chave_publica  BYTEA NOT NULL,          -- COSE, como o autenticador entregou
    contador       BIGINT NOT NULL,
    nome           VARCHAR(80) NOT NULL,    -- "Chrome · Android", editável depois
    criado_em      TIMESTAMPTZ NOT NULL DEFAULT now(),
    ultimo_uso_em  TIMESTAMPTZ,
    removido_em    TIMESTAMPTZ,             -- D-18: remover é marcar
    CONSTRAINT uk_passkey_credencial UNIQUE (credencial_id)
);

CREATE TABLE sessao_acesso (
    id             BIGSERIAL PRIMARY KEY,
    resumo         BYTEA NOT NULL,          -- SHA-256 do valor do cookie (D-109)
    passkey_id     BIGINT REFERENCES passkey(id),   -- nulo quando entrou por código de recuperação
    aparelho       VARCHAR(200),
    criada_em      TIMESTAMPTZ NOT NULL,
    ultimo_uso_em  TIMESTAMPTZ NOT NULL,
    expira_em      TIMESTAMPTZ NOT NULL,
    encerrada_em   TIMESTAMPTZ,
    CONSTRAINT uk_sessao_acesso_d109_resumo UNIQUE (resumo)
);

CREATE TABLE codigo_recuperacao (
    id             BIGSERIAL PRIMARY KEY,
    resumo         BYTEA NOT NULL,          -- SHA-256 do código (D-110)
    criado_em      TIMESTAMPTZ NOT NULL,
    usado_em       TIMESTAMPTZ,
    invalidado_em  TIMESTAMPTZ,
    CONSTRAINT uk_codigo_recuperacao_d110_resumo UNIQUE (resumo)
);
```

Gatilhos (as invariantes de `03_INVARIANTES` §4):

- **`trg_passkey_d107_contador`** (BEFORE UPDATE): `NEW.contador < OLD.contador` falha; e `NEW.contador = OLD.contador` falha quando
  `OLD.contador > 0`. Contador que fica em 0 é o passkey que não conta (passkeys sincronizados de Apple e Google): igualdade em zero passa.
- **`trg_passkey_d111_ultimo`** (BEFORE UPDATE OF `removido_em`): marcar como removido o último passkey ativo falha.
- Nenhuma das três tabelas tem caminho de `DELETE` no código (D-18); a sessão encerra por `encerrada_em`, o código por `usado_em`/`invalidado_em`.

Nada de usuário: há um titular só (ADR-040 continua). O `userHandle` do WebAuthn é uma constante de 16 bytes, igual em todo passkey.

## 3. Rotas

### 3.1 A lista aberta (D-106)

Exatamente estas, no `SessaoAcessoFilter.ABERTAS`:

| Caminho | Por quê |
|---|---|
| `GET /entrar.html`, `GET /js/entrar.js`, `GET /js/api.js` | A porta e o que ela usa; não carregam dado |
| `GET /vendor/**`, `GET /css/**`, `GET /icons/**`, `GET /manifest.webmanifest`, `GET /sw.js` | Casca sem dado; o service worker não pode ser buscado com redirecionamento |
| `GET /api/acesso/estado` e os `POST` de `/api/acesso/{primeiro/opcoes, primeiro, entrar/opcoes, entrar, recuperar}` | O próprio login |
| `GET /actuator/health` | A saúde do contêiner (o deployer do Guardian lê) |
| `/internal/**` | Fora do login; tem o token próprio e a recusa de D-113 |

Todo o resto exige sessão. `GET` que não é `/api/**` nem `/material/**` (uma tela: `/`, `/index.html`, `/js/pages.js`…) leva a `/entrar.html`
(302); dado responde `401` com `codigo = SESSAO_AUSENTE`. O teste estrutural percorre todas as rotas mapeadas no Spring e prova que, fora da lista,
nenhuma responde sem sessão.

### 3.2 Login (`/api/acesso`)

| Rota | Entrada → saída | Regra |
|---|---|---|
| `GET /estado` | → `{logado, primeiroAcesso}` | `primeiroAcesso` só é `true` pela rede privada e sem passkey (D-108); de fora é sempre `false` |
| `POST /primeiro/opcoes` | `{nome}` → `{pedidoId, publicKey}` | 403 `PRIMEIRO_ACESSO_FECHADO` se já existe passkey ou o pedido é público |
| `POST /primeiro` | `{pedidoId, credencial}` → `{codigos[8]}` + cookie | Grava passkey, gera 8 códigos e abre a sessão, na mesma transação; confere D-108 de novo |
| `POST /entrar/opcoes` | → `{pedidoId, publicKey}` | Credencial descoberta pelo autenticador (sem lista de credenciais: a porta não diz quais existem) |
| `POST /entrar` | `{pedidoId, credencial}` → 204 + cookie | D-107 (assinatura e contador), D-112; falha responde sempre `401 NAO_FOI_POSSIVEL_ENTRAR` |
| `POST /recuperar` | `{codigo}` → 204 + cookie | Código válido é consumido (D-110); limite mais estrito (D-112) |

O desafio (`pedidoId`) vive na memória por 5 minutos e vale uma vez. Reiniciar o app no meio do login só pede tocar de novo.

### 3.3 Dentro (exigem sessão)

| Rota | O quê |
|---|---|
| `GET /api/acesso/passkeys` | `[{id, nome, criadoEm, ultimoUsoEm, esteAparelho}]` |
| `POST /api/acesso/passkeys/opcoes`, `POST /api/acesso/passkeys` | Cadastrar outro passkey (D-111: exige sessão; quem entrou por código usa este caminho) |
| `DELETE /api/acesso/passkeys/{id}` | Marca removido; o último responde 409 `ULTIMO_PASSKEY` (D-111, garantido também pelo gatilho) |
| `POST /api/acesso/sair`, `POST /api/acesso/sair-de-todos` | Encerra esta sessão / todas (D-109) |
| `GET /api/acesso/codigos` | `{restantes}` |
| `POST /api/acesso/codigos` | Gera 8 novos (mostrados uma vez) e invalida os anteriores (D-110) |

## 4. Regras no código

- **Rede privada × pública (D-108, D-113).** `OrigemDoPedido.publica(request)`: é pública quando tem o cabeçalho `Tailscale-Funnel-Request`
  (o Funnel o põe em todo pedido vindo da internet). É privada para o primeiro cadastro só quando, além de não ter a marca, traz
  `Tailscale-User-Login` (o `tailscale serve` põe a identidade do usuário da tailnet e apaga o cabeçalho se o cliente o mandar). Exigir a
  prova positiva fecha o caso de um proxy que esqueça a marca: sem identidade da tailnet, o primeiro cadastro não abre.
- **Origem e `rpId`.** Derivados do próprio pedido (`X-Forwarded-Host`/`Host` e `X-Forwarded-Proto`): o navegador só deixa o site `X` usar
  o `rpId` `X`, então espelhar o pedido não abre nada. Como defesa a mais, o host precisa casar com `estudos.acesso.hosts` (padrão
  `*.ts.net,localhost`). Consequência: o passkey fica preso ao nome do nó (ADR-051) e não funciona pelo IP. Não há variável nova no `.env`.
- **Sessão (D-109).** Valor do cookie: 32 bytes aleatórios em base64url; o banco guarda o SHA-256. Cookie `eagora_sessao`, `HttpOnly`,
  `Secure`, `SameSite=Strict`, `Path=/`, `Max-Age` de 30 dias. Renova `expira_em` e o cookie quando o último uso tem mais de 1 hora (uma
  escrita por hora, não por pedido). Pedido que muda dado (`POST/PUT/PATCH/DELETE` fora de `/internal`) precisa de `Origin` igual à do app,
  senão 403 `ORIGEM_RECUSADA`.
- **Contador (D-107).** O WebAuthn é da biblioteca `com.yubico:webauthn-server-core` (a única dependência nova, ADR-051); ela recusa
  assinatura inválida e contador que não avança. O serviço grava o contador novo, e o gatilho é a segunda barreira.
- **Códigos (D-110).** 8 códigos de 12 caracteres do alfabeto base32 sem ambíguos, mostrados como `XXXX-XXXX-XXXX`; gravados como SHA-256 do
  texto normalizado (sem hífen, maiúsculo). Gerar novos marca `invalidado_em` nos que restavam.
- **Freio (D-112).** Em memória (um contêiner só), com `Clock`: cada falha espera 1 s antes de responder. Passkey: 5 falhas por origem em
  10 min bloqueiam a origem por 15 min; 30 falhas no total em 10 min bloqueiam pedidos públicos por 15 min (pedido da tailnet não entra no
  bloqueio total: o titular sempre tem um caminho). Código: 3 falhas por origem em 30 min bloqueiam por 1 h; 10 no total em 24 h bloqueiam
  pedidos públicos por 1 h. A origem é o primeiro endereço de `X-Forwarded-For` (o Tailscale o põe), senão o endereço remoto. Reiniciar o app
  zera os contadores: nunca há bloqueio permanente.
- **Interno (D-113).** `TokenInternoFilter` passa a recusar com 403 `ROTA_INTERNA_PUBLICA` todo pedido com a marca do Funnel, antes de olhar
  o token.
- **Desligado só em teste.** `estudos.acesso.desligado-em-teste=true` desliga o filtro (a base de testes antiga usa a API sem sessão). Ligado
  em produção sempre; desligado, o app registra `WARN` na subida. Os testes do acesso ligam o filtro.

## 5. Tela

- **`entrar.html` + `js/entrar.js`** (porta, fora das oito telas): um botão **Entrar**; **Usar código de recuperação**; com `primeiroAcesso`,
  **Cadastrar este aparelho**, depois os 8 códigos com **Copiar** e a caixa "guardei fora deste aparelho" que libera **Abrir o app**. Fora da
  rede privada e sem `primeiroAcesso`, a linha "o primeiro acesso é feito pela rede privada (tailnet)". Mensagens: "Não foi possível entrar"
  para qualquer falha; bloqueio diz quando volta.
- **`api.js`**: resposta `401 SESSAO_AUSENTE` leva a `/entrar.html`.
- **Ajustes → Acesso**: lista de passkeys (nome, último uso, "este aparelho"), **Cadastrar passkey neste aparelho**, **Remover** (sem botão
  no último), **Sair**, **Sair de todos**, "códigos restantes: N" e **Gerar novos códigos** (com confirmação; mostra os 8 uma vez).
- `CASCA_VERSAO` sobe; `entrar.html` e `js/entrar.js` entram na casca.

## 6. Testes

| Teste | Prova |
|---|---|
| `AcessoRotasTest` (estrutural) | Toda rota mapeada fora de §3.1 responde 401 (dado) ou 302 (tela) sem sessão (D-106) |
| `AcessoPrimeiroTest` | Primeiro cadastro: abre só pela tailnet e sem passkey; público recusado; com um passkey, fecha (D-108) |
| `AcessoEntrarTest` | Autenticador falso (P-256, CBOR à mão): entra, contador grava; contador repetido > 0 recusado; 0 e 0 aceito (D-107) |
| `AcessoSessaoTest` | Cookie com os atributos; banco só com o resumo; expira em 30 dias sem uso, renova com uso (relógio na mão); sair e sair de todos; origem estranha recusada (D-109) |
| `AcessoCodigosTest` | 8 códigos, cada um uma vez, novos invalidam os velhos, código abre sessão e permite cadastrar passkey (D-110) |
| `AcessoPasskeysTest` | Remover o último é recusado no serviço e no banco (gatilho); cadastrar exige sessão (D-111) |
| `FreioDeTentativasTest` | Bloqueio por origem e total, expira, código mais estrito, tailnet fora do bloqueio total (D-112) |
| `TokenInternoFilterTest` | Pedido com a marca do Funnel recusado mesmo com token certo; sem a marca, o token vale (D-113) |
| Chromium (Playwright, autenticador virtual do CDP) | Porta → cadastrar → códigos → app; sair → entrar; Ajustes → Acesso |

## 7. Itens e ordem

| # | Item | Situação |
|---|---|---|
| 30.0 | Especificação (feita em 2026-10-30) e ajuste de D-106/D-107 (v1.32.1) | feito |
| 30.1 | `V23__acesso.sql` com os gatilhos | feito |
| 30.2 | `SessaoAcessoFilter`, `OrigemDoPedido`, lista aberta; D-113 no `TokenInternoFilter` | feito |
| 30.3 | WebAuthn (primeiro, entrar, cadastrar outro) com `webauthn-server-core` | feito |
| 30.4 | Sessão, sair, códigos, freio | feito |
| 30.5 | Porta, `api.js`, Ajustes → Acesso, `CASCA_VERSAO` | feito |
| 30.6 | Testes e Chromium | feito |
| 30.7 | Deploy pelo Guardian (etiqueta `release/N`, com o OK do usuário); depois, `tailscale funnel` por quem tem root | — |

## 8. Decisões e alternativas

| Escolhido | Recusado | O que quebraria |
|---|---|---|
| Filtro próprio com lista aberta | Spring Security | Uma cadeia de filtros inteira para um titular, uma sessão e uma lista; mais configuração para provar "toda rota fechada" |
| `rpId` e origem do próprio pedido, com lista de hosts | Variável no `.env` | O usuário não tem acesso ao Pi para editar o `.env`; o nome da tailnet não está no repositório |
| Prova positiva da tailnet (`Tailscale-User-Login`) para o primeiro cadastro | Só a ausência da marca do Funnel | Um caminho que chegue sem passar pelo `tailscale serve` (outro proxy) pareceria privado |
| Freio em memória | Tabela de tentativas | Um contêiner só; reiniciar zerar é a garantia de "nunca permanente" |
| Desafio em memória por 5 min | Tabela de desafios | Desafio é efêmero; perder no reinício só pede outro toque |
| Igualdade de contador aceita só em zero | Recusar qualquer igualdade | Passkeys sincronizados sempre mandam 0: ninguém com iPhone ou Android entraria |

## 8A. Como ligar depois do deploy (para quem tem root no Pi)

1. Deploy pelo Guardian (etiqueta `release/N`), conferir `estudos-app-1` saudável.
2. Pela tailnet, abrir `https://<nó>.<tailnet>.ts.net:8443`: a porta aparece com **Cadastrar este aparelho**. Cadastrar e guardar os 8 códigos.
3. Só então ligar a internet: no Pi, `tailscale funnel --bg --https=8443 http://127.0.0.1:8080` (o mesmo alvo do `serve` de hoje), e o atributo
   `funnel` para o nó na política da tailnet. De fora, a porta não oferece o primeiro cadastro.
4. Conferir de fora: `/api/acesso/estado` responde `primeiroAcesso: false`; `/api/hoje` responde 401; `/internal/...` responde 403.

## 9. Riscos

- **O cabeçalho do Funnel e a identidade da tailnet** são comportamento do `tailscale serve`/`funnel`; se uma versão do Tailscale mudar, o
  primeiro cadastro fecha (falha segura) e `/internal` continua com o token. Conferir no Pi depois do deploy.
- **Sem Docker nesta sessão**, os testes de schema (`EstruturaSchemaTest`, `RestricoesInvariantesTest`) não rodam aqui; a suíte roda contra um
  Postgres local.
- **App Android nativo** passa a receber 401.
- **Perder todos os passkeys e códigos** deixa sem acesso (ADR-051), até o convite do Guardian existir.

## Changelog

| Versão | Data | Mudança |
|---|---|---|
| 1.1.0 | 2026-11-01 | Itens 30.1 a 30.6 feitos. 26 testes novos (`AcessoLoginTest`, `AcessoRotasTest`, `FreioDeTentativasTest`, `FreioHttpTest`) e a suíte inteira: 397 de 399 verdes; os 2 de schema exigem Docker, ausente nesta sessão (a lista de tabelas recebeu as três novas). Chromium com autenticador virtual: primeiro cadastro pela tailnet, códigos, Ajustes → Acesso, sair, entrar de fora, código de recuperação num aparelho novo e código repetido recusado, sem erro de página. Achado no caminho: o service worker instalado sem sessão guardaria a porta no lugar do `index.html`; a casca passa a ser buscada com `redirect: 'error'` |
| 1.0.0 | 2026-11-01 | Criado, com o ADR-051 aprovado pelo usuário |
