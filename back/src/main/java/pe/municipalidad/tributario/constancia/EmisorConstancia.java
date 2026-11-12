package pe.municipalidad.tributario.constancia;

import java.time.LocalDateTime;
import org.springframework.stereotype.Component;
import pe.municipalidad.tributario.common.ReglaNegocioException;
import pe.municipalidad.tributario.contribuyente.Contribuyente;
import pe.municipalidad.tributario.documentos.DocumentoBuzon;
import pe.municipalidad.tributario.documentos.DocumentoBuzonRepository;
import pe.municipalidad.tributario.documentos.VersionPlantilla;
import pe.municipalidad.tributario.documentos.VersionPlantillaRepository;
import pe.municipalidad.tributario.integracion.firmador.FirmadorGateway;
import pe.municipalidad.tributario.seguridad.Usuario;

/*
 * Genera la Constancia de No Adeudo de una solicitud pagada con la plantilla vigente, la firma la Municipalidad
 * (RF-037) y la deposita en el buzón (RF-030). La usan la emisión manual del operador (HU020) y la automática tras el
 * pago (HU024). No abre transacción propia: participa en la de quien la llama, y todas sus validaciones ocurren antes
 * de modificar datos, así un rechazo no deja la solicitud a medias.
 */
@Component
public class EmisorConstancia {

    public static final String CODIGO_PLANTILLA = "CONSTANCIA_NO_ADEUDO";

    private static final String PREDIOS_INCLUIDOS = "Todos los predios del contribuyente";
    private static final String RANGO_FECHAS = "Hasta la fecha de emisión";

    private final ConstanciaRepository constanciaRepository;
    private final DocumentoBuzonRepository documentoBuzonRepository;
    private final VersionPlantillaRepository versionPlantillaRepository;
    private final FirmadorGateway firmador;
    private final GeneradorConstanciaPdf generadorPdf;

    public EmisorConstancia(ConstanciaRepository constanciaRepository,
            DocumentoBuzonRepository documentoBuzonRepository, VersionPlantillaRepository versionPlantillaRepository,
            FirmadorGateway firmador, GeneradorConstanciaPdf generadorPdf) {
        this.constanciaRepository = constanciaRepository;
        this.documentoBuzonRepository = documentoBuzonRepository;
        this.versionPlantillaRepository = versionPlantillaRepository;
        this.firmador = firmador;
        this.generadorPdf = generadorPdf;
    }

    /* Con operador nulo la emisión es automática y la constancia no lleva trabajador emisor. */
    public ConstanciaEmitida emitir(SolicitudConstancia solicitud, Contribuyente contribuyente, Usuario operador,
            LocalDateTime ahora) {
        if (solicitud.getEstado() != EstadoConstancia.PAGADA) {
            throw new ReglaNegocioException("PAGO_REQUERIDO",
                    "La solicitud está " + solicitud.getEstado() + ": la constancia se emite tras el pago del trámite.");
        }
        VersionPlantilla plantilla = versionPlantillaRepository.findByPlantillaCodigoAndVigenteTrue(CODIGO_PLANTILLA)
                .orElseThrow(() -> new ReglaNegocioException("PLANTILLA_NO_DISPONIBLE",
                        "No hay una versión vigente de la plantilla " + CODIGO_PLANTILLA + "."));

        String numero = constanciaRepository.siguienteNumero(ahora.toLocalDate());
        byte[] pdf = generadorPdf.generar(numero, contribuyente, ahora, firmador.esSimulado());
        FirmadorGateway.DocumentoFirmado firmado = firmador.firmarComoMunicipalidad(pdf);
        if (!firmado.firma().valida()) {
            throw new ReglaNegocioException("FIRMA_MUNICIPAL_INVALIDA",
                    "No se pudo firmar la constancia: " + firmado.firma().motivoRechazo() + ".");
        }

        FirmaDigital firmaMunicipal = new FirmaDigital(TipoFirmante.MUNICIPALIDAD,
                firmado.firma().entidadCertificadora(), ahora, true, firmado.firma().hashDocumento());
        if (operador == null) {
            solicitud.emitirAutomaticamente(firmaMunicipal);
        } else {
            solicitud.emitir(operador, firmaMunicipal, ahora);
        }
        Constancia constancia = constanciaRepository.save(new Constancia(numero, solicitud, plantilla, ahora,
                PREDIOS_INCLUIDOS, RANGO_FECHAS, numero + ".pdf", firmador.esSimulado(), operador,
                firmado.contenido()));
        DocumentoBuzon enBuzon = documentoBuzonRepository.save(
                DocumentoBuzon.deConstancia(constancia, solicitud.getDocumentoContribuyente(), ahora));
        return new ConstanciaEmitida(constancia, enBuzon);
    }

    public record ConstanciaEmitida(Constancia constancia, DocumentoBuzon documentoBuzon) {
    }
}
