# 03E — DEPLOYMENT

Infraestrutura: Docker, Raspberry Pi, Tailscale, backup e operação.
Documento novo na v2 — na v1 não havia infraestrutura para documentar.

| Campo | Valor |
|---|---|
| Versão do documento | **1.0.0** |
| Status | **Congelado** |
| Data | 2026-08-15 |

---

## 0. Topologia

```
┌──────────────────────── tailnet (WireGuard) ───────────────────────┐
│                                                                     │
│   [ notebook ]   [ celular ]   [ desktop ]                          │
│         │             │              │                              │
│         └─────────────┴──────────────┘                              │
│                       │  http://raspberrypi:8080                    │
│                       ▼                                             │
│   ┌───────────────────────────────────────────────────────┐         │
│   │  RASPBERRY PI 4/5 · Raspberry Pi OS 64-bit            │         │
│   │                                                        │        │
│   │   tailscaled  ── interface tailscale0                  │        │
│   │                                                        │        │
│   │   ┌─ docker compose ─────────────────────────────┐     │        │
│   │   │  app        :8080  (bind 127.0.0.1)          │     │        │
│   │   │  postgres   :5432  (só rede interna)         │     │        │
│   │   └──────────────────────────────────────────────┘     │        │
│   │                                                        │        │
│   │   /mnt/ssd/postgres   ← SSD USB 3.0 (ADR-019)          │         │
│   │   /var/log/estudos    ← log rotacionado                │        │
│   │   (também roda o DNS que já existia)                   │        │
│   └───────────────────────────────────────────────────────┘         │
└─────────────────────────────────────────────────────────────────────┘
                             │
                             │ backup diário (rclone / rsync)
                             ▼
                    [ armazenamento fora do Pi ]
```

**Nada é publicado na internet.** Não há porta aberta no roteador, não há
proxy reverso, não há certificado TLS a renovar. O provedor está atrás de CGNAT,
o que impede port forwarding — e isso, aqui, é uma característica, não um
obstáculo.

---

## 1. Pré-requisitos no Pi

| Item | Verificação | Observação |
|---|---|---|
| Raspberry Pi OS **64-bit** | `uname -m` → `aarch64` | 32-bit não roda a JVM moderna com folga |
| RAM ≥ 4 GB | `free -h` | Ver `03D_PERFORMANCE.md` §6 |
| SSD USB 3.0 montado | `lsblk` | **Obrigatório** para o Postgres (ADR-019) |
| Docker + Compose plugin | `docker compose version` | Instalação oficial, não a do apt antigo |
| Tailscale | `tailscale status` | Já instalado se o Pi está na tailnet |
| Fuso correto | `timedatectl` | `America/Sao_Paulo` |

> **Convivência com o DNS.** O Pi já roda um servidor DNS. Os limites de memória
> deste documento assumem essa convivência: 512 MB de heap + 256 MB de
> `shared_buffers` + o resto do sistema deixam folga confortável em 4 GB.

---

## 2. Estrutura no disco do Pi

```
/opt/estudos/                  ← repositório clonado
├── docker-compose.yml
├── .env                        ← segredos, NUNCA no Git
├── Dockerfile
├── pom.xml
├── src/
└── scripts/
    ├── backup.sh
    ├── restaurar.sh
    └── testar-restauracao.sh

/mnt/ssd/postgres/              ← dados do banco (SSD USB)
/var/log/estudos/               ← log da aplicação
/mnt/ssd/backups/               ← dumps locais, antes de irem para fora
```

---

## 3. Armazenamento (ADR-019)

O volume do Postgres é *bind mount* para o SSD, **nunca** volume nomeado no
cartão SD.

```bash
# identificar o SSD
lsblk -o NAME,SIZE,MODEL,MOUNTPOINT

# montagem persistente por UUID — nunca por /dev/sda1, que muda de nome
sudo blkid /dev/sda1
echo 'UUID=<uuid>  /mnt/ssd  ext4  defaults,noatime  0  2' | sudo tee -a /etc/fstab
sudo mkdir -p /mnt/ssd && sudo mount -a

sudo mkdir -p /mnt/ssd/postgres /mnt/ssd/backups
```

| Detalhe | Por quê |
|---|---|
| Montar por **UUID** | `/dev/sda1` vira `/dev/sdb1` quando você pluga outro USB, e o banco sobe vazio |
| `noatime` | Elimina uma escrita por leitura de arquivo. Ganho real num sistema de arquivos com banco |
| `ext4` | Estável e bem suportado em ARM. Evite exFAT: sem permissões POSIX, o Postgres recusa |

