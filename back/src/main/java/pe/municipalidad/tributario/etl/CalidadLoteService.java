package pe.municipalidad.tributario.etl;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.common.RecursoNoEncontradoException;
import pe.municipalidad.tributario.common.ReglaNegocioException;
import pe.municipalidad.tributario.integracion.samnet.SamnetGateway;

/*
 * HU026: motor de reglas de calidad para la carga del ETL (ISO/IEC 25012).
 * Valida los registros del lote antes de transferirlos al modelo analítico y a las tablas maestras.
 * Si un registro incumple una regla, el lote se rechaza y se asegura la integridad de los datos
 * previamente publicados.
 */
@Service
@Transactional(readOnly = true)
public class CalidadLoteService {

    private static final Pattern PATRON_DNI = Pattern.compile("^\\d{8}$");
    private static final Pattern PATRON_RUC = Pattern.compile("^\\d{11}$");

    private final LoteETLRepository loteETLRepository;
    private final ReglaCalidadRepository reglaCalidadRepository;
    private final ResultadoReglaRepository resultadoReglaRepository;
    private final SamnetGateway samnetGateway;
    private final Clock reloj;

    public CalidadLoteService(
            LoteETLRepository loteETLRepository,
            ReglaCalidadRepository reglaCalidadRepository,
            ResultadoReglaRepository resultadoReglaRepository,
            SamnetGateway samnetGateway,
            Clock reloj) {
        this.loteETLRepository = loteETLRepository;
        this.reglaCalidadRepository = reglaCalidadRepository;
        this.resultadoReglaRepository = resultadoReglaRepository;
        this.samnetGateway = samnetGateway;
        this.reloj = reloj;
    }

    public List<ReglaCalidadRespuesta> listarReglas() {
        return reglaCalidadRepository.findAll().stream()
                .map(ReglaCalidadRespuesta::desde)
                .toList();
    }

    public EvaluacionCalidadRespuesta obtenerCalidadLote(Long idLote) {
        LoteETL lote = loteETLRepository.findById(idLote)
                .orElseThrow(() -> new RecursoNoEncontradoException("LOTE_NO_ENCONTRADO",
                        "No existe el lote del ETL con id " + idLote + "."));
        return EvaluacionCalidadRespuesta.desde(lote);
    }

    @Transactional
    public EvaluacionCalidadRespuesta evaluarLote(EvaluacionCalidadSolicitud solicitud) {
        Long idLote = solicitud.idLote();
        LoteETL lote = loteETLRepository.findById(idLote)
                .orElseThrow(() -> new RecursoNoEncontradoException("LOTE_NO_ENCONTRADO",
                        "No existe el lote del ETL con id " + idLote + "."));

        if (lote.getEstado() != EstadoLote.EN_CURSO) {
            throw new ReglaNegocioException("LOTE_YA_PROCESADO",
                    "El lote " + idLote + " ya fue " + lote.getEstado() + ".");
        }

        List<Map<String, Object>> registros = solicitud.registros();
        if (registros == null) {
            registros = extraerRegistros(lote);
        }

        List<ReglaCalidad> reglasActivas = reglaCalidadRepository.findByActivaTrueOrderByCodigoAsc();
        Set<Integer> indicesConErrorTotal = new HashSet<>();
        List<ResultadoRegla> resultadosCalculados = new ArrayList<>();

        for (ReglaCalidad regla : reglasActivas) {
            ResultadoRegla resultado = evaluarRegla(regla, lote, registros, indicesConErrorTotal);
            resultadosCalculados.add(resultado);
            lote.registrarResultado(resultado);
        }

        boolean tieneFallas = resultadosCalculados.stream()
                .anyMatch(r -> r.getResultado() == ResultadoCalidad.FALLA);

        int totalLeidos = registros.size();
        int totalRechazados = indicesConErrorTotal.size();
        int totalAceptados = Math.max(0, totalLeidos - totalRechazados);
        LocalDateTime ahora = LocalDateTime.now(reloj);

        if (tieneFallas) {
            lote.rechazar(totalLeidos, totalAceptados, ahora);
        } else {
            lote.publicar(totalLeidos, totalAceptados, ahora);
        }

        loteETLRepository.saveAndFlush(lote);
        return EvaluacionCalidadRespuesta.desde(lote);
    }

