# syntax=docker/dockerfile:1

# ---- Build ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copiar primero el descriptor para cachear las dependencias entre builds.
COPY pom.xml .
RUN mvn -B -DskipTests dependency:go-offline

# Compilar la aplicación.
COPY src ./src
RUN mvn -B -DskipTests package

# ---- Run ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Usuario sin privilegios.
RUN addgroup -S citero && adduser -S citero -G citero
USER citero

COPY --from=build --chown=citero:citero /app/target/*.jar app.jar

EXPOSE 8080

ENV JAVA_OPTS=""

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD wget -qO- "http://localhost:${PORT:-8080}/actuator/health" || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
