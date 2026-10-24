package pe.municipalidad.tributario.reporte;

import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SolicitudReporteRepository extends JpaRepository<SolicitudReporte, Long> {

    /* contarPorReporte(periodo) del modelo. */
    long countByTipoReporteAndFechaHoraBetween(TipoReporte tipoReporte, LocalDateTime desde, LocalDateTime hasta);
}
