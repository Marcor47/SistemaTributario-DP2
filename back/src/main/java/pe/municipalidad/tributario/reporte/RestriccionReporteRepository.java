package pe.municipalidad.tributario.reporte;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.municipalidad.tributario.seguridad.Rol;

public interface RestriccionReporteRepository extends JpaRepository<RestriccionReporte, Long> {

    List<RestriccionReporte> findByRolAndTipoReporte(Rol rol, TipoReporte tipoReporte);
}
