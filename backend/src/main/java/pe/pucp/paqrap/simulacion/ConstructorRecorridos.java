package pe.pucp.paqrap.simulacion;

import pe.pucp.paqrap.comun.Coordenada;
import pe.pucp.paqrap.flota.Almacen;
import pe.pucp.paqrap.flota.JornadaConductor;
import pe.pucp.paqrap.flota.Vehiculo;
import pe.pucp.paqrap.planificador.RedVial;
import pe.pucp.paqrap.registro.Pedido;

import java.util.ArrayList;
import java.util.List;

/**
 * Convierte una secuencia de paradas en el recorrido real de una unidad:
 * calcula el camino por la reticula de cada tramo (BFS con los bloqueos
 * vigentes a la hora en que sale hacia ese punto, igual que el Planificador),
 * lo recorre nodo a nodo a la velocidad de la unidad, inserta las pausas de
 * alimentacion del conductor (JornadaConductor) y la hora de entrega en cada
 * cliente (B5, B7, B14). Asi las horas de la animacion coinciden con las que
 * estimo el algoritmo para la misma secuencia.
 */
final class ConstructorRecorridos {

    private static final double TOLERANCIA = 1e-9;

    private final RedVial red;
    private final double tiempoServicioH;

    ConstructorRecorridos(RedVial red, double tiempoServicioH) {
        this.red = red;
        this.tiempoServicioH = tiempoServicioH;
    }

    /**
     * Construye una ruta.
     *
     * @param x0           posicion exacta de partida (puede estar a mitad de una calle)
     * @param t0           hora de partida
     * @param esperarHasta la unidad no se mueve antes de esta hora (averia tipo 1); NaN si no espera
     * @param nodoX        nodo de la reticula hacia el que se mueve primero (el mismo punto si ya esta en un nodo)
     */
    RutaEnCurso construir(String idRuta, String causa, Vehiculo v, Almacen salida, Almacen retorno,
                          double x0, double y0, double t0, double esperarHasta, int nodoX, int nodoY,
                          List<Pedido> paradas) {
        Recorrido r = new Recorrido(v, x0, y0, t0);
        if (!Double.isNaN(esperarHasta) && esperarHasta > r.t) {
            r.t = esperarHasta;
            r.tr.agregar(r.x, r.y, r.t);
        }

        int tramos = paradas.size() + 1;
        int[] inicioTramo = new int[tramos];
        int[] finTramo = new int[tramos];
        List<ParadaEnCurso> enCurso = new ArrayList<>();
        int carga = 0;

        inicioTramo[0] = 0;
        r.mover(nodoX, nodoY);
        for (int k = 0; k < paradas.size(); k++) {
            Pedido p = paradas.get(k);
            carga += p.getCantidadUnidades();
            if (k > 0) inicioTramo[k] = r.tr.tamano() - 1;
            r.irA(p.getUbicacion());
            finTramo[k] = r.tr.tamano() - 1;
            double llegada = r.t;
            r.trabajar(tiempoServicioH);
            enCurso.add(new ParadaEnCurso(p, llegada, r.t));
        }
        int ultimo = tramos - 1;
        if (ultimo > 0) inicioTramo[ultimo] = r.tr.tamano() - 1;
        r.irA(retorno.getUbicacion());
        finTramo[ultimo] = r.tr.tamano() - 1;
        double regreso = r.t;

        return new RutaEnCurso(idRuta, causa, v, salida, retorno, t0, regreso, enCurso, r.tr,
                inicioTramo, finTramo, r.pausas, carga);
    }

    /** Hora estimada de llegada a cada parada si la unidad sale de (x, y) en t (sin construir la ruta completa). */
    double[] estimarLlegadas(Vehiculo v, Coordenada desde, double t, List<Pedido> paradas) {
        double[] llegadas = new double[paradas.size()];
        Coordenada pos = desde;
        double hora = t;
        for (int k = 0; k < paradas.size(); k++) {
            Pedido p = paradas.get(k);
            double d = red.calcularDistancia(pos, p.getUbicacion(), hora);
            if (Double.isInfinite(d)) d = red.calcularDistancia(pos, p.getUbicacion());
            hora = v.avanzar(hora, v.calcularTiempoViaje(d));
            llegadas[k] = hora;
            hora = v.avanzar(hora, tiempoServicioH);
            pos = p.getUbicacion();
        }
        return llegadas;
    }

    /** Estado mutable mientras se arma un recorrido. */
    private final class Recorrido {
        final Vehiculo v;
        final JornadaConductor jornada;
        final int correlativo;
        final double velocidad;
        final Trayectoria tr = new Trayectoria();
        final List<double[]> pausas = new ArrayList<>();
        double x, y, t;

