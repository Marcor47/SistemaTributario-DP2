package pe.municipalidad.tributario.tramitepredio;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.municipalidad.tributario.gestiontributaria.TipoPredio;

public interface RequisitoDocumentalRepository extends JpaRepository<RequisitoDocumental, Long> {

    List<RequisitoDocumental> findByTipoTramiteAndTipoPredio(TipoTramitePredio tipoTramite, TipoPredio tipoPredio);
}
