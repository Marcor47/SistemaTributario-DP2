package pe.municipalidad.tributario.reporteria;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.municipalidad.tributario.seguridad.Usuario;

public interface VistaAnaliticaRepository extends JpaRepository<VistaAnalitica, Long> {

    List<VistaAnalitica> findByUsuarioOrderByFechaActualizacionDesc(Usuario usuario);

    Optional<VistaAnalitica> findByUsuarioAndNombre(Usuario usuario, String nombre);

    Optional<VistaAnalitica> findByIdVistaAndUsuario(Long idVista, Usuario usuario);
}
