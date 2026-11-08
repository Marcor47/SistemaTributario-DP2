package pe.municipalidad.tributario.documentos;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AvisoCorreoRepository extends JpaRepository<AvisoCorreo, Long> {

    List<AvisoCorreo> findByEstadoAndFechaProgramadaLessThanEqual(EstadoAviso estado, LocalDateTime hasta);

    List<AvisoCorreo> findByDocumentoContribuyenteOrderByFechaProgramadaDesc(String documentoContribuyente);

    List<AvisoCorreo> findByLoteIdLoteOrderByFechaProgramadaDesc(Long idLote);

    boolean existsByLoteIdLoteAndIdObligacion(Long idLote, String idObligacion);

    @Query("SELECT a FROM AvisoCorreo a WHERE a.documentoContribuyente = :documento ORDER BY a.fechaEnvio DESC NULLS LAST, a.fechaProgramada DESC")
    List<AvisoCorreo> buscarHistorialContribuyente(@Param("documento") String documento);
}
