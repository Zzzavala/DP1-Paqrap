# PaqRap - compila el visualizador (Next.js en modo estatico) y el backend (Spring Boot) y deja
# el ejecutable unico en dist\paqrap-app.jar. Si faltan Java 21 o Node.js 20+, los descarga
# portables. La compilacion (y la cache de Maven) quedan en %LOCALAPPDATA%\PaqRap, fuera de OneDrive.
# Uso: COMPILAR.bat   o   powershell -ExecutionPolicy Bypass -File scripts\windows\compilar.ps1 [-ConPruebas]
param([switch]$ConPruebas)

. (Join-Path $PSScriptRoot 'comun.ps1')

try {
    $inicio = Get-Date
    Escribir-Paso 'Buscando JDK 21 y Node.js...'
    $jdk = Obtener-Java -Jdk
    $node = Obtener-Node
    Write-Host "    JDK:  $jdk"
    Write-Host "    Node: $node"
    $env:JAVA_HOME = $jdk
    $env:PATH = "$node;$jdk\bin;$env:PATH"
    $env:NEXT_TELEMETRY_DISABLED = '1'
    $env:MAVEN_USER_HOME = Join-Path $script:Local 'maven'
    $repositorioMaven = Join-Path $script:Local 'maven\repository'

    $frontend = Join-Path $script:Compilacion 'frontend'
    $backend = Join-Path $script:Compilacion 'backend'
    Escribir-Paso 'Copiando el codigo fuente a la carpeta de compilacion...'
    Copiar-Espejo (Join-Path $script:Raiz 'frontend') $frontend @('node_modules', '.next', 'out', '.vercel')
    Copiar-Espejo (Join-Path $script:Raiz 'backend') $backend @('target')

    # 1) Visualizador: dependencias (solo cuando cambia package-lock.json) y exportacion estatica,
    #    que build-estatico.mjs deja en ..\backend\src\main\resources\static (la copia de compilacion).
    $hash = (Get-FileHash -LiteralPath (Join-Path $frontend 'package-lock.json') -Algorithm SHA256).Hash
    $marca = Join-Path $frontend 'node_modules\.paqrap-lock'
    $instalado = if (Test-Path -LiteralPath $marca) { (Get-Content -LiteralPath $marca -Raw).Trim() } else { '' }
    if ($instalado -ne $hash) {
        Ejecutar-Comando 'Instalando dependencias del visualizador (npm ci)...' $frontend 'npm ci --no-audit --no-fund'
        Set-Content -LiteralPath $marca -Value $hash -Encoding Ascii
    }
    Ejecutar-Comando 'Compilando el visualizador (Next.js, salida estatica)...' $frontend 'node scripts\build-estatico.mjs'

    # 2) Backend con el visualizador incluido.
    $metas = if ($ConPruebas) { 'package' } else { '-DskipTests package' }
    Ejecutar-Comando 'Compilando el backend (Spring Boot)...' $backend ".\mvnw.cmd -B -ntp `"-Dmaven.repo.local=$repositorioMaven`" $metas"

    # 3) Resultado: dist\paqrap-app.jar y la web estatica actualizada tambien en el codigo fuente.
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $script:Jar) | Out-Null
    Copy-Item -LiteralPath (Join-Path $backend 'target\paqrap-app.jar') -Destination $script:Jar -Force
    Copiar-Espejo (Join-Path $backend 'src\main\resources\static') (Join-Path $script:Raiz 'backend\src\main\resources\static')
    $mb = [math]::Round((Get-Item -LiteralPath $script:Jar).Length / 1MB, 1)
    $minutos = [math]::Round(((Get-Date) - $inicio).TotalMinutes, 1)
    Escribir-Ok "Compilacion lista: dist\paqrap-app.jar ($mb MB, $minutos min)"
    exit 0
} catch {
    Escribir-Error "No se pudo compilar PaqRap: $($_.Exception.Message)"
    exit 1
}
