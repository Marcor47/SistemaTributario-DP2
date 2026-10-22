package pe.municipalidad.tributario.indicador;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.common.ConflictoException;
import pe.municipalidad.tributario.common.DatoInvalidoException;
import pe.municipalidad.tributario.common.RecursoNoEncontradoException;
import pe.municipalidad.tributario.common.ReglaNegocioException;

@Service
@Transactional(readOnly = true)
public class MetaIndicadorService {

    private final IndicadorRepository indicadorRepository;
    private final MetaIndicadorRepository metaIndicadorRepository;
    private final Clock reloj;

    public MetaIndicadorService(IndicadorRepository indicadorRepository,
            MetaIndicadorRepository metaIndicadorRepository, Clock reloj) {
        this.indicadorRepository = indicadorRepository;
        this.metaIndicadorRepository = metaIndicadorRepository;
        this.reloj = reloj;
    }

    public List<IndicadorRespuesta> listarIndicadoresActivos() {
        return indicadorRepository.findByActivoTrueOrderByNombreAsc().stream()
                .map(IndicadorRespuesta::desde)
                .toList();
    }

    public List<MetaIndicadorRespuesta> buscarMetas(String indicadorCodigo, Integer anio) {
        LocalDate hoy = LocalDate.now(reloj);
        return metaIndicadorRepository.buscar(indicadorCodigo, anio).stream()
                .map(meta -> MetaIndicadorRespuesta.desde(meta, hoy))
                .toList();
    }

    public MetaIndicadorRespuesta obtenerMeta(Long id) {
        return MetaIndicadorRespuesta.desde(buscarMetaExistente(id), LocalDate.now(reloj));
    }

    @Transactional
    public MetaIndicadorRespuesta registrarMeta(MetaIndicadorSolicitud solicitud) {
        Indicador indicador = buscarIndicadorActivo(solicitud.indicadorCodigo());
        Periodicidad periodicidad = indicador.getPeriodicidad();
        int anio = solicitud.anio();
        int numeroPeriodo = solicitud.numeroPeriodo();

        if (!periodicidad.esPeriodoValido(numeroPeriodo)) {
            throw new DatoInvalidoException("PERIODO_INVALIDO",
                    "El indicador " + indicador.getNombre() + " es " + periodicidad.name().toLowerCase()
                            + ": el número de periodo debe estar entre 1 y " + periodicidad.getPeriodosPorAnio()
                            + ".");
        }
        validarValorSegunUnidad(indicador, solicitud.valorMeta());

        LocalDate hoy = LocalDate.now(reloj);
        if (periodicidad.fechaFin(anio, numeroPeriodo).isBefore(hoy)) {
            throw periodoCerrado(periodicidad.etiqueta(anio, numeroPeriodo));
        }
        if (metaIndicadorRepository.existsByIndicadorCodigoAndAnioAndNumeroPeriodo(indicador.getCodigo(), anio,
                numeroPeriodo)) {
            throw metaDuplicada(indicador, periodicidad.etiqueta(anio, numeroPeriodo));
        }

        MetaIndicador meta = new MetaIndicador(indicador, anio, numeroPeriodo, solicitud.valorMeta(),
                limpiarObservacion(solicitud.observacion()), LocalDateTime.now(reloj));
        try {
            metaIndicadorRepository.saveAndFlush(meta);
        } catch (DataIntegrityViolationException ex) {
            /* Dos usuarios registraron la misma meta a la vez; la restricción única de la BD decide. */
            throw metaDuplicada(indicador, periodicidad.etiqueta(anio, numeroPeriodo));
        }
        return MetaIndicadorRespuesta.desde(meta, hoy);
    }

    @Transactional
    public MetaIndicadorRespuesta actualizarMeta(Long id, MetaIndicadorActualizacion actualizacion) {
        MetaIndicador meta = buscarMetaExistente(id);
        LocalDate hoy = LocalDate.now(reloj);

        /* Una meta de un periodo ya concluido sirve de referencia histórica y no se reescribe. */
        if (meta.fechaFinPeriodo().isBefore(hoy)) {
            throw periodoCerrado(meta.etiquetaPeriodo());
        }
        validarValorSegunUnidad(meta.getIndicador(), actualizacion.valorMeta());

        meta.actualizar(actualizacion.valorMeta(), limpiarObservacion(actualizacion.observacion()),
                LocalDateTime.now(reloj));
        return MetaIndicadorRespuesta.desde(meta, hoy);
    }

    @Transactional
    public void eliminarMeta(Long id) {
        /* Esta operación se restringirá al Administrador cuando se integre el control de acceso. */
        metaIndicadorRepository.delete(buscarMetaExistente(id));
    }

    private Indicador buscarIndicadorActivo(String codigo) {
        Indicador indicador = indicadorRepository.findById(codigo)
                .orElseThrow(() -> new RecursoNoEncontradoException("INDICADOR_NO_ENCONTRADO",
                        "No existe el indicador " + codigo + "."));
        if (!indicador.isActivo()) {
            throw new ReglaNegocioException("INDICADOR_INACTIVO",
                    "El indicador " + indicador.getNombre() + " está inactivo y no admite nuevas metas.");
        }
        return indicador;
    }

    private MetaIndicador buscarMetaExistente(Long id) {
        return metaIndicadorRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("META_NO_ENCONTRADA",
                        "No existe la meta con id " + id + "."));
    }

    private void validarValorSegunUnidad(Indicador indicador, BigDecimal valorMeta) {
        UnidadMedida unidad = indicador.getUnidadMedida();
        if (!unidad.admite(valorMeta)) {
            throw new DatoInvalidoException("VALOR_META_FUERA_DE_RANGO",
                    "El indicador " + indicador.getNombre() + " se mide en " + unidad.name().toLowerCase()
                            + ": la meta no puede superar " + unidad.getValorMaximo().toPlainString() + ".");
        }
    }

    private static ReglaNegocioException periodoCerrado(String periodo) {
        return new ReglaNegocioException("PERIODO_CERRADO",
                "El periodo " + periodo + " ya concluyó; su meta no puede registrarse ni modificarse.");
    }

    private static ConflictoException metaDuplicada(Indicador indicador, String periodo) {
        return new ConflictoException("META_DUPLICADA",
                "Ya existe una meta para " + indicador.getNombre() + " en el periodo " + periodo
                        + ". Modifique la existente.");
    }

    private static String limpiarObservacion(String observacion) {
        if (observacion == null || observacion.isBlank()) {
            return null;
        }
        return observacion.strip();
    }
}
