package pe.pucp.paqrap.persistencia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Repositorio Spring Data de la experimentacion numerica (B23, D20). */
public interface ExperimentoRepositorio extends JpaRepository<ExperimentoEntidad, String> {
    List<ExperimentoEntidad> findTop30ByOrderByCreadoDesc();
}
