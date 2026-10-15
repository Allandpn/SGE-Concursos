# Sprint 18 · Concurso-alvo, disciplinas e edital novo por IA

| Campo | Valor |
|---|---|
| Versão | 1.0.0 |
| Data | 2026-10-15 |
| Status | Vigente |
| Especificação | `01_DOMINIO` v1.23.0 (§3.8, §3.9, D-58 a D-63) · `02_JORNADAS` v1.9.0 (J-5, §4.4, §4.5, J-1) · `03_INVARIANTES` v1.11.0 · `00A_ADR` ADR-042, ADR-043 |

Pedido do usuário (2026-10-15), depois dos wireframes do app: ver as
disciplinas, ver o concurso que está estudando e o histórico de editais, e
subir edital novo — este último lido por IA via OpenRouter, separando e
classificando os assuntos conforme o cargo escolhido.

**Dentro:** entidade `Concurso` com cobertura; cabeçalho em Hoje; resumo por
disciplina; tela Concursos com histórico e reativação; análise de edital
(PDF → cargos → proposta → confirmação); registro do concurso na importação
do planejamento; adoção de assunto sem chave externa na importação.

**Fora:** diagnóstico de edital (retenção × cobertura) e modo reta final —
continuam no mapa, agora com a base pronta; edição livre de nome de assunto
na revisão da proposta (desmarcar e corrigir depois em Assuntos resolve);
dividir/juntar assuntos propostos.

---

## 1. Banco — `V12__concurso_e_analise_edital.sql`

```sql
concurso (
  id, nome NOT NULL, cargo, banca, data_prova DATE,
  origem NOT NULL CHECK IN ('ANALISE_IA','PLANEJAMENTO'),
  slug UNIQUE,                       -- pasta do planejamento; NULL na análise por IA
  ativo BOOLEAN NOT NULL DEFAULT false,
  ativado_em, desativado_em, criado_em
)
ux_concurso_d58_um_ativo  UNIQUE (ativo) WHERE ativo      -- D-58

concurso_assunto (                   -- cobertura do edital (01_DOMINIO §3.8)
  concurso_id FK, assunto_id FK, disciplina_id FK,
  peso NOT NULL CHECK, ordem NOT NULL,
  UNIQUE (concurso_id, assunto_id)
)

analise_edital (
  id, nome_arquivo NOT NULL, texto TEXT NOT NULL,
  status NOT NULL CHECK IN ('LENDO_EDITAL','ESCOLHER_CARGO','SEPARANDO_ASSUNTOS',
                            'PRONTA','CONFIRMADA','FALHOU'),
  etapa_falha,                       -- qual etapa repetir em "tentar de novo"
  erro, modelo,
  concurso_nome, banca, data_prova, cargos TEXT (JSON),
  cargo_escolhido, proposta TEXT (JSON),
  concurso_id FK NULL, criado_em, atualizado_em
)
```

JSON como `TEXT`: o servidor é o único leitor, e o formato é o do record Java
serializado pelo Jackson — não há consulta dentro dele.

## 2. Endpoints

| Método | Caminho | Faz |
|---|---|---|
| GET | `/api/concursos` | Histórico, ativo primeiro, depois por `ativadoEm` decrescente. Cada um com `totalAssuntos` e `emComumComAtivo` |
| GET | `/api/concursos/ativo` | O concurso-alvo, com `diasParaProva` (calculado no servidor, data de hoje do servidor); **204** sem concurso ativo |
| POST | `/api/concursos/{id}/ativar` | Reativa um concurso do histórico — aplica a cobertura dele (D-59, D-60) |
| GET | `/api/disciplinas/resumo` | Por disciplina ativa: `id, nome, peso, backlog, frente, consolidados, total` (contagem das fases derivadas, §4.5) |
| GET | `/api/editais/ia` | `{disponivel, motivo, modelo}` — sem chave configurada, `disponivel=false` com o motivo |
| POST | `/api/editais/analises` | Multipart `arquivo` (PDF, até 30 MB). Extrai o texto e começa a etapa 1. **202** com a análise |
| GET | `/api/editais/analises` | As 10 mais recentes (para retomar) |
| GET | `/api/editais/analises/{id}` | Estado atual; com `status=PRONTA` traz a proposta e o resumo |
| POST | `/api/editais/analises/{id}/cargo` | `{cargo}` — só em `ESCOLHER_CARGO`; começa a etapa 2. **202** |
| POST | `/api/editais/analises/{id}/tentar-de-novo` | Só em `FALHOU`; repete a etapa que falhou com o texto já guardado. **202** |
| POST | `/api/editais/analises/{id}/confirmar` | Só em `PRONTA`. Corpo: a proposta revisada (abaixo). Cria, aplica e ativa (D-62). **200** com o concurso |

