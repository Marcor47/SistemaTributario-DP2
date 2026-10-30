package pe.municipalidad.tributario.constancia;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConstanciaRepository extends JpaRepository<Constancia, String> {

    /* Número asignado por el sistema (RF-055): CNA-AAAA-NNNNNN. */
    @Query(value = "SELECT operativo.fn_siguiente_numero_constancia(:fecha)", nativeQuery = true)
    String siguienteNumero(@Param("fecha") LocalDate fecha);

    Optional<Constancia> findBySolicitud_IdSolicitud(Long idSolicitud);
}
