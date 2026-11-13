package pe.municipalidad.tributario.documentos;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.common.RecursoNoEncontradoException;
import pe.municipalidad.tributario.common.ReglaNegocioException;
import pe.municipalidad.tributario.contribuyente.Contribuyente;
import pe.municipalidad.tributario.etl.EstadoLote;
import pe.municipalidad.tributario.etl.LoteETL;
import pe.municipalidad.tributario.etl.LoteETLRepository;
import pe.municipalidad.tributario.integracion.correo.CorreoGateway;
import pe.municipalidad.tributario.integracion.samnet.SamnetGateway;

/*
 * HU012: Enviar alerta de mora temprana por correo (RF-013).
 * Como Gerencia de Rentas, envía automáticamente un aviso por correo electrónico a los contribuyentes
 * con cuotas vencidas sin pago detectadas en una carga del ETL, en un plazo máximo de 24 horas.
 * Si el contribuyente no tiene correo registrado, queda excluido (WhatsApp, SMS e IVR quedan fuera).
 * Cada envío completado queda registrado en el historial del contribuyente.
 * Dependencia de HU026: Solo lotes validados por reglas de calidad y publicados pueden procesar alertas.
 */
@Service
@Transactional(readOnly = true)
public class AlertaMoraService {

    private final LoteETLRepository loteETLRepository;
    private final AvisoCorreoRepository avisoCorreoRepository;
    private final SamnetGateway samnetGateway;
    private final CorreoGateway correoGateway;
    private final Clock reloj;

    public AlertaMoraService(
            LoteETLRepository loteETLRepository,
            AvisoCorreoRepository avisoCorreoRepository,
            SamnetGateway samnetGateway,
            CorreoGateway correoGateway,
            Clock reloj) {
        this.loteETLRepository = loteETLRepository;
        this.avisoCorreoRepository = avisoCorreoRepository;
        this.samnetGateway = samnetGateway;
        this.correoGateway = correoGateway;
        this.reloj = reloj;
    }

    @Transactional
    public RespuestaProcesamientoAlertasMora procesarAlertasLote(ProcesarAlertasMoraSolicitud solicitud) {
        Long idLote = solicitud.idLote();
        LoteETL lote = loteETLRepository.findById(idLote)
                .orElseThrow(() -> new RecursoNoEncontradoException("LOTE_NO_ENCONTRADO",
                        "No existe el lote del ETL con id " + idLote + "."));

        /* HU026: Un lote rechazado o en curso no debe alimentar el modelo ni generar alertas de mora temprana. */
        if (lote.getEstado() != EstadoLote.PUBLICADO) {
            throw new ReglaNegocioException("LOTE_NO_PUBLICADO",
                    "El lote " + idLote + " está en estado " + lote.getEstado()
                            + ": solo los lotes publicados y aprobados por control de calidad generan alertas de mora.");
        }

        List<Map<String, Object>> registros = solicitud.registros();
        if (registros == null) {
            registros = extraerRegistros(lote);
        }

        int cuotasEvaluadas = registros.size();
        int cuotasVencidasDetectadas = 0;
        int alertasEnviadas = 0;
        int sinCorreoRegistrado = 0;
        int alertasFallidas = 0;
        int alertasSimuladas = 0;
        List<AvisoCorreo> avisosGenerados = new ArrayList<>();
        LocalDateTime ahora = LocalDateTime.now(reloj);
        LocalDate fechaCorte = lote.getFechaCorte();

        for (Map<String, Object> reg : registros) {
            String idObligacion = campo(reg, "id_obligacion", "idObligacion");
            String contribuyenteCodigo = campo(reg, "contribuyente_codigo", "codigoContribuyente", "codigo");
            LocalDate fechaVencimiento = obtenerFecha(reg, "fecha_vencimiento", "fechaVencimiento");
            BigDecimal insoluto = calcularInsoluto(reg);

            /* Detección de cuota vencida sin pago: fecha de vencimiento <= fecha de corte y saldo insoluto > 0 */
            boolean cuotaVencida = fechaVencimiento != null && !fechaVencimiento.isAfter(fechaCorte);
            boolean conSaldoPendiente = insoluto != null && insoluto.compareTo(BigDecimal.ZERO) > 0;

            if (cuotaVencida && conSaldoPendiente && idObligacion != null) {
                cuotasVencidasDetectadas++;

                /* Evitar envíos duplicados si ya se generó aviso para esta cuota en este lote */
                if (avisoCorreoRepository.existsByLoteIdLoteAndIdObligacion(lote.getIdLote(), idObligacion)) {
                    continue;
                }

                String correo = campo(reg, "correo", "email");
                String documento = campo(reg, "numero_documento", "documento", "numeroDocumento");
                String nombre = campo(reg, "nombre", "nombreRazonSocial");

                if ((correo == null || documento == null) && contribuyenteCodigo != null) {
                    Contribuyente contribuyente = buscarContribuyente(contribuyenteCodigo);
                    if (contribuyente != null) {
                        if (correo == null) correo = contribuyente.getCorreo();
                        if (documento == null) documento = contribuyente.getNumeroDocumento();
                        if (nombre == null) nombre = contribuyente.getNombreRazonSocial();
                    }
                }

                /* RF-013: Solo se envía por correo electrónico a quienes tienen correo registrado. */
                if (correo == null || correo.isBlank()) {
                    sinCorreoRegistrado++;
                    continue;
                }

                if (documento == null) {
                    documento = contribuyenteCodigo != null ? contribuyenteCodigo : "DESCONOCIDO";
                }

                /* Plazo máximo de 24 horas: se programa el despacho dentro de las 24 h */
                LocalDateTime fechaProgramada = ahora;
                AvisoCorreo aviso = new AvisoCorreo(
                        TipoAviso.MORA_TEMPRANA,
                        documento,
                        correo.trim(),
                        fechaProgramada,
                        lote,
                        idObligacion);

                String asunto = "Aviso de mora temprana: Cuota vencida de tributos municipales - Obligación #" + idObligacion;
                String cuerpo = armarCuerpoAviso(nombre != null ? nombre : documento, idObligacion, fechaCorte, insoluto);

                aviso.enviar(correoGateway, asunto, cuerpo, ahora);
                avisoCorreoRepository.save(aviso);
                avisosGenerados.add(aviso);

                if (aviso.getEstado() == EstadoAviso.ENVIADO) {
                    alertasEnviadas++;
                } else if (aviso.getEstado() == EstadoAviso.SIMULADO) {
                    alertasSimuladas++;
                } else {
                    alertasFallidas++;
                }
            }
        }

        return new RespuestaProcesamientoAlertasMora(
                lote.getIdLote(),
                fechaCorte,
                cuotasEvaluadas,
                cuotasVencidasDetectadas,
                alertasEnviadas,
                sinCorreoRegistrado,
                alertasFallidas,
                alertasSimuladas,
                avisosGenerados.stream().map(AvisoCorreoRespuesta::desde).toList());
    }