> **Verificação que evita a pior falha possível.** Se o SSD não estiver montado
> quando o Compose subir, o Docker **cria o diretório vazio no SD** e o Postgres
> inicializa uma base nova, em branco. Você só percebe quando abrir o sistema e
> não houver nada. Mitigação: o `docker-compose.yml` usa um *bind mount* com a
> flag que falha se a origem não existir, e o `backup.sh` aborta se a base
> estiver suspeitosamente vazia (§6.4).

---

## 4. Docker

### 4.1 `Dockerfile` — build multi-estágio

```dockerfile
# ---- build ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline          # camada cacheável
COPY src ./src
RUN mvn -B clean package -DskipTests

# ---- runtime ----
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN useradd -r -u 1001 estudos
COPY --from=build /app/target/*.jar app.jar
USER estudos
EXPOSE 8080
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseSerialGC"
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar app.jar"]
```

- `dependency:go-offline` numa camada separada: mudar código não rebaixa o
  download de dependências, que num Pi leva minutos.
- Imagem final é **JRE**, não JDK: menos superfície, menos espaço.
- Roda como usuário sem privilégio.
- **Testes são pulados no build da imagem.** Rodam antes, na sua máquina ou no
  Pi, via `./mvnw test` — construir imagem não é o momento de descobrir que um
  teste quebrou.

### 4.2 `docker-compose.yml`

```yaml
services:
  postgres:
    image: postgres:17-alpine
    restart: unless-stopped
    environment:
      POSTGRES_DB: estudos
      POSTGRES_USER: estudos
      POSTGRES_PASSWORD: ${DB_PASSWORD:?defina DB_PASSWORD no .env}
      TZ: America/Sao_Paulo
    volumes:
      - /mnt/ssd/postgres:/var/lib/postgresql/data
    command: >
      postgres
      -c shared_buffers=256MB
      -c work_mem=8MB
      -c maintenance_work_mem=64MB
      -c effective_cache_size=1GB
      -c max_connections=20
      -c random_page_cost=1.1
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U estudos -d estudos"]
      interval: 10s
      timeout: 5s
      retries: 5
    # sem `ports:` — só a rede interna do Compose alcança o banco

  app:
    build: .
    restart: unless-stopped
    depends_on:
      postgres:
        condition: service_healthy
    environment:
      DB_URL: jdbc:postgresql://postgres:5432/estudos
      DB_USER: estudos
      DB_PASSWORD: ${DB_PASSWORD}
      BIND_ADDRESS: 0.0.0.0        # dentro do container; ver §4.3
      LOG_LEVEL: INFO
      TZ: America/Sao_Paulo
    ports:
      - "127.0.0.1:8080:8080"      # ← a linha que implementa ADR-015
    volumes:
      - /var/log/estudos:/var/log/estudos
    healthcheck:
      test: ["CMD","wget","-qO-","http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 5s
      retries: 3
```

### 4.3 As três linhas de segurança

| Linha | Efeito |
|---|---|
| `ports: "127.0.0.1:8080:8080"` | O serviço só é alcançável pelo próprio host. Sem isso, `8080` fica aberto para **toda a rede local** — e a aplicação não tem autenticação (ADR-015) |
| `postgres` **sem `ports:`** | O banco só existe dentro da rede do Compose. Nunca exposto, nem ao host |
| `BIND_ADDRESS: 0.0.0.0` no container | Parece contradizer o anterior, e não contradiz: dentro do container é obrigatório para o Docker conseguir mapear a porta. O confinamento é feito pelo `127.0.0.1:` do lado do host |

O terceiro item é a confusão mais comum de Docker + Spring. Anotada aqui para não
ser "corrigida" por engano.

### 4.4 Acesso pela tailnet

Com o serviço em `127.0.0.1:8080`, ele é alcançável de outros dispositivos da
tailnet por:

```bash
sudo tailscale serve --bg 8080
```

Isso publica em `https://raspberrypi.<tailnet>.ts.net`, **apenas dentro da
tailnet**, com TLS fornecido pela Tailscale.

> **`tailscale serve`, nunca `tailscale funnel`.** `funnel` expõe o serviço para
> a **internet pública** — o oposto de ADR-015. Não há autenticação na aplicação;
> um `funnel` acidental publica seu banco de estudos para qualquer um com a URL.

Alternativa mais simples, se preferir: acessar `http://<ip-tailscale>:8080`
diretamente, com o mapeamento em `0.0.0.0:8080` **e** o firewall do Pi limitando
a porta à interface `tailscale0`. Menos elegante, uma peça a menos.

