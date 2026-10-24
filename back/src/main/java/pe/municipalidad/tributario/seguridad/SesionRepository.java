package pe.municipalidad.tributario.seguridad;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SesionRepository extends JpaRepository<Sesion, String> {
}
