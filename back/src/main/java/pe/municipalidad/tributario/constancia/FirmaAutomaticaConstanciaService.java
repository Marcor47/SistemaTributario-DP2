package pe.municipalidad.tributario.constancia;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import pe.municipalidad.tributario.common.NegocioException;
import pe.municipalidad.tributario.contribuyente.Contribuyente;
import pe.municipalidad.tributario.gestiontributaria.EstadoCuenta;
import pe.municipalidad.tributario.integracion.samnet.SamnetGateway;
import pe.municipalidad.tributario.obligacion.Obligacion;

/*
 * HU024 (RF-037): en cuanto se confirma el pago del derecho de trámite, el sistema genera la Constancia de No Adeudo,
 * la firma la Municipalidad y la deja en el buzón, sin esperar al operador; así cumple el plazo de 1 minuto.
 *
 * Se ejecuta dentro de la transacción de la confirmación del pago y nunca lanza excepciones: si no puede emitir
 * (deuda nueva, SAMNET responde desde la copia o no responde, plantilla o firmador no disponibles) registra el motivo
 * y devuelve vacío, el pago queda confirmado y la solicitud PAGADA para que el operador la emita o la rechace (HU020).
 * Por eso no lleva @Transactional: una excepción que cruzara un proxy transaccional marcaría el pago para rollback.
 */
@Service
public class FirmaAutomaticaConstanciaService {

    private static final Logger LOG = LoggerFactory.getLogger(FirmaAutomaticaConstanciaService.class);
    private static final Duration PLAZO_MAXIMO = Duration.ofMinutes(1);

    private final SamnetGateway samnet;
    private final EmisorConstancia emisor;
    private final Clock reloj;
    private final boolean habilitada;

    public FirmaAutomaticaConstanciaService(SamnetGateway samnet, EmisorConstancia emisor, Clock reloj,
            @Value("${tributario.constancia.firma-automatica:true}") boolean habilitada) {
        this.samnet = samnet;
        this.emisor = emisor;
        this.reloj = reloj;
        this.habilitada = habilitada;
    }

    public Optional<EmisorConstancia.ConstanciaEmitida> emitirTrasPago(SolicitudConstancia solicitud) {
        if (!habilitada || solicitud.getEstado() != EstadoConstancia.PAGADA) {
            return Optional.empty();
        }
        LocalDateTime inicio = LocalDateTime.now(reloj);
        try {
            Optional<Contribuyente> contribuyente = contribuyenteDe(solicitud.getDocumentoContribuyente());
            if (contribuyente.isEmpty()) {
                LOG.warn("Firma automática pendiente para la solicitud {}: SAMNET no registra al contribuyente",
                        solicitud.getIdSolicitud());
                return Optional.empty();
            }
            EstadoCuenta cuenta = samnet.obtenerEstadoCuenta(contribuyente.get().getCodigoContribuyente());
            if (cuenta.isDesdeCopia()) {
                LOG.warn("Firma automática pendiente para la solicitud {}: la deuda no se pudo verificar en línea",
                        solicitud.getIdSolicitud());
                return Optional.empty();
            }
            BigDecimal deuda = cuenta.getObligaciones().stream().map(Obligacion::saldo)
                    .filter(saldo -> saldo.signum() > 0).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (deuda.signum() > 0) {
                LOG.info("Firma automática detenida para la solicitud {}: registra deuda nueva",
                        solicitud.getIdSolicitud());
                return Optional.empty();
            }

            EmisorConstancia.ConstanciaEmitida emitida = emisor.emitir(solicitud, contribuyente.get(), null,
                    LocalDateTime.now(reloj));
            Duration demora = Duration.between(inicio, LocalDateTime.now(reloj));
            if (demora.compareTo(PLAZO_MAXIMO) > 0) {
                LOG.warn("La constancia {} se firmó en {} s, por encima del plazo de 1 minuto (RF-037)",
                        emitida.constancia().getNumeroConstancia(), demora.toSeconds());
            }
            return Optional.of(emitida);
        } catch (NegocioException ex) {
            LOG.warn("Firma automática pendiente para la solicitud {}: {}", solicitud.getIdSolicitud(),
                    ex.getMessage());
            return Optional.empty();
        }
    }

    /* La solicitud guarda el DNI o RUC; SAMNET lo resuelve al contribuyente titular. */
    private Optional<Contribuyente> contribuyenteDe(String documento) {
        return samnet.buscarContribuyente(documento).stream()
                .filter(contribuyente -> documento.equals(contribuyente.getNumeroDocumento()))
                .findFirst();
    }
}
