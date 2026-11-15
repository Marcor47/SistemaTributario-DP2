package pe.municipalidad.tributario.etl;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/*
 * RF-039, RNF-018: única vía para traducir los seudónimos del modelo estrella a las claves de origen.
 * La lectura la hace el servicio de reidentificación, que audita cada acceso.
 */
public interface CorrespondenciaOrigenRepository extends JpaRepository<CorrespondenciaOrigen, Long> {

    @Query("""
            select c from CorrespondenciaOrigen c
              join fetch c.entidadMaestra e
            where c.sistemaOrigen = :sistemaOrigen
              and e.tipoEntidad = :tipoEntidad
              and e.seudonimo in :seudonimos
            """)
    List<CorrespondenciaOrigen> buscarPorSeudonimos(@Param("seudonimos") Collection<String> seudonimos,
            @Param("tipoEntidad") TipoEntidad tipoEntidad, @Param("sistemaOrigen") String sistemaOrigen);

    /* HU040: entidades maestras a las que está vinculado un código de SAMNET. */
    @Query("""
            select c from CorrespondenciaOrigen c
              join fetch c.entidadMaestra e
            where c.sistemaOrigen = :sistemaOrigen
              and e.tipoEntidad = :tipoEntidad
              and c.claveOrigen = :claveOrigen
            """)
    List<CorrespondenciaOrigen> buscarPorClave(@Param("claveOrigen") String claveOrigen,
            @Param("tipoEntidad") TipoEntidad tipoEntidad, @Param("sistemaOrigen") String sistemaOrigen);

    /* HU045: código de origen -> seudónimo, para resolver el segmento publicado de cada contribuyente. */
    @Query("""
            select c from CorrespondenciaOrigen c
              join fetch c.entidadMaestra e
            where c.sistemaOrigen = :sistemaOrigen
              and e.tipoEntidad = :tipoEntidad
              and c.claveOrigen in :claves
            """)
    List<CorrespondenciaOrigen> buscarPorClaves(@Param("claves") Collection<String> claves,
            @Param("tipoEntidad") TipoEntidad tipoEntidad, @Param("sistemaOrigen") String sistemaOrigen);
}
