package pe.municipalidad.tributario.predios;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import pe.municipalidad.tributario.common.ConflictoException;
import pe.municipalidad.tributario.common.DatoInvalidoException;
import pe.municipalidad.tributario.common.RecursoNoEncontradoException;
import pe.municipalidad.tributario.contribuyente.Contribuyente;
import pe.municipalidad.tributario.contribuyente.EstadoContribuyente;
import pe.municipalidad.tributario.gestiontributaria.Predio;
import pe.municipalidad.tributario.gestiontributaria.Titularidad;
import pe.municipalidad.tributario.integracion.samnet.SamnetGateway;

/*
 * HU036 (RF-048): registra un predio nuevo con su contribuyente en SAMNET, únicamente por la capa de servicios
 * municipal (SamnetGateway). Rechaza un predio que ya existe o un contribuyente que no existe o está inactivo.
 */
@Service
public class RegistroPredioService {

    static final String CONDICION_POR_DEFECTO = "PROPIETARIO";

    private final SamnetGateway samnet;

    public RegistroPredioService(SamnetGateway samnet) {
        this.samnet = samnet;
    }

    public PredioRespuesta registrar(RegistrarPredioSolicitud solicitud) {
        String codigoContribuyente = solicitud.codigoContribuyente().strip();
        String codigoPredio = solicitud.codigoPredio().strip();

        Contribuyente contribuyente = samnet.buscarContribuyente(codigoContribuyente).stream()
                .filter(c -> c.getCodigoContribuyente().equals(codigoContribuyente))
                .findFirst()
                .orElseThrow(() -> new RecursoNoEncontradoException("CONTRIBUYENTE_NO_ENCONTRADO",
                        "No existe un contribuyente con el código " + codigoContribuyente + "."));
        if (contribuyente.getEstado() != EstadoContribuyente.ACTIVO) {
            throw new DatoInvalidoException("CONTRIBUYENTE_INACTIVO",
                    "El contribuyente " + codigoContribuyente + " está inactivo y no puede registrar predios.");
        }
        if (!samnet.buscarPredio(codigoPredio, null).isEmpty()) {
            throw predioYaRegistrado(codigoPredio);
        }

        String condicion = solicitud.condicionPropiedad() == null || solicitud.condicionPropiedad().isBlank()
                ? CONDICION_POR_DEFECTO : solicitud.condicionPropiedad().strip().toUpperCase();
        BigDecimal porcentaje = solicitud.porcentajePropiedad() == null
                ? BigDecimal.valueOf(100) : solicitud.porcentajePropiedad();
        Titularidad titular = new Titularidad(codigoContribuyente, condicion, porcentaje);
        Predio predio = new Predio(codigoPredio, vacioANulo(solicitud.numeroCatastral()),
                solicitud.ubicacion().strip(), vacioANulo(solicitud.usoPredio()), solicitud.tipoPredio(),
                solicitud.areaTerreno(), solicitud.baseImponible(), solicitud.fechaAdquisicion(), null,
                solicitud.zona().strip(), List.of(titular));

        List<String> errores = predio.validar();
        if (!errores.isEmpty()) {
            throw new DatoInvalidoException("PREDIO_INVALIDO", String.join("; ", errores));
        }

        /* El INSERT sin upsert cubre la carrera de dos registros simultáneos del mismo código. */
        samnet.registrarPredioNuevo(predio, codigoContribuyente);

        return new PredioRespuesta(codigoPredio, predio.getNumeroCatastral(), predio.getUbicacion(),
                predio.getUsoPredio(), predio.getTipoPredio().name(), predio.getAreaTerreno(),
                predio.getBaseImponible(), predio.getFechaAdquisicion(), predio.getZona(),
                List.of(new PredioRespuesta.Titular(codigoContribuyente, contribuyente.getNombreRazonSocial(),
                        contribuyente.getTipoDocumento().name(), contribuyente.getNumeroDocumento(), condicion,
                        porcentaje)));
    }

    static ConflictoException predioYaRegistrado(String codigoPredio) {
        return new ConflictoException("PREDIO_YA_REGISTRADO",
                "El predio " + codigoPredio + " ya está registrado en SAMNET.");
    }

    private static String vacioANulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.strip();
    }
}
