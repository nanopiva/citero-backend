# syntax=docker/dockerfile:1

# ---- Build ----
# Imagen base fijada por digest (inmutable); la etiqueta queda como referencia legible.
FROM maven:3.9-eclipse-temurin-21@sha256:99e61abcff91a9b1333463bd8451fb18495d6eba9250ac66a338b518f8278320 AS build
WORKDIR /app

# Copiar primero el descriptor para cachear las dependencias entre builds.
COPY pom.xml .
RUN mvn -B -DskipTests dependency:go-offline

# Compilar la aplicación.
COPY src ./src
RUN mvn -B -DskipTests package

# ---- Run ----
FROM eclipse-temurin:21-jre-alpine@sha256:51ab5e3302e7141ce665ca3ea85e8b5cd648eafbc3c0c90dd79d6537684e4555
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
