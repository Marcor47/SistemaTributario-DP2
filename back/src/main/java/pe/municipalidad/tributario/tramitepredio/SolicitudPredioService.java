package pe.municipalidad.tributario.tramitepredio;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import pe.municipalidad.tributario.common.DatoInvalidoException;
import pe.municipalidad.tributario.common.RecursoNoEncontradoException;
import pe.municipalidad.tributario.contribuyente.ContribuyenteService;
import pe.municipalidad.tributario.gestiontributaria.TipoPredio;

/** HU046: solicitudes y requisitos; los documentos quedan cifrados en la carpeta privada configurada. */
@Service
public class SolicitudPredioService {
    private final SolicitudPredioRepository solicitudes;
    private final RequisitoDocumentalRepository requisitos;
    private final ContribuyenteService contribuyentes;
    private final ArchivoPredioService archivos;
    private final Clock reloj;

    public SolicitudPredioService(SolicitudPredioRepository solicitudes,
            RequisitoDocumentalRepository requisitos, ContribuyenteService contribuyentes,
            ArchivoPredioService archivos, Clock reloj) {
        this.solicitudes = solicitudes;
        this.requisitos = requisitos;
        this.contribuyentes = contribuyentes;
        this.archivos = archivos;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public List<RequisitoRespuesta> requisitos(TipoTramitePredio tramite, TipoPredio predio) {
        return requisitos.findByTipoTramiteAndTipoPredio(tramite, predio).stream()
                .map(RequisitoRespuesta::desde).toList();
    }

    @Transactional
    public SolicitudPredioRespuesta enviar(String codigoContribuyente, TipoTramitePredio tramite,
            TipoPredio tipoPredio, String codigoPredio, List<Long> idsRequisitos,
            List<MultipartFile> documentos) {
        if (idsRequisitos == null || documentos == null || idsRequisitos.isEmpty()
                || idsRequisitos.size() != documentos.size() || idsRequisitos.size() > 5
                || idsRequisitos.contains(null) || new HashSet<>(idsRequisitos).size() != idsRequisitos.size()) {
            throw new DatoInvalidoException("ADJUNTOS_INVALIDOS",
                    "Envíe entre 1 y 5 archivos, cada uno con un requisito distinto.");
        }
        List<RequisitoDocumental> aplicables = requisitos.findByTipoTramiteAndTipoPredio(tramite, tipoPredio);
        var porId = new java.util.HashMap<Long, RequisitoDocumental>();
        aplicables.forEach(r -> porId.put(r.getIdRequisito(), r));
        if (!porId.keySet().containsAll(idsRequisitos)) {
            throw new DatoInvalidoException("REQUISITO_INVALIDO", "Un requisito no corresponde al trámite.");
        }
        String documento = contribuyentes.obtenerPorCodigo(codigoContribuyente).getNumeroDocumento();
        SolicitudPredio solicitud = new SolicitudPredio(tramite, tipoPredio, documento,
                codigoPredio == null || codigoPredio.isBlank() ? null : codigoPredio.strip(), false);
        List<String> guardados = new ArrayList<>();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int estado) {
                if (estado != STATUS_COMMITTED) guardados.forEach(archivos::eliminar);
            }
        });
        try {
            for (int i = 0; i < documentos.size(); i++) {
                ArchivoPredioService.ArchivoGuardado guardado = archivos.guardar(documentos.get(i));
                guardados.add(guardado.ruta());
                solicitud.adjuntar(new DocumentoAdjunto(porId.get(idsRequisitos.get(i)), guardado.original(),
                        guardado.nombre(), guardado.tipo(), guardado.tamano(), guardado.ruta(), guardado.hash()));
            }
            solicitud.enviar(aplicables, LocalDateTime.now(reloj));
            return SolicitudPredioRespuesta.desde(solicitudes.saveAndFlush(solicitud));
        } catch (RuntimeException ex) {
            guardados.forEach(archivos::eliminar);
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public List<SolicitudPredioRespuesta> listar(String codigoContribuyente) {
        return solicitudes.findByDocumentoContribuyenteOrderByFechaEnvioDesc(documento(codigoContribuyente))
                .stream().map(SolicitudPredioRespuesta::desde).toList();
    }

    @Transactional(readOnly = true)
    public SolicitudPredioRespuesta obtener(String codigoContribuyente, Long id) {
        return SolicitudPredioRespuesta.desde(propia(codigoContribuyente, id));
    }

    @Transactional(readOnly = true)
    public DescargaAdjunto descargar(String codigoContribuyente, Long idSolicitud, Long idAdjunto) {
        DocumentoAdjunto adjunto = propia(codigoContribuyente, idSolicitud).getAdjuntos().stream()
                .filter(a -> a.getIdAdjunto().equals(idAdjunto)).findFirst()
                .orElseThrow(() -> new RecursoNoEncontradoException("ADJUNTO_NO_ENCONTRADO", "No existe ese adjunto."));
        return new DescargaAdjunto(adjunto.getNombreOriginal(), adjunto.getTipoContenido(), archivos.leer(adjunto));
    }

    private SolicitudPredio propia(String codigo, Long id) {
        String documento = documento(codigo);
        return solicitudes.findById(id)
                .filter(s -> s.getDocumentoContribuyente().equals(documento))
                .orElseThrow(() -> new RecursoNoEncontradoException("SOLICITUD_NO_ENCONTRADA",
                        "No existe esa solicitud de predio."));
    }

    private String documento(String codigo) {
        return contribuyentes.obtenerPorCodigo(codigo).getNumeroDocumento();
    }

    public record DescargaAdjunto(String nombre, TipoContenido tipo, byte[] contenido) {
    }
}
