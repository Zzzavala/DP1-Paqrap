# PaqRap - inicia el sistema completo (API + visualizador) en segundo plano y abre el navegador.
# Uso: INICIAR.bat   o   powershell -ExecutionPolicy Bypass -File scripts\windows\iniciar.ps1 [-Puerto 8080] [-SinNavegador]
param(
    [int]$Puerto = 0,
    [switch]$SinNavegador
)

. (Join-Path $PSScriptRoot 'comun.ps1')

function Abrir-Navegador([int]$p) {
    if (-not $SinNavegador) { Start-Process "http://localhost:$p" }
}

try {
    New-Item -ItemType Directory -Force -Path $script:Logs, $script:Datos | Out-Null

    # 1) Si ya esta corriendo, solo se abre el navegador.
    $previo = if ($Puerto -gt 0) { $Puerto } else { Puerto-Guardado }
    if (Servicio-Responde $previo) {
        Escribir-Ok "PaqRap ya esta en ejecucion: http://localhost:$previo"
        Abrir-Navegador $previo
        exit 0
    }

    # 2) Puerto libre (8080 o el siguiente disponible).
    if ($Puerto -le 0) {
        $Puerto = 8080
        while ((Puerto-Ocupado $Puerto) -and $Puerto -lt 8100) { $Puerto++ }
    }
    if (Puerto-Ocupado $Puerto) { throw "El puerto $Puerto esta ocupado por otro programa." }

    # 3) Java 21 y el ejecutable de PaqRap (si falta, se compila).
    Escribir-Paso 'Buscando Java 21...'
    $javaHome = Obtener-Java
    $java = Join-Path $javaHome 'bin\java.exe'
    Write-Host "    Java: $javaHome"
    if (-not (Test-Path -LiteralPath $script:Jar)) {
        Escribir-Aviso 'No existe dist\paqrap-app.jar: se compilara primero (unos minutos, solo esta vez).'
        & (Join-Path $PSScriptRoot 'compilar.ps1')
        if (-not (Test-Path -LiteralPath $script:Jar)) { throw 'La compilacion no genero dist\paqrap-app.jar.' }
    }

    # 4) Arranque en segundo plano (ventana oculta). La configuracion va por variables de entorno
    #    (las hereda el proceso Java): base H2 y bitacora en %LOCALAPPDATA%\PaqRap.
    $log = Join-Path $script:Logs 'paqrap.log'
    if (Test-Path -LiteralPath $log) { Remove-Item -LiteralPath $log -Force -ErrorAction SilentlyContinue }
    $env:PAQRAP_DATOS_DIR = ($script:Datos -replace '\\', '/')
    $env:PAQRAP_DATOS_EXTERNOS = ((Join-Path $script:Raiz 'datos-externos') -replace '\\', '/')
    $env:LOGGING_FILE_NAME = $log
    # Temporales de Tomcat en la carpeta de PaqRap (se limpian en cada inicio), no en %TEMP%.
    $temporales = Join-Path $script:Local 'tmp'
    if (Test-Path -LiteralPath $temporales) { Remove-Item -LiteralPath $temporales -Recurse -Force -ErrorAction SilentlyContinue }
    New-Item -ItemType Directory -Force -Path $temporales | Out-Null
    $argumentos = @('-Dfile.encoding=UTF-8', '-Duser.timezone=America/Lima', '-XX:+UseSerialGC', '-XX:-UsePerfData',
        "`"-Djava.io.tmpdir=$temporales`"", '-jar', 'dist\paqrap-app.jar', "--server.port=$Puerto")
    Escribir-Paso "Iniciando PaqRap en el puerto $Puerto..."
    $proceso = Start-Process -FilePath $java -ArgumentList $argumentos -WorkingDirectory $script:Raiz -WindowStyle Hidden -PassThru
    Set-Content -LiteralPath $script:PidArchivo -Value $proceso.Id -Encoding Ascii
    Set-Content -LiteralPath $script:PuertoArchivo -Value $Puerto -Encoding Ascii

    # 5) Espera a que responda /api/salud (hasta 3 minutos).
    $limite = (Get-Date).AddMinutes(3)
    while (-not (Servicio-Responde $Puerto)) {
        if ($proceso.HasExited) {
            Escribir-Error 'PaqRap se cerro al iniciar. Ultimas lineas de la bitacora:'
            if (Test-Path -LiteralPath $log) { Get-Content -LiteralPath $log -Tail 25 | ForEach-Object { Write-Host "    $_" } }
            Write-Host "    Para ver el error completo ejecute: `"$java`" -jar `"$($script:Jar)`""
            Remove-Item -LiteralPath $script:PidArchivo -Force -ErrorAction SilentlyContinue
            exit 1
        }
        if ((Get-Date) -gt $limite) { throw "PaqRap no respondio a tiempo. Revise $log" }
        Write-Host '.' -NoNewline
        Start-Sleep -Seconds 2
    }
    Write-Host ''
    Escribir-Ok "PaqRap esta listo: http://localhost:$Puerto"
    Write-Host "    Bitacora: $log"
    Write-Host '    Para apagarlo use DETENER.bat'
    Abrir-Navegador $Puerto
    exit 0
} catch {
    Escribir-Error "No se pudo iniciar PaqRap: $($_.Exception.Message)"
    exit 1
}
