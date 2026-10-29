# PaqRap - detiene el sistema iniciado con INICIAR.bat (cierra la corrida en curso y apaga el servicio).
param([int]$Puerto = 0)

. (Join-Path $PSScriptRoot 'comun.ps1')

try {
    if ($Puerto -le 0) { $Puerto = Puerto-Guardado }
    Escribir-Paso "Deteniendo PaqRap (puerto $Puerto)..."
    if (Detener-Servicio $Puerto) {
        Escribir-Ok 'PaqRap se detuvo.'
    } else {
        Escribir-Aviso 'PaqRap no estaba en ejecucion.'
    }
    exit 0
} catch {
    Escribir-Error "No se pudo detener PaqRap: $($_.Exception.Message)"
    exit 1
}