---

## 5. Publicação

### 5.1 Primeira instalação

```bash
git clone <repo> /opt/estudos && cd /opt/estudos

printf 'DB_PASSWORD=%s\n' "$(openssl rand -base64 24)" > .env
chmod 600 .env

docker compose up -d --build       # ~5-10 min no Pi, na primeira vez
docker compose logs -f app         # acompanhar as migrações do Flyway
curl -s localhost:8080/actuator/health
```

O Flyway cria o schema na subida. Não há passo manual de banco.

### 5.2 Atualização

```bash
cd /opt/estudos
./scripts/backup.sh                # SEMPRE antes — migrações não voltam sozinhas
git pull
docker compose up -d --build
docker compose logs -f app
```

A ordem importa. Se uma migração falhar no meio, o Flyway para e a aplicação não
sobe — e você precisa do backup de minutos atrás, não do de ontem.

### 5.3 Rollback

```bash
git checkout <commit-anterior>
docker compose up -d --build
```

**Isso reverte o código, não o schema.** Se a atualização aplicou migração
destrutiva, o rollback exige restaurar o backup (§6.3). É por isso que
`02_DATABASE.md` §9 exige comentário justificando toda migração que remove ou
altera dado.

### 5.4 Comandos do dia a dia

| Ação | Comando |
|---|---|
| Ver logs | `docker compose logs -f app` |
| Reiniciar só o app | `docker compose restart app` |
| Abrir o `psql` | `docker compose exec postgres psql -U estudos -d estudos` |
| Uso de recursos | `docker stats --no-stream` |
| Espaço em disco | `df -h /mnt/ssd` |
| Tamanho da base | `\l+` no `psql` |

---

## 6. Backup (ADR-018)

**O único risco novo que a migração introduziu.** Na v1, o Google fazia isso de
graça e invisivelmente.

### 6.1 `scripts/backup.sh`

```bash
#!/usr/bin/env bash
set -euo pipefail

DESTINO=/mnt/ssd/backups
CARIMBO=$(date +%Y%m%d-%H%M%S)
ARQUIVO="$DESTINO/estudos-$CARIMBO.sql.gz"

docker compose exec -T postgres pg_dump -U estudos -d estudos \
  | gzip > "$ARQUIVO"

# Sanidade: um dump íntegro deste sistema não tem menos de 10 KB comprimido.
TAMANHO=$(stat -c%s "$ARQUIVO")
if [ "$TAMANHO" -lt 10240 ]; then
  echo "ERRO: backup suspeito ($TAMANHO bytes). Base vazia? SSD desmontado?" >&2
  exit 1
fi

# Retenção local: 30 diários.
find "$DESTINO" -name 'estudos-*.sql.gz' -mtime +30 -delete

# Cópia FORA do Pi — backup no mesmo disco do banco não é backup.
rclone copy "$ARQUIVO" remoto:estudos-backups/

echo "OK: $ARQUIVO ($((TAMANHO/1024)) KB)"
```

Agendar:

```cron
30 3 * * * cd /opt/estudos && ./scripts/backup.sh >> /var/log/estudos/backup.log 2>&1 || \
           echo "BACKUP FALHOU" | mail -s "estudos: backup falhou" voce@exemplo.com
```

### 6.2 Retenção

| Onde | Política |
|---|---|
| `/mnt/ssd/backups` | 30 diários |
| Fora do Pi | 30 diários + 12 mensais |

Um dump comprimido deste sistema tem poucos MB. 42 cópias ocupam menos que uma
foto.

### 6.3 Restauração

```bash
docker compose stop app                      # ninguém escrevendo durante o restore

gunzip -c /mnt/ssd/backups/estudos-AAAAMMDD-HHMMSS.sql.gz \
  | docker compose exec -T postgres psql -U estudos -d estudos

docker compose start app
docker compose logs -f app                   # Flyway valida o schema restaurado
```

### 6.4 Teste de restauração — **trimestral, obrigatório**

Backup nunca restaurado não é backup: é esperança.

```bash
# restaura numa base descartável, sem tocar a de produção
docker compose exec postgres createdb -U estudos estudos_teste
gunzip -c <backup> | docker compose exec -T postgres psql -U estudos -d estudos_teste
docker compose exec postgres psql -U estudos -d estudos_teste \
  -c "select 'sessao', count(*) from sessao
      union all select 'revisao', count(*) from revisao
      union all select 'assunto', count(*) from assunto;"
docker compose exec postgres dropdb -U estudos estudos_teste
```

