package pe.municipalidad.tributario.reporte;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.municipalidad.tributario.seguridad.Usuario;

public interface VistaReporteRepository extends JpaRepository<VistaReporte, Long> {

    List<VistaReporte> findByUsuarioAndTipoReporte(Usuario usuario, TipoReporte tipoReporte);
}