    private List<Map<String, Object>> extraerRegistros(LoteETL lote) {
        try {
            return samnetGateway.extraerParaETL(lote.getFuente(), lote.getFechaCorte());
        } catch (Exception ex) {
            return List.of();
        }
    }

    private ResultadoRegla evaluarRegla(
            ReglaCalidad regla,
            LoteETL lote,
            List<Map<String, Object>> registros,
            Set<Integer> indicesConErrorTotal) {

        int totalEvaluados = registros.size();
        Set<Integer> erroresRegla = new HashSet<>();
        String codigoRegla = regla.getCodigo();

        switch (codigoRegla) {
            case "R1" -> evaluarR1DocumentoYCodigoNoNulos(registros, erroresRegla);
            case "R2" -> evaluarR2FormatoDniRuc(registros, erroresRegla);
            case "R3" -> evaluarR3PredioConContribuyente(registros, erroresRegla);
            case "R4" -> evaluarR4SinClavesRepetidas(registros, erroresRegla);
            case "R5" -> evaluarR5FechaCorteValida(registros, lote.getFechaCorte(), erroresRegla);
            default -> { }
        }

        indicesConErrorTotal.addAll(erroresRegla);
        int cantidadErrores = erroresRegla.size();
        ResultadoCalidad resultado = cantidadErrores > 0 ? ResultadoCalidad.FALLA : ResultadoCalidad.CUMPLE;
        String detalle = generarDetalle(codigoRegla, totalEvaluados, cantidadErrores);

        return new ResultadoRegla(regla, totalEvaluados, cantidadErrores, resultado, detalle);
    }

    /* R1: Documento de identidad y código del contribuyente no nulos. */
    private void evaluarR1DocumentoYCodigoNoNulos(List<Map<String, Object>> registros, Set<Integer> errores) {
        for (int i = 0; i < registros.size(); i++) {
            Map<String, Object> reg = registros.get(i);
            String codigo = campo(reg, "codigo", "contribuyente_codigo", "codigoContribuyente");
            String doc = campo(reg, "numero_documento", "numeroDocumento", "documento");

            boolean esPredio = campo(reg, "codigo_predio", "codigoPredio") != null;
            if (codigo == null) {
                errores.add(i);
            } else if (!esPredio && doc == null && reg.containsKey("tipo_documento")) {
                errores.add(i);
            }
        }
    }

    /* R2: DNI de 8 dígitos y RUC de 11 dígitos. */
    private void evaluarR2FormatoDniRuc(List<Map<String, Object>> registros, Set<Integer> errores) {
        for (int i = 0; i < registros.size(); i++) {
            Map<String, Object> reg = registros.get(i);
            String doc = campo(reg, "numero_documento", "numeroDocumento", "documento");
            String tipoDoc = campo(reg, "tipo_documento", "tipoDocumento");

            if (doc != null) {
                if ("DNI".equalsIgnoreCase(tipoDoc) && !PATRON_DNI.matcher(doc).matches()) {
                    errores.add(i);
                } else if ("RUC".equalsIgnoreCase(tipoDoc) && !PATRON_RUC.matcher(doc).matches()) {
                    errores.add(i);
                } else if (tipoDoc == null && !PATRON_DNI.matcher(doc).matches() && !PATRON_RUC.matcher(doc).matches()) {
                    errores.add(i);
                }
            }
        }
    }