Corpo de `confirmar`:

```json
{
  "nome": "SEFAZ SC 2026", "cargo": "Auditor — TI", "banca": "FGV",
  "dataProva": "2026-12-07",
  "disciplinas": [
    { "nome": "Banco de Dados", "disciplinaExistenteId": 1, "peso": "ALTO",
      "assuntos": [
        { "nome": "Normalização", "assuntoExistenteId": 4 },
        { "nome": "Data Warehouse", "assuntoExistenteId": null }
      ] }
  ]
}
```

Assunto desmarcado na revisão simplesmente não vai no corpo. `peso` da
disciplina é o que a tela mostra (calculado, ou trocado pelo usuário).
`assuntoExistenteId` revalidado no servidor (D-63) — o corpo não é confiado.

Erros (`problem+json`, ADR-026): `IA_INDISPONIVEL` (503), `PDF_SEM_TEXTO` (422),
`ARQUIVO_NAO_PDF` (422), `ETAPA_INVALIDA` (409, ação fora do estado certo),
`PROPOSTA_VAZIA` (422), e as de `AssuntoService` traduzidas (nome duplicado
na mesma disciplina, por exemplo).

## 3. Aplicar uma cobertura (D-60)

Mesma rotina na confirmação da análise, na reativação e na importação do
planejamento. Entrada: lista de `(assunto, disciplina, peso, ordem)`.

1. Por disciplina tocada: os assuntos cobertos ficam com `ordem` 1..n na
   ordem do edital; os **ativos não cobertos** daquela disciplina vão para
   `n+1…`, na ordem em que já estavam.
2. Como D-48 (ordem única entre ativos) vale a cada escrita, todo assunto cuja
   ordem ou disciplina vai mudar recebe antes uma ordem provisória `−id`
   (mesma técnica de `ImportacaoAssuntoService`, Sprint 16).
3. Grava peso, disciplina e ordem finais. Peso da disciplina também.
4. Desativa o concurso ativo (`desativado_em = now`) **antes** de ativar o
   novo — o índice parcial de D-58 recusaria dois ativos no meio do caminho.
5. Grava a cobertura em `concurso_assunto`.

Escada, sessões, revisões e erros não aparecem na rotina — é isso que D-60
promete, e o teste confere que continuam idênticos.

## 4. A análise (ADR-043)

### 4.1 Estados

```
POST analises ─► LENDO_EDITAL ─(IA etapa 1)─► ESCOLHER_CARGO
                      │                            │ POST cargo
                      ▼                            ▼
                   FALHOU ◄──────────────── SEPARANDO_ASSUNTOS ─(IA etapa 2)─► PRONTA ─► CONFIRMADA
                      │ tentar-de-novo (repete a etapa guardada em etapa_falha)
```

