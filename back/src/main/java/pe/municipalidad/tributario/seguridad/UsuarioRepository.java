package pe.municipalidad.tributario.seguridad;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    @EntityGraph(attributePaths = "roles")
    Optional<Usuario> findByNombreUsuario(String nombreUsuario);

    @EntityGraph(attributePaths = "roles")
    Optional<Usuario> findByNombreUsuarioIgnoreCase(String nombreUsuario);

    boolean existsByCorreoIgnoreCase(String correo);

    @EntityGraph(attributePaths = "roles")
    Optional<Usuario> findConRolesByIdUsuario(Long idUsuario);
}
