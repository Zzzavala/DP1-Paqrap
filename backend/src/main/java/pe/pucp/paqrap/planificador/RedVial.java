package pe.pucp.paqrap.planificador;

import pe.pucp.paqrap.comun.Coordenada;
import pe.pucp.paqrap.flota.Bloqueo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Grafo de calles de doble sentido y calculo de distancias (diagrama, Modulo B):
 * -nodos: List<NodoVial>, -aristas: List<AristaVial>,
 * +calcularDistancia(origen, destino): double,
 * +calcularRutaMasCorta(origen, destino): List<NodoVial>.
 *
 * La ciudad es una reticula de 70 x 50 km con nodos cada 1 km y calles de
 * doble sentido en las 4 direcciones (P4 de Preguntas y Respuestas).
 *
 * Bloqueos programados (P7): cada bloqueo rige en un intervalo de tiempo y
 * bloquea todos los nodos de su poligonal. Un nodo bloqueado no se puede
 * atravesar: se puede llegar hasta el (y entregar ahi), pero no seguir de
 * largo ni girar; hay que volver. Por eso la distancia depende de la hora:
 * calcularDistancia(origen, destino, hora) usa solo los bloqueos vigentes en
 * esa hora.
 *
 * Bloqueos manuales: el visualizador permite cerrar un tramo de calle (una
 * arista) con un clic (prototipo v04, V-2). Ese tramo no se puede recorrer en
 * ningun sentido hasta que el usuario lo vuelva a abrir.
 *
 * - Sin bloqueos vigentes: la distancia es la de Manhattan, que en esta
 *   reticula es exactamente el camino mas corto.
 * - Con bloqueos vigentes: BFS sobre la reticula esquivando los nodos
 *   bloqueados. Los bloqueos solo cambian en los instantes en que alguno
 *   empieza o termina, asi que entre dos de esos instantes (un "tramo
 *   horario") el mapa es el mismo; cada BFS se hace una vez por origen y por
 *   tramo horario y se reutiliza (cache), porque los algoritmos consultan las
 *   mismas distancias miles de veces.
 */
public class RedVial {
    private static final int[][] DIRECCIONES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final boolean[] SIN_BLOQUEOS = new boolean[0];

    private final int anchoKm;
    private final int altoKm;
    private final int filas;                  // nodos por cada x (altoKm + 1)
    private final int totalNodos;
    private List<NodoVial> nodos;
    private List<AristaVial> aristas;

    // Tramos (aristas) cerrados a mano, vigentes hasta que se vuelvan a abrir.
    private final Set<Long> tramosBloqueados = new HashSet<>();

    // Bloqueos programados de los archivos mensuales.
    private List<Bloqueo> bloqueosProgramados = new ArrayList<>();
    private int[][] nodosPorBloqueo = new int[0][];
    private double[] instantesDeCambio = new double[0];
    private final Map<Integer, boolean[]> nodosBloqueadosPorTramo = new HashMap<>();
    private final Map<Long, int[]> cacheDistancias = new HashMap<>();

    public RedVial(int anchoKm, int altoKm) {
        this.anchoKm = anchoKm;
        this.altoKm = altoKm;
        this.filas = altoKm + 1;
        this.totalNodos = (anchoKm + 1) * filas;
    }

    private int indice(int x, int y) { return x * filas + y; }
    private int xDe(int indice) { return indice / filas; }
    private int yDe(int indice) { return indice % filas; }

    private long claveTramo(int a, int b) {
        int menor = Math.min(a, b), mayor = Math.max(a, b);
        return (long) menor * totalNodos + mayor;
    }

    /** Nodos de la reticula (se construyen la primera vez que se piden). */
    public List<NodoVial> getNodos() {
        if (nodos == null) construirReticula();
        return nodos;
    }

    /** Aristas de la reticula (se construyen la primera vez que se piden). */
    public List<AristaVial> getAristas() {
        if (aristas == null) construirReticula();
        return aristas;
    }

    private void construirReticula() {
        nodos = new ArrayList<>(totalNodos);
        aristas = new ArrayList<>();
        NodoVial[][] matriz = new NodoVial[anchoKm + 1][altoKm + 1];
        for (int x = 0; x <= anchoKm; x++) {
            for (int y = 0; y <= altoKm; y++) {
                matriz[x][y] = new NodoVial(x, y);
                nodos.add(matriz[x][y]);
            }
        }
        for (int x = 0; x <= anchoKm; x++) {
            for (int y = 0; y <= altoKm; y++) {
                if (x + 1 <= anchoKm) aristas.add(new AristaVial(matriz[x][y], matriz[x + 1][y], true));
                if (y + 1 <= altoKm) aristas.add(new AristaVial(matriz[x][y], matriz[x][y + 1], true));
            }
        }
    }

    public NodoVial nodoMasCercanoA(Coordenada c) {
        int x = (int) Math.round(Math.max(0, Math.min(anchoKm, c.x())));
        int y = (int) Math.round(Math.max(0, Math.min(altoKm, c.y())));
        return new NodoVial(x, y);
    }

    private int indiceMasCercano(Coordenada c) {
        int x = (int) Math.round(Math.max(0, Math.min(anchoKm, c.x())));
        int y = (int) Math.round(Math.max(0, Math.min(altoKm, c.y())));
        return indice(x, y);
    }

    // ------------------------------------------------------------------
    // Carga de bloqueos programados
    // ------------------------------------------------------------------

    /** Registra los bloqueos programados del periodo (reemplaza los anteriores). */
    public void cargarBloqueosProgramados(List<Bloqueo> bloqueos) {
        this.bloqueosProgramados = new ArrayList<>(bloqueos);
        reconstruirIndices();
    }

    /**
     * Agrega los bloqueos de un nuevo mes (la corrida avanza mes a mes) y
     * descarta los que terminaron antes de la hora indicada, que ya no pueden
     * afectar ninguna ruta.
     */
    public void agregarBloqueosProgramados(List<Bloqueo> nuevos, double descartarTerminadosAntesDe) {
        List<Bloqueo> vigentes = new ArrayList<>();
        for (Bloqueo b : bloqueosProgramados) {
            if (b.getHoraFin() >= descartarTerminadosAntesDe) vigentes.add(b);
        }
        vigentes.addAll(nuevos);
        this.bloqueosProgramados = vigentes;
        reconstruirIndices();
    }

    private void reconstruirIndices() {
        this.nodosPorBloqueo = new int[bloqueosProgramados.size()][];
        TreeSet<Double> instantes = new TreeSet<>();
        for (int i = 0; i < bloqueosProgramados.size(); i++) {
            Bloqueo b = bloqueosProgramados.get(i);
            List<int[]> puntos = b.nodosBloqueados();
            int[] indices = new int[puntos.size()];
            for (int k = 0; k < puntos.size(); k++) {
                int x = Math.max(0, Math.min(anchoKm, puntos.get(k)[0]));
                int y = Math.max(0, Math.min(altoKm, puntos.get(k)[1]));
                indices[k] = indice(x, y);
            }
            nodosPorBloqueo[i] = indices;
            instantes.add(b.getHoraInicio());
            instantes.add(b.getHoraFin());
        }
        this.instantesDeCambio = instantes.stream().mapToDouble(Double::doubleValue).toArray();
        nodosBloqueadosPorTramo.clear();
        cacheDistancias.clear();
    }

    public List<Bloqueo> getBloqueosProgramados() { return bloqueosProgramados; }

    /** Bloqueos programados vigentes en la hora dada. */
    public List<Bloqueo> bloqueosVigentes(double hora) {
        List<Bloqueo> vigentes = new ArrayList<>();
        for (Bloqueo b : bloqueosProgramados) if (b.estaVigente(hora)) vigentes.add(b);
        return vigentes;
    }

    /** Cantidad de bloqueos programados vigentes en la hora dada. */
    public int contarBloqueosVigentes(double hora) {
        int c = 0;
        for (Bloqueo b : bloqueosProgramados) if (b.estaVigente(hora)) c++;
        return c;
    }

    /** Nodos (indices) que bloquea un bloqueo programado, como conjunto de pares x,y. */
    public Set<Long> nodosDe(Bloqueo bloqueo) {
        Set<Long> claves = new HashSet<>();
        for (int[] p : bloqueo.nodosBloqueados()) claves.add(claveNodo(p[0], p[1]));
        return claves;
    }

    public static long claveNodo(int x, int y) { return ((long) x << 32) | (y & 0xffffffffL); }

    // ------------------------------------------------------------------
    // Bloqueos manuales de tramos
    // ------------------------------------------------------------------

    /** Cierra el tramo entre dos nodos vecinos (en ambos sentidos). Devuelve false si no son vecinos. */
    public boolean bloquearTramo(int x1, int y1, int x2, int y2) {
        if (!sonVecinos(x1, y1, x2, y2)) return false;
        tramosBloqueados.add(claveTramo(indice(x1, y1), indice(x2, y2)));
        cacheDistancias.clear();
        return true;
    }

    public boolean desbloquearTramo(int x1, int y1, int x2, int y2) {
        if (!sonVecinos(x1, y1, x2, y2)) return false;
        boolean quitado = tramosBloqueados.remove(claveTramo(indice(x1, y1), indice(x2, y2)));
        if (quitado) cacheDistancias.clear();
        return quitado;
    }

    public boolean estaTramoBloqueado(int x1, int y1, int x2, int y2) {
        if (!sonVecinos(x1, y1, x2, y2)) return false;
        return tramosBloqueados.contains(claveTramo(indice(x1, y1), indice(x2, y2)));
    }

    /** Tramos cerrados a mano como {x1, y1, x2, y2}. */
    public List<int[]> getTramosBloqueados() {
        List<int[]> lista = new ArrayList<>();
        for (long clave : tramosBloqueados) {
            int a = (int) (clave / totalNodos), b = (int) (clave % totalNodos);
            lista.add(new int[]{xDe(a), yDe(a), xDe(b), yDe(b)});
        }
        return lista;
    }

    /** +bloquear(arista) (diagrama, version sobre AristaVial). */
    public void bloquear(AristaVial arista) {
        arista.setBloqueada(true);
        bloquearTramo(arista.getNodoA().getX(), arista.getNodoA().getY(), arista.getNodoB().getX(), arista.getNodoB().getY());
    }

    public void desbloquear(AristaVial arista) {
        arista.setBloqueada(false);
        desbloquearTramo(arista.getNodoA().getX(), arista.getNodoA().getY(), arista.getNodoB().getX(), arista.getNodoB().getY());
    }

    private boolean sonVecinos(int x1, int y1, int x2, int y2) {
        if (x1 < 0 || x1 > anchoKm || x2 < 0 || x2 > anchoKm || y1 < 0 || y1 > altoKm || y2 < 0 || y2 > altoKm) return false;
        return Math.abs(x1 - x2) + Math.abs(y1 - y2) == 1;
    }

    /**
     * Libera de la cache los tramos horarios que ya quedaron atras. El ciclo
     * por ventanas solo avanza hacia adelante, asi que nada anterior a la
     * ventana actual se vuelve a consultar.
     */
    public void descartarCacheAnteriorA(double hora) {
        int tramoActual = tramoDe(hora);
        cacheDistancias.keySet().removeIf(k -> (int) (k >>> 20) - 1 >= 0 && (int) (k >>> 20) - 1 < tramoActual);
        nodosBloqueadosPorTramo.keySet().removeIf(t -> t < tramoActual);
    }

    // ------------------------------------------------------------------
    // Distancias
    // ------------------------------------------------------------------

    /** +calcularDistancia(origen, destino): double (diagrama). Sin bloqueos programados. */
    public double calcularDistancia(NodoVial origen, NodoVial destino) {
        return distancia(indice(origen.getX(), origen.getY()), indice(destino.getX(), destino.getY()), -1, null);
    }

    /** Distancia entre dos coordenadas sin considerar bloqueos programados. */
    public double calcularDistancia(Coordenada origen, Coordenada destino) {
        return distancia(indiceMasCercano(origen), indiceMasCercano(destino), -1, null);
    }

    /**
     * Distancia (km) sobre la red vial saliendo a la hora dada, esquivando los
     * bloqueos programados vigentes en ese momento. Devuelve infinito si el
     * destino queda encerrado.
     */
    public double calcularDistancia(Coordenada origen, Coordenada destino, double hora) {
        int tramo = tramoDe(hora);
        return distancia(indiceMasCercano(origen), indiceMasCercano(destino), tramo, bloqueadosEnTramo(tramo, hora));
    }

    private double distancia(int origen, int destino, int tramo, boolean[] bloqueados) {
        if (origen == destino) return 0.0;
        boolean hayBloqueos = (bloqueados != null && bloqueados != SIN_BLOQUEOS) || !tramosBloqueados.isEmpty();
        if (!hayBloqueos) {
            return Math.abs(xDe(origen) - xDe(destino)) + Math.abs(yDe(origen) - yDe(destino));
        }
        long claveCache = ((long) (tramo + 1) << 20) | origen;
        int[] dist = cacheDistancias.get(claveCache);
        if (dist == null) {
            dist = bfs(origen, bloqueados == SIN_BLOQUEOS ? null : bloqueados, null);
            cacheDistancias.put(claveCache, dist);
        }
        return dist[destino] < 0 ? Double.POSITIVE_INFINITY : dist[destino];
    }

    /** +calcularRutaMasCorta(origen, destino): List<NodoVial> (diagrama). Sin bloqueos programados. */
    public List<NodoVial> calcularRutaMasCorta(NodoVial origen, NodoVial destino) {
        int[] camino = camino(indice(origen.getX(), origen.getY()), indice(destino.getX(), destino.getY()), null);
        return aNodos(camino);
    }

    /** Secuencia de nodos del camino mas corto saliendo a la hora dada (vacia si no hay camino). */
    public List<NodoVial> calcularRutaMasCorta(Coordenada origen, Coordenada destino, double hora) {
        return aNodos(caminoMasCorto(origen, destino, hora));
    }

    /**
     * Camino mas corto saliendo a la hora dada como indices de nodo
     * (origen y destino incluidos); null si el destino queda encerrado.
     */
    public int[] caminoMasCorto(Coordenada origen, Coordenada destino, double hora) {
        int tramo = tramoDe(hora);
        boolean[] bloqueados = bloqueadosEnTramo(tramo, hora);
        return camino(indiceMasCercano(origen), indiceMasCercano(destino), bloqueados == SIN_BLOQUEOS ? null : bloqueados);
    }

    /** Camino recto en L (primero en x, luego en y) ignorando bloqueos: respaldo cuando no hay camino. */
    public int[] caminoSinBloqueos(Coordenada origen, Coordenada destino) {
        int o = indiceMasCercano(origen), d = indiceMasCercano(destino);
        int x = xDe(o), y = yDe(o), xd = xDe(d), yd = yDe(d);
        int largo = Math.abs(xd - x) + Math.abs(yd - y) + 1;
        int[] camino = new int[largo];
        int k = 0;
        camino[k++] = indice(x, y);
        while (x != xd) { x += Integer.signum(xd - x); camino[k++] = indice(x, y); }
        while (y != yd) { y += Integer.signum(yd - y); camino[k++] = indice(x, y); }
        return camino;
    }

    public int xDeNodo(int indiceNodo) { return xDe(indiceNodo); }
    public int yDeNodo(int indiceNodo) { return yDe(indiceNodo); }

    /** true si el nodo (x, y) esta bloqueado por algun bloqueo programado vigente en la hora dada. */
    public boolean estaNodoBloqueado(int x, int y, double hora) {
        if (x < 0 || x > anchoKm || y < 0 || y > altoKm) return false;
        int tramo = tramoDe(hora);
        boolean[] bloqueados = bloqueadosEnTramo(tramo, hora);
        return bloqueados != SIN_BLOQUEOS && bloqueados[indice(x, y)];
    }

    private int[] camino(int o, int d, boolean[] bloqueados) {
        if (o == d) return new int[]{o};
        int[] padre = new int[totalNodos];
        int[] dist = bfs(o, bloqueados, padre);
        if (dist[d] < 0) return null;
        int[] camino = new int[dist[d] + 1];
        int k = camino.length - 1;
        for (int cursor = d; cursor != -1; cursor = padre[cursor]) camino[k--] = cursor;
        return camino;
    }

    private List<NodoVial> aNodos(int[] camino) {
        if (camino == null) return List.of();
        LinkedList<NodoVial> ruta = new LinkedList<>();
        for (int i : camino) ruta.add(new NodoVial(xDe(i), yDe(i)));
        return ruta;
    }

    /**
     * BFS desde el origen. Un nodo bloqueado se puede alcanzar (queda con su
     * distancia) pero no se expande: no se atraviesa. El origen siempre se
     * expande, aunque este bloqueado, para que un vehiculo que acaba de
     * entregar en un nodo bloqueado pueda salir de el. Los tramos cerrados a
     * mano no se recorren en ningun sentido.
     */
    private int[] bfs(int origen, boolean[] bloqueados, int[] padre) {
        int[] dist = new int[totalNodos];
        Arrays.fill(dist, -1);
        if (padre != null) Arrays.fill(padre, -1);
        int[] cola = new int[totalNodos];
        int cabeza = 0, fin = 0;
        cola[fin++] = origen;
        dist[origen] = 0;
        boolean hayTramos = !tramosBloqueados.isEmpty();
        while (cabeza < fin) {
            int u = cola[cabeza++];
            if (u != origen && bloqueados != null && bloqueados[u]) continue;
            int ux = xDe(u), uy = yDe(u);
            for (int[] dir : DIRECCIONES) {
                int vx = ux + dir[0], vy = uy + dir[1];
                if (vx < 0 || vx > anchoKm || vy < 0 || vy > altoKm) continue;
                int v = indice(vx, vy);
                if (dist[v] != -1) continue;
                if (hayTramos && tramosBloqueados.contains(claveTramo(u, v))) continue;
                dist[v] = dist[u] + 1;
                if (padre != null) padre[v] = u;
                cola[fin++] = v;
            }
        }
        return dist;
    }

    // ------------------------------------------------------------------
    // Tramos horarios
    // ------------------------------------------------------------------

    /** Indice del tramo horario que contiene la hora: cantidad de cambios ocurridos hasta ese instante. */
    private int tramoDe(double hora) {
        int bajo = 0, alto = instantesDeCambio.length;
        while (bajo < alto) {
            int medio = (bajo + alto) >>> 1;
            if (instantesDeCambio[medio] <= hora) bajo = medio + 1; else alto = medio;
        }
        return bajo;
    }

    /** Nodos bloqueados en un tramo horario (SIN_BLOQUEOS si ninguno rige). */
    private boolean[] bloqueadosEnTramo(int tramo, double hora) {
        boolean[] bloqueados = nodosBloqueadosPorTramo.get(tramo);
        if (bloqueados != null) return bloqueados;
        boolean alguno = false;
        bloqueados = new boolean[totalNodos];
        for (int i = 0; i < bloqueosProgramados.size(); i++) {
            if (!bloqueosProgramados.get(i).estaVigente(hora)) continue;
            for (int n : nodosPorBloqueo[i]) bloqueados[n] = true;
            alguno = true;
        }
        if (!alguno) bloqueados = SIN_BLOQUEOS;
        nodosBloqueadosPorTramo.put(tramo, bloqueados);
        return bloqueados;
    }

    public int getAnchoKm() { return anchoKm; }
    public int getAltoKm() { return altoKm; }
}