    public List<AvisoCorreoRespuesta> obtenerHistorialContribuyente(String codigoODocumento) {
        if (codigoODocumento == null || codigoODocumento.isBlank()) {
            return List.of();
        }
        String documento = codigoODocumento.trim();
        try {
            List<Contribuyente> contribuyentes = samnetGateway.buscarContribuyente(documento);
            if (!contribuyentes.isEmpty()) {
                documento = contribuyentes.getFirst().getNumeroDocumento();
            }
        } catch (Exception ignored) {
        }
        return avisoCorreoRepository.buscarHistorialContribuyente(documento).stream()
                .map(AvisoCorreoRespuesta::desde)
                .toList();
    }

    public List<AvisoCorreoRespuesta> listarPorLote(Long idLote) {
        return avisoCorreoRepository.findByLoteIdLoteOrderByFechaProgramadaDesc(idLote).stream()
                .map(AvisoCorreoRespuesta::desde)
                .toList();
    }

    public AvisoCorreoRespuesta obtenerPorId(Long idAviso) {
        AvisoCorreo aviso = avisoCorreoRepository.findById(idAviso)
                .orElseThrow(() -> new RecursoNoEncontradoException("AVISO_NO_ENCONTRADO",
                        "No existe el aviso con id " + idAviso + "."));
        return AvisoCorreoRespuesta.desde(aviso);
    }

    private List<Map<String, Object>> extraerRegistros(LoteETL lote) {
        try {
            String fuente = lote.getFuente() != null && !lote.getFuente().isBlank()
                    ? lote.getFuente() : "ps_etl_cobranza";
            return samnetGateway.extraerParaETL(fuente, lote.getFechaCorte());
        } catch (Exception ex) {
            return List.of();
        }
    }

    private Contribuyente buscarContribuyente(String codigoODocumento) {
        try {
            return samnetGateway.buscarContribuyente(codigoODocumento).stream().findFirst().orElse(null);
        } catch (Exception ex) {
            return null;
        }
    }

    private static BigDecimal calcularInsoluto(Map<String, Object> reg) {
        BigDecimal insoluto = obtenerMonto(reg, "insoluto", "monto_insoluto");
        if (insoluto != null) {
            return insoluto;
        }
        BigDecimal emitido = obtenerMonto(reg, "monto_emitido", "montoEmitido");
        BigDecimal pagado = obtenerMonto(reg, "monto_pagado", "montoPagado");
        if (emitido != null) {
            if (pagado != null) {
                return emitido.subtract(pagado).max(BigDecimal.ZERO);
            }
            return emitido;
        }
        return null;
    }

    private static String armarCuerpoAviso(String destinatario, String idObligacion, LocalDate fechaCorte, BigDecimal insoluto) {
        return "Estimado(a) contribuyente " + destinatario + ",\n\n"
                + "La Gerencia de Rentas le informa que en el control de cobranza con fecha de corte " + fechaCorte
                + " se ha detectado una cuota vencida sin pago (Obligación #" + idObligacion + ")"
                + (insoluto != null ? " con un saldo pendiente de S/ " + insoluto.toPlainString() : "") + ".\n\n"
                + "Le recordamos que puede regularizar su deuda tributaria de manera rápida a través del Portal Tributario "
                + "para evitar recargos por intereses moratorios y el inicio del procedimiento de cobranza coactiva.\n\n"
                + "Atentamente,\n"
                + "Gerencia de Rentas - Municipalidad Distrital de San Miguel";
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

    private static BigDecimal obtenerMonto(Map<String, Object> reg, String... nombres) {
        for (String nombre : nombres) {
            Object valor = reg.get(nombre);
            if (valor instanceof BigDecimal bd) return bd;
            if (valor instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
            if (valor != null) {
                try {
                    return new BigDecimal(valor.toString().trim());
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }

    private static LocalDate obtenerFecha(Map<String, Object> reg, String... nombres) {
        for (String nombre : nombres) {
            Object valor = reg.get(nombre);
            if (valor instanceof LocalDate ld) return ld;
            if (valor instanceof java.sql.Date sd) return sd.toLocalDate();
            if (valor instanceof java.util.Date d) {
                return d.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            }
            if (valor != null) {
                try {
                    return LocalDate.parse(valor.toString().trim());
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }
}

