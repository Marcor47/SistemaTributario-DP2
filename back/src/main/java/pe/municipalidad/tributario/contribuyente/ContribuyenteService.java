package pe.municipalidad.tributario.contribuyente;

import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.common.DatoInvalidoException;
import pe.municipalidad.tributario.common.RecursoNoEncontradoException;

@Service
@Transactional(readOnly = true)
public class ContribuyenteService {

    private final ContribuyenteRepository contribuyenteRepository;

    public ContribuyenteService(ContribuyenteRepository contribuyenteRepository) {
        this.contribuyenteRepository = contribuyenteRepository;
    }

    public ContribuyenteRespuesta consultarPorDocumento(TipoDocumento tipoDocumento, String numeroDocumento) {
        /* Se toleran espacios y minúsculas que suelen venir al copiar el documento desde otro sistema. */
        String numeroNormalizado = numeroDocumento.replaceAll("\\s", "").toUpperCase(Locale.ROOT);

        if (!tipoDocumento.esFormatoValido(numeroNormalizado)) {
            throw new DatoInvalidoException("DOCUMENTO_INVALIDO",
                    "El " + tipoDocumento.name() + " debe tener " + tipoDocumento.getFormatoEsperado() + ".");
        }

        return contribuyenteRepository.findByTipoDocumentoAndNumeroDocumento(tipoDocumento, numeroNormalizado)
                .map(ContribuyenteRespuesta::desde)
                .orElseThrow(() -> new RecursoNoEncontradoException("CONTRIBUYENTE_NO_ENCONTRADO",
                        "No existe un contribuyente registrado con " + tipoDocumento.name() + " "
                                + numeroNormalizado + "."));
    }

    /* Usado por otros módulos que necesitan asegurar que el contribuyente existe antes de continuar. */
    public Contribuyente obtenerPorCodigo(String codigo) {
        return contribuyenteRepository.findById(codigo)
                .orElseThrow(() -> new RecursoNoEncontradoException("CONTRIBUYENTE_NO_ENCONTRADO",
                        "No existe un contribuyente con código " + codigo + "."));
    }
}
