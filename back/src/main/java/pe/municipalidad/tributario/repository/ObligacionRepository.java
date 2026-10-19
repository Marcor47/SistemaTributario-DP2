package pe.municipalidad.tributario.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pe.municipalidad.tributario.model.Obligacion;

import java.util.List;

@Repository
public interface ObligacionRepository extends JpaRepository<Obligacion, Long> {

    List<Obligacion> findByContribuyenteIdOrderByPeriodoDescCuotaAsc(Long contribuyenteId);

    /**
     * Invoca la función / procedimiento almacenado de PostgreSQL: analitica.sp_consultar_estado_cuenta
     */
    @Query(value = "SELECT * FROM analitica.sp_consultar_estado_cuenta(:identificador)", nativeQuery = true)
    List<ObligacionViewProjection> consultarPorProcedimientoAlmacenado(@Param("identificador") String identificador);

    /**
     * Consulta directamente sobre la vista analítica: analitica.vw_estado_cuenta_contribuyente
     */
    @Query(value = "SELECT * FROM analitica.vw_estado_cuenta_contribuyente v " +
                   "WHERE UPPER(TRIM(v.codigo_contribuyente)) = UPPER(TRIM(:identificador)) " +
                   "   OR UPPER(TRIM(v.numero_documento)) = UPPER(TRIM(:identificador)) " +
                   "ORDER BY v.periodo DESC, v.cuota ASC, v.tributo_id ASC", nativeQuery = true)
    List<ObligacionViewProjection> consultarPorVistaAnalitica(@Param("identificador") String identificador);
}
