package pe.municipalidad.tributario.tramitepredio;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.common.DatoInvalidoException;
import pe.municipalidad.tributario.common.NoAutenticadoException;
import pe.municipalidad.tributario.common.RecursoNoEncontradoException;
import pe.municipalidad.tributario.common.ReglaNegocioException;
import pe.municipalidad.tributario.contribuyente.Contribuyente;
import pe.municipalidad.tributario.contribuyente.EstadoContribuyente;
import pe.municipalidad.tributario.documentos.DocumentoBuzon;
import pe.municipalidad.tributario.documentos.DocumentoBuzonRepository;
import pe.municipalidad.tributario.estadocuenta.EstadoCuentaFilaRespuesta;
import pe.municipalidad.tributario.gestiontributaria.EstadoCuenta;
import pe.municipalidad.tributario.gestiontributaria.Predio;
import pe.municipalidad.tributario.gestiontributaria.Titularidad;
import pe.municipalidad.tributario.integracion.samnet.SamnetGateway;
import pe.municipalidad.tributario.obligacion.TasaInteresMoratorio;
import pe.municipalidad.tributario.predios.RegistrarPredioSolicitud;
import pe.municipalidad.tributario.predios.RegistroPredioService;
import pe.municipalidad.tributario.seguridad.Usuario;
import pe.municipalidad.tributario.seguridad.UsuarioRepository;

/*
 * HU043 (RF-056): Ventanilla revisa las solicitudes de registro o traspaso de predio que envió el contribuyente
 * (HU046). Ve los adjuntos y la deuda del predio y la acepta, haciendo el registro o el traspaso en SAMNET solo por la
 * capa de servicios (RNF-001), o la rechaza con un motivo. En ambos casos el resultado, con la deuda existente del
 * predio, llega al buzón del contribuyente (RF-030) y queda registrado quién decidió y cuándo. La revisión es siempre
 * humana: no hay aceptación automática.
 */
@Service
@Transactional(readOnly = true)
public class RevisionSolicitudPredioService {

    static final String CONDICION_POR_DEFECTO = "PROPIETARIO";
    static final BigDecimal PORCENTAJE_POR_DEFECTO = BigDecimal.valueOf(100);

    private final SolicitudPredioRepository solicitudes;
    private final DocumentoBuzonRepository buzon;
    private final UsuarioRepository usuarios;
    private final ArchivoPredioService archivos;
    private final RegistroPredioService registroPredio;
    private final SamnetGateway samnet;
    private final TasaInteresMoratorio tim;
    private final Clock reloj;

    public RevisionSolicitudPredioService(SolicitudPredioRepository solicitudes, DocumentoBuzonRepository buzon,
            UsuarioRepository usuarios, ArchivoPredioService archivos, RegistroPredioService registroPredio,
            SamnetGateway samnet, TasaInteresMoratorio tim, Clock reloj) {
        this.solicitudes = solicitudes;
        this.buzon = buzon;
        this.usuarios = usuarios;
        this.archivos = archivos;
        this.registroPredio = registroPredio;
        this.samnet = samnet;
        this.tim = tim;
        this.reloj = reloj;
    }

    /* Sin estado, la bandeja trae las pendientes (EN_REVISION), de la más antigua a la más reciente. */
    public List<SolicitudPredioBandejaRespuesta> listar(EstadoSolicitudPredio estado) {
        return solicitudes.findByEstadoOrderByFechaEnvioAsc(estado == null ? EstadoSolicitudPredio.EN_REVISION : estado)
                .stream().map(SolicitudPredioBandejaRespuesta::desde).toList();
    }

    public SolicitudPredioRevisionRespuesta obtener(Long idSolicitud) {
        return detalle(buscar(idSolicitud));
    }

