package pe.municipalidad.tributario.reporte;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pe.municipalidad.tributario.common.NoAutenticadoException;
import pe.municipalidad.tributario.common.ReglaNegocioException;
import pe.municipalidad.tributario.etl.EstadoLote;
import pe.municipalidad.tributario.etl.LoteETL;
import pe.municipalidad.tributario.etl.LoteETLRepository;
import pe.municipalidad.tributario.integracion.samnet.SamnetGateway;
import pe.municipalidad.tributario.reporte.ReportePricosRespuesta.DeudaAnioCorte;
import pe.municipalidad.tributario.reporte.ReportePricosRespuesta.Documento;
import pe.municipalidad.tributario.reporte.ReportePricosRespuesta.DomicilioFiscal;
import pe.municipalidad.tributario.reporte.ReportePricosRespuesta.FilaPricos;
import pe.municipalidad.tributario.reporte.ReportePricosRespuesta.Tributos;
import pe.municipalidad.tributario.seguridad.Rol;
import pe.municipalidad.tributario.seguridad.Usuario;
import pe.municipalidad.tributario.seguridad.UsuarioRepository;

/*
 * HU037 (RF-050): reporte de Principales Contribuyentes. La fecha de corte es la de la última carga publicada del
 * ETL (RF-008). Los datos salen de los procedimientos autorizados de SAMNET para el ETL, porque el modelo estrella
 * solo guarda seudónimos y el reporte debe mostrar la identidad del contribuyente (RNF-018). Cada generación
 * queda registrada como solicitud de reporte (RF-059).
 *
 * Deuda: insoluto de las cuotas ya vencidas a la fecha de corte; el insoluto de SAMNET ya descuenta los beneficios
 * aplicados. Base imponible: la de los predios del contribuyente según su porcentaje de propiedad.
 */
@Service
@Transactional(readOnly = true)
public class ReportePricosService {

    private static final String TRIBUTO_PREDIAL = "IMPUESTO_PREDIAL";
    private static final String TRIBUTO_ARBITRIOS = "ARBITRIOS";
    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    private final LoteETLRepository loteETLRepository;
    private final SamnetGateway samnet;
    private final UsuarioRepository usuarioRepository;
    private final RestriccionReporteRepository restriccionReporteRepository;
    private final SolicitudReporteRepository solicitudReporteRepository;
    private final Clock reloj;

    public ReportePricosService(LoteETLRepository loteETLRepository, SamnetGateway samnet,
            UsuarioRepository usuarioRepository, RestriccionReporteRepository restriccionReporteRepository,
            SolicitudReporteRepository solicitudReporteRepository, Clock reloj) {
        this.loteETLRepository = loteETLRepository;
        this.samnet = samnet;
        this.usuarioRepository = usuarioRepository;
        this.restriccionReporteRepository = restriccionReporteRepository;
        this.solicitudReporteRepository = solicitudReporteRepository;
        this.reloj = reloj;
    }

    @Transactional
    public ReportePricosRespuesta generar(String nombreUsuario, CriterioPricos criterio, int limite) {
        Usuario usuario = usuarioRepository.findByNombreUsuario(nombreUsuario).orElseThrow(() ->
                new NoAutenticadoException("SESION_REQUERIDA", "Inicie sesión como usuario interno."));
        exigirAcceso(usuario);

        LoteETL lote = loteETLRepository.findTopByEstadoOrderByFechaCorteDescIdLoteDesc(EstadoLote.PUBLICADO)
                .orElseThrow(() -> new ReglaNegocioException("SIN_CARGA_ETL",
                        "No hay una carga publicada del ETL: no se puede generar el reporte PRICOS."));
        LocalDate fechaCorte = lote.getFechaCorte();

        List<Map<String, Object>> contribuyentes = samnet.extraerParaETL("ps_etl_contribuyentes", fechaCorte);
        Map<String, BigDecimal> basesImponibles = basesImponibles(samnet.extraerParaETL("ps_etl_predios", fechaCorte));
        Map<String, Deudas> deudas = deudas(samnet.extraerParaETL("ps_etl_cobranza", fechaCorte), fechaCorte);

        List<Candidato> candidatos = new ArrayList<>();
        for (Map<String, Object> contribuyente : contribuyentes) {
            String codigo = texto(contribuyente, "codigo");
            candidatos.add(new Candidato(contribuyente, basesImponibles.getOrDefault(codigo, BigDecimal.ZERO),
                    deudas.getOrDefault(codigo, Deudas.VACIAS)));
        }
        candidatos.sort(ordenPara(criterio));

        List<FilaPricos> filas = new ArrayList<>();
        for (Candidato candidato : candidatos.stream().limit(limite).toList()) {
            filas.add(fila(filas.size() + 1, candidato));
        }

        solicitudReporteRepository.save(new SolicitudReporte(TipoReporte.PRICOS, usuario, LocalDateTime.now(reloj),
                "{\"criterio\":\"" + criterio + "\",\"limite\":" + limite + "}", FormatoExportacion.NINGUNO));
        return new ReportePricosRespuesta(lote.getIdLote(), fechaCorte, criterio, limite, candidatos.size(), filas);
    }

