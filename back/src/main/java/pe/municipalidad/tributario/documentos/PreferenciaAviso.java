package pe.municipalidad.tributario.documentos;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/*
 * HU016 (RF-022): preferencia del contribuyente para recibir el recordatorio de vencimiento por correo.
 * No tiene FK hacia SAMNET: el código de contribuyente es una referencia externa, como en el resto de operativo.
 */
@Entity
@Table(name = "preferencia_aviso", schema = "operativo")
public class PreferenciaAviso {

    @Id
    @Column(name = "codigo_contribuyente", nullable = false, length = 10)
    private String codigoContribuyente;

    @Column(name = "recordatorio_vencimiento", nullable = false)
    private boolean recordatorioVencimiento;

    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn;

    protected PreferenciaAviso() {
    }

    public PreferenciaAviso(String codigoContribuyente, boolean recordatorioVencimiento, LocalDateTime actualizadoEn) {
        this.codigoContribuyente = codigoContribuyente;
        this.recordatorioVencimiento = recordatorioVencimiento;
        this.actualizadoEn = actualizadoEn;
    }

    public void cambiarRecordatorio(boolean activo, LocalDateTime ahora) {
        this.recordatorioVencimiento = activo;
        this.actualizadoEn = ahora;
    }

    public String getCodigoContribuyente() {
        return codigoContribuyente;
    }

    public boolean isRecordatorioVencimiento() {
        return recordatorioVencimiento;
    }

    public LocalDateTime getActualizadoEn() {
        return actualizadoEn;
    }
}
