package pe.municipalidad.tributario.contribuyente;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContribuyenteRepository extends JpaRepository<Contribuyente, String> {

    Optional<Contribuyente> findByTipoDocumentoAndNumeroDocumento(TipoDocumento tipoDocumento, String numeroDocumento);

    /* HU004: busqueda libre por codigo, nombre o numero de documento (insensible a mayusculas). */
    @Query("""
            select c from Contribuyente c
            where upper(c.codigo)          like upper(concat('%', :q, '%'))
               or upper(c.nombre)          like upper(concat('%', :q, '%'))
               or upper(c.numeroDocumento) like upper(concat('%', :q, '%'))
            order by c.nombre
            """)
    List<Contribuyente> buscarPorTexto(@Param("q") String q);
}
