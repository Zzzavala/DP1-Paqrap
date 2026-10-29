package pe.pucp.paqrap.persistencia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDateTime;

/** Detalle persistido de cada corrida (D24): parametros, fechas, duracion real y cierre. */
@Entity
@Table(name = "corrida")
public class CorridaEntidad {

    @Id
    @Column(length = 40)
    private String id;
    @Column(length = 40)
    private String escenario;
    @Column(length = 10)
    private String algoritmo;
    private long semilla;
    private LocalDateTime fechaInicioSim;
    private LocalDateTime fechaFinSim;
    private Instant inicioReal;
    private Instant finReal;
    @Column(length = 20)
    private String estado;
    @Column(length = 40)
    private String motivoFin;
    private Double cumplimiento;
    private Double costoTotal;
    private Integer pedidos;
    @Column(columnDefinition = "TEXT")
    private String parametrosJson;
    @Column(columnDefinition = "TEXT")
    private String cierreJson;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getEscenario() { return escenario; }
    public void setEscenario(String escenario) { this.escenario = escenario; }
    public String getAlgoritmo() { return algoritmo; }
    public void setAlgoritmo(String algoritmo) { this.algoritmo = algoritmo; }
    public long getSemilla() { return semilla; }
    public void setSemilla(long semilla) { this.semilla = semilla; }
    public LocalDateTime getFechaInicioSim() { return fechaInicioSim; }
    public void setFechaInicioSim(LocalDateTime fechaInicioSim) { this.fechaInicioSim = fechaInicioSim; }
    public LocalDateTime getFechaFinSim() { return fechaFinSim; }
    public void setFechaFinSim(LocalDateTime fechaFinSim) { this.fechaFinSim = fechaFinSim; }
    public Instant getInicioReal() { return inicioReal; }
    public void setInicioReal(Instant inicioReal) { this.inicioReal = inicioReal; }
    public Instant getFinReal() { return finReal; }
    public void setFinReal(Instant finReal) { this.finReal = finReal; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public String getMotivoFin() { return motivoFin; }
    public void setMotivoFin(String motivoFin) { this.motivoFin = motivoFin; }
    public Double getCumplimiento() { return cumplimiento; }
    public void setCumplimiento(Double cumplimiento) { this.cumplimiento = cumplimiento; }
    public Double getCostoTotal() { return costoTotal; }
    public void setCostoTotal(Double costoTotal) { this.costoTotal = costoTotal; }
    public Integer getPedidos() { return pedidos; }
    public void setPedidos(Integer pedidos) { this.pedidos = pedidos; }
    public String getParametrosJson() { return parametrosJson; }
    public void setParametrosJson(String parametrosJson) { this.parametrosJson = parametrosJson; }
    public String getCierreJson() { return cierreJson; }
    public void setCierreJson(String cierreJson) { this.cierreJson = cierreJson; }
}