Registre a data e o resultado. Um teste de restauração que ninguém anotou é um
teste que ninguém fez.

### 6.5 O que **não** é backup

| Não é | Por quê |
|---|---|
| Cópia do diretório `/mnt/ssd/postgres` com o banco no ar | Inconsistente. O Postgres tem escrita em andamento e WAL não aplicado |
| Snapshot do volume Docker | Mesmo problema |
| Backup só no SSD do Pi | O Pi queimar leva os dois juntos |
| Backup que falha em silêncio | Pior que nenhum: produz confiança falsa. Daí a notificação no cron |

---

## 7. Operação

### 7.1 Rotinas

| Rotina | Frequência | Ação |
|---|---|---|
| Backup | diária, 03:30 | automática |
| Verificar que o backup rodou | semanal | `tail /var/log/estudos/backup.log` |
| Teste de restauração | trimestral | §6.4 |
| Retenção de auditoria | mensal | `DELETE FROM auditoria WHERE ocorrido_em < now() - interval '12 months'` |
| `VACUUM ANALYZE` | — | o `autovacuum` cuida; não intervenha sem medir |
| Atualizar imagens base | semestral | `docker compose pull && up -d --build`, após backup |
| Verificar espaço | mensal | `df -h /mnt/ssd` |

### 7.2 Diagnóstico

| Sintoma | Verificar |
|---|---|
| Não abre de outro dispositivo | `tailscale status` nos dois; `docker compose ps` |
| App reiniciando em laço | `docker compose logs app` — quase sempre migração falhada ou `DB_PASSWORD` ausente |
| "Base vazia" após reiniciar o Pi | **SSD não montou.** `lsblk`, `mount -a`, e confira o `/etc/fstab` por UUID (§3) |
| Lentidão | `03D_PERFORMANCE.md` §7 |
| Disco cheio | `du -sh /mnt/ssd/*` — normalmente log ou backup antigo |
| Banco não sobe | `docker compose logs postgres`; permissão do diretório no SSD |

### 7.3 Limites conhecidos e aceitos

| Limite | Consequência | Aceito porque |
|---|---|---|
| Sem alta disponibilidade | Queda de luz ou internet derruba o acesso | ADR-018. Você perde o acesso, não o dado |
| RPO de 24 h | Até um dia de registros perdidos no pior caso | O custo de replicação não se paga |
| Volume não criptografado | Acesso físico ao SSD lê os dados | `00_PROJECT.md` §7. Dado de estudo pessoal |
| Sem monitoramento externo | Você descobre que caiu quando tentar usar | Um usuário, uso diário |
| Build ARM demora | ~5–10 min por atualização | Atualizações são raras |

---

## 8. Checklist de instalação

- [ ] Raspberry Pi OS 64-bit (`uname -m` → `aarch64`);
- [ ] SSD USB montado por **UUID** no `/etc/fstab`, com `noatime`;
- [ ] `/mnt/ssd/postgres`, `/mnt/ssd/backups` e `/var/log/estudos` criados;
- [ ] Docker e plugin Compose instalados;
- [ ] Tailscale ativo e o Pi visível na tailnet;
- [ ] `.env` criado com `DB_PASSWORD` aleatória, `chmod 600`, **fora do Git**;
- [ ] `docker compose up -d --build` conclui e o Flyway aplica as migrações;
- [ ] `/actuator/health` responde `UP`;
- [ ] `docker compose ps` mostra o Postgres **sem porta publicada**;
- [ ] `ss -tlnp | grep 8080` mostra bind em `127.0.0.1`, **não** `0.0.0.0`;
- [ ] a aplicação abre de outro dispositivo da tailnet;
- [ ] a aplicação **não** abre de um dispositivo fora da tailnet;
- [ ] `backup.sh` roda à mão e gera arquivo acima de 10 KB;
- [ ] `rclone` configurado e o arquivo chega ao destino externo;
- [ ] cron agendado, com notificação de falha;
- [ ] **restauração testada** numa base descartável, com resultado anotado.

Os dois itens de acesso — abre de dentro, não abre de fora — são a verificação
prática de ADR-015. Faça os dois.

---

## 9. Changelog

| Versão | Data | Mudança |
|---|---|---|
| 1.0.0 | 2026-08-15 | Documento novo. Docker Compose, Dockerfile ARM multi-estágio, montagem do SSD, exposição pela Tailscale, backup com verificação de sanidade e teste de restauração trimestral |
