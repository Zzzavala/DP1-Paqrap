// Compila el visualizador como sitio estatico (NEXT_OUTPUT=export) y lo copia en
// backend/src/main/resources/static para que el propio servicio Spring Boot lo publique en "/".
// Uso: npm run build:estatico   (PAQRAP_DESTINO_ESTATICO cambia la carpeta de destino)
import { spawnSync } from 'node:child_process';
import { cpSync, existsSync, mkdirSync, rmSync } from 'node:fs';
import path from 'node:path';

const env = { ...process.env, NEXT_OUTPUT: 'export' };
const nextBin = path.join('node_modules', 'next', 'dist', 'bin', 'next');
const r = spawnSync(process.execPath, [nextBin, 'build'], { stdio: 'inherit', env });
if (r.status !== 0) process.exit(r.status ?? 1);

if (!existsSync('out')) {
  console.error('No se genero la carpeta "out".');
  process.exit(1);
}
const destino = process.env.PAQRAP_DESTINO_ESTATICO || path.join('..', 'backend', 'src', 'main', 'resources', 'static');
rmSync(destino, { recursive: true, force: true });
mkdirSync(destino, { recursive: true });
cpSync('out', destino, { recursive: true });
console.log('Visualizador estatico copiado en', path.resolve(destino));
