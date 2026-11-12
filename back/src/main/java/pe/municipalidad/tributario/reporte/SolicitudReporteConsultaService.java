package pe.municipalidad.tributario.reporte;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.common.DatoInvalidoException;
import pe.municipalidad.tributario.reporte.SolicitudesReporteRespuesta.FrecuenciaReporte;
import pe.municipalidad.tributario.reporte.SolicitudesReporteRespuesta.SolicitudReporteItem;

@Service
@Transactional(readOnly = true)
public class SolicitudReporteConsultaService {

    private static final Set<Integer> TAMANIOS_PERMITIDOS = Set.of(10, 25, 50, 100);
    private final SolicitudReporteRepository solicitudes;
    private final EntityManager entityManager;

    public SolicitudReporteConsultaService(SolicitudReporteRepository solicitudes, EntityManager entityManager) {
        this.solicitudes = solicitudes;
        this.entityManager = entityManager;
    }

    public SolicitudesReporteRespuesta consultar(String usuario, TipoReporte tipo, LocalDate desde, LocalDate hasta,
            int pagina, int tamanio) {
        validar(desde, hasta, pagina, tamanio);
        String usuarioNormalizado = normalizarUsuario(usuario);
        LocalDateTime fechaDesde = desde == null ? null : desde.atStartOfDay();
        LocalDateTime fechaHasta = hasta == null ? null : hasta.plusDays(1).atStartOfDay();
        Specification<SolicitudReporte> filtros = filtros(usuarioNormalizado, tipo, fechaDesde, fechaHasta);
        Page<SolicitudReporte> resultado = solicitudes.findAll(filtros,
                PageRequest.of(pagina, tamanio, Sort.by(Sort.Order.desc("fechaHora"), Sort.Order.desc("idSolicitud"))));

        EnumMap<TipoReporte, Long> conteos = contarPorTipo(usuarioNormalizado, tipo, fechaDesde, fechaHasta);
        var frecuencias = java.util.Arrays.stream(TipoReporte.values())
                .map(reporte -> new FrecuenciaReporte(reporte, reporte.getTitulo(), conteos.getOrDefault(reporte, 0L)))
                .toList();
        var items = resultado.getContent().stream().map(this::item).toList();
        long usuarios = contarUsuariosDistintos(usuarioNormalizado, tipo, fechaDesde, fechaHasta);
        return new SolicitudesReporteRespuesta(resultado.getTotalElements(), usuarios, frecuencias, items,
                resultado.getNumber(), resultado.getSize(), resultado.getTotalPages());
    }

    private long contarUsuariosDistintos(String usuario, TipoReporte tipo, LocalDateTime desde, LocalDateTime hasta) {
        var cb = entityManager.getCriteriaBuilder();
        var consulta = cb.createQuery(Long.class);
        var raiz = consulta.from(SolicitudReporte.class);
        consulta.select(cb.countDistinct(raiz.get("usuario")))
                .where(condiciones(raiz, usuario, tipo, desde, hasta));
        return entityManager.createQuery(consulta).getSingleResult();
    }

    private EnumMap<TipoReporte, Long> contarPorTipo(String usuario, TipoReporte tipo, LocalDateTime desde,
            LocalDateTime hasta) {
        var cb = entityManager.getCriteriaBuilder();
        var consulta = cb.createTupleQuery();
        var raiz = consulta.from(SolicitudReporte.class);
        consulta.multiselect(raiz.get("tipoReporte"), cb.count(raiz))
                .where(condiciones(raiz, usuario, tipo, desde, hasta))
                .groupBy(raiz.get("tipoReporte"));
        EnumMap<TipoReporte, Long> conteos = new EnumMap<>(TipoReporte.class);
        entityManager.createQuery(consulta).getResultList().forEach(fila ->
                conteos.put(fila.get(0, TipoReporte.class), fila.get(1, Long.class)));
        return conteos;
    }

