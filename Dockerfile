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
