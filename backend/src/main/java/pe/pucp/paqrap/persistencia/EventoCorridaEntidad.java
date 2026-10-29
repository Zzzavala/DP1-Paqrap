package pe.pucp.paqrap.persistencia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** Incidencias y replanificaciones de una corrida (B25, D19): bloqueos, averias, reasignaciones, colapso. */
@Entity
@Table(name = "evento_corrida", indexes = @Index(name = "idx_evento_corrida", columnList = "corridaId"))
public class EventoCorridaEntidad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(length = 40)
    private String corridaId;
    private LocalDateTime horaSim;
    @Column(length = 40)
    private String tipo;
    @Column(length = 10)
    private String vehiculo;
    @Column(length = 40)
    private String pedido;
    @Column(length = 2000)
    private String detalle;

    public Long getId() { return id; }
    public String getCorridaId() { return corridaId; }
    public void setCorridaId(String corridaId) { this.corridaId = corridaId; }
    public LocalDateTime getHoraSim() { return horaSim; }
    public void setHoraSim(LocalDateTime horaSim) { this.horaSim = horaSim; }
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public String getVehiculo() { return vehiculo; }
    public void setVehiculo(String vehiculo) { this.vehiculo = vehiculo; }
    public String getPedido() { return pedido; }
    public void setPedido(String pedido) { this.pedido = pedido; }
    public String getDetalle() { return detalle; }
    public void setDetalle(String detalle) { this.detalle = detalle; }
}