    private Predicate[] condiciones(Root<SolicitudReporte> raiz, String usuario, TipoReporte tipo,
            LocalDateTime desde, LocalDateTime hasta) {
        var cb = entityManager.getCriteriaBuilder();
        var condiciones = new java.util.ArrayList<Predicate>();
        if (usuario != null) {
            var asociado = raiz.join("usuario", JoinType.INNER);
            String busqueda = patron(usuario);
            condiciones.add(cb.or(cb.like(cb.lower(asociado.get("nombreUsuario")), busqueda),
                    cb.like(cb.lower(asociado.get("nombreCompleto")), busqueda)));
        }
        if (tipo != null) condiciones.add(cb.equal(raiz.get("tipoReporte"), tipo));
        if (desde != null) condiciones.add(cb.greaterThanOrEqualTo(raiz.get("fechaHora"), desde));
        if (hasta != null) condiciones.add(cb.lessThan(raiz.get("fechaHora"), hasta));
        return condiciones.toArray(Predicate[]::new);
    }

    private Specification<SolicitudReporte> filtros(String usuario, TipoReporte tipo, LocalDateTime desde,
            LocalDateTime hasta) {
        return (raiz, consulta, cb) -> {
            var condiciones = cb.conjunction();
            if (usuario != null) {
                var asociado = raiz.join("usuario", JoinType.INNER);
                String busqueda = patron(usuario);
                condiciones = cb.and(condiciones, cb.or(
                        cb.like(cb.lower(asociado.get("nombreUsuario")), busqueda),
                        cb.like(cb.lower(asociado.get("nombreCompleto")), busqueda)));
            }
            if (tipo != null) condiciones = cb.and(condiciones, cb.equal(raiz.get("tipoReporte"), tipo));
            if (desde != null) condiciones = cb.and(condiciones, cb.greaterThanOrEqualTo(raiz.get("fechaHora"), desde));
            if (hasta != null) condiciones = cb.and(condiciones, cb.lessThan(raiz.get("fechaHora"), hasta));
            return condiciones;
        };
    }

    private SolicitudReporteItem item(SolicitudReporte solicitud) {
        var usuario = solicitud.getUsuario();
        FormatoExportacion formato = solicitud.getFormatoExportacion();
        String operacion = formato == FormatoExportacion.NINGUNO ? "VISUALIZACION" : formato.name();
        return new SolicitudReporteItem(solicitud.getIdSolicitud(), usuario.getNombreUsuario(),
                usuario.getNombreCompleto(), solicitud.getTipoReporte(), solicitud.getTipoReporte().getTitulo(),
                solicitud.getFechaHora(), operacion, solicitud.getFiltrosAplicados());
    }

    private void validar(LocalDate desde, LocalDate hasta, int pagina, int tamanio) {
        if ((desde == null) != (hasta == null)) throw new DatoInvalidoException("PERIODO_INCOMPLETO",
                "Debe indicar la fecha inicial y la fecha final del periodo.");
        if (desde != null && desde.isAfter(hasta)) throw new DatoInvalidoException("PERIODO_INVALIDO",
                "La fecha inicial no puede ser posterior a la fecha final.");
        if (pagina < 0) throw new DatoInvalidoException("PAGINA_INVALIDA", "La página no puede ser negativa.");
        if (!TAMANIOS_PERMITIDOS.contains(tamanio)) throw new DatoInvalidoException("TAMANIO_INVALIDO",
                "El tamaño de página debe ser 10, 25, 50 o 100.");
    }

    private String normalizarUsuario(String usuario) {
        if (usuario == null || usuario.isBlank()) return null;
        String normalizado = usuario.trim().toLowerCase(Locale.ROOT);
        if (normalizado.length() > 200) throw new DatoInvalidoException("USUARIO_INVALIDO",
                "La búsqueda de usuario no puede superar 200 caracteres.");
        return normalizado;
    }

    private String patron(String usuario) {
        return usuario == null ? null : "%" + usuario + "%";
    }
}
