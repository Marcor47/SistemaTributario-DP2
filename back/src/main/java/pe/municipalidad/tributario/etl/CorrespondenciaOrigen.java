package pe.municipalidad.tributario.etl;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/*
 * Vínculo entre una entidad maestra y un registro de origen de SAMNET (RF-039). Es el dato reidentificable:
 * vive en el esquema maestros y solo lo leen el servicio de reidentificación y el Administrador (RNF-018).
 */
@Entity
@Table(name = "correspondencia_origen", schema = "maestros")
public class CorrespondenciaOrigen {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_correspondencia")
    private Long idCorrespondencia;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_maestro", nullable = false)
    private EntidadMaestra entidadMaestra;

    @Column(name = "sistema_origen", nullable = false, length = 20)
    private String sistemaOrigen;

    @Column(name = "clave_origen", nullable = false, length = 40)
    private String claveOrigen;

    @Column(name = "fecha_vinculo", nullable = false)
    private LocalDateTime fechaVinculo;

    protected CorrespondenciaOrigen() {
    }

    public CorrespondenciaOrigen(String sistemaOrigen, String claveOrigen, LocalDateTime fechaVinculo) {
        this.sistemaOrigen = sistemaOrigen;
        this.claveOrigen = claveOrigen;
        this.fechaVinculo = fechaVinculo;
    }

    void asignarA(EntidadMaestra entidadMaestra) {
        this.entidadMaestra = entidadMaestra;
    }

    public Long getIdCorrespondencia() {
        return idCorrespondencia;
    }

    public EntidadMaestra getEntidadMaestra() {
        return entidadMaestra;
    }

    public String getSistemaOrigen() {
        return sistemaOrigen;
    }

    public String getClaveOrigen() {
        return claveOrigen;
    }

    public LocalDateTime getFechaVinculo() {
        return fechaVinculo;
    }
}
