package pe.pucp.paqrap.persistencia;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repositorio Spring Data del historial de rutas (B25). */
public interface RutaHistorialRepositorio extends JpaRepository<RutaHistorialEntidad, Long> {
    Page<RutaHistorialEntidad> findByCorridaIdOrderByIdAsc(String corridaId, Pageable pagina);

    long countByCorridaId(String corridaId);
}
