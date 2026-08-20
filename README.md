# Gestão de Estudos para Concursos

Sistema pessoal de gestão de estudos: registra sessões, agenda revisões
espaçadas automaticamente, classifica erros e recomenda o que estudar.

Roda num Raspberry Pi, acessado pela Tailscale. Sem exposição pública, sem
autenticação na aplicação — a rede WireGuard **é** a autenticação (ADR-015).

| | |
|---|---|
| Backend | Java 21 · Spring Boot 4.1 · PostgreSQL 17 |
| Frontend | HTML + Tailwind + Alpine.js, estático (a partir da Sprint 3) |
| Infra | Docker Compose no Raspberry Pi 4/5 · Tailscale |
| Especificação | `docs/` — 20 documentos, fonte única de verdade |

> **O código é consequência da documentação, nunca o contrário.**
> Se algo não está em `docs/`, não deve ser implementado. Se está, deve ser
> implementado exatamente como está. Divergência se resolve editando o
> documento **primeiro**.
>
> Comece por `docs/00_PROJECT.md` §1.

---

## Estado: Sprint 1 concluída

A Sprint 1 entrega **ambiente, schema e migrações**. Ainda não há entidade,
repositório, service, endpoint nem interface — é deliberado
(`docs/08_SPRINTS.md`, Sprint 1, "Fora de escopo").

O que já está garantido, **antes de existir uma linha de regra de negócio**:

| Invariante | Garantida por |
|---|---|
| INV-001 IDs únicos | `PRIMARY KEY` sobre `GENERATED ALWAYS AS IDENTITY` |
| INV-002 FKs válidas | 6 `FOREIGN KEY` |
| INV-003 nomes únicos | `ux_disciplina_nome`, `ux_assunto_nome` (por expressão) |
| INV-005 valores de sessão coerentes | `ck_sessao_tempo`, `ck_sessao_questoes`, `ck_sessao_flashcards` |
| INV-007 uma revisão pendente por assunto | `ux_revisao_pendente_por_assunto` (único **parcial**) |
| INV-012 revisão concluída tem data | `ck_revisao_realizada` (bicondicional) |
| INV-013 assunto concluído tem data | `ck_assunto_concluido` (bicondicional) |
| INV-014 erro resolvido tem data | `ck_erro_resolucao` (bicondicional) |

Total no schema: **7 tabelas · 25 constraints nomeadas · 17 índices ·
2 funções `IMMUTABLE` · 21 parâmetros · 0 triggers**.

---

## Subir pela primeira vez

Pré-requisitos no Pi em `docs/03E_DEPLOYMENT.md` §1. O item que mais causa
problema é o SSD.

```bash
git clone <repo> /opt/estudos && cd /opt/estudos

# senha do banco — sem ela a aplicação não sobe, de propósito
printf 'DB_PASSWORD=%s\n' "$(openssl rand -base64 24)" > .env
chmod 600 .env

# confira que o SSD está montado ANTES de subir (ver aviso abaixo)
lsblk | grep /mnt/ssd

docker compose up -d --build      # ~5-10 min no Pi, na primeira vez
docker compose logs -f app        # acompanhar as migrações do Flyway

curl -s localhost:8080/actuator/health
```

> ### ⚠️ O erro mais caro possível
> Se o SSD **não** estiver montado quando o Compose subir, o Docker cria
> `/mnt/ssd/postgres` vazio **no cartão SD** e o Postgres inicializa uma base
> em branco. Falha silenciosa: você só percebe quando abrir o sistema e não
> houver nada.
>
> Mitigações: montagem por **UUID** no `/etc/fstab`
> (`docs/03E_DEPLOYMENT.md` §3) e a verificação de sanidade do `backup.sh`,
> que aborta se o dump vier suspeito de pequeno.

### Acesso pela tailnet

```bash
sudo tailscale serve --bg 8080
```

Publica em `https://raspberrypi.<tailnet>.ts.net`, **apenas dentro da
tailnet**, com TLS da Tailscale.

> **`tailscale serve`, nunca `tailscale funnel`.** `funnel` expõe o serviço
> para a internet pública — o oposto de ADR-015. A aplicação não tem
> autenticação; um `funnel` acidental publica seu banco para qualquer um com
> a URL.