Processamento num executor de **uma thread** (uma análise por vez no Pi). Ao
subir, o servidor marca como `FALHOU` ("interrompida por reinício do
servidor") toda análise que estava em `LENDO_EDITAL` ou
`SEPARANDO_ASSUNTOS`.

### 4.2 Extração do texto

Apache PDFBox 3, `PDFTextStripper`, páginas em ordem. Menos de 500 caracteres
úteis → `PDF_SEM_TEXTO`. Texto acima de 600 mil caracteres é cortado, com
registro no log — o limite cobre editais de ~300 páginas.

### 4.3 Chamada ao OpenRouter

`POST {base}/chat/completions`, `Authorization: Bearer {OPENROUTER_API_KEY}`,
corpo `{model, messages:[system, user], temperature: 0,
response_format:{type:"json_object"}}`. Timeout de leitura: 5 minutos. Da
resposta, lê `choices[0].message.content`, tira cercas de código e pega do
primeiro `{` ao último `}`. JSON que não bate com o formato esperado →
`FALHOU` com "A IA respondeu fora do formato esperado", e "tentar de novo".

Configuração (`application.yml`, variáveis no `.env` do Pi):

| Propriedade | Variável | Padrão |
|---|---|---|
| `estudos.ia.chave` | `OPENROUTER_API_KEY` | vazio → indisponível |
| `estudos.ia.modelo` | `OPENROUTER_MODEL` | `anthropic/claude-sonnet-5.5` |
| `estudos.ia.url-base` | `OPENROUTER_URL` | `https://openrouter.ai/api/v1` |

### 4.4 Etapa 1 — concurso e cargos

Resposta esperada:

```json
{ "concurso": "SEFAZ SC 2026", "banca": "FGV", "dataProva": "2026-12-07",
  "cargos": ["Auditor Fiscal da Receita Estadual — Tecnologia da Informação", "..."] }
```

`dataProva` só se o edital fixa uma data (`AAAA-MM-DD`), senão `null`. Lista
de cargos vazia → falha com "nenhum cargo identificado".

### 4.5 Etapa 2 — disciplinas e assuntos do cargo

Entrada extra no prompt: o cargo escolhido, e a base atual como lista
`{id, nome, disciplina}` de assuntos ativos e `{id, nome}` de disciplinas
ativas. Resposta esperada:

```json
{ "disciplinas": [
  { "nome": "Banco de Dados", "questoes": 12, "disciplinaExistenteId": 1,
    "assuntos": [ { "nome": "Normalização", "assuntoExistenteId": 4 } ] } ] }
```

Instruções fixas no prompt: só o conteúdo programático que vale para o
cargo escolhido (inclui conhecimentos gerais/básicos que o edital aplica a
ele); um assunto por tópico, na ordem do edital, separando itens agrupados
só quando são conteúdos distintos; nome fiel ao edital, até 250 caracteres;
`questoes` = número de questões daquela disciplina para o cargo no quadro de
provas, `null` se o edital não informa — **nunca estimar**; correspondência
só quando é claramente o mesmo conteúdo.

O servidor então: calcula o peso (D-61, `PesoPorQuestoes`); descarta
correspondência com id fora da lista enviada ou de assunto arquivado (D-63);
descarta disciplina sem assunto; corta nomes acima de 250; remove assunto
repetido dentro da mesma disciplina (comparação sem acento e sem caixa).

### 4.6 Resumo da proposta

Calculado na leitura (derivado, não guardado): `novos` (sem correspondência),
`emComum` (com correspondência), `foraDoEdital` (assuntos ativos que nenhuma
correspondência cita — continuam como estão).

## 5. Importação do planejamento

- `POST /api/assuntos/importacoes/confirmar` aceita uma parte opcional
  `concurso` (o `concurso.json`: `slug`, `nome`, `dataProva`). Com ela, e sem
  linha recusada, a confirmação registra (ou reencontra pelo `slug`) o
  concurso de origem `PLANEJAMENTO`, grava a cobertura com os assuntos do
  arquivo e o ativa — pela mesma rotina de §3.
- `scripts/importar-segmentos.sh` envia `-F concurso=@concurso.json` quando o
  arquivo existe na pasta.
- **Ordem ocupada por assunto fora do arquivo** (J-1): sai do caminho com
  ordem provisória e vai para o fim da disciplina. Antes, trocar de edital
  pelo planejamento recusava o arquivo inteiro por D-48 sempre que um assunto
  do edital antigo ocupava a ordem que o novo pedia.
- **Adoção** (J-1): linha sem `id`, com `chaveExterna` desconhecida, e assunto
  da mesma disciplina **sem** chave externa e com o mesmo nome normalizado →
  atualiza esse assunto e grava a chave nele.

## 6. Telas (SPA e app)

- **Hoje:** linha do concurso-alvo no topo (§4.4 de `02_JORNADAS`), toque abre
  Concursos.
- **Assuntos:** cartões de disciplina (peso, backlog/frente/consolidados), toque
  expande os assuntos.
- **Concursos** (nova, a partir de Hoje e de Ajustes): concurso-alvo, histórico
  com "Reativar" (com confirmação), botão "Novo edital" — indisponível com o
  motivo quando `/api/editais/ia` diz que não há chave.
- **Novo edital:** escolher PDF → "Lendo o edital…" (consulta a cada 3 s) →
  lista de cargos → "Separando os assuntos…" → revisão (resumo; por disciplina:
  questões, peso trocável, "peso não determinado"; por assunto: caixa de
  incluir, "novo" ou "já existe: X", "tratar como novo"; nome, banca, data da
  prova editáveis) → "Confirmar e ativar".

## 7. Testes

- `ConcursoTest`: D-58 (escrita do segundo ativo falha), D-59 (histórico,
  período), D-60 (peso/ordem aplicados, fora do edital depois, escada e sessões
  intactas), reativação, `diasParaProva`, 204 sem ativo.
- `PesoPorQuestoesTest` (unitário): tercis, empates, sem contagem.
- `AnaliseEditalTest`: com um servidor HTTP falso no lugar do OpenRouter e PDF
  gerado no teste — fluxo completo até `CONFIRMADA`; D-62 (nada criado antes
  de confirmar); D-63 (id inexistente vira novo); `PDF_SEM_TEXTO`; resposta
  fora do formato → `FALHOU` → tentar de novo; sem chave → `IA_INDISPONIVEL`.
- `ImportacaoAssuntoTest`: adoção; `concurso.json` registra e ativa.
- `DisciplinaResumoTest`: contagem por fase.
