# Testes manuais de endpoint (IntelliJ HTTP Client)

Um arquivo `.http` por recurso, na ordem em que os dados dependem uns dos
outros. Abra qualquer um no IntelliJ Ultimate (ele reconhece `.http` nativamente)
e clique no ícone de "run" ao lado de cada `###`.

## Antes de rodar

O ambiente selecionado no canto superior direito do editor precisa ser **dev**
(`http-client.env.json`, `baseUrl = http://localhost:8080`).

A aplicação precisa estar de pé contra um Postgres real (nunca H2 — ADR-017).
`docker-compose.yml` da raiz é para o Raspberry Pi (bind mount `/mnt/ssd/postgres`,
não existe neste ambiente) — para rodar localmente, suba um Postgres descartável
e aponte as variáveis de ambiente que `application.yml` exige:

```
docker run --rm -d --name estudos-dev -p 5432:5432 \
  -e POSTGRES_DB=estudos -e POSTGRES_USER=estudos -e POSTGRES_PASSWORD=estudos \
  postgres:17-alpine

# num terminal com essas variáveis no ambiente:
DB_URL=jdbc:postgresql://localhost:5432/estudos
DB_USER=estudos
DB_PASSWORD=estudos
mvn spring-boot:run
```

Flyway aplica as 8 migrações sozinho na subida. Pra recomeçar do zero, derrube
o container (`docker rm -f estudos-dev`) e suba de novo — schema descartável.

## Ordem recomendada

Os arquivos são numerados na ordem de dependência de dados, não de sprint:

| Arquivo | Cobre |
|---|---|
| `01-disciplinas.http` | CRUD de disciplina |
| `02-assuntos.http` | CRUD de assunto (precisa de uma disciplina) |
| `03-importacao-exportacao.http` | CSV — `validar`/`confirmar`/exportação (veja layout abaixo) |
| `04-sessoes-e-revisoes.http` | Os 4 tipos de sessão, a escada, D-09 (lote mínimo) |
| `05-erros.http` | Banco de erros |
| `06-frente.http` | Frente/backlog/consolidado, D-31 |
| `07-turno.http` | Plano de turno (fila + bloco) |
| `08-metricas.http` | M-1 a M-4 |
| `09-simulados.http` | Simulado, tudo-ou-nada |

`01`, `02` e `04` capturam id em variáveis globais (`client.global.set`) que os
arquivos seguintes reaproveitam — rode nessa ordem na primeira vez. Depois,
qualquer request isolado pode rodar de novo sozinho (ex.: repetir um `POST` de
sessão pra ver D-45 devolver o mesmo `id`).

## Layout do CSV (importação/exportação de assunto)

Fonte: `02_JORNADAS.md` linhas 124-158 (dono do formato — `03_INVARIANTES`
nunca redefine, só cita). `03-importacao-exportacao.http` referencia os
arquivos de `csv/`.

```csv
disciplina,assunto,peso,ordem
Banco de Dados,Modelagem Conceitual de Dados (MER e DER),ALTO,1
Banco de Dados,Modelagem Lógica e Física de Dados,ALTO,2
```

| Coluna | Obrigatória | Regra |
|---|---|---|
| `id` | não | Vazio → cria. Preenchido → atualiza aquele registro. Inexistente → **recusa o arquivo inteiro** |
| `disciplina` | sim | Criada se não existir, com `peso = MEDIO` (CSV é por assunto, sem coluna de peso de disciplina) |
| `assunto` | sim | Único dentro da disciplina, comparado sem acento e sem caixa (`unaccent_imutavel`, J-1) |
| `peso` | não | `ALTO` \| `MEDIO` \| `BAIXO` (masculino). Ausente → `MEDIO` |
| `ordem` | não | Inteiro. Ausente → ordem de aparição no arquivo, por disciplina |
| `dificuldadePercebida` | não | Inteiro 1..5. Ausente → `3` |

A ordem das colunas no arquivo **não importa** — o parser lê por nome de
cabeçalho (`commons-csv`, `.setHeader().setSkipHeaderRecord(true)`), não por
posição. A exportação (`GET /api/assuntos/exportacao`) sempre devolve nesta
ordem: `id,disciplina,assunto,peso,ordem,dificuldadePercebida`.

Regra 1 (tudo ou nada): qualquer linha recusada — por `id` inexistente ou por
qualquer outro motivo (`peso` fora do enum, por exemplo) — recusa o **arquivo
inteiro**. Não existe importação parcial. `validar` nunca grava (força
rollback mesmo sem erro); `confirmar` grava só se `recusadas` vier vazio.

## Formato de erro

Todo erro de domínio vem como `application/problem+json` (RFC 9457,
ADR-026), com dois campos extras: `codigo` (o identificador estável que os
testes automatizados conferem) e `requestId` (mesmo valor do header
`X-Request-Id` da resposta — correlaciona com o log).
