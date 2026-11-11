package pe.municipalidad.tributario.constancia;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SolicitudConstanciaRepository extends JpaRepository<SolicitudConstancia, Long> {

    List<SolicitudConstancia> findByDocumentoContribuyenteOrderByFechaSolicitudDesc(String documentoContribuyente);

    Optional<SolicitudConstancia> findFirstByDocumentoContribuyenteAndEstadoNotInOrderByFechaSolicitudDesc(
            String documentoContribuyente, Collection<EstadoConstancia> estados);

    /* HU042 por pasos: la solicitud abierta de ventanilla se retoma, no se duplica. */
    Optional<SolicitudConstancia> findFirstByDocumentoContribuyenteAndCanalAndEstadoNotInOrderByFechaSolicitudDesc(
            String documentoContribuyente, CanalSolicitud canal, Collection<EstadoConstancia> estados);

    Optional<SolicitudConstancia> findFirstByDocumentoContribuyenteAndEstadoOrderByFechaSolicitudDesc(
            String documentoContribuyente, EstadoConstancia estado);

    List<SolicitudConstancia> findByEstadoOrderByFechaSolicitudAsc(EstadoConstancia estado);

    /* HU020: abiertas que ya llegaron al operador; en la app, solo las que el contribuyente firmó y envió. */
    @Query("""
            select s from SolicitudConstancia s
            where s.estado not in :cerrados
              and not (s.canal = pe.municipalidad.tributario.constancia.CanalSolicitud.APP
                       and s.estado = pe.municipalidad.tributario.constancia.EstadoConstancia.INICIADA)
            order by s.fechaSolicitud asc, s.idSolicitud asc
            """)
    List<SolicitudConstancia> buscarRecibidasAbiertas(@Param("cerrados") Collection<EstadoConstancia> cerrados);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SolicitudConstancia s where s.idSolicitud = :id")
    Optional<SolicitudConstancia> bloquearPorId(@Param("id") Long id);
}
