package pe.municipalidad.tributario.modelo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import pe.municipalidad.tributario.common.ReglaNegocioException;
import pe.municipalidad.tributario.constancia.CanalSolicitud;
import pe.municipalidad.tributario.constancia.EstadoConstancia;
import pe.municipalidad.tributario.constancia.FirmaDigital;
import pe.municipalidad.tributario.constancia.SolicitudConstancia;
import pe.municipalidad.tributario.constancia.TipoFirmante;
import pe.municipalidad.tributario.constancia.TransaccionPago;
import pe.municipalidad.tributario.gestiontributaria.TipoPredio;
import pe.municipalidad.tributario.ia.ModeloIA;
import pe.municipalidad.tributario.ia.ResumenEjecutivo;
import pe.municipalidad.tributario.ia.Segmento;
import pe.municipalidad.tributario.ia.TipoModeloIA;
import pe.municipalidad.tributario.reclamo.AreaMunicipal;
import pe.municipalidad.tributario.reclamo.EstadoReclamo;
import pe.municipalidad.tributario.reclamo.Reclamo;
import pe.municipalidad.tributario.reclamo.TipoReclamo;
import pe.municipalidad.tributario.seguridad.NombreRol;
import pe.municipalidad.tributario.seguridad.Rol;
import pe.municipalidad.tributario.seguridad.Usuario;
import pe.municipalidad.tributario.tramitepredio.SolicitudPredio;
import pe.municipalidad.tributario.tramitepredio.TipoTramitePredio;

