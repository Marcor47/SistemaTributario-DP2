package pe.municipalidad.tributario.documentos;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
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

    public BuzonRespuesta listar(String codigoContribuyente, VistaBuzon vista, String busqueda,
            TipoDocumentoBuzon tipo, Boolean leido, int pagina, int tamanio) {
        String documento = documentoDe(codigoContribuyente);
        Specification<DocumentoBuzon> filtros = (raiz, consulta, cb) -> cb.equal(
                raiz.get("documentoContribuyente"), documento);
        if (vista == VistaBuzon.RECIBIDOS) filtros = filtros.and((raiz, consulta, cb) -> cb.isFalse(raiz.get("archivado")));
        if (vista == VistaBuzon.DESTACADOS) filtros = filtros.and((raiz, consulta, cb) -> cb.isTrue(raiz.get("destacado")));
        if (vista == VistaBuzon.ARCHIVADOS) filtros = filtros.and((raiz, consulta, cb) -> cb.isTrue(raiz.get("archivado")));
        if (tipo != null) filtros = filtros.and((raiz, consulta, cb) -> cb.equal(raiz.get("tipo"), tipo));
        if (leido != null) filtros = filtros.and((raiz, consulta, cb) -> cb.equal(raiz.get("leidoActualmente"), leido));
        if (busqueda != null && !busqueda.isBlank()) {
            String patron = "%" + busqueda.trim().toLowerCase(Locale.ROOT) + "%";
            filtros = filtros.and((raiz, consulta, cb) -> cb.or(
                    cb.like(cb.lower(raiz.get("titulo")), patron),
                    cb.like(cb.lower(cb.coalesce(raiz.get("contenido"), "")), patron)));
        }
        int paginaSegura = Math.max(0, pagina);
        int tamanioSeguro = Math.min(Math.max(1, tamanio), 100);
        Page<DocumentoBuzon> resultado = documentoBuzonRepository.findAll(filtros,
                PageRequest.of(paginaSegura, tamanioSeguro, Sort.by(Sort.Direction.DESC, "fechaEnvio")));
        long sinLeer = documentoBuzonRepository
                .countByDocumentoContribuyenteAndArchivadoFalseAndLeidoActualmenteFalse(documento);
        return BuzonRespuesta.de(resultado, sinLeer);
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

    @Transactional
    public DocumentoBuzonRespuesta marcarNoLeido(String codigoContribuyente, Long idDocumento) {
        DocumentoBuzon buscado = documentoBuzonRepository.bloquearPropio(idDocumento, documentoDe(codigoContribuyente))
                .orElseThrow(() -> new RecursoNoEncontradoException("DOCUMENTO_NO_ENCONTRADO",
                        "No tiene un mensaje en su buzón con N.° " + idDocumento + "."));
        buscado.marcarNoLeido();
        return DocumentoBuzonRespuesta.desde(documentoBuzonRepository.saveAndFlush(buscado));
    }

    @Transactional
    public DocumentoBuzonRespuesta cambiarDestacado(String codigoContribuyente, Long idDocumento, boolean valor) {
        DocumentoBuzon buscado = propio(codigoContribuyente, idDocumento);
        buscado.cambiarDestacado(valor);
        return DocumentoBuzonRespuesta.desde(documentoBuzonRepository.saveAndFlush(buscado));
    }

    @Transactional
    public DocumentoBuzonRespuesta cambiarArchivado(String codigoContribuyente, Long idDocumento, boolean valor) {
        DocumentoBuzon buscado = propio(codigoContribuyente, idDocumento);
        buscado.cambiarArchivado(valor, LocalDateTime.now(reloj));
        return DocumentoBuzonRespuesta.desde(documentoBuzonRepository.saveAndFlush(buscado));
    }

    private DocumentoBuzon propio(String codigoContribuyente, Long idDocumento) {
        return documentoBuzonRepository.bloquearPropio(idDocumento, documentoDe(codigoContribuyente))
                .orElseThrow(() -> new RecursoNoEncontradoException("DOCUMENTO_NO_ENCONTRADO",
                        "No tiene un mensaje en su buzón con N.° " + idDocumento + "."));
    }

    private String documentoDe(String codigoContribuyente) {
        return contribuyenteService.obtenerPorCodigo(codigoContribuyente).getNumeroDocumento();
    }
}
