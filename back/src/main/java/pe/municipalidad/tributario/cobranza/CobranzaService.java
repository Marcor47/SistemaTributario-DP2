package pe.municipalidad.tributario.cobranza;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import pe.municipalidad.tributario.common.RecursoNoEncontradoException;
import pe.municipalidad.tributario.common.ReglaNegocioException;
import pe.municipalidad.tributario.contribuyente.Contribuyente;
import pe.municipalidad.tributario.contribuyente.ContribuyenteService;
import pe.municipalidad.tributario.integracion.samnet.SamnetGateway;
import pe.municipalidad.tributario.obligacion.EstadoObligacion;
import pe.municipalidad.tributario.obligacion.Obligacion;

/* HU013: la obligación y su historial de cobranza se consultan en SAMNET mediante SamnetGateway. */
@Service
public class CobranzaService {

    private final SamnetGateway samnet;
    private final ContribuyenteService contribuyenteService;
    private final Clock reloj;

    public CobranzaService(SamnetGateway samnet, ContribuyenteService contribuyenteService, Clock reloj) {
        this.samnet = samnet;
        this.contribuyenteService = contribuyenteService;
        this.reloj = reloj;
    }

    public EtapaCobranzaRespuesta consultarEtapa(Long obligacionId) {
        Obligacion obligacion = samnet.obtenerObligacion(obligacionId)
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

        Contribuyente contribuyente = contribuyenteService.obtenerPorCodigo(obligacion.getCodigoContribuyente());
        List<EtapaRespuesta> historial = armarHistorial(obligacion,
                samnet.obtenerSeguimientoCobranza(obligacionId));
        EtapaRespuesta etapaActual = historial.getLast();

        return new EtapaCobranzaRespuesta(
                ObligacionResumen.desde(obligacion, contribuyente),
                hoy,
                obligacion.diasVencidaAl(hoy),
                etapaActual,
                ChronoUnit.DAYS.between(etapaActual.fechaInicio(), hoy),
                historial);
    }

    public List<ObligacionVencidaRespuesta> listarVencidas(String contribuyenteCodigo) {
        contribuyenteService.obtenerPorCodigo(contribuyenteCodigo);
        LocalDate hoy = LocalDate.now(reloj);

        return samnet.obtenerEstadoCuenta(contribuyenteCodigo).getObligaciones().stream()
                .filter(obligacion -> obligacion.estaVencida(hoy))
                .sorted(Comparator.comparing(Obligacion::getFechaVencimiento)
                        .thenComparing(obligacion -> obligacion.getTributo().name()))
                .map(obligacion -> {
                    /* Sin etapas registradas en SAMNET, la deuda está en mora temprana desde su vencimiento. */
                    EtapaCobranza etapaActual = obligacion.getEtapaCobranza() == null
                            ? EtapaCobranza.MORA_TEMPRANA
                            : obligacion.getEtapaCobranza();
                    return new ObligacionVencidaRespuesta(
                            obligacion.getIdObligacion(),
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
     * Une la mora temprana implícita con las etapas registradas y calcula la
     * fecha de fin de cada tramo como la fecha de inicio del siguiente. Los
     * seguimientos llegan ordenados por fecha de inicio.
     */
    private List<EtapaRespuesta> armarHistorial(Obligacion obligacion, List<SeguimientoCobranza> seguimientos) {
        List<EtapaRespuesta> tramos = new ArrayList<>();
        boolean moraTempranaRegistrada = !seguimientos.isEmpty()
                && seguimientos.getFirst().etapa() == EtapaCobranza.MORA_TEMPRANA;
        if (!moraTempranaRegistrada) {
            EtapaCobranza moraTemprana = EtapaCobranza.MORA_TEMPRANA;
            tramos.add(new EtapaRespuesta(moraTemprana, moraTemprana.getNombre(), moraTemprana.getDescripcion(),
                    obligacion.getFechaVencimiento().plusDays(1), null, null, null, false));
        }
        for (SeguimientoCobranza seguimiento : seguimientos) {
            EtapaCobranza etapa = seguimiento.etapa();
            tramos.add(new EtapaRespuesta(etapa, etapa.getNombre(), etapa.getDescripcion(),
                    seguimiento.fechaInicio(), null, seguimiento.documentoReferencia(),
                    seguimiento.observacion(), true));
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