/* Reglas de las clases del modelo que no dependen de la base de datos. */
class ReglasDominioTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 19, 12, 0);

    @Test
    void laConstanciaLaGestionaUnOperadorEnElOrdenFirmaDeudaOrdenDePagoPagoEmision() {
        SolicitudConstancia solicitud = new SolicitudConstancia(CanalSolicitud.APP, "41028765", null, AHORA);
        Usuario operador = usuario("operador", NombreRol.VENTANILLA);

        assertThatThrownBy(() -> solicitud.verificarDeuda(operador, BigDecimal.ZERO, AHORA))
                .isInstanceOf(ReglaNegocioException.class);
        solicitud.registrarFirma(firma(TipoFirmante.CONTRIBUYENTE));
        assertThat(solicitud.verificarDeuda(operador, BigDecimal.ZERO, AHORA)).isTrue();

        TransaccionPago ordenPago = TransaccionPago.deDerechoTramite(solicitud, new BigDecimal("15.00"), AHORA);
        solicitud.emitirOrdenPago(ordenPago);
        assertThat(solicitud.getEstado()).isEqualTo(EstadoConstancia.ORDEN_PAGO_EMITIDA);
        assertThatThrownBy(() -> solicitud.confirmarPago(ordenPago)).isInstanceOf(ReglaNegocioException.class);
        ordenPago.confirmar("COMP-001");
        solicitud.confirmarPago(ordenPago);
        solicitud.emitir(operador, firma(TipoFirmante.MUNICIPALIDAD), AHORA);

        assertThat(solicitud.getEstado()).isEqualTo(EstadoConstancia.EMITIDA);
        assertThat(solicitud.getOperador()).isSameAs(operador);
        assertThat(solicitud.getFirmas()).hasSize(2);
    }

    @Test
    void enVentanillaNoSeFirmaYElTrabajadorVerificaLaDeuda() {
        Usuario trabajador = usuario("ventanilla", NombreRol.VENTANILLA);
        SolicitudConstancia solicitud = new SolicitudConstancia(CanalSolicitud.VENTANILLA, "41028765", trabajador,
                AHORA);

        assertThatThrownBy(() -> solicitud.registrarFirma(firma(TipoFirmante.CONTRIBUYENTE)))
                .isInstanceOf(ReglaNegocioException.class);
        assertThat(solicitud.verificarDeuda(trabajador, BigDecimal.ZERO, AHORA)).isTrue();
    }

    @Test
    void conDeudaElOperadorRechazaLaConstancia() {
        SolicitudConstancia solicitud = new SolicitudConstancia(CanalSolicitud.APP, "20456781239", null, AHORA);
        solicitud.registrarFirma(firma(TipoFirmante.CONTRIBUYENTE));

        assertThat(solicitud.verificarDeuda(usuario("operador", NombreRol.VENTANILLA), new BigDecimal("992.16"),
                AHORA)).isFalse();
        assertThat(solicitud.getEstado()).isEqualTo(EstadoConstancia.RECHAZADA);
        assertThat(solicitud.getMotivoRechazo()).contains("992.16");
    }

    @Test
    void elReclamoSoloAdmiteLosTiposDeSuReclamante() {
        assertThatThrownBy(() -> Reclamo.deContribuyente("REC-2026-000001", "41028765", TipoReclamo.AREA_RELACIONADA,
                null, AHORA.minusHours(2), "Incidente", AHORA)).isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> Reclamo.deTrabajador("REC-2026-000002", usuario("analista", NombreRol.ANALISTA_RENTAS),
                TipoReclamo.ATENCION, null, AHORA.minusHours(2), "Incidente", AHORA))
                .isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> Reclamo.deContribuyente("REC-2026-000003", "41028765", TipoReclamo.COBRANZA, null,
                AHORA.plusDays(1), "Incidente", AHORA)).isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    void elReclamoSeDerivaPorElGestorYSeResponde() {
        Reclamo reclamo = Reclamo.deContribuyente("REC-2026-000004", "41028765", TipoReclamo.COBRANZA, "OP-2026-000101",
                AHORA.minusDays(1), "Me cobraron una cuota que ya pagué.", AHORA);
        AreaMunicipal recaudacion = new AreaMunicipal("REGISTRO_TRIBUTARIO",
                "Unidad de Registro Tributario y Control de la Deuda");
        Usuario responsable = usuario("analista", NombreRol.ANALISTA_RENTAS);

        assertThatThrownBy(() -> reclamo.derivar(recaudacion, responsable, responsable, null, AHORA))
                .isInstanceOf(ReglaNegocioException.class);
        reclamo.derivar(recaudacion, responsable, usuario("gestor", NombreRol.GESTOR_RECLAMOS), null, AHORA);
        assertThat(reclamo.consultarEstado()).isEqualTo(EstadoReclamo.EN_REVISION);
        assertThat(reclamo.getDerivaciones()).hasSize(1);

        reclamo.responder("Se verificó el pago y se anuló la cuota.", AHORA);
        assertThat(reclamo.consultarEstado()).isEqualTo(EstadoReclamo.APROBADO_CON_RESPUESTA);
        assertThatThrownBy(reclamo::cancelar).isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    void laTransaccionCalculaElTotalConElDescuento() {
        TransaccionPago pago = TransaccionPago.deObligaciones("41028765", Set.of("37", "38"),
                new BigDecimal("200.00"), new BigDecimal("50.00"), AHORA);

        assertThat(pago.getMontoTotal()).isEqualByComparingTo("150.00");
        assertThat(pago.getObligaciones()).containsExactlyInAnyOrder("37", "38");
    }

    @Test
    void elTraspasoExigeElPredio() {
        SolicitudPredio solicitud = new SolicitudPredio(TipoTramitePredio.TRASPASO, TipoPredio.URBANO, "41028765",
                null, true);

        assertThatThrownBy(() -> solicitud.enviar(List.of(), AHORA))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("código del predio");
    }

    @Test
    void unSegmentoSoloSePublicaTrasLaAprobacion() {
        Segmento segmento = new Segmento(modelo(TipoModeloIA.SEGMENTACION), "Pagadores tardíos", null, 120, "{}");

        assertThatThrownBy(segmento::publicar).isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    void elResumenNoSuperaLas300PalabrasYSeRevisaAntesDeDescargar() {
        String largo = "palabra ".repeat(301);
        assertThatThrownBy(() -> new ResumenEjecutivo(modelo(TipoModeloIA.RESUMEN_EJECUTIVO), "2026-10", largo, "[]",
                AHORA)).isInstanceOf(ReglaNegocioException.class);

        ResumenEjecutivo resumen = new ResumenEjecutivo(modelo(TipoModeloIA.RESUMEN_EJECUTIVO), "2026-10",
                "La morosidad bajó a 13,25 %.", "[]", AHORA);
        assertThatThrownBy(resumen::descargar).isInstanceOf(ReglaNegocioException.class);
    }

    private static FirmaDigital firma(TipoFirmante firmante) {
        return new FirmaDigital(firmante, "ENTIDAD DE PRUEBA", AHORA, true, "a".repeat(64));
    }

    private static Usuario usuario(String nombreUsuario, NombreRol rol) {
        return new Usuario(nombreUsuario, nombreUsuario.toUpperCase(), nombreUsuario + "@muni.test", "hash",
                new Rol(rol, null, ""), AHORA);
    }

    private static ModeloIA modelo(TipoModeloIA tipo) {
        return new ModeloIA("modelo-prueba", tipo, "1.0", "b".repeat(64), "ONNX", AHORA);
    }
}
