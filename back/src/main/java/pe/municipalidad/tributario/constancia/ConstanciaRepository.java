package pe.municipalidad.tributario.constancia;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConstanciaRepository extends JpaRepository<Constancia, String> {

    /* Código único y no predecible asignado por el sistema (RF-055): CND-AAAA-XXXXXX (V52). */
    @Query(value = "SELECT operativo.fn_siguiente_numero_constancia(:fecha)", nativeQuery = true)
    String siguienteNumero(@Param("fecha") LocalDate fecha);

    Optional<Constancia> findBySolicitud_IdSolicitud(Long idSolicitud);

    List<Constancia> findBySolicitud_DocumentoContribuyenteOrderByFechaEmisionDesc(String documentoContribuyente);

    List<Constancia> findTop50ByOrderByFechaEmisionDesc();
}
