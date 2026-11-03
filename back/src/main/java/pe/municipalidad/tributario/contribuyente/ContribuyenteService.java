package pe.municipalidad.tributario.contribuyente;

import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import pe.municipalidad.tributario.common.DatoInvalidoException;
import pe.municipalidad.tributario.common.RecursoNoEncontradoException;
import pe.municipalidad.tributario.integracion.samnet.SamnetGateway;

/* HU001: los datos del contribuyente y sus predios se consultan en SAMNET mediante SamnetGateway (RNF-001). */
@Service
public class ContribuyenteService {

    private final SamnetGateway samnet;

    public ContribuyenteService(SamnetGateway samnet) {
        this.samnet = samnet;
    }

    public ContribuyenteRespuesta consultarPorDocumento(TipoDocumento tipoDocumento, String numeroDocumento) {
        /* Se toleran espacios y minúsculas que suelen venir al copiar el documento desde otro sistema. */
        String numeroNormalizado = numeroDocumento.replaceAll("\\s", "").toUpperCase(Locale.ROOT);

        if (!tipoDocumento.esFormatoValido(numeroNormalizado)) {
            throw new DatoInvalidoException("DOCUMENTO_INVALIDO",
                    "El " + tipoDocumento.name() + " debe tener " + tipoDocumento.getFormatoEsperado() + ".");
        }

        /* El SP busca por código o documento; se descartan coincidencias de otro tipo de documento. */
        return samnet.buscarContribuyente(numeroNormalizado).stream()
                .filter(c -> c.getTipoDocumento() == tipoDocumento
                        && c.getNumeroDocumento().equals(numeroNormalizado))
                .findFirst()
                .map(this::conPredios)
                .orElseThrow(() -> new RecursoNoEncontradoException("CONTRIBUYENTE_NO_ENCONTRADO",
                        "No existe un contribuyente registrado con " + tipoDocumento.name() + " "
                                + numeroNormalizado + "."));
    }

    /* Los predios salen de SAMNET (ps_predio) filtrados por el contribuyente titular. */
    private ContribuyenteRespuesta conPredios(Contribuyente contribuyente) {
        String codigo = contribuyente.getCodigoContribuyente();
        List<PredioContribuyenteRespuesta> predios = samnet.buscarPredio(null, codigo).stream()
                .map(predio -> PredioContribuyenteRespuesta.desde(predio, codigo))
                .toList();
        return ContribuyenteRespuesta.desde(contribuyente, predios);
    }

    /* HU001: coincidencias por código, nombre o documento para el buscador interno de la ficha. */
    public List<ContribuyenteCoincidenciaRespuesta> buscar(String texto) {
        if (texto == null || texto.isBlank()) {
            return List.of();
        }
        return samnet.buscarContribuyentes(texto.trim()).stream()
                .map(ContribuyenteCoincidenciaRespuesta::desde)
                .toList();
    }

    /* Usado por otros módulos que necesitan asegurar que el contribuyente existe antes de continuar. */
    public Contribuyente obtenerPorCodigo(String codigo) {
        return samnet.buscarContribuyente(codigo).stream()
                .filter(c -> c.getCodigoContribuyente().equals(codigo))
                .findFirst()
                .orElseThrow(() -> new RecursoNoEncontradoException("CONTRIBUYENTE_NO_ENCONTRADO",
                        "No existe un contribuyente con código " + codigo + "."));
    }
}
