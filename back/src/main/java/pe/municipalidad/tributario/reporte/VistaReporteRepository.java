package pe.municipalidad.tributario.reporte;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.municipalidad.tributario.seguridad.Usuario;

public interface VistaReporteRepository extends JpaRepository<VistaReporte, Long> {

    List<VistaReporte> findByUsuarioOrderByFechaCreacionDesc(Usuario usuario);

    List<VistaReporte> findByUsuarioAndTipoReporte(Usuario usuario, TipoReporte tipoReporte);

    List<VistaReporte> findByUsuarioAndTipoReporteOrderByFechaCreacionDesc(Usuario usuario, TipoReporte tipoReporte);

    Optional<VistaReporte> findByIdVistaAndUsuario(Long idVista, Usuario usuario);

    Optional<VistaReporte> findByUsuarioAndTipoReporteAndNombre(Usuario usuario, TipoReporte tipoReporte, String nombre);

    boolean existsByUsuarioAndTipoReporteAndNombre(Usuario usuario, TipoReporte tipoReporte, String nombre);
}
