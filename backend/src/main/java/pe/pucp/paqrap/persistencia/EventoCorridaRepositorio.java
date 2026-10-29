package pe.pucp.paqrap.persistencia;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repositorio Spring Data de las incidencias y replanificaciones de cada corrida (B25, D19). */
public interface EventoCorridaRepositorio extends JpaRepository<EventoCorridaEntidad, Long> {
    Page<EventoCorridaEntidad> findByCorridaIdOrderByIdAsc(String corridaId, Pageable pagina);
}
