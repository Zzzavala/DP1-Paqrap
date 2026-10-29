package pe.pucp.paqrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Punto de entrada del sistema PaqRap (backend Spring Boot).
 *
 * Reune los cuatro componentes del sistema en un unico servicio:
 * - pe.pucp.paqrap.registro: registro de pedidos y clientes (Modulo A).
 * - pe.pucp.paqrap.planificador: planificador con las dos metaheuristicas (Modulo B).
 * - pe.pucp.paqrap.visualizador: estado que consume el visualizador web (Modulo C).
 * - pe.pucp.paqrap.flota y pe.pucp.paqrap.simulacion: flota, turnos, simulacion y reportes (Modulo D).
 *
 * Cuando el frontend se compila en modo estatico, sus archivos se empaquetan en
 * src/main/resources/static y este mismo servicio los publica en "/".
 */
@SpringBootApplication
@EnableScheduling
public class PaqRapApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaqRapApplication.class, args);
    }
}
