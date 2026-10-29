package pe.pucp.paqrap.persistencia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** Historial de todas las rutas planificadas y replanificadas, con la causa del cambio (B25, A20). */
@Entity
@Table(name = "ruta_historial", indexes = @Index(name = "idx_ruta_corrida", columnList = "corridaId"))
public class RutaHistorialEntidad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(length = 40)
    private String corridaId;
    @Column(length = 30)
    private String idRuta;
    @Column(length = 10)
    private String vehiculo;
    @Column(length = 40)
    private String causa;
    private LocalDateTime horaSalida;
    private LocalDateTime horaRegreso;
    @Column(length = 60)
    private String almacenSalida;
    @Column(length = 60)
    private String almacenRetorno;
    @Column(length = 4000)
    private String pedidos;
    private int unidades;
    private double km;
    private double costo;

    public Long getId() { return id; }
    public String getCorridaId() { return corridaId; }
    public void setCorridaId(String corridaId) { this.corridaId = corridaId; }
    public String getIdRuta() { return idRuta; }
    public void setIdRuta(String idRuta) { this.idRuta = idRuta; }
    public String getVehiculo() { return vehiculo; }
    public void setVehiculo(String vehiculo) { this.vehiculo = vehiculo; }
    public String getCausa() { return causa; }
    public void setCausa(String causa) { this.causa = causa; }
    public LocalDateTime getHoraSalida() { return horaSalida; }
    public void setHoraSalida(LocalDateTime horaSalida) { this.horaSalida = horaSalida; }
    public LocalDateTime getHoraRegreso() { return horaRegreso; }
    public void setHoraRegreso(LocalDateTime horaRegreso) { this.horaRegreso = horaRegreso; }
    public String getAlmacenSalida() { return almacenSalida; }
    public void setAlmacenSalida(String almacenSalida) { this.almacenSalida = almacenSalida; }
    public String getAlmacenRetorno() { return almacenRetorno; }
    public void setAlmacenRetorno(String almacenRetorno) { this.almacenRetorno = almacenRetorno; }
    public String getPedidos() { return pedidos; }
    public void setPedidos(String pedidos) { this.pedidos = pedidos; }
    public int getUnidades() { return unidades; }
    public void setUnidades(int unidades) { this.unidades = unidades; }
    public double getKm() { return km; }
    public void setKm(double km) { this.km = km; }
    public double getCosto() { return costo; }
    public void setCosto(double costo) { this.costo = costo; }
}
