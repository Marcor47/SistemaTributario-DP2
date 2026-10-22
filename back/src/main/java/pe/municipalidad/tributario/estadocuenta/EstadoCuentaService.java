package pe.municipalidad.tributario.estadocuenta;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.common.RecursoNoEncontradoException;
import pe.municipalidad.tributario.contribuyente.Contribuyente;
import pe.municipalidad.tributario.contribuyente.ContribuyenteRepository;
import pe.municipalidad.tributario.obligacion.ObligacionRepository;

/**
 * HU004 - logica de negocio para el estado de cuenta de un contribuyente.
 *
 * <p>Hoy sirve los datos de la tabla tributario.obligacion (llenada con datos ficticios de
 * prueba). Cuando el ETL este disponible, el esquema ya estara en produccion y estos endpoints
 * funcionaran sin cambios: solo habra que reemplazar los datos de prueba por los reales.</p>
 *
 * <p>desactualizado siempre es false aqui porque la fuente es la propia BD; el flag existe
 * para cuando se agregue un servicio externo (SAMNET) que pueda estar caido, reproduciendo
 * el flujo alterno que ya tiene el front.</p>
 */
@Service
@Transactional(readOnly = true)
public class EstadoCuentaService {

    private final ContribuyenteRepository contribuyenteRepository;
    private final ObligacionRepository obligacionRepository;
    private final Clock reloj;

    public EstadoCuentaService(ContribuyenteRepository contribuyenteRepository,
            ObligacionRepository obligacionRepository, Clock reloj) {
        this.contribuyenteRepository = contribuyenteRepository;
        this.obligacionRepository = obligacionRepository;
        this.reloj = reloj;
    }

    /** Busca contribuyentes por codigo, nombre o numero de documento (coincidencia parcial). */
    public List<ContribuyenteCuentaRespuesta> buscarContribuyentes(String q) {
        if (q == null || q.isBlank()) {
            return List.of();
        }
        return contribuyenteRepository.buscarPorTexto(q.trim())
                .stream()
                .map(ContribuyenteCuentaRespuesta::desde)
                .toList();
    }

    /**
     * Devuelve el estado de cuenta completo, desglosado por tributo y periodo.
     *
     * @param codigo codigo del contribuyente (ej. "021390")
     * @throws RecursoNoEncontradoException si no existe el contribuyente
     */
    public EstadoCuentaRespuesta obtenerEstadoCuenta(String codigo) {
        Contribuyente contribuyente = contribuyenteRepository.findById(codigo)
                .orElseThrow(() -> new RecursoNoEncontradoException("CONTRIBUYENTE_NO_ENCONTRADO",
                        "No existe un contribuyente con codigo " + codigo + "."));

        LocalDate fechaCorte = LocalDate.now(reloj);

        List<EstadoCuentaFilaRespuesta> filas = obligacionRepository
                .findAllByContribuyenteCodigo(codigo)
                .stream()
                .map(o -> EstadoCuentaFilaRespuesta.desde(o, fechaCorte))
                .toList();

        return new EstadoCuentaRespuesta(
                ContribuyenteCuentaRespuesta.desde(contribuyente),
                fechaCorte,
                false,
                filas);
    }
}
