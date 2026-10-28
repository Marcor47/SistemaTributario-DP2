package pe.municipalidad.tributario.indicador;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.common.RecursoNoEncontradoException;
import pe.municipalidad.tributario.common.ReglaNegocioException;
import pe.municipalidad.tributario.etl.EstadoLote;
import pe.municipalidad.tributario.etl.LoteETL;
import pe.municipalidad.tributario.etl.LoteETLRepository;

/*
 * HU039 (RF-052): cada carga publicada del ETL se compara con las metas. Si el desvío desfavorable supera el
 * umbral de la meta aparece la alerta; si el indicador vuelve dentro del umbral, la alerta se desactiva.
 */
@Service
@Transactional(readOnly = true)
public class AlertaDesvioService {

    private static final Pageable SOLO_EL_MAS_RECIENTE = PageRequest.of(0, 1);

    private final AlertaDesvioRepository alertaDesvioRepository;
    private final ValorIndicadorRepository valorIndicadorRepository;
    private final MetaIndicadorRepository metaIndicadorRepository;
    private final LoteETLRepository loteETLRepository;
    private final Clock reloj;

    public AlertaDesvioService(AlertaDesvioRepository alertaDesvioRepository,
            ValorIndicadorRepository valorIndicadorRepository, MetaIndicadorRepository metaIndicadorRepository,
            LoteETLRepository loteETLRepository, Clock reloj) {
        this.alertaDesvioRepository = alertaDesvioRepository;
        this.valorIndicadorRepository = valorIndicadorRepository;
        this.metaIndicadorRepository = metaIndicadorRepository;
        this.loteETLRepository = loteETLRepository;
        this.reloj = reloj;
    }

    public List<AlertaDesvioRespuesta> listarActivas(String indicadorCodigo) {
        String filtro = indicadorCodigo == null || indicadorCodigo.isBlank() ? null : indicadorCodigo.strip();
        return alertaDesvioRepository.buscarActivas(filtro).stream()
                .map(AlertaDesvioRespuesta::desde)
                .toList();
    }

    @Transactional
    public EvaluacionLoteRespuesta evaluarLote(Long idLote) {
        LoteETL lote = loteETLRepository.findById(idLote)
                .orElseThrow(() -> new RecursoNoEncontradoException("LOTE_NO_ENCONTRADO",
                        "No existe el lote del ETL con id " + idLote + "."));
        if (lote.getEstado() != EstadoLote.PUBLICADO) {
            throw new ReglaNegocioException("LOTE_NO_PUBLICADO",
                    "El lote " + idLote + " está " + lote.getEstado()
                            + ": solo los lotes publicados se comparan con las metas.");
        }

        LocalDateTime ahora = LocalDateTime.now(reloj);
        List<ValorIndicador> valores = valorIndicadorRepository.buscarPorLote(idLote);
        List<AlertaDesvio> activas = new ArrayList<>();
        int valoresSinMeta = 0;
        int generadas = 0;
        int resueltas = 0;
        for (ValorIndicador valor : valores) {
            Optional<MetaIndicador> meta = buscarMetaDe(valor);
            if (meta.isEmpty()) {
                valoresSinMeta++;
                continue;
            }
            Efecto efecto = aplicar(meta.get(), valor, ahora);
            if (efecto.generada()) {
                generadas++;
            }
            if (efecto.resuelta()) {
                resueltas++;
            }
            efecto.alertaActiva().ifPresent(activas::add);
        }
        return new EvaluacionLoteRespuesta(lote.getIdLote(), lote.getFechaCorte(), valores.size(), valoresSinMeta,
                generadas, resueltas, activas.stream().map(AlertaDesvioRespuesta::desde).toList());
    }

    /* Al cambiar el valor o el umbral de una meta (HU009), su alerta se recalcula con el último valor publicado. */
    @Transactional
    public void reevaluarMeta(MetaIndicador meta) {
        ultimoValorPublicado(meta).ifPresent(valor -> aplicar(meta, valor, LocalDateTime.now(reloj)));
    }

    /* Las alertas se derivan de la meta; sin ella no tienen sentido y bloquearían su eliminación por la FK. */
    @Transactional
    public void eliminarAlertasDe(MetaIndicador meta) {
        alertaDesvioRepository.deleteByMeta(meta);
        alertaDesvioRepository.flush();
    }

    private Efecto aplicar(MetaIndicador meta, ValorIndicador valor, LocalDateTime ahora) {
        Optional<AlertaDesvio> vigente = alertaDesvioRepository.buscarActivaDeMeta(meta);

        /* Solo la carga más reciente decide la alerta; un lote antiguo evaluado tarde no la reemplaza. */
        if (!esUltimoValorPublicado(meta, valor)) {
            return new Efecto(vigente, false, false);
        }

        Optional<AlertaDesvio> nueva = AlertaDesvio.evaluar(meta, valor, ahora);
        if (vigente.isPresent() && nueva.isPresent() && mismaEvaluacion(vigente.get(), nueva.get())) {
            return new Efecto(vigente, false, false);
        }
        vigente.ifPresent(AlertaDesvio::desactivar);
        nueva.ifPresent(alertaDesvioRepository::save);
        return new Efecto(nueva, vigente.isEmpty() && nueva.isPresent(), vigente.isPresent() && nueva.isEmpty());
    }

    /* Reevaluar el mismo lote con la misma meta no debe duplicar la alerta. */
    private static boolean mismaEvaluacion(AlertaDesvio vigente, AlertaDesvio nueva) {
        return vigente.getValorIndicador().getIdValor().equals(nueva.getValorIndicador().getIdValor())
                && vigente.getValorMeta().compareTo(nueva.getValorMeta()) == 0
                && vigente.getDesvioPorcentual().compareTo(nueva.getDesvioPorcentual()) == 0;
    }

    private Optional<MetaIndicador> buscarMetaDe(ValorIndicador valor) {
        return metaIndicadorRepository.findByIndicadorCodigoAndAnioAndNumeroPeriodo(
                        valor.getIndicador().getCodigo(), valor.getAnio(), valor.getNumeroPeriodo())
                .filter(meta -> meta.getAlcance() == valor.getAlcance()
                        && Objects.equals(meta.getValorAlcance(), valor.getValorAlcance()));
    }

    private boolean esUltimoValorPublicado(MetaIndicador meta, ValorIndicador valor) {
        return ultimoValorPublicado(meta)
                .map(ultimo -> ultimo.getIdValor().equals(valor.getIdValor()))
                .orElse(false);
    }

    private Optional<ValorIndicador> ultimoValorPublicado(MetaIndicador meta) {
        return valorIndicadorRepository.buscarPublicadosDeMeta(meta.getIndicador().getCodigo(), meta.getAnio(),
                        meta.getNumeroPeriodo(), meta.getAlcance(), meta.getValorAlcance(), SOLO_EL_MAS_RECIENTE)
                .stream()
                .findFirst();
    }

    private record Efecto(Optional<AlertaDesvio> alertaActiva, boolean generada, boolean resuelta) {
    }
}
