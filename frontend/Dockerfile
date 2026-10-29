# syntax=docker/dockerfile:1
#
# PaqRap - visualizador Next.js como servidor Node (modo standalone). Las llamadas a /api/*
# se reenvian al backend indicado en BACKEND_URL (se fija al construir la imagen).
#
#   docker build --build-arg BACKEND_URL=http://backend:8080 -t paqrap-frontend .

FROM node:22-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

FROM node:22-alpine AS build
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .
ARG BACKEND_URL=http://backend:8080
ENV BACKEND_URL=${BACKEND_URL} NEXT_OUTPUT=standalone NEXT_TELEMETRY_DISABLED=1
RUN npx next build

FROM node:22-alpine
WORKDIR /app
ENV NODE_ENV=production PORT=3000 HOSTNAME=0.0.0.0 NEXT_TELEMETRY_DISABLED=1
RUN addgroup -S paqrap && adduser -S paqrap -G paqrap
COPY --from=build /app/public ./public
COPY --from=build --chown=paqrap:paqrap /app/.next/standalone ./
COPY --from=build --chown=paqrap:paqrap /app/.next/static ./.next/static
USER paqrap
EXPOSE 3000
CMD ["node", "server.js"]
