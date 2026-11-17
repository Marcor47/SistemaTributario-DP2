package pe.municipalidad.tributario.documentos;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AvisoCorreoRepository extends JpaRepository<AvisoCorreo, Long> {

    List<AvisoCorreo> findByEstadoAndFechaProgramadaLessThanEqual(EstadoAviso estado, LocalDateTime hasta);

    /* HU016: intentos de recordatorio programados en un rango de fechas, del más reciente al más antiguo. */
    Page<AvisoCorreo> findByTipoAndFechaProgramadaBetween(TipoAviso tipo, LocalDateTime desde, LocalDateTime hasta,
            Pageable pageable);

    List<AvisoCorreo> findByDocumentoContribuyenteOrderByFechaProgramadaDesc(String documentoContribuyente);

    List<AvisoCorreo> findByLoteIdLoteOrderByFechaProgramadaDesc(Long idLote);

    long countByLoteIdLote(Long idLote);

    boolean existsByLoteIdLoteAndIdObligacion(Long idLote, String idObligacion);

    Optional<AvisoCorreo> findFirstByLoteIdLoteAndIdObligacionOrderByIdAvisoDesc(Long idLote, String idObligacion);

    boolean existsByTipoAndIdObligacionAndEstado(TipoAviso tipo, String idObligacion, EstadoAviso estado);

    @Query("SELECT a FROM AvisoCorreo a WHERE a.documentoContribuyente = :documento ORDER BY a.fechaEnvio DESC NULLS LAST, a.fechaProgramada DESC")
    List<AvisoCorreo> buscarHistorialContribuyente(@Param("documento") String documento);
}
