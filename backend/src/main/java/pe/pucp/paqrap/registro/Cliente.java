package pe.pucp.paqrap.registro;

import pe.pucp.paqrap.comun.Coordenada;

/**
 * Cliente del Modulo A (Registro de Pedidos y Clientes): -idCliente, -nombre,
 * relacion "ubicado en" con Coordenada (A5).
 *
 * NOTA DE ALCANCE: el diagrama de clases define a Cliente con mas operaciones
 * propias (registrar(), actualizarDatos(), consultarHistorialPedidos()). El
 * historial de pedidos de un cliente (A11) se consulta filtrando los pedidos
 * de la corrida por idCliente (ver ServicioSimulacion), por lo que esta clase
 * se mantiene como un contenedor inmutable.
 */
public class Cliente {
    private final String idCliente;
    private final String nombre;
    private final Coordenada coordenadas;

    public Cliente(String idCliente, String nombre, Coordenada coordenadas) {
        this.idCliente = idCliente;
        this.nombre = nombre;
        this.coordenadas = coordenadas;
    }

    public String getIdCliente() { return idCliente; }
    public String getNombre() { return nombre; }
    public Coordenada getCoordenadas() { return coordenadas; }

    @Override
    public String toString() {
        return "Cliente#" + idCliente + "[" + nombre + ", " + coordenadas + "]";
    }
}
