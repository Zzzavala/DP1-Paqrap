package pe.pucp.paqrap.persistencia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Repositorio Spring Data de las corridas (D24). */
public interface CorridaRepositorio extends JpaRepository<CorridaEntidad, String> {
    List<CorridaEntidad> findTop50ByOrderByInicioRealDesc();
}
