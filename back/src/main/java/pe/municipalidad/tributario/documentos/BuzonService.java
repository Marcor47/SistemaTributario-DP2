package pe.municipalidad.tributario.documentos;

import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.common.RecursoNoEncontradoException;
import pe.municipalidad.tributario.contribuyente.ContribuyenteService;

/*
 * HU019 (RF-030): el contribuyente ve los documentos de su buzón y, al abrir uno, queda registrada la fecha y hora
 * de la primera lectura. El destinatario se toma de la sesión, nunca de la petición (RNF-023).
 */
@Service
@Transactional(readOnly = true)
public class BuzonService {

    private final DocumentoBuzonRepository documentoBuzonRepository;
    private final ContribuyenteService contribuyenteService;
    private final Clock reloj;

    public BuzonService(DocumentoBuzonRepository documentoBuzonRepository,
            ContribuyenteService contribuyenteService, Clock reloj) {
        this.documentoBuzonRepository = documentoBuzonRepository;
        this.contribuyenteService = contribuyenteService;
        this.reloj = reloj;
    }

    public BuzonRespuesta listar(String codigoContribuyente) {
        String documento = documentoDe(codigoContribuyente);
        return BuzonRespuesta.de(documentoBuzonRepository.findByDocumentoContribuyenteOrderByFechaEnvioDesc(documento));
    }

    /* Abrir el documento registra la primera lectura; las siguientes aperturas no la cambian. */
    @Transactional
    public DocumentoBuzonRespuesta abrir(String codigoContribuyente, Long idDocumento) {
        String documento = documentoDe(codigoContribuyente);
        /* Un documento de otro contribuyente se responde igual que uno inexistente, sin revelar datos (RNF-023). */
        DocumentoBuzon buscado = documentoBuzonRepository.bloquearPropio(idDocumento, documento)
                .orElseThrow(() -> new RecursoNoEncontradoException("DOCUMENTO_NO_ENCONTRADO",
                        "No tiene un documento en su buzón con N.° " + idDocumento + "."));
        buscado.marcarLeido(LocalDateTime.now(reloj));
        return DocumentoBuzonRespuesta.desde(documentoBuzonRepository.saveAndFlush(buscado));
    }

    @Transactional
    public byte[] descargar(String codigoContribuyente, Long idDocumento) {
        DocumentoBuzon buscado = documentoBuzonRepository.bloquearPropio(idDocumento, documentoDe(codigoContribuyente))
                .orElseThrow(() -> new RecursoNoEncontradoException("DOCUMENTO_NO_ENCONTRADO", "No existe ese documento."));
        if (buscado.getConstancia() == null || buscado.getConstancia().getContenidoPdf() == null) {
            throw new RecursoNoEncontradoException("ARCHIVO_NO_DISPONIBLE", "Este documento no tiene un archivo descargable.");
        }
        buscado.marcarLeido(LocalDateTime.now(reloj));
        return buscado.getConstancia().getContenidoPdf();
    }

    private String documentoDe(String codigoContribuyente) {
        return contribuyenteService.obtenerPorCodigo(codigoContribuyente).getNumeroDocumento();
    }
}