---

## Comandos do dia a dia

| Ação | Comando |
|---|---|
| Logs | `docker compose logs -f app` |
| Reiniciar o app | `docker compose restart app` |
| Abrir o `psql` | `docker compose exec postgres psql -U estudos -d estudos` |
| Verificar o schema | `docker compose exec app java -jar app.jar --verificar-schema` |
| Recursos | `docker stats --no-stream` |
| Espaço | `df -h /mnt/ssd` |

### Atualizar

```bash
./scripts/backup.sh          # SEMPRE antes — migrações não voltam sozinhas
git pull
docker compose up -d --build
docker compose logs -f app
```

A ordem importa: se uma migração falhar no meio, o Flyway para, a aplicação
não sobe, e você precisa do backup de minutos atrás — não do de ontem.

### Backup

```bash
./scripts/backup.sh                    # diário, via cron
./scripts/testar-restauracao.sh        # TRIMESTRAL, obrigatório
./scripts/restaurar.sh <arquivo.gz>    # destrutivo, pede confirmação
```

Agendamento e política de retenção em `docs/03E_DEPLOYMENT.md` §6.

> **Backup nunca restaurado não é backup: é esperança.** É a única salvaguarda
> do sistema sem teste automatizado. Rode o teste trimestral e **anote a
> data** — teste de restauração que ninguém anotou é teste que ninguém fez.

---

## Desenvolvimento

```bash
mvn test                  # tudo (exige Docker, por causa do Testcontainers)
mvn test -Psem-docker     # pula os testes de integração
mvn spring-boot:run       # local, contra um Postgres na porta 5432
```

Os testes de integração rodam contra um **PostgreSQL de verdade**, com **as
mesmas migrações** de produção. Não existe schema de teste
(`docs/10_TESTS.md` §1).

Eles **falham** quando o Docker não está disponível, em vez de serem pulados
em silêncio — teste pulado sem aviso é teste que não existe. Use
`-Psem-docker` quando isso for intencional.

> O projeto ainda não inclui o wrapper Maven. Para gerá-lo:
> `mvn wrapper:wrapper` — depois disso, use `./mvnw` no lugar de `mvn`, como
> a documentação assume.

---

## Estrutura

```
├── docs/                       ← especificação: fonte única de verdade
│   └── _apps-script-v1/        ← a v1 (Google Sheets), preservada
├── src/main/
│   ├── java/br/com/estudos/
│   │   ├── EstudosApplication.java
│   │   └── shared/schema/      ← VerificadorSchema
│   └── resources/
│       ├── application.yml
│       └── db/migration/       ← V1–V4
├── src/test/java/br/com/estudos/
│   ├── TesteComBanco.java      ← base Testcontainers
│   ├── schema/SchemaTest.java
│   └── invariantes/InvariantesDeBancoTest.java
├── scripts/                    ← backup, restauração, teste de restauração
├── Dockerfile
├── docker-compose.yml
└── pom.xml
```

---

## Três coisas que parecem erro e não são

**1. `BIND_ADDRESS: 0.0.0.0` no compose, com `server.address: 127.0.0.1` no
`application.yml`.** Dentro do contêiner o bind precisa ser `0.0.0.0` para o
Docker mapear a porta; o confinamento vem do `127.0.0.1:8080:8080` do lado do
host. Não "corrija" nenhum dos dois (`docs/03E_DEPLOYMENT.md` §4.3).

**2. `unaccent_imutavel`, um wrapper de uma função que já existe.**
`unaccent()` é `STABLE`, não `IMMUTABLE`, e índice por expressão exige
`IMMUTABLE`. Sem o wrapper, o `CREATE INDEX` falha
(`docs/02_DATABASE.md` §3).

**3. Lacunas na numeração dos ids.** A sequence avança mesmo em `INSERT`
recusado por constraint. É comportamento normal do PostgreSQL, e nada no
sistema depende de ids contíguos (ADR-023).

---

## Próxima sprint

**Sprint 2 — Backend: CRUD e serviços.** 14 endpoints (`/api/disciplinas`,
`/api/assuntos`, `/api/sessoes` parcial), tratamento de erro em RFC 9457,
trilha de auditoria. Ordem estrita em `docs/03_BACKEND.md` §9.
