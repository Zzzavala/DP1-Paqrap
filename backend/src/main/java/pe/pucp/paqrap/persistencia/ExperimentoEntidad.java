package pe.pucp.paqrap.persistencia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Experimentacion numerica entre los dos algoritmos (B23, D20, C25). */
@Entity
@Table(name = "experimento")
public class ExperimentoEntidad {

    @Id
    @Column(length = 40)
    private String id;
    private Instant creado;
    private Instant terminado;
    @Column(length = 20)
    private String estado;
    @Column(length = 300)
    private String descripcion;
    @Column(columnDefinition = "TEXT")
    private String contenidoJson;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Instant getCreado() { return creado; }
    public void setCreado(Instant creado) { this.creado = creado; }
    public Instant getTerminado() { return terminado; }
    public void setTerminado(Instant terminado) { this.terminado = terminado; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public String getContenidoJson() { return contenidoJson; }
    public void setContenidoJson(String contenidoJson) { this.contenidoJson = contenidoJson; }
}
