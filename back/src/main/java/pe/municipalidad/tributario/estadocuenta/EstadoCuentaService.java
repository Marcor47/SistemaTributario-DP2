package pe.municipalidad.tributario.estadocuenta;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Predicate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.contribuyente.Contribuyente;
import pe.municipalidad.tributario.contribuyente.ContribuyenteService;
import pe.municipalidad.tributario.gestiontributaria.EstadoCuenta;
import pe.municipalidad.tributario.gestiontributaria.EstadoCuentaCopia;
import pe.municipalidad.tributario.gestiontributaria.EstadoCuentaCopiaRepository;
import pe.municipalidad.tributario.integracion.samnet.SamnetGateway;
import pe.municipalidad.tributario.integracion.samnet.SamnetNoDisponibleException;
import pe.municipalidad.tributario.obligacion.TasaInteresMoratorio;
import pe.municipalidad.tributario.obligacion.Tributo;
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

    private static final String ESTADO_VENCIDA = "Vencida";

    private final SamnetGateway samnet;
    private final ContribuyenteService contribuyenteService;
    private final EstadoCuentaCopiaRepository copiaRepository;
    private final JsonMapper json;
    private final Clock reloj;
    private final TasaInteresMoratorio tim;

    public EstadoCuentaService(SamnetGateway samnet, ContribuyenteService contribuyenteService,
            EstadoCuentaCopiaRepository copiaRepository, JsonMapper json, Clock reloj, TasaInteresMoratorio tim) {
        this.samnet = samnet;
        this.contribuyenteService = contribuyenteService;
        this.copiaRepository = copiaRepository;
        this.json = json;
        this.reloj = reloj;
        this.tim = tim;
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
     * Devuelve el estado de cuenta completo: cada cuota por tributo, periodo y predio, el resumen de cada año con
     * lo que es Impuesto Predial y lo que es arbitrios, y los predios del contribuyente.
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
                    .map(o -> EstadoCuentaFilaRespuesta.desde(o, estadoCuenta.obtenerFechaCorte(), tim.mensual()))
                    .toList();
            List<EstadoCuentaRespuesta.PredioCuenta> predios = samnet.buscarPredio(null, codigo).stream()
                    .map(p -> new EstadoCuentaRespuesta.PredioCuenta(p.getCodigoPredio(), p.getUbicacion(),
                            p.getUsoPredio(), p.getZona(),
                            p.getTitulares().stream().filter(t -> t.codigoContribuyente().equals(codigo))
                                    .map(t -> t.porcentajePropiedad()).findFirst().orElse(null)))
                    .toList();
            EstadoCuentaRespuesta respuesta = new EstadoCuentaRespuesta(
                    ContribuyenteCuentaRespuesta.desde(contribuyente),
                    estadoCuenta.obtenerFechaCorte(),
                    false,
                    tim.mensual(),
                    resumenAnual(filas),
                    predios,
                    filas);
            guardarCopia(estadoCuenta, respuesta);
            return respuesta;
        } catch (SamnetNoDisponibleException ex) {
            return copiaRepository.findById(codigo)
                    .map(this::desdeCopia)
                    .orElseThrow(() -> ex);
        }
    }

    /* Responde a la pregunta de cuánto es Impuesto Predial y cuánto arbitrios en cada año, con lo vencido aparte. */
    static List<EstadoCuentaRespuesta.ResumenAnual> resumenAnual(List<EstadoCuentaFilaRespuesta> filas) {
        Map<Integer, List<EstadoCuentaFilaRespuesta>> porAnio = new TreeMap<>();
        filas.forEach(f -> porAnio.computeIfAbsent(f.anio(), a -> new ArrayList<>()).add(f));
        return porAnio.entrySet().stream()
                .map(e -> new EstadoCuentaRespuesta.ResumenAnual(e.getKey(),
                        totales(e.getValue(), f -> Tributo.IMPUESTO_PREDIAL.name().equals(f.tributoCodigo())),
                        totales(e.getValue(), f -> Tributo.ARBITRIOS.name().equals(f.tributoCodigo())),
                        totales(e.getValue(), f -> true)))
                .toList();
    }

    private static EstadoCuentaRespuesta.Totales totales(List<EstadoCuentaFilaRespuesta> filas,
            Predicate<EstadoCuentaFilaRespuesta> filtro) {
        List<EstadoCuentaFilaRespuesta> seleccion = filas.stream().filter(filtro).toList();
        return new EstadoCuentaRespuesta.Totales(
                suma(seleccion, EstadoCuentaFilaRespuesta::emitido),
                suma(seleccion, EstadoCuentaFilaRespuesta::pagado),
                suma(seleccion, EstadoCuentaFilaRespuesta::deuda),
                suma(seleccion.stream().filter(f -> ESTADO_VENCIDA.equals(f.estado())).toList(),
                        EstadoCuentaFilaRespuesta::deuda),
                suma(seleccion, EstadoCuentaFilaRespuesta::interesMoratorio),
                suma(seleccion, EstadoCuentaFilaRespuesta::totalAPagar));
    }

    private static BigDecimal suma(List<EstadoCuentaFilaRespuesta> filas,
            Function<EstadoCuentaFilaRespuesta, BigDecimal> campo) {
        return filas.stream().map(campo).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void guardarCopia(EstadoCuenta estadoCuenta, EstadoCuentaRespuesta respuesta) {
        EstadoCuentaCopia copia = copiaRepository.findById(estadoCuenta.getCodigoContribuyente())
                .orElseGet(() -> new EstadoCuentaCopia(estadoCuenta.getCodigoContribuyente()));
        copia.actualizar(estadoCuenta.obtenerFechaCorte(), estadoCuenta.getTotalDeuda(),
                estadoCuenta.getTotalPagado(), json.writeValueAsString(respuesta), LocalDateTime.now(reloj));
        copiaRepository.save(copia);
    }

    /* La copia conserva los montos e intereses a su fecha de corte; no se recalculan. */
    private EstadoCuentaRespuesta desdeCopia(EstadoCuentaCopia copia) {
        EstadoCuentaRespuesta guardada = json.readValue(copia.getDetalle(), EstadoCuentaRespuesta.class);
        return new EstadoCuentaRespuesta(guardada.contribuyente(), copia.getFechaCorte(), true, guardada.timMensual(),
                guardada.resumenAnual(), guardada.predios(), guardada.filas());
    }
}
