/**
 * Configuracion de Next.js para los tres modos de despliegue de PaqRap:
 *
 * - Vercel / servidor Node (por defecto): el navegador llama a "/api/..." en el
 *   mismo dominio y Next.js lo reenvia al backend Spring Boot indicado en
 *   BACKEND_URL (por ejemplo https://paqrap-backend.onrender.com o la URL del
 *   balanceador en AWS). Asi no hay problemas de CORS ni de contenido mixto.
 * - Docker (NEXT_OUTPUT=standalone): igual que el anterior, empaquetado como
 *   servidor Node minimo.
 * - Estatico (NEXT_OUTPUT=export): genera HTML/JS/CSS que el propio backend
 *   publica en "/" (un solo proceso Java sirve la API y el visualizador).
 */
const modo = process.env.NEXT_OUTPUT;
const exportar = modo === 'export';
const backend = (process.env.BACKEND_URL || 'http://localhost:8080').replace(/\/+$/, '');

/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  poweredByHeader: false,
  images: { unoptimized: true },
  ...(exportar ? { output: 'export' } : {}),
  ...(modo === 'standalone' ? { output: 'standalone' } : {}),
  ...(exportar
    ? {}
    : {
        async rewrites() {
          return [{ source: '/api/:path*', destination: `${backend}/api/:path*` }];
        },
      }),
};

export default nextConfig;