    public SolicitudPredioService.DescargaAdjunto descargar(Long idSolicitud, Long idAdjunto) {
        DocumentoAdjunto adjunto = buscar(idSolicitud).getAdjuntos().stream()
                .filter(a -> a.getIdAdjunto().equals(idAdjunto)).findFirst()
                .orElseThrow(() -> new RecursoNoEncontradoException("ADJUNTO_NO_ENCONTRADO",
                        "La solicitud N.° " + idSolicitud + " no tiene el adjunto N.° " + idAdjunto + "."));
        return new SolicitudPredioService.DescargaAdjunto(adjunto.getNombreOriginal(), adjunto.getTipoContenido(),
                archivos.leer(adjunto));
    }

    /*
     * Primero se valida todo y recién después se escribe en SAMNET, para no registrar un predio de una solicitud ya
     * cerrada. En un traspaso la deuda se calcula antes de cambiar de titular: es la que arrastra el predio.
     */
    @Transactional
    public SolicitudPredioRevisionRespuesta aceptar(Long idSolicitud, AceptarSolicitudPredio peticion,
            String nombreRevisor) {
        SolicitudPredio solicitud = buscarBloqueada(idSolicitud);
        solicitud.exigirEnRevision();
        Usuario revisor = revisor(nombreRevisor);
        Contribuyente contribuyente = contribuyenteDe(solicitud).orElseThrow(() -> new RecursoNoEncontradoException(
                "CONTRIBUYENTE_NO_ENCONTRADO",
                "SAMNET no registra al contribuyente de la solicitud N.° " + idSolicitud + "."));
        String condicion = peticion.condicionPropiedad() == null || peticion.condicionPropiedad().isBlank()
                ? CONDICION_POR_DEFECTO : peticion.condicionPropiedad().strip().toUpperCase();
        BigDecimal porcentaje = peticion.porcentajePropiedad() == null
                ? PORCENTAJE_POR_DEFECTO : peticion.porcentajePropiedad();

        String codigoPredio;
        DeudaPredio deuda;
        if (solicitud.getTipoTramite() == TipoTramitePredio.REGISTRO) {
            codigoPredio = registrar(solicitud, contribuyente, peticion.predio(), condicion, porcentaje);
            deuda = deudaDe(codigoPredio).orElse(DeudaPredio.SIN_DEUDA);
        } else {
            codigoPredio = solicitud.getCodigoPredio();
            Predio predio = predioDe(codigoPredio).orElseThrow(() -> new RecursoNoEncontradoException(
                    "PREDIO_NO_ENCONTRADO", "El predio " + codigoPredio + " no está registrado en SAMNET."));
            exigirActivo(contribuyente);
            exigirCambioDeTitular(predio, contribuyente);
            deuda = deudaDe(predio);
            samnet.traspasarPredio(codigoPredio,
                    new Titularidad(contribuyente.getCodigoContribuyente(), condicion, porcentaje));
        }

        LocalDateTime ahora = LocalDateTime.now(reloj);
        solicitud.aceptar(revisor, codigoPredio, deuda.total(), ahora);
        notificar(solicitud, "aceptada", ahora);
        return detalle(solicitudes.saveAndFlush(solicitud));
    }

    @Transactional
    public SolicitudPredioRevisionRespuesta rechazar(Long idSolicitud, String motivo, String nombreRevisor) {
        SolicitudPredio solicitud = buscarBloqueada(idSolicitud);
        solicitud.exigirEnRevision();
        Usuario revisor = revisor(nombreRevisor);
        BigDecimal deuda = solicitud.getCodigoPredio() == null ? null
                : deudaDe(solicitud.getCodigoPredio()).map(DeudaPredio::total).orElse(null);

        LocalDateTime ahora = LocalDateTime.now(reloj);
        solicitud.rechazar(revisor, motivo.strip(), deuda, ahora);
        notificar(solicitud, "rechazada", ahora);
        return detalle(solicitudes.saveAndFlush(solicitud));
    }

