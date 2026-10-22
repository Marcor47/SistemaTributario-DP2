package pe.municipalidad.tributario.cobranza;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.common.RecursoNoEncontradoException;
import pe.municipalidad.tributario.common.ReglaNegocioException;
import pe.municipalidad.tributario.contribuyente.ContribuyenteService;
import pe.municipalidad.tributario.obligacion.EstadoObligacion;
import pe.municipalidad.tributario.obligacion.Obligacion;
import pe.municipalidad.tributario.obligacion.ObligacionRepository;

@Service
@Transactional(readOnly = true)
public class CobranzaService {

    private final ObligacionRepository obligacionRepository;
    private final SeguimientoCobranzaRepository seguimientoCobranzaRepository;
    private final ContribuyenteService contribuyenteService;
    private final Clock reloj;

    public CobranzaService(ObligacionRepository obligacionRepository,
            SeguimientoCobranzaRepository seguimientoCobranzaRepository,
            ContribuyenteService contribuyenteService, Clock reloj) {
        this.obligacionRepository = obligacionRepository;
        this.seguimientoCobranzaRepository = seguimientoCobranzaRepository;
        this.contribuyenteService = contribuyenteService;
        this.reloj = reloj;
    }

    public EtapaCobranzaRespuesta consultarEtapa(Long obligacionId) {
        Obligacion obligacion = obligacionRepository.findWithContribuyenteById(obligacionId)
                .orElseThrow(() -> new RecursoNoEncontradoException("OBLIGACION_NO_ENCONTRADA",
                        "No existe la obligación con id " + obligacionId + "."));
        LocalDate hoy = LocalDate.now(reloj);

        EstadoObligacion estado = obligacion.estadoAl(hoy);
        if (estado != EstadoObligacion.VENCIDA) {
            throw new ReglaNegocioException("OBLIGACION_NO_VENCIDA",
                    "La obligación " + obligacion.getTributo().getDescripcion() + " " + obligacion.etiquetaPeriodo()
                            + " está " + estado.name().toLowerCase() + "; solo las obligaciones vencidas tienen"
                            + " etapa de cobranza.");
        }

        List<EtapaRespuesta> historial = armarHistorial(obligacion,
                seguimientoCobranzaRepository.findByObligacionIdOrderByFechaInicioAscIdAsc(obligacionId));
        EtapaRespuesta etapaActual = historial.getLast();

        return new EtapaCobranzaRespuesta(
                ObligacionResumen.desde(obligacion),
                hoy,
                obligacion.diasVencidaAl(hoy),
                etapaActual,
                ChronoUnit.DAYS.between(etapaActual.fechaInicio(), hoy),
                historial);
    }

    public List<ObligacionVencidaRespuesta> listarVencidas(String contribuyenteCodigo) {
        contribuyenteService.obtenerPorCodigo(contribuyenteCodigo);
        LocalDate hoy = LocalDate.now(reloj);

        List<Obligacion> vencidas = obligacionRepository.buscarVencidas(contribuyenteCodigo, hoy);
        if (vencidas.isEmpty()) {
            return List.of();
        }
        Map<Long, List<SeguimientoCobranza>> seguimientosPorObligacion = seguimientoCobranzaRepository
                .findByObligacionIdInOrderByFechaInicioAscIdAsc(vencidas.stream().map(Obligacion::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(SeguimientoCobranza::getObligacionId));

        return vencidas.stream()
                .map(obligacion -> {
                    List<SeguimientoCobranza> seguimientos =
                            seguimientosPorObligacion.getOrDefault(obligacion.getId(), List.of());
                    EtapaCobranza etapaActual = seguimientos.isEmpty()
                            ? EtapaCobranza.ORDINARIA
                            : seguimientos.getLast().getEtapa();
                    return new ObligacionVencidaRespuesta(
                            obligacion.getId(),
                            obligacion.getTributo(),
                            obligacion.getTributo().getDescripcion(),
                            obligacion.etiquetaPeriodo(),
                            obligacion.getCodigoPredio(),
                            obligacion.getFechaVencimiento(),
                            obligacion.saldo(),
                            obligacion.diasVencidaAl(hoy),
                            etapaActual,
                            etapaActual.getNombre());
                })
                .toList();
    }

    /*
     * Une la cobranza ordinaria implícita con las etapas registradas y calcula la
     * fecha de fin de cada tramo como la fecha de inicio del siguiente. Los
     * seguimientos llegan ordenados por fecha de inicio.
     */
    private List<EtapaRespuesta> armarHistorial(Obligacion obligacion, List<SeguimientoCobranza> seguimientos) {
        List<EtapaRespuesta> tramos = new ArrayList<>();
        boolean ordinariaRegistrada = !seguimientos.isEmpty()
                && seguimientos.getFirst().getEtapa() == EtapaCobranza.ORDINARIA;
        if (!ordinariaRegistrada) {
            EtapaCobranza ordinaria = EtapaCobranza.ORDINARIA;
            tramos.add(new EtapaRespuesta(ordinaria, ordinaria.getNombre(), ordinaria.getDescripcion(),
                    obligacion.getFechaVencimiento().plusDays(1), null, null, null, false));
        }
        for (SeguimientoCobranza seguimiento : seguimientos) {
            EtapaCobranza etapa = seguimiento.getEtapa();
            tramos.add(new EtapaRespuesta(etapa, etapa.getNombre(), etapa.getDescripcion(),
                    seguimiento.getFechaInicio(), null, seguimiento.getDocumentoReferencia(),
                    seguimiento.getObservacion(), true));
        }

        List<EtapaRespuesta> historial = new ArrayList<>(tramos.size());
        for (int i = 0; i < tramos.size(); i++) {
            EtapaRespuesta tramo = tramos.get(i);
            LocalDate fechaFin = i + 1 < tramos.size() ? tramos.get(i + 1).fechaInicio() : null;
            historial.add(new EtapaRespuesta(tramo.etapa(), tramo.nombre(), tramo.descripcion(), tramo.fechaInicio(),
                    fechaFin, tramo.documentoReferencia(), tramo.observacion(), tramo.registrada()));
        }
        return historial;
    }
}
