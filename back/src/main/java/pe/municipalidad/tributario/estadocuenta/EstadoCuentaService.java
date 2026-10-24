package pe.municipalidad.tributario.estadocuenta;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.contribuyente.Contribuyente;
import pe.municipalidad.tributario.contribuyente.ContribuyenteService;
import pe.municipalidad.tributario.gestiontributaria.EstadoCuenta;
import pe.municipalidad.tributario.gestiontributaria.EstadoCuentaCopia;
import pe.municipalidad.tributario.gestiontributaria.EstadoCuentaCopiaRepository;
import pe.municipalidad.tributario.integracion.samnet.SamnetGateway;
import pe.municipalidad.tributario.integracion.samnet.SamnetNoDisponibleException;
import tools.jackson.databind.json.JsonMapper;

/**
 * HU004 - estado de cuenta de un contribuyente, obtenido de SAMNET con SamnetGateway (ps_ctacte).
 *
 * <p>Cada consulta exitosa guarda la última copia. Si SAMNET no responde, se devuelve esa copia con su
 * fecha de corte y desactualizado = true (RNF-002); si no hay copia, se informa que SAMNET no está
 * disponible.</p>
 */
@Service
public class EstadoCuentaService {

    private final SamnetGateway samnet;
    private final ContribuyenteService contribuyenteService;
    private final EstadoCuentaCopiaRepository copiaRepository;
    private final JsonMapper json;
    private final Clock reloj;

    public EstadoCuentaService(SamnetGateway samnet, ContribuyenteService contribuyenteService,
            EstadoCuentaCopiaRepository copiaRepository, JsonMapper json, Clock reloj) {
        this.samnet = samnet;
        this.contribuyenteService = contribuyenteService;
        this.copiaRepository = copiaRepository;
        this.json = json;
        this.reloj = reloj;
    }

    /** Busca contribuyentes cuyo codigo, nombre o numero de documento contengan el texto (coincidencia parcial). */
    public List<ContribuyenteCuentaRespuesta> buscarContribuyentes(String q) {
        if (q == null || q.isBlank()) {
            return List.of();
        }
        return samnet.buscarContribuyentes(q.trim())
                .stream()
                .map(ContribuyenteCuentaRespuesta::desde)
                .toList();
    }

    /**
     * Devuelve el estado de cuenta completo, desglosado por tributo y periodo.
     *
     * @param codigo codigo del contribuyente (ej. "021390")
     */
    @Transactional
    public EstadoCuentaRespuesta obtenerEstadoCuenta(String codigo) {
        try {
            Contribuyente contribuyente = contribuyenteService.obtenerPorCodigo(codigo);
            EstadoCuenta estadoCuenta = samnet.obtenerEstadoCuenta(codigo);

            List<EstadoCuentaFilaRespuesta> filas = estadoCuenta.getObligaciones()
                    .stream()
                    .map(o -> EstadoCuentaFilaRespuesta.desde(o, estadoCuenta.obtenerFechaCorte()))
                    .toList();
            EstadoCuentaRespuesta respuesta = new EstadoCuentaRespuesta(
                    ContribuyenteCuentaRespuesta.desde(contribuyente),
                    estadoCuenta.obtenerFechaCorte(),
                    false,
                    filas);
            guardarCopia(estadoCuenta, respuesta);
            return respuesta;
        } catch (SamnetNoDisponibleException ex) {
            return copiaRepository.findById(codigo)
                    .map(this::desdeCopia)
                    .orElseThrow(() -> ex);
        }
    }

    private void guardarCopia(EstadoCuenta estadoCuenta, EstadoCuentaRespuesta respuesta) {
        EstadoCuentaCopia copia = copiaRepository.findById(estadoCuenta.getCodigoContribuyente())
                .orElseGet(() -> new EstadoCuentaCopia(estadoCuenta.getCodigoContribuyente()));
        copia.actualizar(estadoCuenta.obtenerFechaCorte(), estadoCuenta.getTotalDeuda(),
                estadoCuenta.getTotalPagado(), json.writeValueAsString(respuesta), LocalDateTime.now(reloj));
        copiaRepository.save(copia);
    }

    private EstadoCuentaRespuesta desdeCopia(EstadoCuentaCopia copia) {
        EstadoCuentaRespuesta guardada = json.readValue(copia.getDetalle(), EstadoCuentaRespuesta.class);
        return new EstadoCuentaRespuesta(guardada.contribuyente(), copia.getFechaCorte(), true, guardada.filas());
    }
}
