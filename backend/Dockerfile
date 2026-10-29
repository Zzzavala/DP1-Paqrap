# syntax=docker/dockerfile:1
#
# PaqRap - imagen completa (un solo contenedor): compila el visualizador Next.js en modo
# estatico, lo empaqueta dentro del backend Spring Boot y publica la API y la web en el
# puerto 8080. Es la imagen que se usa en Render, AWS (ECS Fargate / EC2) y docker compose.
#
# Construir desde la carpeta "Resultado":
#   docker build -f backend/Dockerfile -t paqrap-backend .

# ---------- 1) visualizador (Next.js, salida estatica) ----------
FROM node:22-alpine AS web
WORKDIR /app/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
ENV NEXT_TELEMETRY_DISABLED=1 NEXT_OUTPUT=export
RUN npx next build

# ---------- 2) backend (Spring Boot, Java 21) ----------
FROM eclipse-temurin:21-jdk AS api
WORKDIR /app/backend
COPY backend/.mvn .mvn
COPY backend/mvnw backend/pom.xml ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY backend/src src
RUN rm -rf src/main/resources/static
COPY --from=web /app/frontend/out src/main/resources/static
RUN ./mvnw -B -q -DskipTests package

# ---------- 3) imagen de ejecucion ----------
FROM eclipse-temurin:21-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 --home-dir /app paqrap \
    && mkdir -p /data /app \
    && chown -R paqrap /data /app
WORKDIR /app
COPY --from=api /app/backend/target/paqrap-app.jar /app/app.jar
USER paqrap
ENV PORT=8080 \
    PAQRAP_DATOS_DIR=/data \
    TZ=America/Lima \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -Dfile.encoding=UTF-8"
EXPOSE 8080
VOLUME ["/data"]
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fsS "http://localhost:${PORT}/api/salud" || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