    /* HU036 valida que el contribuyente esté activo y que el código predial no exista antes de escribir. */
    private String registrar(SolicitudPredio solicitud, Contribuyente contribuyente,
            AceptarSolicitudPredio.DatosPredio datos, String condicion, BigDecimal porcentaje) {
        if (datos == null) {
            throw new DatoInvalidoException("DATOS_PREDIO_REQUERIDOS",
                    "Para aceptar un registro, ingrese los datos del predio que constan en los documentos.");
        }
        return registroPredio.registrar(new RegistrarPredioSolicitud(contribuyente.getCodigoContribuyente(),
                datos.codigoPredio(), datos.numeroCatastral(), datos.ubicacion(), datos.usoPredio(),
                solicitud.getTipoPredio(), datos.areaTerreno(), datos.baseImponible(), datos.fechaAdquisicion(),
                datos.zona(), condicion, porcentaje)).codigoPredio();
    }

    private static void exigirActivo(Contribuyente contribuyente) {
        if (contribuyente.getEstado() != EstadoContribuyente.ACTIVO) {
            throw new DatoInvalidoException("CONTRIBUYENTE_INACTIVO", "El contribuyente "
                    + contribuyente.getCodigoContribuyente() + " está inactivo y no puede recibir el predio.");
        }
    }

    private static void exigirCambioDeTitular(Predio predio, Contribuyente adquirente) {
        List<Titularidad> titulares = predio.getTitulares();
        boolean yaEsUnicoTitular = titulares.size() == 1
                && titulares.get(0).codigoContribuyente().equals(adquirente.getCodigoContribuyente());
        if (yaEsUnicoTitular) {
            throw new ReglaNegocioException("TRASPASO_SIN_CAMBIO",
                    "El contribuyente ya es el único titular del predio " + predio.getCodigoPredio() + ".");
        }
    }

    /* El resultado se deposita una sola vez en el buzón (SolicitudPredio 1 respuesta 0..1 DocumentoBuzon). */
    private void notificar(SolicitudPredio solicitud, String resultado, LocalDateTime ahora) {
        String tramite = solicitud.getTipoTramite() == TipoTramitePredio.REGISTRO ? "registro" : "traspaso";
        buzon.save(DocumentoBuzon.respuestaASolicitud(solicitud,
                "Solicitud de " + tramite + " de predio N.° " + solicitud.getIdSolicitud() + ": " + resultado,
                null, ahora));
    }

    private SolicitudPredioRevisionRespuesta detalle(SolicitudPredio solicitud) {
        Optional<Contribuyente> contribuyente = contribuyenteDe(solicitud);
        Optional<Predio> predio = solicitud.getCodigoPredio() == null ? Optional.empty()
                : predioDe(solicitud.getCodigoPredio());
        boolean enRevision = solicitud.getEstado() == EstadoSolicitudPredio.EN_REVISION;
        DeudaPredio deuda = enRevision ? predio.map(this::deudaDe).orElse(null) : null;

        return new SolicitudPredioRevisionRespuesta(solicitud.getIdSolicitud(), solicitud.getTipoTramite(),
                solicitud.getTipoPredio(), solicitud.getEstado(), solicitud.getFechaEnvio(),
                solicitud.isFirmaElectronica(), solicitud.getDocumentoContribuyente(),
                contribuyente.map(RevisionSolicitudPredioService::resumen).orElse(null),
                solicitud.getCodigoPredio(), predio.map(RevisionSolicitudPredioService::resumen).orElse(null),
                solicitud.getAdjuntos().stream()
                        .map(a -> SolicitudPredioRevisionRespuesta.AdjuntoRevision.desde(solicitud, a)).toList(),
                enRevision ? (deuda == null ? null : deuda.total()) : solicitud.getDeudaPredio(),
                deuda == null ? List.of() : deuda.obligaciones(),
                solicitud.getRevisor() == null ? null : solicitud.getRevisor().getNombreUsuario(),
                solicitud.getFechaRevision(), solicitud.getMotivoRechazo());
    }

    private Optional<DeudaPredio> deudaDe(String codigoPredio) {
        return predioDe(codigoPredio).map(this::deudaDe);
    }

