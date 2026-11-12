package pe.municipalidad.tributario.reporte;

import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SolicitudReporteRepository extends JpaRepository<SolicitudReporte, Long>,
        JpaSpecificationExecutor<SolicitudReporte> {

    /* contarPorReporte(periodo) del modelo. */
    long countByTipoReporteAndFechaHoraBetween(TipoReporte tipoReporte, LocalDateTime desde, LocalDateTime hasta);

}
