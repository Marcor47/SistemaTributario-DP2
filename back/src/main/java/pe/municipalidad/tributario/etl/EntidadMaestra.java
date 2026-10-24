package pe.municipalidad.tributario.etl;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;

/*
 * Registro único de un contribuyente o predio tras eliminar duplicados, identificado por un seudónimo
 * HMAC-SHA-256 (RF-039, RNF-018). EntidadMaestra 1 ◆ 1..* CorrespondenciaOrigen. La reidentificación
 * (obtenerReidentificado) la hace el servicio de reidentificación, que audita cada acceso.
 */
@Entity
@Table(name = "entidad_maestra", schema = "maestros")
public class EntidadMaestra {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_maestro")
    private Long idMaestro;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_entidad", nullable = false, length = 15)
    private TipoEntidad tipoEntidad;

    @Column(name = "seudonimo", nullable = false, unique = true, length = 64)
    private String seudonimo;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoMaestro estado;

    @OneToMany(mappedBy = "entidadMaestra", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CorrespondenciaOrigen> correspondencias = new ArrayList<>();

    protected EntidadMaestra() {
    }

    public EntidadMaestra(TipoEntidad tipoEntidad, String seudonimo, EstadoMaestro estado) {
        this.tipoEntidad = tipoEntidad;
        this.seudonimo = seudonimo;
        this.estado = estado;
    }

    public void vincular(CorrespondenciaOrigen correspondencia) {
        correspondencia.asignarA(this);
        correspondencias.add(correspondencia);
    }

    public void activar() {
        this.estado = EstadoMaestro.ACTIVA;
    }

    public Long getIdMaestro() {
        return idMaestro;
    }

    public TipoEntidad getTipoEntidad() {
        return tipoEntidad;
    }

    public String getSeudonimo() {
        return seudonimo;
    }

    public EstadoMaestro getEstado() {
        return estado;
    }

    public List<CorrespondenciaOrigen> getCorrespondencias() {
        return List.copyOf(correspondencias);
    }
}