        Recorrido(Vehiculo v, double x0, double y0, double t0) {
            this.v = v;
            this.jornada = v.getJornada();
            this.correlativo = v.getCorrelativo();
            this.velocidad = v.getVelocidadPromedioKmH();
            this.x = x0;
            this.y = y0;
            this.t = t0;
            tr.agregar(x, y, t);
        }

        /**
         * Camino mas corto (con los bloqueos vigentes a la hora de salida) hasta el destino. P7: en un nodo
         * bloqueado no se puede seguir de largo ni girar; la unidad que llego a el desde una calle abierta
         * (por ejemplo, para entregar) sale dando media vuelta por donde vino.
         */
        void irA(Coordenada destino) {
            Coordenada desde = new Coordenada(Math.round(x), Math.round(y));
            int[] previo = vecinoDeLlegada();
            if (previo != null && (desde.x() != destino.x() || desde.y() != destino.y())
                    && red.estaNodoBloqueado((int) desde.x(), (int) desde.y(), t)
                    && !red.estaNodoBloqueado(previo[0], previo[1], t)) {
                mover(previo[0], previo[1]);
                desde = new Coordenada(previo[0], previo[1]);
            }
            int[] camino = red.caminoMasCorto(desde, destino, t);
            if (camino == null) camino = red.caminoSinBloqueos(desde, destino);
            for (int i = 1; i < camino.length; i++) {
                mover(red.xDeNodo(camino[i]), red.yDeNodo(camino[i]));
            }
        }

        /** Nodo vecino desde el que la unidad llego a su posicion actual (null si recien parte o no esta en un nodo). */
        int[] vecinoDeLlegada() {
            if (x != Math.rint(x) || y != Math.rint(y)) return null;
            for (int i = tr.tamano() - 1; i >= 0; i--) {
                double px = tr.x(i), py = tr.y(i);
                if (px == x && py == y) continue;
                if (px != x && py != y) return null;
                int nx = (int) x + (int) Math.signum(px - x), ny = (int) y + (int) Math.signum(py - y);
                if (nx < 0 || nx > red.getAnchoKm() || ny < 0 || ny > red.getAltoKm()) return null;
                return new int[]{nx, ny};
            }
            return null;
        }

        /** Avanza en linea recta (sobre un eje) hasta (nx, ny), deteniendose en la pausa de alimentacion si toca. */
        void mover(double nx, double ny) {
            double distancia = Math.abs(nx - x) + Math.abs(ny - y);
            if (distancia <= 0) return;
            if (x != nx && y != ny) {
                // nunca deberia pasar en la reticula; se resuelve en dos tramos rectos
                mover(nx, y);
                mover(nx, ny);
                return;
            }
            double restante = distancia / velocidad;
            if (jornada.getDuracionAlimentacionH() <= 0) {
                t += restante;
                x = nx;
                y = ny;
                tr.agregar(x, y, t);
                return;
            }
            while (true) {
                double pausa = jornada.proximaPausa(correlativo, t);
                if (t >= pausa) {
                    double fin = pausa + jornada.getDuracionAlimentacionH();
                    pausas.add(new double[]{t, fin});
                    t = fin;
                    tr.agregar(x, y, t);
                    continue;
                }
                if (t + restante <= pausa + TOLERANCIA) {
                    t += restante;
                    x = nx;
                    y = ny;
                    tr.agregar(x, y, t);
                    return;
                }
                double f = (pausa - t) / restante;
                x = x + (nx - x) * f;
                y = y + (ny - y) * f;
                restante -= pausa - t;
                t = pausa;
                tr.agregar(x, y, t);
            }
        }

        /** Trabajo detenido en el lugar (entrega), con la pausa de alimentacion si cae en medio. */
        void trabajar(double duracion) {
            if (duracion <= 0) return;
            if (jornada.getDuracionAlimentacionH() <= 0) {
                t += duracion;
                tr.agregar(x, y, t);
                return;
            }
            double restante = duracion;
            while (true) {
                double pausa = jornada.proximaPausa(correlativo, t);
                if (t >= pausa) {
                    double fin = pausa + jornada.getDuracionAlimentacionH();
                    pausas.add(new double[]{t, fin});
                    t = fin;
                    continue;
                }
                if (t + restante <= pausa + TOLERANCIA) {
                    t += restante;
                    break;
                }
                restante -= pausa - t;
                t = pausa;
            }
            tr.agregar(x, y, t);
        }
    }
}
