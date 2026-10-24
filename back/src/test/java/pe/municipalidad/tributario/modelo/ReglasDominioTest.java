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
import pe.municipalidad.tributario.tramitepredio.SolicitudPredio;
import pe.municipalidad.tributario.tramitepredio.TipoTramitePredio;

/* Reglas de las clases del modelo que no dependen de la base de datos. */
class ReglasDominioTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 19, 12, 0);

    @Test
    void laConstanciaSoloAvanzaEnElOrdenDeudaFirmaPagoEmision() {
        SolicitudConstancia solicitud = new SolicitudConstancia(CanalSolicitud.APP, "41028765", null, AHORA);

        assertThatThrownBy(() -> solicitud.emitir(firma(TipoFirmante.MUNICIPALIDAD)))
                .isInstanceOf(ReglaNegocioException.class);
        assertThat(solicitud.verificarDeuda(BigDecimal.ZERO)).isTrue();
        solicitud.registrarFirma(firma(TipoFirmante.CONTRIBUYENTE));

        TransaccionPago pago = TransaccionPago.deDerechoTramite(solicitud, new BigDecimal("15.00"), AHORA);
        assertThatThrownBy(() -> solicitud.confirmarPago(pago)).isInstanceOf(ReglaNegocioException.class);
        pago.confirmar("COMP-001");
        solicitud.confirmarPago(pago);
        solicitud.emitir(firma(TipoFirmante.MUNICIPALIDAD));

        assertThat(solicitud.getEstado()).isEqualTo(EstadoConstancia.EMITIDA);
        assertThat(solicitud.getFirmas()).hasSize(2);
    }

    @Test
    void conDeudaLaConstanciaSeRechaza() {
        SolicitudConstancia solicitud = new SolicitudConstancia(CanalSolicitud.APP, "20456781239", null, AHORA);

        assertThat(solicitud.verificarDeuda(new BigDecimal("992.16"))).isFalse();
        assertThat(solicitud.getEstado()).isEqualTo(EstadoConstancia.RECHAZADA);
        assertThat(solicitud.getMotivoRechazo()).contains("992.16");
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

    private static ModeloIA modelo(TipoModeloIA tipo) {
        return new ModeloIA("modelo-prueba", tipo, "1.0", "b".repeat(64), "ONNX", AHORA);
    }
}