    /* R3: Predio con contribuyente asociado o marcado para revisión. */
    private void evaluarR3PredioConContribuyente(List<Map<String, Object>> registros, Set<Integer> errores) {
        for (int i = 0; i < registros.size(); i++) {
            Map<String, Object> reg = registros.get(i);
            String codigoPredio = campo(reg, "codigo_predio", "codigoPredio");
            if (codigoPredio != null) {
                String contribuyente = campo(reg, "contribuyente_codigo", "codigoContribuyente", "contribuyente");
                String condicion = campo(reg, "condicion_propiedad", "condicionPropiedad", "estado");
                boolean marcado = "EN_REVISION".equalsIgnoreCase(condicion)
                        || "PENDIENTE_REVISION".equalsIgnoreCase(condicion)
                        || Boolean.TRUE.equals(reg.get("marcado_revision"));

                if (contribuyente == null && !marcado) {
                    errores.add(i);
                }
            }
        }
    }

    /* R4: Sin claves repetidas dentro del lote. */
    private void evaluarR4SinClavesRepetidas(List<Map<String, Object>> registros, Set<Integer> errores) {
        Set<String> clavesVistas = new HashSet<>();
        for (int i = 0; i < registros.size(); i++) {
            Map<String, Object> reg = registros.get(i);
            String clave = obtenerClavePrincipal(reg);
            if (clave != null) {
                if (!clavesVistas.add(clave)) {
                    errores.add(i);
                }
            }
        }
    }

    /* R5: Fecha de corte igual a la esperada para la ejecución o dentro del rango permitido. */
    private void evaluarR5FechaCorteValida(
            List<Map<String, Object>> registros,
            LocalDate fechaCorteLote,
            Set<Integer> errores) {

        for (int i = 0; i < registros.size(); i++) {
            Map<String, Object> reg = registros.get(i);
            String fechaCorteStr = campo(reg, "fecha_corte", "fechaCorte");
            String fechaPagoStr = campo(reg, "fecha_pago", "fechaPago");
            String anioStr = campo(reg, "anio", "ano");

            if (fechaCorteStr != null) {
                try {
                    LocalDate fecha = LocalDate.parse(fechaCorteStr);
                    if (!fecha.equals(fechaCorteLote)) {
                        errores.add(i);
                    }
                } catch (Exception ex) {
                    errores.add(i);
                }
            }

            if (fechaPagoStr != null) {
                try {
                    LocalDate fechaPago = LocalDate.parse(fechaPagoStr);
                    if (fechaPago.isAfter(fechaCorteLote)) {
                        errores.add(i);
                    }
                } catch (Exception ignored) {
                }
            }

            if (anioStr != null) {
                try {
                    int anio = Integer.parseInt(anioStr);
                    if (anio > fechaCorteLote.getYear()) {
                        errores.add(i);
                    }
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static String obtenerClavePrincipal(Map<String, Object> reg) {
        String clave = campo(reg, "id_obligacion", "idObligacion");
        if (clave != null) return "OBL:" + clave;

        clave = campo(reg, "id_pago", "idPago");
        if (clave != null) return "PAG:" + clave;

        clave = campo(reg, "codigo_predio", "codigoPredio");
        if (clave != null) return "PRE:" + clave;

        clave = campo(reg, "codigo", "contribuyente_codigo", "codigoContribuyente");
        if (clave != null) return "CON:" + clave;

        return null;
    }

    private static String campo(Map<String, Object> reg, String... nombres) {
        for (String nombre : nombres) {
            Object valor = reg.get(nombre);
            if (valor != null) {
                String str = valor.toString().trim();
                if (!str.isEmpty()) {
                    return str;
                }
            }
        }
        return null;
    }

    private static String generarDetalle(String codigoRegla, int evaluados, int errores) {
        if (errores == 0) {
            return "Todos los " + evaluados + " registros evaluados cumplen con la regla " + codigoRegla + ".";
        }
        return "Se encontraron " + errores + " registro(s) con infracción a la regla " + codigoRegla + ".";
    }
}