    /* RF-060: un rol con el reporte completo restringido no puede generarlo. */
    private void exigirAcceso(Usuario usuario) {
        for (Rol rol : usuario.getRoles()) {
            boolean restringido = restriccionReporteRepository.findByRolAndTipoReporte(rol, TipoReporte.PRICOS)
                    .stream().anyMatch(restriccion -> !restriccion.permite(TipoReporte.PRICOS,
                            DimensionRestringida.REPORTE, null));
            if (restringido) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tiene permiso para este reporte.");
            }
        }
    }

    private static Comparator<Candidato> ordenPara(CriterioPricos criterio) {
        Comparator<Candidato> porCriterio = criterio == CriterioPricos.DEUDA_TOTAL
                ? Comparator.comparing((Candidato candidato) -> candidato.deudas().total()).reversed()
                : Comparator.comparing(Candidato::baseImponible).reversed();
        return porCriterio.thenComparing(candidato -> texto(candidato.contribuyente(), "codigo"));
    }

    private static FilaPricos fila(int posicion, Candidato candidato) {
        Map<String, Object> c = candidato.contribuyente();
        Deudas deudas = candidato.deudas();
        return new FilaPricos(
                posicion,
                texto(c, "codigo"),
                texto(c, "nombre"),
                texto(c, "regimen"),
                new Documento(texto(c, "tipo_documento"), texto(c, "numero_documento")),
                new DomicilioFiscal(texto(c, "domicilio_fiscal"), texto(c, "dom_via"), texto(c, "dom_numero"),
                        texto(c, "dom_departamento"), texto(c, "dom_interior"), texto(c, "dom_manzana"),
                        texto(c, "dom_lote"), texto(c, "dom_distrito")),
                candidato.baseImponible(),
                new Tributos(deudas.emisionPredial(), deudas.emisionArbitrios()),
                new Tributos(deudas.anteriorPredial(), deudas.anteriorArbitrios()),
                new DeudaAnioCorte(deudas.actualPredial(), deudas.actualArbitrios(), deudas.descuentoArbitrios()),
                texto(c, "condicion"),
                texto(c, "tipo_persona"),
                texto(c, "correo"),
                texto(c, "telefonos"));
    }

    private static Map<String, BigDecimal> basesImponibles(List<Map<String, Object>> predios) {
        Map<String, BigDecimal> bases = new HashMap<>();
        for (Map<String, Object> predio : predios) {
            String titular = texto(predio, "contribuyente_codigo");
            if (titular == null || predio.get("fecha_baja") != null) {
                continue;
            }
            BigDecimal porcentaje = monto(predio, "porcentaje_propiedad", CIEN);
            BigDecimal parte = monto(predio, "base_imponible", BigDecimal.ZERO).multiply(porcentaje)
                    .divide(CIEN, 2, RoundingMode.HALF_UP);
            bases.merge(titular, parte, BigDecimal::add);
        }
        return bases;
    }

    private static Map<String, Deudas> deudas(List<Map<String, Object>> cuotas, LocalDate fechaCorte) {
        Map<String, Deudas> deudas = new HashMap<>();
        for (Map<String, Object> cuota : cuotas) {
            String codigo = texto(cuota, "contribuyente_codigo");
            boolean predial = TRIBUTO_PREDIAL.equals(texto(cuota, "tributo"));
            boolean arbitrios = TRIBUTO_ARBITRIOS.equals(texto(cuota, "tributo"));
            if (codigo == null || !(predial || arbitrios)) {
                continue;
            }
            int anio = ((Number) cuota.get("anio")).intValue();
            Deudas acumulado = deudas.getOrDefault(codigo, Deudas.VACIAS);
            if (anio == fechaCorte.getYear()) {
                acumulado = acumulado.conEmision(predial, monto(cuota, "monto_emitido", BigDecimal.ZERO));
            }
            if (fecha(cuota, "fecha_vencimiento").isBefore(fechaCorte)) {
                BigDecimal insoluto = monto(cuota, "insoluto", BigDecimal.ZERO);
                if (insoluto.signum() > 0) {
                    acumulado = acumulado.conDeuda(anio < fechaCorte.getYear(), predial, insoluto,
                            monto(cuota, "porcentaje_descuento", BigDecimal.ZERO));
                }
            }
            deudas.put(codigo, acumulado);
        }
        return deudas;
    }

    private static String texto(Map<String, Object> fila, String columna) {
        Object valor = fila.get(columna);
        return valor == null ? null : valor.toString();
    }

    private static BigDecimal monto(Map<String, Object> fila, String columna, BigDecimal porDefecto) {
        Object valor = fila.get(columna);
        return valor == null ? porDefecto : new BigDecimal(valor.toString());
    }

    private static LocalDate fecha(Map<String, Object> fila, String columna) {
        return ((Date) fila.get(columna)).toLocalDate();
    }

    private record Candidato(Map<String, Object> contribuyente, BigDecimal baseImponible, Deudas deudas) {
    }

    /* Acumulado de un contribuyente: emisión y deuda del año de corte y deuda de años anteriores. */
    private record Deudas(BigDecimal emisionPredial, BigDecimal emisionArbitrios, BigDecimal anteriorPredial,
            BigDecimal anteriorArbitrios, BigDecimal actualPredial, BigDecimal actualArbitrios,
            BigDecimal descuentoArbitrios) {

        static final Deudas VACIAS = new Deudas(cero(), cero(), cero(), cero(), cero(), cero(), cero());

        private static BigDecimal cero() {
            return BigDecimal.ZERO.setScale(2);
        }

        BigDecimal total() {
            return anteriorPredial.add(anteriorArbitrios).add(actualPredial).add(actualArbitrios);
        }

        Deudas conEmision(boolean predial, BigDecimal monto) {
            return predial
                    ? new Deudas(emisionPredial.add(monto), emisionArbitrios, anteriorPredial, anteriorArbitrios,
                            actualPredial, actualArbitrios, descuentoArbitrios)
                    : new Deudas(emisionPredial, emisionArbitrios.add(monto), anteriorPredial, anteriorArbitrios,
                            actualPredial, actualArbitrios, descuentoArbitrios);
        }

        Deudas conDeuda(boolean anterior, boolean predial, BigDecimal insoluto, BigDecimal porcentajeDescuento) {
            if (anterior) {
                return predial
                        ? new Deudas(emisionPredial, emisionArbitrios, anteriorPredial.add(insoluto),
                                anteriorArbitrios, actualPredial, actualArbitrios, descuentoArbitrios)
                        : new Deudas(emisionPredial, emisionArbitrios, anteriorPredial,
                                anteriorArbitrios.add(insoluto), actualPredial, actualArbitrios, descuentoArbitrios);
            }
            return predial
                    ? new Deudas(emisionPredial, emisionArbitrios, anteriorPredial, anteriorArbitrios,
                            actualPredial.add(insoluto), actualArbitrios, descuentoArbitrios)
                    : new Deudas(emisionPredial, emisionArbitrios, anteriorPredial, anteriorArbitrios,
                            actualPredial, actualArbitrios.add(insoluto),
                            descuentoArbitrios.max(porcentajeDescuento));
        }
    }
}
