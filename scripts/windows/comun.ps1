# PaqRap - funciones compartidas por los scripts de Windows (PowerShell 5.1 o 7).
# Nada se instala en el sistema: lo que haga falta (Java o Node portables, Maven y sus
# dependencias), la base de datos, las bitacoras y la carpeta de compilacion quedan en una sola
# carpeta, %LOCALAPPDATA%\PaqRap (fuera de OneDrive; se puede borrar sin problema).
# La variable de entorno PAQRAP_LOCAL permite usar otra carpeta.

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
try {
    [Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12
} catch { }

$script:Raiz = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$script:Jar = Join-Path $script:Raiz 'dist\paqrap-app.jar'
$script:Local = if ($env:PAQRAP_LOCAL) { $env:PAQRAP_LOCAL } elseif ($env:LOCALAPPDATA) { Join-Path $env:LOCALAPPDATA 'PaqRap' } else { Join-Path $env:TEMP 'PaqRap' }
$script:Herramientas = Join-Path $script:Local 'herramientas'
$script:Compilacion = Join-Path $script:Local 'compilacion'
$script:Datos = Join-Path $script:Local 'datos'
$script:Logs = Join-Path $script:Local 'logs'
$script:PidArchivo = Join-Path $script:Logs 'paqrap.pid'
$script:PuertoArchivo = Join-Path $script:Logs 'paqrap.puerto'
$script:VersionNode = 'v22.23.3'
$script:JavaMinimo = 21

# La cache de npm/npx tambien queda en la carpeta de PaqRap (no en la del usuario).
$env:NPM_CONFIG_CACHE = Join-Path $script:Local 'npm-cache'
$env:NPM_CONFIG_UPDATE_NOTIFIER = 'false'

function Escribir-Paso([string]$texto) { Write-Host "==> $texto" -ForegroundColor Cyan }
function Escribir-Ok([string]$texto) { Write-Host $texto -ForegroundColor Green }
function Escribir-Aviso([string]$texto) { Write-Host $texto -ForegroundColor Yellow }
function Escribir-Error([string]$texto) { Write-Host $texto -ForegroundColor Red }

# Ejecuta un programa sin consola y devuelve su codigo de salida y todo lo que escribio.
function Invocar-Proceso([string]$exe, [string]$argumentos) {
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $exe
    $psi.Arguments = $argumentos
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $psi.UseShellExecute = $false
    $psi.CreateNoWindow = $true
    $p = [System.Diagnostics.Process]::Start($psi)
    $tareaError = $p.StandardError.ReadToEndAsync()
    $salida = $p.StandardOutput.ReadToEnd()
    $p.WaitForExit()
    return [pscustomobject]@{ Codigo = $p.ExitCode; Salida = ($salida + $tareaError.Result) }
}

# Ejecuta un comando de consola (npm, mvnw, vercel...) en la misma ventana, con su salida e
# interaccion normales; lanza un error si termina con codigo distinto de 0.
function Ejecutar-Comando([string]$descripcion, [string]$carpeta, [string]$comando) {
    Escribir-Paso $descripcion
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = 'cmd.exe'
    $psi.Arguments = "/d /s /c `"$comando`""
    $psi.WorkingDirectory = $carpeta
    $psi.UseShellExecute = $false
    $p = [System.Diagnostics.Process]::Start($psi)
    $p.WaitForExit()
    if ($p.ExitCode -ne 0) { throw "Fallo: $descripcion (codigo de salida $($p.ExitCode))" }
}

# Copia una carpeta con robocopy (espejo); los nombres de -Excluir no se copian ni se borran.
function Copiar-Espejo([string]$origen, [string]$destino, [string[]]$excluir = @()) {
    New-Item -ItemType Directory -Force -Path $destino | Out-Null
    $argumentos = "`"$origen`" `"$destino`" /MIR /NFL /NDL /NJH /NJS /NP /R:2 /W:1"
    if ($excluir.Count -gt 0) { $argumentos += ' /XD ' + (($excluir | ForEach-Object { "`"$_`"" }) -join ' ') }
    $r = Invocar-Proceso 'robocopy.exe' $argumentos
    if ($r.Codigo -ge 8) { throw "No se pudo copiar $origen a $destino`n$($r.Salida)" }
}

function Version-Java([string]$java) {
    if (-not $java -or -not (Test-Path -LiteralPath $java)) { return 0 }
    try {
        $r = Invocar-Proceso $java '-version'
        if ($r.Salida -match 'version "(\d+)(\.(\d+))?') {
            $mayor = [int]$Matches[1]
            if ($mayor -eq 1 -and $Matches[3]) { $mayor = [int]$Matches[3] }
            return $mayor
        }
    } catch { }
    return 0
}

# Carpeta real de instalacion de un java.exe (el de C:\...\Oracle\Java\javapath es solo un lanzador).
function Inicio-De-Java([string]$java) {
    try {
        $r = Invocar-Proceso $java '-XshowSettings:properties -version'
        foreach ($linea in ($r.Salida -split "`r?`n")) {
            if ($linea -match '^\s*java\.home\s*=\s*(.+)$') { return $Matches[1].Trim() }
        }
    } catch { }
    return $null
}

function Arquitectura([string]$x64, [string]$arm) {
    if ($env:PROCESSOR_ARCHITECTURE -eq 'ARM64' -or $env:PROCESSOR_ARCHITEW6432 -eq 'ARM64') { return $arm }
    return $x64
}

# Devuelve la carpeta de un Java 21+ (JDK si se pide -Jdk). Si no hay, descarga Java 21 (Eclipse Temurin).
function Obtener-Java([switch]$Jdk) {
    $candidatos = @()
    if ($env:JAVA_HOME) { $candidatos += (Join-Path $env:JAVA_HOME 'bin\java.exe') }
    $enPath = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($enPath) { $candidatos += $enPath.Source }
    $candidatos += (Join-Path $script:Herramientas 'jdk\bin\java.exe')
    if (-not $Jdk) { $candidatos += (Join-Path $script:Herramientas 'jre\bin\java.exe') }
    foreach ($c in $candidatos) {
        if ((Version-Java $c) -lt $script:JavaMinimo) { continue }
        $inicio = Inicio-De-Java $c
        if (-not $inicio) { continue }
        if ($Jdk -and -not (Test-Path -LiteralPath (Join-Path $inicio 'bin\javac.exe'))) { continue }
        return $inicio
    }
    $tipo = if ($Jdk) { 'jdk' } else { 'jre' }
    $arq = Arquitectura 'x64' 'aarch64'
    $destino = Join-Path $script:Herramientas $tipo
    Escribir-Paso "No se encontro Java $($script:JavaMinimo) o superior: descargando Java 21 ($tipo) portable (solo la primera vez)..."
    New-Item -ItemType Directory -Force -Path $script:Herramientas | Out-Null
    $zip = Join-Path $script:Herramientas "java-$tipo.zip"
    $temporal = Join-Path $script:Herramientas "java-$tipo-tmp"
    Invoke-WebRequest -UseBasicParsing -Uri "https://api.adoptium.net/v3/binary/latest/21/ga/windows/$arq/$tipo/hotspot/normal/eclipse?project=jdk" -OutFile $zip
    if (Test-Path -LiteralPath $temporal) { Remove-Item -LiteralPath $temporal -Recurse -Force }
    Expand-Archive -LiteralPath $zip -DestinationPath $temporal -Force
    $interna = Get-ChildItem -LiteralPath $temporal -Directory | Select-Object -First 1
    if (Test-Path -LiteralPath $destino) { Remove-Item -LiteralPath $destino -Recurse -Force }
    Move-Item -LiteralPath $interna.FullName -Destination $destino
    Remove-Item -LiteralPath $temporal -Recurse -Force
    Remove-Item -LiteralPath $zip -Force
    return $destino
}

# Devuelve la carpeta de Node.js 20+ (el del sistema o uno portable descargado la primera vez).
function Obtener-Node {
    $enPath = Get-Command node.exe -ErrorAction SilentlyContinue
    if ($enPath) {
        $r = Invocar-Proceso $enPath.Source '--version'
        if ($r.Salida -match 'v(\d+)\.' -and [int]$Matches[1] -ge 20) { return (Split-Path -Parent $enPath.Source) }
    }
    $dir = Join-Path $script:Herramientas 'node'
    if (Test-Path -LiteralPath (Join-Path $dir 'node.exe')) { return $dir }
    $arq = Arquitectura 'x64' 'arm64'
    $nombre = "node-$($script:VersionNode)-win-$arq"
    Escribir-Paso "Descargando Node.js $($script:VersionNode) portable (solo la primera vez)..."
    New-Item -ItemType Directory -Force -Path $script:Herramientas | Out-Null
    $zip = Join-Path $script:Herramientas "$nombre.zip"
    Invoke-WebRequest -UseBasicParsing -Uri "https://nodejs.org/dist/$($script:VersionNode)/$nombre.zip" -OutFile $zip
    Expand-Archive -LiteralPath $zip -DestinationPath $script:Herramientas -Force
    if (Test-Path -LiteralPath $dir) { Remove-Item -LiteralPath $dir -Recurse -Force }
    Move-Item -LiteralPath (Join-Path $script:Herramientas $nombre) -Destination $dir
    Remove-Item -LiteralPath $zip -Force
    return $dir
}

function Puerto-Ocupado([int]$puerto) {
    $cliente = New-Object System.Net.Sockets.TcpClient
    try {
        $intento = $cliente.BeginConnect('127.0.0.1', $puerto, $null, $null)
        $conecto = $intento.AsyncWaitHandle.WaitOne(500)
        if ($conecto) { $cliente.EndConnect($intento) }
        return ($conecto -and $cliente.Connected)
    } catch {
        return $false
    } finally {
        $cliente.Close()
    }
}

function Servicio-Responde([int]$puerto) {
    try {
        $r = Invoke-RestMethod -Uri "http://localhost:$puerto/api/salud" -TimeoutSec 3
        return ($r.estado -eq 'OK')
    } catch {
        return $false
    }
}

# Puerto en el que quedo PaqRap la ultima vez que se inicio (8080 si no se sabe).
function Puerto-Guardado {
    if (Test-Path -LiteralPath $script:PuertoArchivo) {
        $valor = 0
        if ([int]::TryParse((Get-Content -LiteralPath $script:PuertoArchivo -Raw).Trim(), [ref]$valor) -and $valor -gt 0) { return $valor }
    }
    return 8080
}

# Detiene PaqRap: cierra la corrida en curso (para guardar su cierre) y termina el proceso Java.
function Detener-Servicio([int]$puerto) {
    $detenido = $false
    if (Servicio-Responde $puerto) {
        try { Invoke-RestMethod -Method Post -Uri "http://localhost:$puerto/api/simulacion/detener" -TimeoutSec 20 | Out-Null } catch { }
        Start-Sleep -Seconds 2
    }
    if (Test-Path -LiteralPath $script:PidArchivo) {
        $id = 0
        [int]::TryParse((Get-Content -LiteralPath $script:PidArchivo -Raw).Trim(), [ref]$id) | Out-Null
        $proceso = if ($id -gt 0) { Get-Process -Id $id -ErrorAction SilentlyContinue } else { $null }
        if ($proceso -and $proceso.ProcessName -like 'java*') {
            Stop-Process -Id $id -Force
            $detenido = $true
        }
        Remove-Item -LiteralPath $script:PidArchivo -Force -ErrorAction SilentlyContinue
    }
    if (-not $detenido) {
        try {
            $conexion = Get-NetTCPConnection -LocalPort $puerto -State Listen -ErrorAction Stop | Select-Object -First 1
            $proceso = Get-Process -Id $conexion.OwningProcess -ErrorAction SilentlyContinue
            if ($proceso -and $proceso.ProcessName -like 'java*') {
                Stop-Process -Id $proceso.Id -Force
                $detenido = $true
            }
        } catch { }
    }
    return $detenido
}