    /*
     * Deuda existente del predio: insoluto de las cuotas con saldo de ese predio en la cuenta de cada titular actual.
     * El total es el insoluto, igual que en la constancia (HU020); cada fila trae además su interés moratorio.
     */
    private DeudaPredio deudaDe(Predio predio) {
        List<EstadoCuentaFilaRespuesta> pendientes = new ArrayList<>();
        for (Titularidad titular : predio.getTitulares()) {
            EstadoCuenta cuenta = samnet.obtenerEstadoCuenta(titular.codigoContribuyente());
            cuenta.getObligaciones().stream()
                    .filter(o -> predio.getCodigoPredio().equals(o.getCodigoPredio()))
                    .filter(o -> o.saldo().signum() > 0)
                    .map(o -> EstadoCuentaFilaRespuesta.desde(o, cuenta.obtenerFechaCorte(), tim.mensual()))
                    .forEach(pendientes::add);
        }
        BigDecimal total = pendientes.stream().map(EstadoCuentaFilaRespuesta::deuda)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new DeudaPredio(total, pendientes);
    }

    private Optional<Predio> predioDe(String codigoPredio) {
        return samnet.buscarPredio(codigoPredio, null).stream()
                .filter(p -> p.getCodigoPredio().equals(codigoPredio)).findFirst();
    }

    /* La solicitud guarda el DNI o RUC; SAMNET lo resuelve al contribuyente. */
    private Optional<Contribuyente> contribuyenteDe(SolicitudPredio solicitud) {
        String documento = solicitud.getDocumentoContribuyente();
        return samnet.buscarContribuyente(documento).stream()
                .filter(c -> documento.equals(c.getNumeroDocumento())).findFirst();
    }

    private static SolicitudPredioRevisionRespuesta.ContribuyenteResumen resumen(Contribuyente contribuyente) {
        return new SolicitudPredioRevisionRespuesta.ContribuyenteResumen(contribuyente.getCodigoContribuyente(),
                contribuyente.getNombreRazonSocial(), contribuyente.getTipoDocumento().name(),
                contribuyente.getNumeroDocumento(), contribuyente.getEstado().name());
    }

    private static SolicitudPredioRevisionRespuesta.PredioResumen resumen(Predio predio) {
        return new SolicitudPredioRevisionRespuesta.PredioResumen(predio.getCodigoPredio(), predio.getUbicacion(),
                predio.getZona(), predio.getUsoPredio(), predio.getTitulares().stream()
                        .map(t -> new SolicitudPredioRevisionRespuesta.Titular(t.codigoContribuyente(),
                                t.condicionPropiedad(), t.porcentajePropiedad()))
                        .toList());
    }

    private SolicitudPredio buscar(Long idSolicitud) {
        return solicitudes.findById(idSolicitud).orElseThrow(() -> noEncontrada(idSolicitud));
    }

    private SolicitudPredio buscarBloqueada(Long idSolicitud) {
        return solicitudes.bloquearPorId(idSolicitud).orElseThrow(() -> noEncontrada(idSolicitud));
    }

    private static RecursoNoEncontradoException noEncontrada(Long idSolicitud) {
        return new RecursoNoEncontradoException("SOLICITUD_NO_ENCONTRADA",
                "No existe la solicitud de predio N.° " + idSolicitud + ".");
    }

    private Usuario revisor(String nombreUsuario) {
        return usuarios.findByNombreUsuario(nombreUsuario)
                .filter(Usuario::isActivo)
                .orElseThrow(() -> new NoAutenticadoException("REVISOR_NO_IDENTIFICADO",
                        "Inicie sesión con un usuario interno activo."));
    }

    private record DeudaPredio(BigDecimal total, List<EstadoCuentaFilaRespuesta> obligaciones) {
        static final DeudaPredio SIN_DEUDA = new DeudaPredio(BigDecimal.ZERO, List.of());
    }
}
