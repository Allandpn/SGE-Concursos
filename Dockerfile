# =============================================================================
# Build multi-estágio para ARM64 (Raspberry Pi 4/5).
# Fonte: 03E_DEPLOYMENT.md §4.1
# =============================================================================

# ---- estágio de build -------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Camada separada e cacheável: mudar código não rebaixa o download das
# dependências, que num Pi leva minutos.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src

# Testes são PULADOS aqui de propósito. Eles rodam antes, via `mvn test`, e
# exigem Docker (Testcontainers) — construir imagem não é o momento de
# descobrir que um teste quebrou, nem de aninhar contêineres.
RUN mvn -B clean package -DskipTests


# ---- estágio de runtime -----------------------------------------------------
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# ADR-045: a IA pela assinatura do Claude roda o Claude Code (`claude -p`) neste
# contêiner. Repositório apt assinado da Anthropic, canal stable; a chave é
# conferida pela impressão digital publicada. Sem atualização automática: a versão
# muda quando a imagem é reconstruída.
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl gnupg ca-certificates \
 && install -d -m 0755 /etc/apt/keyrings \
 && curl -fsSL https://downloads.claude.ai/keys/claude-code.asc -o /etc/apt/keyrings/claude-code.asc \
 && gpg --show-keys --with-colons /etc/apt/keyrings/claude-code.asc | grep -q 31DDDE24DDFAB679F42D7BD2BAA929FF1A7ECACE \
 && echo "deb [signed-by=/etc/apt/keyrings/claude-code.asc] https://downloads.claude.ai/claude-code/apt/stable stable main" \
      > /etc/apt/sources.list.d/claude-code.list \
 && apt-get update \
 && apt-get install -y --no-install-recommends claude-code \
 && apt-get purge -y gnupg && apt-get autoremove -y \
 && rm -rf /var/lib/apt/lists/*
ENV DISABLE_AUTOUPDATER=1

# Usuário sem privilégio.
RUN useradd --system --uid 1001 --create-home estudos \
 && mkdir -p /var/log/estudos \
 && chown -R estudos:estudos /var/log/estudos /app

COPY --from=build --chown=estudos:estudos /app/target/*.jar app.jar

USER estudos
EXPOSE 8080

# 512 MB de heap é folgado para este sistema; mais que isso só aumenta a pausa
# de GC. SerialGC tem menos sobrecarga que G1 com heap pequeno e poucos núcleos.
# Ver 03D_PERFORMANCE.md §6.1.
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseSerialGC"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
