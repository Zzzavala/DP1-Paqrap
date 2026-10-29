package pe.pucp.paqrap.simulacion;

/**
 * Recibe lo que la corrida debe persistir mientras avanza (B25: historial de
 * rutas planificadas y replanificadas con la causa del cambio; D19: incidencias).
 * Las corridas de experimentacion no usan oyente.
 */
public interface OyenteCorrida {

    /** Ruta despachada o recalculada en camino. */
    record RegistroRuta(String idRuta, String vehiculo, String causa, double horaSalida, double horaRegreso,
                        String almacenSalida, String almacenRetorno, String pedidos, int unidades, double km, double costo) {
    }

    void rutaRegistrada(RegistroRuta ruta);

    void eventoRegistrado(EventoSimulacion evento);
}
