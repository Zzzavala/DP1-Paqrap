# PaqRap

Planificación y visualización de entregas de PaqRap: un planificador (Algoritmo Genético y Colonia de Hormigas)
y un visualizador web para los tres escenarios del curso: operación día a día, simulación del periodo de 5 días
y simulación hasta el colapso logístico.

## Requisitos

- Windows con PowerShell 5.1 o superior.
- Java 21 y Node.js 20 o superior. Si faltan, `COMPILAR.bat` descarga versiones portables la primera vez (necesita internet).

## Uso

1. `COMPILAR.bat`: compila el visualizador y el backend y deja `dist\paqrap-app.jar`.
2. `INICIAR.bat`: inicia el servicio y abre http://localhost:8080.
3. `DETENER.bat`: detiene el servicio.

La base de datos (H2), las bitácoras y la carpeta de compilación quedan en `%LOCALAPPDATA%\PaqRap`.

## Estructura

| Carpeta | Contenido |
|---|---|
| `backend/` | Spring Boot 3.5 (Java 21): motor de simulación, planificador, API REST y datos del curso (`src/main/resources/datos`). |
| `frontend/` | Visualizador en Next.js 15 + React 19 (TypeScript). Se exporta estático dentro del backend. |
| `scripts/windows/` | Scripts de compilación, inicio y detención que usan los `.bat`. |
| `docs/` | Diagramas del consumo de datos y del uso con varios navegadores. |

## Configuración

El backend se configura con variables de entorno (ver `backend/src/main/resources/application.yml`), por ejemplo
`PORT`, `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` y `PAQRAP_CORS_ORIGENES`.
El visualizador usa `BACKEND_URL` (ver `frontend/.env.example`). No se versionan credenciales.

## Pruebas

```
cd backend
mvnw.cmd test
```
